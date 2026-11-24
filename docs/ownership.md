# Titularidad de snippets

API interna de Permissions. Snippets obtiene la identidad del contexto de la
operación y envía el ID del propietario o del actor, según corresponda.
Permissions persiste la relación sin autenticar la llamada ni consultar los
datos del snippet. [Acceso y uso compartido](access.md).

La URL entre contenedores es `http://permissions-service:8080`. Desde el host
local, si está publicado el puerto de desarrollo, es `http://localhost:8081`.

## Identificadores

- `snippetId` es el UUID de 36 caracteres con guiones generado por Snippets.
- `ownerId`, `actorId` y `recipientId` son cadenas no vacías y no compuestas
  solo por espacios. Se comparan exactamente. Pueden contener un identificador
  del entorno local como `dev-thiago` o un `sub` de Auth0.
- Codificá los IDs que viajan como parámetros de URL. Un `+` literal se
  representa como `%2B`.

## Registrar propietario

`PUT /ownership/550e8400-e29b-41d4-a716-446655440000` con
`Content-Type: application/json`:

```json
{"ownerId":"dev-thiago"}
```

Si es nuevo, devuelve `201 Created` con el encabezado
`Location: /ownership/550e8400-e29b-41d4-a716-446655440000`:

```json
{"snippetId":"550e8400-e29b-41d4-a716-446655440000","ownerId":"dev-thiago"}
```

Repetir el registro con el mismo propietario devuelve `200 OK` y el mismo
cuerpo. Un ID de propietario distinto para el mismo UUID devuelve
`409 Conflict` y conserva la titularidad registrada.

La clave primaria de PostgreSQL garantiza una sola relación por snippet
también con pedidos concurrentes. El éxito se devuelve después del commit.
Si un timeout deja el resultado incierto, repetí el pedido con el mismo UUID
y el mismo ID de propietario.

## Consultar propietario

`GET /ownership/{snippetId}` devuelve `200 OK` con la misma representación
del registro. Si falta el registro, devuelve `404 ownership-not-found`.

## Comprobar modificación

`GET /ownership/{snippetId}/can-modify?actorId=dev-thiago` devuelve
`200 OK`. Si el actor es el propietario:

```json
{"allowed":true}
```

Si es otro actor, incluso un destinatario con acceso compartido:

```json
{"allowed":false}
```

Un actor ausente o en blanco devuelve `400`. Si falta el registro de titularidad,
devuelve `404`. Una falla técnica devuelve `500`.

## Errores

Los errores usan `application/problem+json`. Ejemplo al intentar registrar
otro propietario para el mismo snippet:

```json
{"type":"urn:permissions:problem:owner-conflict","title":"Conflict","status":409,"detail":"The snippet already has a different owner.","instance":"/ownership/550e8400-e29b-41d4-a716-446655440000"}
```

| Estado HTTP | `type` | Significado |
| --- | --- | --- |
| 400 | `urn:permissions:problem:invalid-request` | UUID, identidad o cuerpo inválido, incluidos campos obligatorios ausentes. |
| 404 | `urn:permissions:problem:ownership-not-found` | Falta el registro de titularidad. |
| 409 | `urn:permissions:problem:owner-conflict` | Ya existe otro propietario para el UUID. |
| 500 | `urn:permissions:problem:technical-failure` | La operación falló. |
| Otros errores HTTP, como 405 o 415 | `urn:permissions:problem:http-error` | Error HTTP general, como método o representación no soportados. |

Usá `type` como identificador estable. `title` y `detail` describen el problema
para quien lo lee. `instance` identifica la ruta. Las respuestas de falla
técnica excluyen SQL, credenciales y otros detalles internos.

## Verificación

Flyway administra el esquema en la base propia de Permissions.
`OwnershipDocumentationTest` comprueba los ejemplos JSON con HTTP y PostgreSQL.
`./gradlew check` ejecuta la suite, que requiere JDK 21 y Docker para
Testcontainers.
