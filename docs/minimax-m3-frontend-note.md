# Client frontend note

This note outlines a client module structure that can be shared by both the plaintext messenger (`docs/minimax-m3.md`) and the E2EE messenger (`docs/minimax-m3-e2ee.md`), plus a broad implementation plan for getting there.

## Goal

The two messengers share the same UI and ViewModel layer. They differ only in the data layer (plaintext vs encrypted storage) and in the crypto layer (none vs libsignal). The "encryption at the boundary" pattern keeps the UI agnostic to what's below it.

## Module structure

```
:domain              shared: use cases, DTOs (Message, Conversation, User, ...)
:viewmodel           shared: per-screen state holders
:ui:cli              shared: stdin/stdout client
:ui:tui              shared: lanterna client
:ui:gui              shared: Compose Multiplatform Desktop client
:network:ktor        shared: Ktor client, envelope types, WebSocket
:server:plaintext    plaintext messenger's Ktor server
:server:e2ee         E2EE messenger's Ktor server
:storage:plain       plaintext messenger's data layer (plain SQLite)
:storage:encrypted   E2EE messenger's data layer (encrypted SQLite)
:crypto:libsignal    E2EE-only: libsignal wrapper
:client:plaintext    composition root: wires :domain + :viewmodel + :ui:* + :network:ktor + :storage:plain
:client:e2ee         composition root: wires :domain + :viewmodel + :ui:* + :network:ktor + :storage:encrypted + :crypto:libsignal
```

The shared modules depend only on `:domain` (and each other). The per-messenger composition roots wire up the right storage and (for E2EE) crypto modules.

## How sharing works

The UI depends on `:viewmodel` and `:domain` only. The `Message` type is identical in both messengers. The `ChatViewModel` is byte-for-byte identical. The only thing that changes between messengers is which `MessageRepository` is passed in at startup — plaintext or E2EE.

E2EE-specific UI (verification badges, safety numbers, device list, "rotate identity key", recovery flow) is feature-flagged: the plaintext build doesn't see this code; the E2EE build does. Everything else (conversation list, chat view, compose box, settings) is the same composable in both.

## Implementation plan

**Step 1 — Both messengers in parallel, shared UI from day 1 (4–6 weeks for v1 of both).**
Build the plaintext and E2EE messengers in lockstep, sharing `:domain`, `:viewmodel`, `:ui:cli`, `:ui:tui`, `:ui:gui`, and `:network:ktor` from Phase 0. Each messenger has its own `:storage:*` (`plain` or `encrypted`); the E2EE messenger adds `:crypto:libsignal`. The two composition roots (`:client:plaintext` and `:client:e2ee`) wire the right storage and crypto into the shared UI. E2EE-specific UI affordances (verification, safety numbers, identity rotation, recovery) are feature-flagged in the shared UI; `:client:e2ee` enables the flags, `:client:plaintext` leaves them off. The shared modules are populated incrementally as both messengers' work progresses — there is no separate "extract shared modules" step. The per-messenger cost is roughly: shared UI (done once) + per-messenger storage + per-messenger server endpoints + (E2EE only) `:crypto:libsignal`. The plaintext plan (`docs/minimax-m3.md`) and the E2EE plan (`docs/minimax-m3-e2ee.md`) follow this same Phase 0–7 structure, with per-messenger work called out per phase.

**Step 2 — Iterate on both.**
Each messenger now has its own roadmap: the plaintext one can grow UX polish quickly (no crypto to worry about); the E2EE one can grow crypto features (recovery, voice calls, multi-device — if ever revisited) without touching the shared UI. The two clients can share releases: same UI version, different data-layer versions.
