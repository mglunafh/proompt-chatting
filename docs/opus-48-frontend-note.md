# Frontend note — writing the UI once

Goal: write the console, TUI, and GUI frontends **once**, and have them work
against either backend — the plaintext messenger or the E2EE one. The frontends
should never know which they are talking to.

This is viable because the thing a frontend actually consumes is identical in
both variants: a message with a sender, a conversation, a timestamp, and body
text. Whether that body arrived as plaintext or came out of a Double Ratchet is
the engine's business, not the UI's.

The change is small: `:client:core` stops being a single module and becomes
**one interface with two implementations**.

## Module structure

```
:protocol            — wire DTOs (envelope shape differs per variant)

:client:api          — ChatEngine interface + domain model   ← the UIs depend on this, and only this
:client:core-plain   — implements ChatEngine, plaintext backend
:client:core-e2ee    — implements ChatEngine, E2EE backend (libsignal lives here)

:client:console      — Clikt, line-based
:client:tui          — Mosaic
:client:gui          — Compose Desktop (deferred)

:server              — unchanged by any of this
```

Dependency edges — the only rule that matters:

```
:client:console ─┐
:client:tui     ─┼─→ :client:api ←─ :client:core-plain
:client:gui     ─┘                ←─ :client:core-e2ee
```

A frontend depends on **`:client:api` only**. It never depends on a core
implementation, on `:protocol`, or on any crypto library.

`settings.gradle.kts`:

```kotlin
include(
    ":protocol",
    ":client:api",
    ":client:core-plain",
    ":client:core-e2ee",
    ":client:console",
    ":client:tui",
    ":client:gui",
    ":server",
)
```

## The engine interface

`:client:api` holds the interface and the domain model — no implementation, no
dependencies beyond coroutines. `ChatEvent` is the engine's own domain event,
one layer above the wire and not a server frame.

```kotlin
interface ChatEngine {
    val events: Flow<ChatEvent>              // messages, presence, typing — already decrypted
    suspend fun send(conversation: Id, text: String)
    suspend fun login(user: String, password: String)
    suspend fun loadMore(conversation: Id, before: Id): List<Message>

    // null in plaintext — the UI simply doesn't show these
    val verification: Verification?
    suspend fun unlock(passphrase: String): Boolean   // no-op in plaintext
}
```

Two nullable / no-op members cover everything that is E2EE-only. A frontend shows
a "verify contact" entry **only if `verification != null`**, and prompts for a
passphrase only if the engine asks for one. That is one `if` per UI, not a
capability framework.

## Notes

- **Introduce `:client:api` from M0.** The cost today is a single interface file.
  Retrofitting it after three frontends are written against a concrete class means
  touching all three. This is the one decision here with a bad reversal cost.

- **The E2EE-only surfaces are exactly two:** contact verification (safety
  numbers, key-change warnings) and the keystore passphrase unlock at startup.
  Everything else — sending, receiving, presence, typing, unread, search — is the
  same call from the UI's point of view.

- **Search needs nothing special.** Same UI, different implementation behind the
  engine: a server-side `tsvector` query in one, a local index in the other. The
  frontend calls one method either way.

- **Delivery receipts need nothing special.** A nullable receipt state on a
  message: populated in E2EE (where store-and-forward already requires a
  per-recipient ack), possibly absent in plaintext. The UI renders it if present.

- **Where `main` lives.** Simplest is a `main` in each frontend module that wires
  up a concrete engine. That does mean the frontend module can *see* a core
  implementation on its compile path. If you want "a frontend never touches a key"
  enforced by the build graph rather than by discipline, move `main` into thin
  `:client:app-console` / `:client:app-tui` modules that depend on both a frontend
  and a core. Worth doing only if the guarantee is wanted; it is otherwise pure
  module overhead.

- **The `:protocol` envelope differs between variants** (plaintext body vs.
  ciphertext + prekey types). This is a core concern, never a UI concern — split
  `:protocol` only if the two engines end up sharing enough to justify it. Do not
  split it on the frontends' behalf; they do not depend on it.

## Deliberately not built

Keeping the abstraction shallow on purpose. Explicitly rejected:

- A capability-flag system or feature-probe API — two nullable members do the job.
- Separate `:protocol:common` / `:protocol:plain` / `:protocol:e2ee` modules.
- A `historyScope` enum — there is nothing to model.
- Per-variant frontend modules or per-variant UI code of any kind. If a variant
  ever needs genuinely different UI, that is a signal the engine interface is
  leaking, not a reason to fork the frontend.
