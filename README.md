# Permissions Service

Servicio HTTP interno de Snippet Searcher. Registra quién es dueño de cada snippet, aplica los permisos de lectura y modificación y guarda con quién se comparte. Un acceso compartido concede lectura. La modificación y la posibilidad de compartir están reservadas al propietario.

Permissions persiste las relaciones en PostgreSQL mediante JDBC y Flyway administra las migraciones. Snippets autentica al usuario y pasa su identidad a Permissions por la red interna.

## API interna

| Operación | Rutas | Contrato |
| --- | --- | --- |
| Registrar o consultar el propietario y comprobar si puede modificar | `PUT /ownership/{snippetId}`, `GET /ownership/{snippetId}`, `GET /ownership/{snippetId}/can-modify` | [Ownership](docs/ownership.md) |
| Listar acceso, consultar acceso a un snippet y compartirlo | `GET /access`, `GET /ownership/{snippetId}/access`, `PUT /ownership/{snippetId}/shares/{recipientId}` | [Acceso y uso compartido](docs/access.md) |

Los contratos describen las respuestas, la idempotencia y los errores Problem Details. Sus ejemplos se verifican en los tests.

## Desarrollo local

Usá JDK 21 y el wrapper Gradle 9.3.0. El servicio usa la convención publicada `jjt.spring-service:0.3.0` cuando están definidas `GITHUB_ACTOR` y `GITHUB_TOKEN` con acceso de lectura a GitHub Packages. Si falta alguna variable y existe el clon hermano `../gradle-conventions`, Gradle usa esa copia local.

```powershell
.\gradlew.bat check
```

En Linux y macOS usá `./gradlew check`. Para correr solo la suite, usá la tarea `test`. Los tests de integración necesitan Docker para levantar PostgreSQL 18.6 con Testcontainers. Si PostgreSQL rechaza la zona horaria de Java en Windows, ejecutá esa prueba con `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC` en la terminal.

## Docker

Con Docker, BuildKit y las credenciales de GitHub Packages cargadas:

```bash
docker build --secret id=github_actor,env=GITHUB_ACTOR --secret id=github_token,env=GITHUB_TOKEN -t jjt-permissions:local .
```

El [Dockerfile](Dockerfile) copia las entradas de `bootJar` y reutiliza la caché de Gradle de BuildKit. Los secretos se montan durante la compilación. La imagen Temurin JRE 21 incluye `app.jar` y los archivos de New Relic, escucha en `8080` y corre como `10001:10001`. El stack configura la activación del agente en runtime.

Para probar el contenedor de forma aislada, usá [`.env.example`](.env.example) como configuración local y levantá PostgreSQL en la misma red:

```bash
docker network create permissions-local
docker run --rm -d --name permissions-db --network permissions-local --env-file .env.example postgres:18.6
```

Cuando `docker exec permissions-db pg_isready -h 127.0.0.1 -U permissions -d permissions` confirme que la base está lista:

```bash
docker run --rm -d --name permissions-service --network permissions-local --env-file .env.example -p 127.0.0.1:8081:8080 jjt-permissions:local
curl --fail http://localhost:8081/actuator/health
```

La respuesta esperada es HTTP 200 con `"status":"UP"`. Al terminar la prueba, detené ambos contenedores con `docker stop permissions-service permissions-db` y eliminá la red con `docker network rm permissions-local`. Para levantar el entorno completo, usá el [Compose de infraestructura](https://github.com/JJT-INGSIS/snippet-searcher-infra).

## CI/CD

El [pipeline](.github/workflows/pipeline.yml) usa `kotlin-service-pipeline.yml@v0.6.8`. Las PRs a `dev` y `main` ejecutan CI. Los pushes a `dev` publican en `ghcr.io/jjt-ingsis/permissions-service` después de CI y despliegan en DEV si `CD_ENABLED=true`. Una ejecución manual desde `dev` puede pedir publicación con `publish`.

En `main`, el despliegue requiere `CD_ENABLED=true` y `PROD_DEPLOY_ENABLED=true`. PROD promueve el digest verificado en DEV para el mismo árbol Git. La configuración, la evidencia y el rollback están en el [manual operativo de infraestructura](https://github.com/JJT-INGSIS/snippet-searcher-infra/blob/dev/docs/sni-25-cd.md).
