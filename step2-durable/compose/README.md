# Deployment files for `step2-durable`

## Bringing the database up

```sh
cp secrets/db_password.example secrets/db_password   # then edit it
docker compose up -d db
docker compose ps                                    # wait for "healthy"
```

The data lives in the named volume `pgdata`.

## Secrets

Every secret the server takes is accepted as both `X` and `X_FILE`.
Setting both forms of one secret is a configuration error similar to
the official `postgres` image policy.

`secrets/` is ignored by git apart from the `.example` files.

## Server settings

| Variable                   | Property                   | Default     |
|----------------------------|----------------------------|-------------|
| `DB_HOST`                  | `db.host`                  | `localhost` |
| `DB_PORT`                  | `db.port`                  | `5432`      |
| `DB_NAME`                  | `db.name`                  | `chatting`  |
| `DB_USER`                  | `db.user`                  | `chatting`  |
| `DB_PASSWORD`              | `db.password`              | required    |
| `DB_POOL_MAX_SIZE`         | `db.pool.max-size`         | `10`        |
| `DB_CONNECTION_TIMEOUT_MS` | `db.connection-timeout-ms` | `5000`      |

Each is resolved from the first source that answers: a system property, then
the environment, then the file named by `-Dserver.config`, then the one
packaged in the jar, then the default above. Each also takes a `_FILE` /
`.file` spelling naming a file to read the value from. Full rules in
[notes-configuration.md](../../docs/general/notes-configuration.md).

Running the server from Gradle needs the password, since the mount only exists
inside a container. Either put it in the environment:

```sh
DB_PASSWORD="$(cat step2-durable/compose/secrets/db_password)" \
  ./gradlew :step2-durable:server:run
```

or keep a properties file and point at it, which is less to retype:

```properties
# step2-durable/server/server.properties
db.host=127.0.0.1
db.password.file=../compose/secrets/db_password
```

```sh
./gradlew :step2-durable:server:run -Dserver.config=server.properties
```

`-Dserver.config` is resolved against the running process's working directory,
and `gradlew run` sets that to `step2-durable/server` — hence the bare name
above rather than a path from the repository root. The `db.password.file` inside
the file is different: it anchors to the file's own directory, so it keeps
pointing at the same secret wherever the server is started from.
