# step1-simple

The first learning step. A minimal client/server split demonstrating:

- A Ktor **server** application with a single `GET /` route that returns
  `"Hello, World!"`, bootable via the `application` plugin's `run` task on the
  CIO engine (port 8080).
- A Ktor **client** module with a `fun main()` that prints `"Hello, World!"` to stdout.
- A **shared** module for the serialization model both sides will depend on
  (empty for now).

**Group ID:** `dev.burufi.chatting.simple`
**Base package:** `dev.burufi.chatting.simple`

## Modules

| Module path                  | Type        | Description                                           |
|------------------------------|-------------|-------------------------------------------------------|
| `:step1-simple:shared`       | Library     | Shared serialization model (empty for now)            |
| `:step1-simple:server`       | Application | Ktor `Application.module()` with one route + `main()` |
| `:step1-simple:client`       | Application | `fun main()` printing `Hello, World!`                 |

## Package layout

```
dev/burufi/chatting/simple/
├── shared/                 (reserved for the shared protocol module)
├── server/
│   └── Server.kt    fun Application.module() { routing { get("/") { call.respondText("Hello, World!") } } }
│                     fun main() { embeddedServer(CIO, port = 8080) { module() }.start(wait = true) }
└── client/
    └── Client.kt    fun main() { println("Hello, World!") }
```

## Build

```sh
./gradlew build                          # from the repo root: build shared, server, and client
./gradlew :step1-simple:shared:build     # shared only
./gradlew :step1-simple:server:build     # server only
./gradlew :step1-simple:client:build     # client only
```

## Run

```sh
./gradlew :step1-simple:server:run    # boots CIO on :8080, serves GET / -> "Hello, World!"
./gradlew :step1-simple:client:run     # prints "Hello, World!"
```
