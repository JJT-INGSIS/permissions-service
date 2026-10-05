# Contrato de ownership

API interna de Permissions para Snippets. No autentica al caller: Snippets debe obtener la identidad del contexto de la operación y enviarla explícitamente. No se debe aceptar un owner arbitrario enviado por el frontend.

Base URL entre contenedores: `http://permissions-service:8080`. Desde el host, con el puerto de desarrollo publicado: `http://localhost:8081`.

## Identificadores

- `snippetId`: UUID en formato de 36 caracteres con guiones. Lo genera Snippets; Permissions conserva el mismo identificador.
- `ownerId` y `actorId`: strings obligatorios, no vacíos ni compuestos sólo por espacios. Se comparan exactamente: no se recortan, convierten a minúsculas ni interpretan. Por ejemplo, `dev-thiago` en desarrollo o un futuro subject de Auth0.
- Permissions no consulta la existencia del snippet en otro servicio.
- Los requests y responses exitosos usan `application/json`. Un JSON mal formado, un UUID inválido, campos obligatorios ausentes/nulos, un owner que no sea string o una identidad en blanco devuelve `400`.

## Registrar propietario

`PUT /ownership/550e8400-e29b-41d4-a716-446655440000`, con `Content-Type: application/json`:

```json
{"ownerId":"dev-thiago"}
```

Si es nuevo, responde `201 Created`, con `Location: /ownership/550e8400-e29b-41d4-a716-446655440000`:

```json
{"snippetId":"550e8400-e29b-41d4-a716-446655440000","ownerId":"dev-thiago"}
```

Repetir el mismo registro devuelve `200 OK` y el mismo body, sin duplicar datos. Registrar otro owner para ese UUID devuelve `409 Conflict` y conserva el original. El endpoint no transfiere ownership.

Ante concurrencia, PostgreSQL decide un único ganador mediante la clave primaria. Las solicitudes del mismo owner pueden reintentarse; las de otro owner reciben conflicto. La respuesta de éxito se envía después del commit.

## Consultar propietario

`GET /ownership/{snippetId}` devuelve `200` con la misma representación del registro. Si no hay relación de ownership, devuelve `404`.

## Comprobar modificación

`GET /ownership/{snippetId}/can-modify?actorId=dev-thiago`:

```json
{"allowed":true}
```

Otro actor recibe `200 OK`:

```json
{"allowed":false}
```

Un actor vacío o ausente devuelve `400`. Si no existe la relación, devuelve `404`. Una falla técnica devuelve `500`, nunca `allowed: false`. El valor del query parameter debe codificarse como URL; un `+` literal requiere `%2B`.

## Errores

Los errores HTTP usan `application/problem+json`. Por ejemplo, al registrar un owner diferente:

```json
{"type":"urn:permissions:problem:owner-conflict","title":"Conflict","status":409,"detail":"The snippet already has a different owner.","instance":"/ownership/550e8400-e29b-41d4-a716-446655440000"}
```

| Status | type | Significado |
| --- | --- | --- |
| 400 | `urn:permissions:problem:invalid-request` | Request inválido; revisar la integración que lo construye. |
| 404 | `urn:permissions:problem:ownership-not-found` | No hay un propietario registrado para ese snippet. |
| 409 | `urn:permissions:problem:owner-conflict` | El UUID ya pertenece a otro actor. |
| 500 | `urn:permissions:problem:technical-failure` | La operación falló; no asumir registro exitoso ni permiso denegado. |
| Otros errores HTTP, como 405/415 | `urn:permissions:problem:http-error` | Método o representación no soportados. |

`type` es el identificador estable para decidir comportamiento. `title` y `detail` son mensajes humanos y pueden cambiar. `instance` identifica la ruta de la solicitud. Los errores técnicos no incluyen SQL, credenciales ni detalles internos.

Si un request no obtiene respuesta, Snippets debe tratarlo como falla técnica. Tras un timeout de registro, puede repetir el mismo UUID y owner para resolver un resultado incierto de forma idempotente.

## Coordinación con Snippets y PrintScript

Snippets coordina la validación de código, almacenamiento, metadatos y registro de ownership; define cómo recuperar altas parciales. Permissions sólo persiste y consulta la relación.

PrintScript valida por `POST /validate`, con `code` y `version` obligatorios. Sólo un `200` con `valid: true` habilita continuar el guardado. Un `200` con `valid: false` indica código inválido; un `400`, `422`, `500` o ausencia de respuesta tiene otro significado. Esta API de Permissions no llama a PrintScript.

## Verificación

`sh ./gradlew check` ejecuta pruebas unitarias y HTTP contra PostgreSQL 18.6 con Testcontainers, además de ktlint y detekt. Requiere Docker disponible y JDK 21. Los ejemplos JSON de esta página se leen y comprueban contra la API en `OwnershipDocumentationTest`.

Flyway crea el esquema automáticamente en una base vacía y valida las migraciones al reiniciar. La relación vive en la base propia de Permissions. El usuario de base necesita permiso para crear la tabla y el historial de migraciones.

Las pruebas verifican idempotencia, concurrencia, conservación del propietario, rollback, persistencia tras reiniciar la aplicación y separación entre denegación y falla técnica.
