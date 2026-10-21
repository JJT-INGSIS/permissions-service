# Contrato de acceso y sharing — SNI-26 y SNI-28

API interna de Permissions para Snippets: qué snippets puede ver un actor, qué
relación tiene con uno y cómo se registra un share. SNI-26 acordó el contrato y
SNI-28 lo implementa. El contrato de ownership sigue en [ownership.md](ownership.md).

Los ejemplos JSON de esta página son tests: `AccessDocumentationTest` los envía a
la API real con PostgreSQL y compara las respuestas completas. Si el comportamiento
cambia y el ejemplo no, el build falla.

Lo usa el contrato público de Snippets para listado, detalle y sharing
([ui-integration.md de Snippets](https://github.com/JJT-INGSIS/snippets-service/blob/dev/docs/ui-integration.md)).

Base URL entre contenedores: `http://permissions-service:8080`. Desde el host:
`http://localhost:8081`.

## Reglas comunes

- Igual que ownership: no autentica al caller. Snippets envía la identidad del
  actor que obtuvo del contexto de la operación.
- `snippetId` es un UUID de 36 caracteres. `actorId` y `recipientId` son strings no
  blancos que se comparan exactamente, sin recortar ni pasar a minúsculas.
- Permissions no consulta si el snippet existe ni si está confirmado en Snippets.
- Los errores usan `application/problem+json` con `type`
  `urn:permissions:problem:<categoría>`, igual que ownership.
- Una falla técnica responde `500` y nunca equivale a una lista vacía, a
  `relation: none` ni a un acceso denegado.

## Relaciones

| `relation` | Significado |
| --- | --- |
| `owned` | El actor es el owner registrado. |
| `shared` | El owner le compartió el snippet al actor. |
| `none` | El snippet tiene owner, pero el actor no tiene acceso. |

Un share concede lectura. No concede modificar ni volver a compartir:
`can-modify` sigue respondiendo `true` solo para el owner.

## Snippets accesibles

`GET /access?actorId=dev-juan&relation=all`

| Parámetro | Obligatorio | Valores |
| --- | --- | --- |
| `actorId` | Sí | String no blanco, codificado como URL. |
| `relation` | No, default `all` | `all`, `owned`, `shared` |

Responde `200 OK`:

```json
{"actorId":"dev-juan","snippets":[{"snippetId":"0f8b3c1e-4a2d-4d3b-9b7a-2c6e5f1a9d40","ownerId":"dev-juan","relation":"owned"},{"snippetId":"7d2e9a4f-1b3c-4e5d-8f6a-0b1c2d3e4f50","ownerId":"dev-thiago","relation":"shared"}]}
```

- Devuelve **todos** los snippets accesibles con esa relación, ordenados por
  `snippetId`, sin paginar. Snippets filtra, ordena y pagina después, con sus
  metadatos.
- Un actor sin snippets recibe `snippets: []`.
- Puede incluir UUIDs que en Snippets siguen pendientes o no existen: Snippets los
  descarta.
- Para el volumen del TP alcanza. Con muchos snippets por actor haría falta un
  contrato paginado.

## Relación con un snippet

`GET /ownership/{snippetId}/access?actorId=dev-juan`

Responde `200 OK`:

```json
{"snippetId":"7d2e9a4f-1b3c-4e5d-8f6a-0b1c2d3e4f50","ownerId":"dev-thiago","relation":"shared"}
```

- `relation: none` cuando el actor no tiene acceso. Snippets lo traduce a `404`
  para no revelar que el snippet existe.
- Sin ownership registrado responde `404 ownership-not-found`, igual que `can-modify`.

## Compartir

`PUT /ownership/{snippetId}/shares/{recipientId}`, con `Content-Type: application/json`:

```json
{"actorId":"dev-juan"}
```

`actorId` es quien pide compartir. Permissions verifica que sea el owner y
registra el share en una sola sentencia SQL: no hay una ventana entre comprobar y
guardar.

Si es nuevo, responde `201 Created`:

```json
{"snippetId":"0f8b3c1e-4a2d-4d3b-9b7a-2c6e5f1a9d40","recipientId":"dev-thiago"}
```

Repetir el mismo share devuelve `200 OK` con el mismo body, sin duplicar datos.
Ante pedidos concurrentes, la clave primaria decide un único registro y los demás
reciben `200`. La respuesta se envía después del commit. Tras un timeout, Snippets
puede repetir el pedido para resolver el resultado.

Si el actor no es el owner, responde `403` con la relación que tiene, para que
Snippets elija entre `403` y `404`:

```json
{"type":"urn:permissions:problem:share-denied","title":"Forbidden","status":403,"detail":"Only the owner can share this snippet.","instance":"/ownership/0f8b3c1e-4a2d-4d3b-9b7a-2c6e5f1a9d40/shares/dev-ana","relation":"shared"}
```

## Errores

| Status | `type` | Significado |
| --- | --- | --- |
| 400 | `urn:permissions:problem:invalid-request` | UUID inválido, `actorId` ausente o en blanco, `relation` desconocida o body mal formado. |
| 403 | `urn:permissions:problem:share-denied` | El actor no es el owner. `relation` vale `shared` o `none`. |
| 404 | `urn:permissions:problem:ownership-not-found` | No hay owner registrado para ese snippet. |
| 422 | `urn:permissions:problem:invalid-recipient` | El owner intenta compartirse el snippet a sí mismo. |
| 500 | `urn:permissions:problem:technical-failure` | La operación falló; no asumir share registrado, lista vacía ni acceso denegado. |

Permissions no sabe si el destinatario existe: no es dueño de usuarios. Snippets
lo comprueba antes contra su directorio de desarrollo y, en el futuro, contra Auth0.

## Cómo se garantiza

| Garantía | Cómo |
| --- | --- |
| Solo el owner comparte | Una sola sentencia `INSERT … SELECT` inserta el share únicamente si `snippet_ownership` tiene a `actorId` como owner. Comprobar y guardar ocurren juntos: no hay una ventana entre las dos cosas. |
| Sin duplicados | Clave primaria `(snippet_id, recipient_id)` con `ON CONFLICT DO NOTHING`. Entre pedidos simultáneos iguales, uno recibe `201` y el resto `200`. |
| Sin IDs repetidos en la lista | La consulta devuelve una fila por ownership, unida al share del actor si existe. Un snippet no puede aparecer dos veces. |
| Un share no habilita edición | `can-modify` sigue comparando solo contra el owner. |
| Falla no es ausencia | Las fallas JDBC y de transacción se informan como `technical-failure`, nunca como lista vacía, `none` ni `share-denied`. |

La regla «solo el owner comparte y nadie se comparte a sí mismo» la decide el caso
de uso `ShareSnippet`. La persistencia solo inserta el share si el owner es el que
recibe; no decide autorización.

Si el actor pide compartirse el snippet a sí mismo, `ShareSnippet` no intenta el
registro: consulta su relación y responde `422` si es el owner o `403` si no lo es.
Cuando el registro no inserta porque el actor no es owner, la misma consulta de
relación elige el `relation` del `403`.

## Persistencia

La migración `V2__create_snippet_shares.sql` crea `snippet_shares` en la base de
Permissions:

| Columna | Tipo | Regla |
| --- | --- | --- |
| `snippet_id` | UUID | Referencia a `snippet_ownership`. |
| `recipient_id` | TEXT | No blanco. |

La clave primaria `(snippet_id, recipient_id)` evita shares duplicados. Un índice
sobre `recipient_id` sirve a la lista de compartidos y otro sobre
`snippet_ownership(owner_id)` a la de propios. No se guarda contenido, metadatos
ni perfiles de usuario. No existe revocación: US7 no la pide.

## Qué no cambia

`PUT /ownership/{snippetId}`, `GET /ownership/{snippetId}` y
`GET /ownership/{snippetId}/can-modify` conservan su contrato
([ownership.md](ownership.md)).

## Verificación

`sh ./gradlew check` ejecuta estas pruebas junto con las de ownership. Las que usan
HTTP o PostgreSQL requieren Docker para Testcontainers.

| Prueba | Qué comprueba |
| --- | --- |
| `AccessRelationTest` | La regla de relación: el owner gana sobre un share y las identidades se comparan exactas. |
| `ShareSnippetTest` | Cada resultado del caso de uso sin base de datos, incluido que compartirse a uno mismo no intenta registrar nada. |
| `AccessHttpTest` | Los tres endpoints: el destinatario lee sin poder modificar, `403` con su `relation`, `422`, `404`, listas propias/compartidas/todas sin repetidos y `400` ante entradas inválidas. |
| `ShareConcurrencyTest` | Ocho shares iguales simultáneos crean una sola fila; ocho intentos simultáneos de no-owners no crean ninguna. |
| `OwnershipPersistenceTest` | La migración V2 se aplica una vez y un share sigue visible después de reiniciar la aplicación. |
| `OwnershipFailureTest` y `JdbcOwnershipFailureTest` | Una base caída se informa como `technical-failure`, nunca como lista vacía, `none`, `share-denied` ni éxito. |
| `AccessDocumentationTest` | Los ejemplos JSON de esta página coinciden con las respuestas reales.
