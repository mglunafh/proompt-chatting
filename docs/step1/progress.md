# Implementation progress

- **W-03 Validation** — pure functions for body size, line count, C0 and C1 rejection, and the name rules. Table-driven tests. SEC-07, ST1-01.
- **W-02 Protocol module** — the command and event hierarchy as sealed types with their serializers, and the cap constants. Tested by JSON round-trip. MSG-04, SEC-07.
- **W-01 Build scaffolding** — a `shared` module under `:step1-simple` alongside its `server` and `client`
