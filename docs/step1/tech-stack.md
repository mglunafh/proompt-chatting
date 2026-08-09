# Step 1 tech stack

## Libraries and services

- **Kotlin (JVM)** — implementation language for both server and client.
- **Gradle (Kotlin DSL)** — build tool, with the version catalog holding every version.
- **kotlinx-coroutines** — the concurrency model: one read loop per socket, and the fan-out to connected clients.
- **kotlinx.serialization** — JSON encoding for the typed frames.
- **Ktor server core** — hosts the single WebSocket endpoint.
- **Ktor CIO engine (server)** — pure-Kotlin engine, no Netty.
- **ktor-server-websockets** — the WebSocket plugin.
- **ktor-serialization-kotlinx-json** — the WebSocket content converter, so frames are sent and received as typed objects rather than strings.
- **Ktor client core** — the console client's networking.
- **Ktor CIO engine (client)** — the same engine family as the server.
- **ktor-client-websockets** — client-side WebSocket support.
- **Clikt** — the client's name, host and port arguments.
- **Logback (SLF4J)** — Ktor's logging binding, plain text to stdout.
- **Gradle `application` plugin** — the client's `run` task.
- **ktlint** (ktlint-gradle plugin) — code formatting.
- **JUnit 5** — test framework.
- **Ktor `testApplication`** — in-process WebSocket surface tests.
- **kotlinx-coroutines-test** — deterministic testing of the read loops and fan-out.

## Built without a dependency

- **In-process connection registry** — a `ConcurrentHashMap` of connected name to session, serving routing and the roster alike; no database, no broker.
