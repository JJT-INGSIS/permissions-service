# Permissions Service

Servicio HTTP de permisos de Snippet Searcher con Kotlin, Spring Boot y conexión a PostgreSQL mediante Spring Data JPA. Todavía no define entidades, tablas ni endpoints de negocio. Hibernate no crea ni modifica el esquema automáticamente.

Usa `jjt.spring-service:0.2.0` de `gradle-conventions`, JDK 21 y el wrapper Gradle 9.3.0.

```powershell
.\gradlew.bat check
.\gradlew.bat bootRun
```

`check` ejecuta tests, ktlint y detekt, y genera el reporte JaCoCo. Gradle resuelve la convención publicada en GitHub Packages mediante `GITHUB_ACTOR` y `GITHUB_TOKEN` con acceso de lectura.

Para probar cambios de `gradle-conventions` sin publicarlos, con ese repositorio clonado al lado de este:

```powershell
.\gradlew.bat check --include-build ..\gradle-conventions
```

## Git hooks

Una vez por clon:

```powershell
.\gradlew.bat installGitHooks
```

- `pre-commit`: formatea con ktlint los archivos Kotlin en stage, los vuelve a agregar al commit y corre detekt.
- `pre-push`: corre `check` completo, con los tests.

El detalle está en el README de `gradle-conventions`.

## Docker: construir la imagen

Requisitos: Docker con BuildKit y una cuenta/token con acceso de lectura al paquete `jjt.spring-service:0.2.0` en GitHub Packages. No es necesario instalar Java o Gradle en el host: el build usa JDK 21 y el wrapper del proyecto; la imagen final usa JRE 21.

Desde esta carpeta, configurar las credenciales en la terminal (bash o zsh). El token se ingresa sin mostrarlo y no se escribe en el comando ni en `.env`:

```bash
export GITHUB_ACTOR="tu-usuario-github"
printf 'Token de GitHub Packages: '
read -r -s GITHUB_TOKEN
printf '\n'
export GITHUB_TOKEN

docker build \
  --secret id=github_actor,env=GITHUB_ACTOR \
  --secret id=github_token,env=GITHUB_TOKEN \
  -t jjt-permissions:local .

unset GITHUB_TOKEN
```

Los dos secretos son obligatorios y se montan únicamente durante el comando de compilación. No se usan `ARG` ni `ENV` para conservarlos en la imagen. La imagen final contiene el JAR ejecutable y corre con UID/GID `10001`.

`bootJar` genera `build/libs/app.jar`. El build Docker empaqueta la aplicación; las verificaciones de calidad se ejecutan por separado con `check`.

## Docker: levantar permisos con PostgreSQL 18.6

Esta prueba independiente usa dos contenedores. El Compose del proyecto vivirá solamente en `snippets-service`; este repositorio no incorpora otro Compose.

```bash
cp .env.example .env
docker network create permissions-local
docker volume create permissions-local-data

docker run -d --name permissions-db \
  --network permissions-local \
  --env-file .env \
  --mount type=volume,source=permissions-local-data,target=/var/lib/postgresql \
  --health-cmd='pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  --health-interval=5s --health-timeout=5s --health-retries=10 \
  --health-start-period=10s \
  postgres:18.6
```

Antes de iniciar permisos, consultar la salud de PostgreSQL hasta que indique `healthy`:

```bash
docker inspect --format '{{.State.Health.Status}}' permissions-db
```

Después iniciar la aplicación:

```bash
docker run -d --name permissions-service \
  --network permissions-local \
  --env-file .env \
  -p 127.0.0.1:8081:8080 \
  jjt-permissions:local

docker logs -f permissions-service
```

Docker carga `.env` porque se indica `--env-file`; no lo lee automáticamente. Las variables `POSTGRES_*` inicializan la base y las variables `SPRING_DATASOURCE_*` configuran la conexión de la aplicación. Los valores de usuario, contraseña y nombre de base deben coincidir. Las contraseñas del ejemplo son exclusivamente para desarrollo local.

