# step1-simple

A minimal direct-message chat over a single WebSocket connection per client.
Identity is a name passed at connect time; the server holds an in-process
registry of connected names, and any client can send a message to any other
connected name.

## Modules

- `:step1-simple:shared` — protocol types and validation rules used by both
  sides.
- `:step1-simple:server` — a Ktor CIO server that hosts the WebSocket
  endpoint, the connection registry, and the message router.
- `:step1-simple:client` — a console client (Clikt-based) that connects, sends
  and receives, and exits on close.

## Run

```sh
./gradlew :step1-simple:server:run                            # boots the CIO server on :8080
./gradlew :step1-simple:client:run --args="--name=alice"      # runs the client as alice
```

The client takes `--name` (required), `--host` (default `127.0.0.1`) and
`--port` (default `8080`).

## Typing into the client

Two sigils divide the input grammar:

- `@<recipient> <body>` — sends the body to the named recipient as a direct
  message. The server validates the recipient and the body and either delivers
  the message or replies with a typed `error` frame.
- `/<command>` — a client command:
  - `/exit` closes the connection.
  - `/help` prints the available sigils.
  - Anything else is refused locally as an unknown command (not sent to the
    server, so it is not mistaken for a malformed message).

Lines that start with neither sigil are ignored. The client exits when the
WebSocket closes or when stdin reaches end-of-input.
