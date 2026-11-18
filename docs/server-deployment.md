# Server Java version and deployment

The Play backend is a standalone fat JAR. The supplied
[Compose file](../deployment/server/docker-compose.yml) runs that JAR in a published
JRE image; there is no Dockerfile or Compose `build:` step.

## Which Java runs where?

| Scope | Configuration |
| --- | --- |
| Local sbt build / `deployAll` | The JVM chosen by the sbt launcher or IDE. The build does not pin `javaHome`; use JDK 21 to match CI and deployment. |
| GitHub builds/tests | Java 21 in [tests.yml](../.github/workflows/tests.yml) |
| Deployed Play server | `image: eclipse-temurin:21-jre-alpine` for service `edusquirrel` in Compose; its command executes the image's `java -jar /app/server.jar` |

There is no `java4shell` reference in the repository. If that tool changes your
shell's Java or `JAVA_HOME`, it can affect a locally launched sbt or `java -jar`.
An IDE may use a separately configured sbt JDK. Neither changes Java inside the
container: host Java is not mounted or selected by Compose.
The repository's `.jvmopts` configures heap size and garbage collection, not the Java version.

Check the build launcher with `java -version` and `sbt about` (including sbt's
startup banner). On the deployment host, from the directory containing the deployed
Compose file and `.env`, check the actual runtime with:

```sh
docker compose exec edusquirrel java -version
```

## Deploy a new server JAR

From the repository, with JDK 21:

```sh
sbt buildServerDeploy
```

This assembles the server and copies it to `artifacts/newest/server.jar`, stable
and history. `deployAll` builds the client and worker too; neither task uploads files
or restarts a remote server. The scripts under `deployment/deploy-all.*` are placeholders.

The supplied bind mount reads
`/home/andre/javaserver/artifacts/newest/server.jar` on the deployment host. Adjust
that path in Compose for another installation. Upload the artifact as a temporary
file, then rename it so the running JVM cannot read a partially copied JAR:

```sh
# On the build machine; replace USER@HOST with your deployment host:
scp artifacts/newest/server.jar USER@HOST:/home/andre/javaserver/artifacts/newest/server.jar.next
```

Then on the server, from its deployed Compose directory:

```sh
mv /home/andre/javaserver/artifacts/newest/server.jar.next /home/andre/javaserver/artifacts/newest/server.jar
docker compose up -d --no-deps --force-recreate edusquirrel
docker compose ps
docker compose logs --tail=100 edusquirrel
```

Recreation mounts the replacement file and starts a new JVM. Rebuilding an image
with `docker compose build` does not rebuild this JAR because the service has no
build definition. Database settings and secrets come from the deployed `.env`;
source changes or `deployAll` alone do not update a running installation.

## Update the Java runtime

To obtain the newest Java 21 patch image and recreate only the backend:

```sh
docker compose pull edusquirrel
docker compose up -d --no-deps --force-recreate edusquirrel
docker compose exec edusquirrel java -version
```

For a different Java major version, change the image tag and verify the application
and build JDK against it before deployment. A moving `21-jre-alpine` tag permits
patch updates; pinning an image digest gives reproducible runtime contents but makes
updates explicit. `docker compose down` / `up` recreates containers, but does not
compile the JAR or necessarily pull a newer cached image. It is unnecessary for an
ordinary backend artifact/runtime update; network changes require recreation of the
network too, as described in the [database/proxy guide](database/README.md).
