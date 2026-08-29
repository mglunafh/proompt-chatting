# Implementation progress

- **W-10 Client commands** — `/exit` closes the socket, `/help` lists the `@` and `/` sigils, an unknown `/word` is refused locally so it never reaches the server's message-syntax path. ST1-02.
- **W-08 Client** — Clikt `--name`, `--host` and `--port`, the WebSocket connection at `/chat?name=<name>`, and concurrent send and receive loops that exit when the socket closes. MSG-04.
- **W-07 Message routing** — validate the send frame, write it to the recipient's socket and echo it to the sender, refuse a name that is not connected. MSG-01.
- **W-06 Roster and edges** — the snapshot as the first frame after the upgrade, and join and leave broadcast to every other connected client. Two in-process clients per test. USR-04, USR-05.
- **W-05 Endpoint and upgrade** — the WebSocket route: take the name, validate it, register or refuse, run the read loop, answer a rejected client frame with a typed error frame. MSG-04, ST1-01.
- **W-04 Connection registry** — the `ConcurrentHashMap` of name to session, insert returning the roster snapshot in the same operation, removal, and refusal of a duplicate name. USR-04, USR-05, ST1-01.
- **W-03 Validation** — pure functions for body size, line count, C0 and C1 rejection, and the name rules. Table-driven tests. SEC-07, ST1-01.
- **W-02 Protocol module** — the command and event hierarchy as sealed types with their serializers, and the cap constants. Tested by JSON round-trip. MSG-04, SEC-07.
- **W-01 Build scaffolding** — a `shared` module under `:step1-simple` alongside its `server` and `client`
