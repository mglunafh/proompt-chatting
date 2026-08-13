# Implementation progress 

- **W-03 Validation** — pure functions for body size, line count, C0 and C1 rejection, and the name rules. SEC-07, ST1-01.
- **W-02 Protocol module** — the client and server frame hierarchies as sealed types with their serializers, and the cap constants. Tested by JSON round-trip. MSG-04, SEC-07.
- **W-01 Build scaffolding** — a `shared` module under `:step1-simple` alongside its `server` and `client`, catalog entries for ktor-websockets, kotlinx.serialization, Clikt, Logback, ktlint, JUnit 5 and coroutines-test, and the `application` plugin on both the client and the server, which gains the CIO engine and an entry point. Carries no feature.
