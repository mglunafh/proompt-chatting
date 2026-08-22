# Implementation progress

- **W-31 Server configuration** — the `Setting` registry carrying spellings, the type, the default and the secret flag, and the resolver over the four sources in precedence order: system property, environment, the file named by `-Dserver.config`, then the one packaged in the jar. OPS-07.
- **W-02 Postgres and Compose** — Docker Compose stack under `step2-durable/compose`: a Postgres service with a named volume, a health check and its password as a mounted secret. The server's connection settings read from the environment, every secret taken as both `X` and `X_FILE`, and the pool opened once at boot and closed on shutdown. Carries no feature.
- **W-01 Build scaffolding** — Gradle submodule structure for step2-durable, added relevant entries to version catalogs.
