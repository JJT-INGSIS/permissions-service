# Permissions Service

Servicio HTTP de permisos de Snippet Searcher con Kotlin, Spring Boot y PostgreSQL mediante JDBC. Registra el propietario de cada snippet y comprueba si un actor puede modificarlo. Flyway crea y versiona el esquema; no se utiliza generación automática de tablas con Hibernate.

El contrato HTTP, las respuestas idempotentes y los errores Problem Details están documentados en [docs/ownership.md](docs/ownership.md), con ejemplos verificados en cada build. La API es interna y todavía no autentica al caller.

Usa `jjt.spring-service:0.2.0` de `gradle-conventions`, JDK 21 y el wrapper Gradle 9.3.0.

Gradle selecciona JDK 21 para su daemon mediante `gradle/gradle-daemon-jvm.properties`, aunque el Java predeterminado de la terminal sea otra versión compatible con el wrapper. Debe haber un JDK 21 instalado.

Si no están definidas ambas credenciales `GITHUB_ACTOR` y `GITHUB_TOKEN`, y existe `../gradle-conventions`, el build usa automáticamente esa copia local. En ese entorno alcanza con `sh ./gradlew build --no-daemon`. Con credenciales definidas, CI y los builds Docker siguen utilizando la convención publicada. En un clon sin la carpeta hermana se necesitan las credenciales de GitHub Packages.

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

Esta prueba independiente usa dos contenedores. El Compose compartido del proyecto corresponde al repositorio `infra`; este repositorio no incorpora otro Compose.

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

Para comprobar persistencia, registrar un owner mediante `PUT /ownership/{snippetId}`, recrear solamente el contenedor `permissions-service` con el mismo comando y verificarlo mediante `GET /ownership/{snippetId}`. También se puede recrear `permissions-db` conservando su volumen y consultar la misma relación. No cambiar a una versión mayor de PostgreSQL reutilizando directamente este volumen.

Para revisar que los secretos de build no están configurados en runtime:

```bash
docker image inspect --format '{{json .Config.Env}}' jjt-permissions:local
docker history --no-trunc jjt-permissions:local
```

Las credenciales de Packages no deben aparecer. Las variables y contraseñas de PostgreSQL sí forman parte de la configuración de los contenedores de desarrollo.

Los tests de contexto, HTTP y persistencia usan PostgreSQL 18.6 con Testcontainers. Docker debe estar iniciado; los contenedores temporales se eliminan automáticamente al finalizar el proceso de pruebas. Ejecutar con JDK 21 y credenciales de Packages configuradas:

```bash
sh ./gradlew check --no-daemon
```

También se puede verificar usando la convención clonada como carpeta hermana, sin descargar ese paquete:

```bash
sh ./gradlew check --no-daemon --include-build ../gradle-conventions
```

Las pruebas también verifican los ejemplos del contrato, registros concurrentes, fallas técnicas y migraciones. Para verificar el empaquetado junto con los checks, agregar `bootJar` al comando.

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

Clonar `infra`, `snippets-service`, `permissions-service` y `printscript-service` como carpetas hermanas. Desde el Compose ubicado en `infra`, la imagen de permisos se construye con contexto `../permissions-service` y su Dockerfile predeterminado. Levantar el entorno desde ese directorio con `docker compose up --build`.

El Compose debe suministrar:

- Secretos de build `github_actor` y `github_token`, a partir de las credenciales locales de Packages.
- `SPRING_DATASOURCE_URL=jdbc:postgresql://permissions-db:5432/permissions` y las variables de usuario y contraseña que correspondan a su base.
- Puerto publicado `127.0.0.1:8081:8080`.
- Una base `postgres:18.6`, con volumen propio montado en `/var/lib/postgresql`, y un healthcheck con `pg_isready`.
- Una dependencia `service_healthy` respecto de PostgreSQL antes de iniciar permisos.

Los demás contenedores llaman a permisos mediante `http://permissions-service:8080`. Su endpoint de salud es `/actuator/health`.

El usuario de PostgreSQL debe poder crear el esquema de ownership y el historial de Flyway. No se necesita conexión desde Permissions a Snippets o PrintScript.

`.gitattributes` garantiza LF en `gradlew` y scripts Linux. Después de actualizar un checkout previo con CRLF, restaurar `gradlew` desde Git únicamente si no tiene cambios locales que preservar.

## CI, publicación y branches — SNI-23

Las ramas por cambio nacen desde `dev` y vuelven mediante PR; la promoción es PR `dev` → `main`. `dev` es la default branch. Usar squash para cambios individuales y merge commit para promociones. Proteger dev/main con PR, CI requerido y actualización con la base, sin aprobación humana obligatoria.

`.github/workflows/pipeline.yml` define los triggers y llama a `kotlin-service-pipeline.yml@v0.3.1` de [github-workflows](https://github.com/JJT-INGSIS/github-workflows). Publicar ese tag antes de integrar los callers definitivos; para verificar el candidato, usar temporalmente su SHA siguiendo el README central.

| Evento | Resultado |
| --- | --- |
| PR a dev/main | CI con `build`; no publica |
| Push a dev | CI → publicar únicamente la imagen de permisos |
| Push a main | CI; promoción a prod pendiente de SNI-25 |
| Ejecución manual en dev/main | CI; publica solo si se activa `publish` |

`workflow_dispatch` está definido en la default branch, actualmente `dev`. Se conservan los IDs del check `verify / verify / build`; confirmar su nombre exacto en Actions antes de exigirlo. Si CI falla o se cancela, no se publica.

Paquete: `ghcr.io/jjt-ingsis/permissions-service`. El resumen informa SHA, Git tree, plataformas y digest. Usar `image-ref` (`imagen@sha256:...`) para descargar/desplegar; los tags `sha-<SHA completo>` y `run-<run_id>-<run_attempt>` sirven para localizar publicaciones. PostgreSQL y sus credenciales continúan siendo configuración externa; no se incluyen en la imagen.

`GH_PACKAGES_USER` y `GH_PACKAGES_READ_TOKEN` son secrets de dependencias, preferentemente de organización con acceso a este repo. Se reenvían como secretos BuildKit. La publicación usa el `GITHUB_TOKEN` automático del repo con `packages: write`, sin PAT adicional de escritura.

`DOCKER_PLATFORMS` es una variable de repositorio opcional; default `linux/amd64`. Confirmar la arquitectura con Thiago. Valores admitidos: `linux/amd64`, `linux/arm64` o `linux/amd64,linux/arm64`.

Preparar GitHub Environments `dev` limitado a dev y `prod` limitado a main. El contrato de SNI-25 define variables `SSH_HOST`, `SSH_USER`, `SSH_PORT` y secrets `SSH_PRIVATE_KEY`, `SSH_KNOWN_HOSTS`. Los valores reales se cargan/verifican con las VMs y el stack disponibles. Estos environments no configuran automáticamente el datasource ni el perfil de Spring.

Ver inputs/outputs, visibilidad de paquetes, permisos y orden de integración en el [README central](https://github.com/JJT-INGSIS/github-workflows/blob/main/README.md). Las primeras publicaciones y verificaciones del pipeline nuevo quedan pendientes de integrar/publicar la versión central.
