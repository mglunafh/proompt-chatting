# step1-simple

A minimal direct-message chat over a single WebSocket connection per client.
Identity is a name passed at connect time; the server holds an in-process
registry of connected names, and any client can send a message to any other
connected name.

## Modules

- `:step1-simple:shared` — protocol types and validation rules used by both
  sides (W-02, W-03).
- `:step1-simple:server` — a Ktor CIO server that hosts the WebSocket
  endpoint, the connection registry, and the message router.
- `:step1-simple:client` — a console client (Clikt-based) that connects, sends
  and receives, and exits on close.

## Run

```sh
./gradlew :step1-simple:server:run     # boots the CIO server on :8080
./gradlew :step1-simple:client:run     # runs the client main
```
