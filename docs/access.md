# Acceso y uso compartido

API interna que usa Snippets para listar relaciones de acceso, consultar la
relación con un snippet y compartirlo. Snippets obtiene la identidad del actor
del contexto de la operación y verifica el destinatario en el directorio
configurado, incluido Auth0 cuando corresponde. Permissions conserva relaciones
por ID y no consulta el directorio ni el contenido del snippet.
[Identificadores y errores comunes](ownership.md).

| `relation` | Significado |
| --- | --- |
| `owned` | El actor es el propietario registrado. |
| `shared` | El propietario compartió el snippet con el actor. |
| `none` | El snippet tiene propietario y el actor carece de acceso. |

Compartir concede lectura. La modificación y la posibilidad de compartir
siguen reservadas al propietario.

## Snippets accesibles

`GET /access?actorId=dev-juan&relation=all`

`actorId` es obligatorio y se codifica como parámetro de URL. `relation` admite
`all` (predeterminado), `owned` o `shared`.

Responde `200 OK`:

```json
{"actorId":"dev-juan","snippets":[{"snippetId":"0f8b3c1e-4a2d-4d3b-9b7a-2c6e5f1a9d40","ownerId":"dev-juan","relation":"owned"},{"snippetId":"7d2e9a4f-1b3c-4e5d-8f6a-0b1c2d3e4f50","ownerId":"dev-thiago","relation":"shared"}]}
```

Permissions ordena los resultados por `snippetId` y devuelve todas las
relaciones que coincidan. Si no hay ninguna, devuelve `snippets: []`.
Snippets verifica el estado de cada snippet y aplica los filtros, el orden
y la paginación de la API pública.

## Relación con un snippet

`GET /ownership/{snippetId}/access?actorId=dev-juan`

Responde `200 OK` con la relación del actor:

```json
{"snippetId":"7d2e9a4f-1b3c-4e5d-8f6a-0b1c2d3e4f50","ownerId":"dev-thiago","relation":"shared"}
```

Sin acceso devuelve `relation: none`. Snippets lo traduce a `404` en su API
pública. Si falta el registro de titularidad, Permissions devuelve
`404 ownership-not-found`.

## Compartir

`PUT /ownership/{snippetId}/shares/{recipientId}` con
`Content-Type: application/json`:

```json
{"actorId":"dev-juan"}
```

La escritura condiciona el alta a que `actorId` sea el propietario.
Una relación nueva devuelve `201 Created`:

```json
{"snippetId":"0f8b3c1e-4a2d-4d3b-9b7a-2c6e5f1a9d40","recipientId":"dev-thiago"}
```

Repetir el pedido devuelve `200 OK` con el mismo cuerpo. PostgreSQL garantiza
una sola fila para la pareja `(snippet_id, recipient_id)` aun con pedidos
concurrentes. El éxito se informa después del commit. Si un timeout deja
dudas, Snippets puede repetir el mismo pedido.

Cuando el actor carece de permiso para compartir, Permissions devuelve
`403` y la relación de ese actor. Snippets usa la relación para elegir entre
`403` y `404` en la API pública:

```json
{"type":"urn:permissions:problem:share-denied","title":"Forbidden","status":403,"detail":"Only the owner can share this snippet.","instance":"/ownership/0f8b3c1e-4a2d-4d3b-9b7a-2c6e5f1a9d40/shares/dev-ana","relation":"shared"}
```

El propietario que intenta compartirse su propio snippet recibe `422`.
La API admite altas y consultas de acceso compartido.

## Errores

| Estado HTTP | `type` | Significado |
| --- | --- | --- |
| 400 | `urn:permissions:problem:invalid-request` | UUID, identidad, filtro o cuerpo inválido. Un destinatario en blanco también se rechaza. |
| 403 | `urn:permissions:problem:share-denied` | El actor carece de permiso para compartir. `relation` vale `shared` o `none`. |
| 404 | `urn:permissions:problem:ownership-not-found` | Falta el registro de titularidad. |
| 422 | `urn:permissions:problem:invalid-recipient` | El propietario eligió su propio ID como destinatario. |
| 500 | `urn:permissions:problem:technical-failure` | Una falla técnica impidió obtener un resultado confiable. |

## Verificación

`AccessDocumentationTest` comprueba los ejemplos JSON contra HTTP y
PostgreSQL. `./gradlew check` ejecuta la suite, que requiere JDK 21 y Docker
para Testcontainers.
