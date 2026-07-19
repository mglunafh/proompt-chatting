# Client architecture note

Guiding principle: **simplicity and clean modularization** in UI work —
the UI is not the main priority of the project.

## Goal

Reuse client code across two dimensions:

- engines: plaintext and E2EE messengers (both live in one git repo)
- frontends: console (line-based), TUI, possibly GUI

## Module layout (Gradle)

```text
:root
├── :plain:protocol           wire envelopes
├── :plain:server             Ktor application
├── :e2ee:protocol            wire envelopes
├── :e2ee:server              Ktor application
├── :client:api               ChatEngine contract: events, view models,
│                             Capabilities, EngineState
│                             (pure Kotlin — no toolkit, no transport)
├── :client:app-core          presentation layer: session/conversation
│                             state, event→state reduction, user intents,
│                             unread counts, notification decisions
│                             (pure Kotlin, Flow-based)
├── :client:engine:plaintext  ChatEngine over WS + JSON
├── :client:engine:e2ee       ChatEngine over WS + libsignal + encrypted H2
├── :client:frontend:console  line-based app (readln + Mordant)
├── :client:frontend:tui      terminal UI (Mordant), minimal scope
└── :client:frontend:gui      Compose for Desktop — parked/optional
```

## Dependency rules

- frontends → `:client:app-core` → `:client:api` ← engines
- engines never import UI; app-core never imports a UI toolkit;
  frontend code depends only on api/app-core types (the engine impl is
  wired in the frontend's `main` composition root)
- acid test: a new frontend can be written in a weekend implementing
  only rendering

## What is shared between what

- plaintext ↔ E2EE: everything above the engines (api, app-core, all
  frontends); engine differences are handled via `Capabilities` and
  nullable view-model fields
- console ↔ TUI: terminal rendering code (both Mordant)
- TUI ↔ GUI: no rendering code, but 100% of app-core

## Engine-specific UI without a plugin system

Four generic mechanisms cover engine-specific UI needs (E2EE unlock,
verification, key changes) — no plugin/screen-slot machinery:

- **Engine lifecycle state** — `EngineState = Locked | Ready | Failed`;
  `unlock(credentials: CharArray)` is generic auth (E2EE passphrase or
  plaintext login). Credentials as `CharArray`, not `String` — zeroed
  after use; Strings linger in heap.
- **Nullable verification metadata** — `ContactView.verification`
  (`Verified | Unverified | KeyChanged`) + `verificationFingerprint`;
  null for plaintext engines. Per-frontend rendering: text marker in
  console, colored glyph in TUI, icon in GUI.
- **Key change as event** — `ChatEvent.IdentityKeyChanged(contactId)`;
  prominent rendering is the frontend's whole job.
- **Ceremonies as commands** — `/fingerprints <contact>`,
  `/verify <contact>`; work identically in console and TUI.

A real plugin/screen-slot system is deferred (YAGNI); even future needs
like QR codes dissolve into generic frontend capabilities ("render this
string as QR" — the engine just supplies the string).

## Decisions (resolved for simplicity)

- **Key-change policy: warn-and-allow** — event + badge +
  `/trust <contact>` to reset; no send-blocking machinery. Blocking can
  be added later in app-core only, frontends unchanged.
- **Unlock: single passphrase, eager, at startup** — unlocks all
  configured profiles; lazy per-profile unlock only if startup ever
  annoys.
- **Verification: commands only** — no dedicated screens in any frontend.
- **Console frontend is the reference implementation**, not a stepping
  stone: it ships with M1 and *is* the client until a concrete need
  arises.
- **TUI: minimal scope** — scrollback, input line, channel list, status
  markers; time-boxed; thin renderer over app-core.
- **GUI: parked** — the module is created only when someone wants it.
- **No speculative UI work** — any logic a UI feature needs goes into
  app-core, never into a frontend; this keeps all frontends in sync
  feature-wise by construction.

## Implementation plan notes

- Create `:client:api` and `:client:app-core` at M1, but expect API
  churn until the TUI lands (middle ground between premature abstraction
  and painful retrofit).
- Keep protocol types out of UI code from day one — the discipline is
  free, the premature interface is not.
- Mordant only (Mosaic considered and rejected: experimental, API churn).
- Multiple server profiles live in app-core → every frontend gets them
  for free.
- A FakeEngine emitting scripted events enables UI development and tests
  without a server.
