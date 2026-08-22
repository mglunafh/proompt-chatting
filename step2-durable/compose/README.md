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

## Server environment

| Variable                   | Default     |
|----------------------------|-------------|
| `DB_HOST`                  | `localhost` |
| `DB_PORT`                  | `5432`      |
| `DB_NAME`                  | `chatting`  |
| `DB_USER`                  | `chatting`  |
| `DB_PASSWORD`              | required    |
| `DB_POOL_MAX_SIZE`         | `10`        |
| `DB_CONNECTION_TIMEOUT_MS` | `5000`      |

Every variable is also accepted as `<NAME>_FILE` naming a file to read it
from.

Running the server from Gradle against this stack needs the password in the
environment, since the mount only exists inside a container:

```sh
DB_PASSWORD="$(cat step2-durable/compose/secrets/db_password)" \
  ./gradlew :step2-durable:server:run
```
