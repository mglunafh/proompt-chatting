# Tech stack

## Libraries and services

- **Kotlin (JVM)** — implementation language for both server and client.
- **Gradle (Kotlin DSL)** — build tool for the multi-module project.
- **kotlinx-coroutines** — the concurrency model across server and client.
- **kotlinx.serialization** — JSON encoding for WebSocket frames and REST bodies.
- **Ktor server** — the web framework hosting both surfaces below.
- **Ktor WebSockets** — real-time transport for the live message stream.
- **Ktor client** — the JVM console client's networking, over `wss://` and `https://`.
- **PostgreSQL** — single instance, source of truth for users, conversations, and message history.
- **Exposed** — type-safe SQL access from Kotlin.
- **Flyway** — schema migrations under version control, from the first table onward.
- **Caffeine** — expiring in-memory maps: login failure counters, per-user rate-limit buckets, and the typing floor.
- **tika-core** — content-type sniffing for uploads; `tika-core` alone, never `tika-parsers`.
- **password4j (Argon2id)** — password hashing.
- **Clikt** — command and argument parsing for the client.
- **Mordant** — terminal styling for the line-based console client.
- **Mosaic** — Compose-for-terminal rendering for the full-screen TUI client.
- **Compose Desktop** (optional for GUI) — deferred pointer-driven frontend.
- **ktlint** (ktlint-gradle plugin) — code formatting, enforced in CI.
- **SQLite** — client-side local store: the session token and the message cache.
- **Exposed** (optional SQLDelight) — client-side query layer over SQLite.
- **JUnit 5** — test framework.
- **Ktor `testApplication`** — in-process HTTP + WebSocket surface tests.
- **Testcontainers (Postgres)** — integration tests against a real database.
- **MockK** — mocking for small external seams (fakes preferred at interface boundaries).
- **kotlinx-coroutines-test** — deterministic virtual-time testing of coroutines and flows.
- **Shadow JAR** (Gradle Shadow plugin) — self-contained fat JARs for server and clients.
- **Logback (SLF4J)** with **logstash-logback-encoder** — structured logging, JSON to stdout.
- **Micrometer** (`ktor-server-metrics-micrometer`) — metrics through a Prometheus registry, exposed at `GET /metrics`.
- **Docker Compose** — deployment and local development.
- **Caddy** — reverse proxy terminating TLS with automatic Let's Encrypt certificates.
- **Prometheus** — scrapes and stores the Micrometer series.
- **Grafana Loki** — log aggregation, fed by the Docker Loki logging driver rather than an agent.
- **Grafana** — a single dashboard over both data sources.

## Built without a dependency

- **In-process connection registry** — a `ConcurrentHashMap` of live sessions for routing and fan-out; no Redis, no external message broker.
- **In-memory ephemeral state** — presence is counted off the registry and the offline grace window is a coroutine `Job`; nothing ephemeral reaches Postgres ([notes-user-presence.md](notes-user-presence.md)).
- **Local filesystem for attachments** — no object storage, no CDN.
- **Manual DI** (maybe Koin later) — wiring via a composition root at each entry point.
