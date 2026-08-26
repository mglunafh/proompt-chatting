# Implementation progress

- **W-06 Roster and edges** — the snapshot as the first frame after the upgrade, and join and leave broadcast to every other connected client. Two in-process clients per test. USR-04, USR-05.
- **W-05 Endpoint and upgrade** — the WebSocket route: take the name, validate it, register or refuse, run the read loop, answer a rejected client frame with a typed error frame. MSG-04, ST1-01.
- **W-04 Connection registry** — the `ConcurrentHashMap` of name to session, insert returning the roster snapshot in the same operation, removal, and refusal of a duplicate name. USR-04, USR-05, ST1-01.
- **W-03 Validation** — pure functions for body size, line count, C0 and C1 rejection, and the name rules. Table-driven tests. SEC-07, ST1-01.
- **W-02 Protocol module** — the command and event hierarchy as sealed types with their serializers, and the cap constants. Tested by JSON round-trip. MSG-04, SEC-07.
- **W-01 Build scaffolding** — a `shared` module under `:step1-simple` alongside its `server` and `client`
