# Configuration notes

How the server resolves a setting. Why secrets arrive as mounted files is SEC-03
in [feature-set.md](feature-set.md).

## Sources

Highest first. The first source that answers supplies the value; the search stops there.

1. **System property** — `-Ddb.host=…`, one invocation.
2. **Environment variable** — `DB_HOST=…`, the deployment's contract.
3. **Named properties file** — `db.host=…`, path given by `-Dserver.config`.
4. **Packaged properties file** — `/server.properties` on the classpath, shipped in the jar.
5. **Declared default** — from the setting registry.

Both files may be present at once and are consulted per setting: a name the
named file does not answer still falls through to the packaged one.

The packaged file travels inside the artifact, so it may hold only values that
are correct on every machine that runs it — no secrets, and no paths, which
resolve against a working directory it cannot know.

Each setting is declared once, in an enum carrying both spellings, its type, its
default (absent = required) and whether it is a secret.

## Resolution

Every setting takes a second spelling suffixed for the file form: `DB_HOST_FILE`
in the environment, `db.host.file` as a property.

```
resolve(setting):
    for source in [systemProperty, environment, namedFile, packagedFile]:  # precedence order
        direct = source[setting.name]                       # DB_HOST     / db.host
        path   = source[setting.fileName]                   # DB_HOST_FILE / db.host.file

        if direct and path:  fail "both forms set in <source>"
        if path:             return read(path).trimEnd("\r\n")
        if direct:           return direct
        # neither: fall to the next source

    return setting.default ?: fail "<NAME> is required; set it or <NAME>_FILE"

resolveAll():
    collect every failure across all settings, then fail once with all of them
```

Two consequences worth stating:

- A source answers in **either** form, and answering wins outright — `DB_PASSWORD_FILE` in the environment beats `db.password` in the file, and the two are never compared.
- Both forms together are an error **only within one source**, which is the ambiguity the `postgres` image also refuses.
- A relative path written in a named properties file resolves against **that file's own directory**, not the working directory, so moving the file does not silently change what it points at. Paths from a system property or the environment resolve as given.

## Registry

| Setting         | Environment                | Property                   | Type   | Default            |
|-----------------|----------------------------|----------------------------|--------|--------------------|
| host            | `DB_HOST`                  | `db.host`                  | string | `localhost`        |
| port            | `DB_PORT`                  | `db.port`                  | int    | `5432`             |
| database        | `DB_NAME`                  | `db.name`                  | string | `chatting`         |
| user            | `DB_USER`                  | `db.user`                  | string | `chatting`         |
| password        | `DB_PASSWORD`              | `db.password`              | string | *required, secret* |
| pool size       | `DB_POOL_MAX_SIZE`         | `db.pool.max-size`         | int    | `10`               |
| connect timeout | `DB_CONNECTION_TIMEOUT_MS` | `db.connection-timeout-ms` | int    | `5000`             |

## Examples

One source per block, highest first.

System property:

```sh
java -Ddb.host=staging.internal -Dserver.config=./server.properties -jar server.jar
```

Environment, under Compose:

```yaml
environment:
  DB_HOST: db
  DB_PASSWORD_FILE: /run/secrets/db_password
secrets: [db_password]
```

Named file:

```properties
db.host=127.0.0.1
db.password.file=../compose/secrets/db_password
```

Packaged `/server.properties`:

```properties
db.pool.max-size=20
```

## Errors

- Every problem is reported at once, not the first one only.
- The boot report lists each setting, its value and which source supplied it; secrets render as `***`.
- A missing required setting names both spellings.
- A `-Dserver.config` path that cannot be read is an error.

## Not included

Read once at boot: no reload, no remote configuration service, and no validation
beyond presence and type.