PostgreSQL 18.6 usa `PGDATA=/var/lib/postgresql/18/docker`; el volumen se monta en `/var/lib/postgresql`. La base no publica un puerto en el host. Permisos se conecta a `permissions-db:5432` mediante la red interna y el host accede a la aplicación en `localhost:8081`.

## Comprobaciones

```bash
curl --fail http://localhost:8081/actuator/health
docker exec permissions-service id
docker logs permissions-db
docker logs permissions-service
```

La salud debe devolver HTTP `200` y un JSON con `"status":"UP"`, sin detalles de conexión. Spring Boot también puede incluir los nombres de los grupos de salud `liveness` y `readiness`. La salud global incluye la comprobación del datasource. `/actuator/env` y los demás endpoints de Actuator no están expuestos. `id` debe informar UID/GID `10001`.

Para comprobar una caída y recuperación de la base:

```bash
docker stop permissions-db
curl -i http://localhost:8081/actuator/health
docker start permissions-db
curl -i http://localhost:8081/actuator/health
```

Con la base detenida, la salud debe terminar en HTTP `503` y `DOWN`; la consulta puede tardar hasta el timeout de conexión. Tras iniciar la base, esperar que esté `healthy` y comprobar que la aplicación vuelve a `UP`.

Para comprobar persistencia, ejecutar `SELECT version()` y crear un dato de prueba en PostgreSQL, retirar y recrear solamente el contenedor `permissions-db` con el mismo comando y volumen, y verificar que el dato sigue presente. No cambiar a una versión mayor de PostgreSQL reutilizando directamente este volumen.

Para revisar que los secretos de build no están configurados en runtime:

```bash
docker image inspect --format '{{json .Config.Env}}' jjt-permissions:local
docker history --no-trunc jjt-permissions:local
```

Las credenciales de Packages no deben aparecer. Las variables y contraseñas de PostgreSQL sí forman parte de la configuración de los contenedores de desarrollo.

Los tests de contexto y HTTP usan H2 únicamente en el perfil `test`. Ejecutar con JDK 21 y credenciales de Packages configuradas:

```bash
sh ./gradlew check --no-daemon
```

También se puede verificar usando la convención clonada como carpeta hermana, sin descargar ese paquete:

```bash
sh ./gradlew check --no-daemon --include-build ../gradle-conventions
```

H2 no sustituye la verificación de conexión con PostgreSQL 18.6.

## Detener y limpiar

Para detener los contenedores conservándolos junto con los datos:

```bash
docker stop permissions-service permissions-db
```

Para retirarlos y eliminar la red, conservando el volumen:

```bash
docker rm permissions-service permissions-db
docker network rm permissions-local
```

Para eliminar también **todos los datos de esta base local**, después de retirar los contenedores:

```bash
docker volume rm permissions-local-data
```

Las variables `POSTGRES_*` inicializan un volumen vacío. Cambiar `.env` no cambia el usuario ni la contraseña de una base ya inicializada.

## Contrato para el Compose central

Clonar `snippets-service` y `permissions-service` como carpetas hermanas. Desde un `compose.yaml` ubicado en `snippets-service`, la imagen de permisos se construye con contexto `../permissions-service` y su Dockerfile predeterminado.

El Compose debe suministrar:

- Secretos de build `github_actor` y `github_token`, a partir de las credenciales locales de Packages.
- `SPRING_DATASOURCE_URL=jdbc:postgresql://permissions-db:5432/permissions` y las variables de usuario y contraseña que correspondan a su base.
- Puerto publicado `127.0.0.1:8081:8080`.
- Una base `postgres:18.6`, con volumen propio montado en `/var/lib/postgresql`, y un healthcheck con `pg_isready`.
- Una dependencia `service_healthy` respecto de PostgreSQL antes de iniciar permisos.

Los demás contenedores llaman a permisos mediante `http://permissions-service:8080`. Su endpoint de salud es `/actuator/health`.
