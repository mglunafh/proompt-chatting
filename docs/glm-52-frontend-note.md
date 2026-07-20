# GLM-52 Frontend note

## Gradle structure

```
settings.gradle.kts
include(":shared")              // domain DTOs shared across all frontends and both backends
include(":backend:api")         // ChatClient interface + event types (depends :shared)
include(":backend:plain")       // plaintext Ktor client + plain storage (depends :backend:api)
include(":backend:e2ee")        // libsignal + encrypted H2 (depends :backend:api)
include(":frontend:console")    // BufferedReader + ANSI (depends :backend:api)
include(":frontend:tui")        // Compose for Desktop terminal mode (depends :backend:api)
include(":frontend:gui")        // Compose for Desktop Swing (depends :backend:api)
include(":client-app")          // main(); wires backend + frontend by CLI flag
include(":server")              // Ktor server (separate from client, listed for context)
include(":shared-protocol")     // wire-protocol envelopes shared between client backends and server
```

### Dependency hygiene
- `:shared` and `:backend:api` have **zero backend-specific deps** (no libsignal, no DB driver, no HTTP client).
- `:frontend:*` modules depend on `:backend:api` — never on concrete backends. Compile-time enforcement.
- `:backend:plain` and `:backend:e2ee` never depend on `:frontend:*`.
- `:shared-protocol` (wire envelopes) is shared between server and both client backends *only*; frontends never see it.
- `:backend:api` exposes a `FakeChatClient` so each frontend module is testable in pure isolation.

## Capability advertising

```kotlin
interface ChatClient {
    val events: Flow<ClientEvent>
    val capabilities: ClientCapabilities
    // baseline ops...
}

interface E2eeCapabilities {
    suspend fun verifySafetyNumber(userId: UserId): SafetyNumber
    suspend fun resetSession(chatId: ChatId)
    // ...
}
```

`ChatClient.capabilities` tells the frontend what to show/enable. Plaintext backend: `{ e2ee = false }`; e2ee backend: `{ e2ee = true }` + `ChatClient is E2eeCapabilities`. Frontends hide/gray menus accordingly.

## Implementation notes

1. **Bake in the seam from M0.** Both docs currently say "Gradle multi-module: `shared`, `server`, `client`". Replace with the expanded module tree above. The cost of retrofitting the seam later is *much* higher than building it greenfield — every feature touches both sides.
2. **Move `FakeChatClient` into M0.** Lets frontend dev start before any backend exists. With fake events and canned responses, the console/TUI/GUI frontend can build and be tested against a stub instantly.
3. **Define `ChatClient` interface first, not last.** Lock the seam before any feature lands. The first vertical slice (M2 in both docs) updates the interface, both backends, and at least the console frontend simultaneously — proving the seam works.
4. **Tags: `[PLAIN]` and `[E2EE]` in the feature lists.** Every feature gets a backend-availability tag. Most are both; features tagged E2EE-only (safety numbers, key change alerts, session reset) are no-op in plain and hidden in the frontend UI. The `E2eeCapable` runtime check keeps the frontend code path branchless.
5. **Frontends beyond console land as separate calendar phases after core stability.** The console frontend stays the reference implementation; TUI/GUI land after each backend's M8. This keeps the server-side milestones focused and avoids deliverable sprawl.
6. **Wire-protocol module (`:shared-protocol`) owns the wire shapes only;** it's consumed by `:server`, `:backend:plain`, and `:backend:e2ee`. The backends translate between `WireEnvelope` (transport) and `ClientEvent` (frontend-facing DTO in `:backend:api`). Keeps the frontend backend-agnostic and wires a clear data-flow boundary that makes backends independently swappable.
7. **Client-local persistence is per-backend, not per-frontend.** Plaintext uses plain SQLite/H2; e2ee uses encrypted H2 with key held in the keystore. Frontend never touches storage directly — it only consumes `Flow<ClientEvent>` and calls interface methods.
8. **Tests live per-module.** `:frontend:*` tests use `FakeChatClient`; `:backend:*` tests can use a fake server or in-memory WS. CI runs each module's tests independently so a breakage in `frontend:tui` doesn't block `backend:e2ee`.
