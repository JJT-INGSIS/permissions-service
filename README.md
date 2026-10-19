# Permissions Service

Servicio HTTP de permisos de Snippet Searcher con Kotlin, Spring Boot y PostgreSQL mediante JDBC. Registra el propietario de cada snippet y comprueba si un actor puede modificarlo. Flyway crea y versiona el esquema; no se utiliza generación automática de tablas con Hibernate.

El contrato HTTP, las respuestas idempotentes y los errores Problem Details están documentados en [docs/ownership.md](docs/ownership.md), con ejemplos verificados en cada build. La API es interna y todavía no autentica al caller.

El contrato propuesto de acceso y sharing para listado, detalle y US7 está en [docs/access.md](docs/access.md) (SNI-26). Todavía no está implementado.

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

## CI/CD y branches — SNI-23 / SNI-25

Crear ramas cortas desde `dev`, abrir PR a dev y promover mediante PR dev → main.
Dev es la default branch; usar squash para cambios individuales y merge commit
para promociones. Main debe exigir PR y CI con base actualizada, aplicar las reglas
a administradores y no admitir bypass de pushes directos. El merge de esa PR es
la autorización humana; no se agrega otro approval en GitHub Environments.

El caller `.github/workflows/pipeline.yml` prepara
`kotlin-service-pipeline.yml@v0.5.0` de [github-workflows](https://github.com/JJT-INGSIS/github-workflows).
**Esta versión debe integrarse, publicarse y comprobarse antes de integrar el caller.**
El candidato se prueba por SHA siguiendo la
[guía central](https://github.com/JJT-INGSIS/github-workflows/blob/main/docs/sni-25-integration.md).

| Evento | Resultado |
| --- | --- |
| PR dev/main | `build` completo y agregador requerido; sin publicación ni despliegue |
| Push dev | CI → publicar imagen; con CD habilitado, desplegar y verificar dev |
| Push main | CI → recuperar digest verificado del mismo Git tree → prod, sin reconstruir |
| Manual dev | CI; publicación opcional o incluida por CD habilitado |
| Manual main | CI y reintento de promoción; activar `publish` falla |

CI fallido o cancelado bloquea CD. `Resultado requerido` solo comprueba el estado
de CI y no repite Gradle. Conservar el check `verify / verify / build` durante la
transición; exigir el agregador nuevo cuando aparezca y pase en GitHub.

Imagen pública: `ghcr.io/jjt-ingsis/permissions-service`. Usar siempre `imagen@sha256:...`;
los tags `sha-<SHA completo>` y `run-<run_id>-<run_attempt>` sirven para localizarla.
El resumen y las etiquetas registran origen, Git tree, digest e intento de build.
Prod exige coincidencia exacta de todo el repositorio, incluso README/workflows;
sin evidencia confiable falla. Dev puede haber avanzado al promover.

Secrets `GH_PACKAGES_USER` y `GH_PACKAGES_READ_TOKEN`: lectura de dependencias
Gradle y secretos BuildKit. GHCR usa `GITHUB_TOKEN` automático con `packages: write`;
CD agrega lectura de Actions/PR y `deployments: write`, sin otro PAT para las VMs.
`DOCKER_PLATFORMS` tiene default linux/amd64, compatible con las VMs x64.

En **este repo**, environments dev (solo dev) y prod (solo main): variables
`SSH_HOST`, `SSH_USER`, `SSH_PORT`; secrets `SSH_PRIVATE_KEY`, `SSH_KNOWN_HOSTS`.
Los resuelve el job final del workflow reutilizable. No son las contraseñas de
runtime ni activan el perfil Spring. Configurar una clave dedicada y host keys
verificadas con Thiago.

Habilitación gradual con variables **de repositorio**:

- Sin `CD_ENABLED=true`, continúa CI/publicación sin despliegue remoto.
- Habilitar dev después del bootstrap, credenciales y revisión fija de infra.
- Configurar `PROD_DEPLOY_ENABLED=true` solo después de verificar las reglas
  completas de main y prod. El 13/10/2026 se activó y se verificó `Do not allow
  bypassing the above settings` en main: PR, CI y base actualizada se exigen también
  a administradores. El despliegue automático a producción sigue deshabilitado.

El despliegue cambia solo esta aplicación y conserva bases y otras imágenes.
Una pérdida SSH se reconcilia con el journal de la VM; rollback mantiene la Action
roja y no revierte migraciones ni datos. Conservar contratos y esquemas compatibles
con la versión anterior. Una VM apagada produce fallo, sin encendido automático.

Ver [operación y recuperación SNI-25](https://github.com/JJT-INGSIS/snippet-searcher-infra/blob/dev/docs/sni-25-cd.md).
La integración por tag, resolución de environment secrets y el recorrido real
dev → prod quedan pendientes de la configuración y pruebas con VMs encendidas.
