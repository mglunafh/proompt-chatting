# Step 1 tasks

- **W-02 Protocol module** — the command and event hierarchy as sealed types with their serializers, and the cap constants. Tested by JSON round-trip. MSG-04, SEC-07.
- **W-03 Validation** — pure functions for body size, line count, C0 and C1 rejection, and the name rules. Table-driven tests. SEC-07, ST1-01.
- **W-04 Connection registry** — the `ConcurrentHashMap` of name to session, insert returning the roster snapshot in the same operation, removal, and refusal of a duplicate name. Tested without Ktor, concurrent inserts included. USR-04, USR-05, ST1-01.
- **W-05 Endpoint and upgrade** — the WebSocket route: take the name, validate it, register or refuse, run the read loop, answer a rejected client frame with a typed error frame. First unit under `testApplication`. MSG-04, ST1-01.
- **W-06 Roster and edges** — the snapshot as the first frame after the upgrade, and join and leave broadcast to every other connected client. Two in-process clients per test. USR-04, USR-05.
- **W-07 Message routing** — validate the send frame, write it to the recipient's socket and echo it to the sender, refuse a name that is not connected. MSG-01.
- **W-08 Client** — Clikt name, host and port, the connection, concurrent send and receive loops, and exit on close. MSG-04.
- **W-09 Client rendering** — inert rendering of bodies and names on the way to the terminal, the `run` task and the README. SEC-09.

----

- **W-01 Build scaffolding** — a `shared` module under `:step1-simple` alongside its `server` and `client`, catalog entries for ktor-websockets, kotlinx.serialization, Clikt, Logback, ktlint, JUnit 5 and coroutines-test, and the `application` plugin on both the client and the server, which gains the CIO engine and an entry point. Carries no feature.
