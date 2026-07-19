# Kotlin E2EE messenger, general info

## Tech Stack

### Server

**Core**
- Kotlin (JVM) + Coroutines
- Ktor — async web framework with WebSocket support
- Gradle (Kotlin DSL)

**Persistence**
- PostgreSQL — primary store (ciphertext + metadata only; no plaintext)
- Exposed or jOOQ — type-safe SQL
- Flyway — schema migrations
- Caffeine — in-process state (rate limit counters, ephemeral session state)

**Auth / Security**
- JWT (access + refresh) — nimbus-jose-jwt
- bcrypt — password hashing
- Ktor Auth — auth pipeline
- Web Push + VAPID — browser notifications (when applicable)

**Real-time / Messaging**
- Ktor WebSockets — envelope fan-out only, no plaintext
- In-process pub/sub for routing

**Observability**
- Logback with JSON encoder
- Micrometer + Prometheus + Grafana

**Build / Quality**
- detekt / ktlint
- Kotest + MockK + Testcontainers

**Deploy**
- Docker + Docker Compose
- Caddy (or nginx) — reverse proxy + automatic HTTPS (Let's Encrypt)
- Nightly `pg_dump` + rclone to Backblaze B2

**Server stores only:**
- Auth credentials and the device's key metadata (identity key fingerprint, signed prekey, one-time prekeys)
- Message envelopes (`sender_device_id`, `recipient_device_id`, `ciphertext`, header) — no `body` column
- Encrypted attachment blobs (decryption key held by client only)
- Group membership and per-member sender keys
- Server-side encrypted backup blobs (decryptable only with the user's passphrase)

### Client (JVM, modular)

**Architecture**
- Gradle multi-module, layered: UI → ViewModels → Domain (use cases) → Data (repositories) → (Network + Storage). Crypto sits *below* the boundary, not above the UI.
- Modules (matching the frontend note's layout — see `docs/minimax-m3-frontend-note.md`):
  - `:domain` — DTOs and use cases (`Message`, `Conversation`, `User`); the **same types** the plaintext messenger sees
  - `:viewmodel` — per-screen state holders
  - `:ui:cli`, `:ui:tui`, `:ui:gui` — swappable UI entry points shared by both messengers
  - `:network:ktor` — Ktor client, envelope types, WebSocket
  - `:storage:encrypted` — E2EE-only encrypted SQLite cache
  - `:crypto:libsignal` — E2EE-only libsignal wrapper (X3DH, Double Ratchet, sender keys, identity)
  - `:client:e2ee` — composition root: wires `:domain + :viewmodel + :ui:* + :network:ktor + :storage:encrypted + :crypto:libsignal`
- The "encryption at the boundary" rule: `:domain` exposes only post-decryption DTOs — the same `Message` / `Conversation` / `User` types the plaintext messenger sees. Raw envelope bodies, ciphertext, auth claims, and prekey bundles live in `:network:ktor` / `:storage:encrypted` and never leak into `:domain`. Only `:crypto:libsignal` ever holds plaintext in conjunction with ciphertext; the boundary is the repository interface in `:domain`. If a DTO in `:domain` ever has to know whether a message is plaintext or ciphertext, the boundary is wrong.
- E2EE-specific UI (verification badges, safety numbers, identity key rotation, recovery flow) is feature-flagged in `:ui:*` and `:viewmodel`: the flags are wired on in `:client:e2ee`, off in `:client:plaintext`. The shared UI code is the same composable in both — only the flags and the composition root differ.

**Core / State**
- Kotlin (JVM) + Coroutines
- StateFlow / SharedFlow for ViewModel state
- kotlinx.serialization for JSON

**Crypto**
- libsignal-protocol-jvm — X3DH + Double Ratchet (single-device profile; Sesame multi-device sync is not used)
- Argon2id — passphrase-derived key for local file encryption
- JCA / Bouncy Castle — primitives (AES-GCM, X25519, Ed25519) as needed

**Network**
- Ktor client (JVM) + OkHttp engine
- WebSocket for real-time envelope delivery
- kotlinx.serialization for envelope encoding

**Local Storage**
- SQLite (sqldelight or sqlite-jdbc) — messages, sessions, contacts
- File-based encrypted store for identity key + signed prekey + one-time prekeys
- At-rest encryption (v1): whole-DB encryption with passphrase-derived key
- Platform secure storage (v2): macOS Keychain / Windows Credential Manager / Linux libsecret

**UI (swappable)**

*CLI (v1.0)*
- stdin/stdout, readline loop
- No third-party library beyond coroutines

*TUI (v1.5)*
- lanterna — full-screen terminal UI
- Mordant — Markdown, tables, colored output
- Clikt — command-and-args parsing

*GUI (v2.0)*
- Compose Multiplatform Desktop
- Material 3 widgets

**Wire format**
- JSON envelopes over WebSocket
- Envelope types live in `:network:ktor` (shared between server and client); prekey bundle types live in `:crypto:libsignal`; user-facing DTOs (`Message`, `Conversation`, `User`) live in `:domain`

**Build / Quality**
- detekt / ktlint
- Kotest + MockK
- libsignal test vectors — protocol conformance

## Features

Each feature has a stable ID (`E-NN`) so it can be referenced from other docs (plaintext vs E2EE, implementation plan, etc.). The two subsections below list the **same** set of features, organised two ways: first by area, then by difficulty. Where a feature also exists in the plaintext messenger, the `E-NN` ID matches its `F-NN` counterpart (e.g. `E-04` is the E2EE version of `F-04` Login); E2EE-specific features (prekey bundle, X3DH, libsignal wrapper, etc.) start at `E-48`. The original **Server** / **Client** split is preserved per-feature in the description, since this distinction is fundamental to E2EE (the server sees only ciphertext; the client owns crypto). Multi-device and link previews are removed entirely (single device per user, no previews). The original E2EE "Hardcore" tier is removed — post-compromise security, deniability, and forward secrecy are properties of libsignal's ratchet and come for free; the multi-region / sharding / scale items were already removed from the plaintext doc (F-48).

The E2EE scope intentionally excludes `F-26` Threads (not in the original E2EE feature list), so `E-26` is skipped.

### By area

#### Core messaging
- **E-11** Send / fetch text messages (client; server relays envelopes)
- **E-12** Create 1:1 chat (client + server; find-or-create, idempotent)
- **E-13** Conversation list (client)
- **E-14** Pagination of message history (client; keyset cursor)
- **E-15** Read receipts (client; encrypted metadata)
- **E-16** Edit / delete own message (client)
- **E-17** Reply to message (client)
- **E-18** Forward message (client)
- **E-19** Emoji reactions (client)
- **E-20** Idempotency key on `POST /messages` (client; server enforces)
- **E-21** Soft delete + "delete for everyone" within N minutes (client)
- **E-22** Server-side edit history + audit log (server; metadata only — body is ciphertext)

#### Users & presence
- **E-02** User registration (server)
- **E-03** Invites (server; admin: create / list / revoke; later: all users can create)
- **E-06** Get / update own profile (server; avatar is an encrypted blob)
- **E-25** Block / unblock user (client)
- **E-33** Online / offline presence (client; server only sees "active")
- **E-34** Last seen (client; server only sees "active")
- **E-35** Typing indicator (client; encrypted metadata)

#### Group / community management
- **E-27** Group chats (client + server; sender keys for group E2EE)
- **E-28** Group roles: owner / admin / member (client + server)
- **E-29** Pinned messages in groups (client)
- **E-30** Polls in groups (client)
- **E-47** Federation (Matrix-style) (server)

#### Notifications & UX
- **E-24** @mentions + unread counters (client; encrypted)
- **E-38** Web Push notifications (VAPID) (server; ciphertext is pushed, client decrypts)

#### Search & content
- **E-23** Search in own chats (client; over decrypted history)

#### Security
- **E-04** Login (JWT access + refresh) (server)
- **E-05** Logout / token revocation (server)
- **E-07** Password reset via email (server)
- **E-08** Refresh token rotation (server)
- **E-09** Rate limiting (server; Caffeine counters, per-user + per-IP)
- **E-10** 2FA (TOTP) (server)
- **E-62** Safety number computation (client; per-contact verification)

#### Admin / ops
- **E-01** Health check endpoint (server)
- **E-31** Simple admin role + kick/ban (server)
- **E-45** Backups (server; pg_dump + rclone — for the server's own metadata, separate from E-52)
- **E-46** Caddy + automatic Let's Encrypt in front of Ktor (server)

#### Transport resilience
- **E-32** WebSocket gateway (server; relays envelopes, no plaintext)
- **E-36** WebSocket reconnection + offline message backlog (client)

#### Attachments
- **E-39** Image / file attachment (client; server stores opaque blob, key held by client)
- **E-40** Voice messages (client; server stores opaque blob)

#### Nice-to-have
- **E-42** Voice / video calls (DTLS-SRTP) (client)
- **E-43** Sticker / custom emoji packs (client)
- **E-44** Story / status (24h ephemeral) (client; server stores opaque)
- **E-57** CLI shell (client; stdin/stdout, readline loop)
- **E-58** TUI shell (client; lanterna — placeholder for v1.5)
- **E-59** GUI shell (client; Compose Multiplatform Desktop — placeholder for v2.0)

#### Crypto / server
- **E-48** Prekey bundle storage (server; POST/GET per device — just bytes)
- **E-49** Envelope relay (server; store and forward ciphertext)
- **E-50** Encrypted attachment storage (server; POST/GET opaque blobs)
- **E-53** Group sender key distribution (server; just forwards)
- **E-54** Prekey bundle rotation endpoint (server; stores the new bundle)
- **E-55** One-time prekey replenishment endpoint (server; stores the new prekeys)

#### Crypto / client
- **E-60** libsignal wrapper module (client; thin Kotlin wrapper around the Java API)
- **E-63** Key generation on first run (client; identity + signed prekey + N one-time prekeys)
- **E-64** Session persistence (client; ratchet state save / load)
- **E-65** Encrypted local message history (client)
- **E-66** X3DH first-contact handshake (client)
- **E-67** Prekey bundle fetch from server (client)
- **E-68** Prekey bundle rotation (client; signed prekey, periodic)
- **E-69** One-time prekey replenishment (client)
- **E-72** Per-group sender key generation (client)
- **E-73** Sender key distribution in groups (client)
- **E-74** Identity key rotation (client; on compromise)

#### Recovery & backup
- **E-52** Server-side encrypted backup blob storage (server; opaque blob + retention policy)
- **E-61** Passphrase lock (client; derive key, decrypt local DB on open)
- **E-70** Server-side encrypted backup generation (client; encrypts `identity + sessions + recent_history` with passphrase-derived key, uploads)
- **E-71** Backup restore from passphrase (client)

### By difficulty

#### Trivial
- **E-01** Health check endpoint
- **E-02** User registration — MVP: validate invite token, insert user
- **E-03** Invites — MVP: admin can create / list / revoke
- **E-04** Login → JWT access + refresh — MVP: bcrypt compare, return both tokens
- **E-05** Logout / token revocation — MVP: delete refresh token from store
- **E-06** Get / update own profile — MVP: text fields only
- **E-12** Create 1:1 chat (find-or-create, idempotent)
- **E-14** Pagination of message history (keyset cursor)
- **E-32** WebSocket gateway — MVP: upgrade, register session, route by message type (relays envelopes)
- **E-33** Online / offline presence
- **E-34** Last seen
- **E-45** Backups: cron `pg_dump` to local + rclone to Backblaze B2
- **E-46** Caddy + automatic Let's Encrypt in front of Ktor
- **E-48** Prekey bundle storage (POST/GET per device)
- **E-49** Envelope relay (store and forward ciphertext)
- **E-50** Encrypted attachment storage (POST/GET opaque blobs)
- **E-57** CLI shell (stdin/stdout, readline loop)

#### Easy
- **E-02** User registration — Production: rate limit, bound email, revocation, per-user rate limit on invite creation
- **E-03** Invites — Production: open to all users
- **E-04** Login → JWT access + refresh — Production: rate limit, constant-time, breach check, refresh rotation
- **E-05** Logout / token revocation — Production: bump session-version claim, optional JTI blocklist
- **E-06** Get / update own profile — Production: avatar upload (encrypted)
- **E-07** Password reset via email — MVP: token in DB, reset endpoint
- **E-08** Refresh token rotation — MVP: issue new access from valid refresh
- **E-09** Rate limiting (Caffeine counters, per-user + per-IP)
- **E-10** 2FA (TOTP) (secret + QR + verify + recovery codes)
- **E-11** Send / fetch text messages — MVP
- **E-13** Conversation list (MVP: list my conversations; Production: last-message preview, unread counts, pinned/muted/archived)
- **E-15** Read receipts — MVP: explicit mark-read endpoint, per-conversation last_read_id
- **E-16** Edit / delete own message (MVP: PATCH/DELETE with owner check, "delete for everyone" within time window; Production: "(edited)" indicator, edit history)
- **E-17** Reply to message (in-conversation; show quoted block)
- **E-18** Forward message (cross-conversation; show attribution)
- **E-19** Emoji reactions (separate table, unique on `(message_id, user_id, emoji)`)
- **E-20** Idempotency key on `POST /messages` (client UUID, PK conflict returns existing)
- **E-21** Soft delete + "delete for everyone" within N minutes
- **E-22** Server-side edit history + audit log (metadata only in E2EE — body is ciphertext)
- **E-23** Search in own chats (client-side, over decrypted history)
- **E-24** @mentions + unread counters (MVP: parse `@username` in body; Production: denormalized counter, fan-out)
- **E-25** Block / unblock user (blocks table, filter on read paths)
- **E-27** Group chats — MVP: schema + CRUD + roles
- **E-28** Group roles: owner / admin / member
- **E-29** Pinned messages in groups
- **E-30** Polls in groups (create / vote / close)
- **E-31** Simple admin role + kick/ban
- **E-35** Typing indicator (as encrypted metadata)
- **E-39** Image / file attachment — MVP: client encrypts, server stores opaque blob
- **E-40** Voice messages — MVP: client encrypts, server stores opaque blob
- **E-44** Story / status (24h ephemeral) (schema + `expires_at` index + cleanup cron)
- **E-52** Server-side encrypted backup blob storage (opaque blob + retention policy)
- **E-53** Group sender key distribution (server just forwards)
- **E-58** TUI shell (lanterna — placeholder for v1.5)
- **E-59** GUI shell (Compose Multiplatform Desktop — placeholder for v2.0)
- **E-60** libsignal wrapper module
- **E-61** Passphrase lock (derive key, decrypt local DB on open)
- **E-62** Safety number computation

#### Medium
- **E-07** Password reset via email — Production: SMTP infra, token hashing, expiry + single-use, rate limit
- **E-08** Refresh token rotation — Production: theft detection, reuse invalidates family
- **E-11** Send / fetch text messages — Production: authz, body size, HTML escape, idempotency, stable ordering
- **E-15** Read receipts — Production: per-recipient semantics in groups, "who read", aggregate unread counters
- **E-36** WebSocket reconnection + offline message backlog on reconnect
- **E-38** Web Push notifications (VAPID)
- **E-39** Image / file attachment — Production: mime sniff, thumbnails, auth-gated download
- **E-40** Voice messages — Production: ffmpeg transcoding, waveform, async worker
- **E-42** Voice / video calls (DTLS-SRTP, separate from plain WebRTC)
- **E-43** Sticker / custom emoji packs
- **E-54** Prekey bundle rotation endpoint (server stores the new bundle)
- **E-55** One-time prekey replenishment endpoint (server stores the new prekeys)
- **E-63** Key generation on first run (identity + signed prekey + N one-time prekeys)
- **E-64** Session persistence (ratchet state save / load)
- **E-65** Encrypted local message history
- **E-66** X3DH first-contact handshake
- **E-67** Prekey bundle fetch from server
- **E-68** Prekey bundle rotation (signed prekey, periodic)
- **E-69** One-time prekey replenishment
- **E-70** Server-side encrypted backup generation
- **E-71** Backup restore from passphrase
- **E-72** Per-group sender key generation
- **E-73** Sender key distribution in groups

#### Hard
- **E-27** Group chats — Production: system messages, last-admin-leaves, **re-keying on member add/remove**
- **E-32** WebSocket gateway — Production: auth on upgrade, structured routing, heartbeat, backpressure, graceful shutdown
- **E-74** Identity key rotation (on compromise)

#### Very hard
- *empty*

#### Ignore
- **E-47** Federation (Matrix-style) — only if explicitly desired; not justified at 10–50 users

## Implementation plan

Guiding principles: server is "boring half" — relay ciphertext, store opaque bytes; client is the bulk of the work; crypto integration must succeed before anything else; the smallest usable E2EE messenger is a CLI talking to a near-trivial server; **the plaintext and E2EE messengers are developed in parallel from day 1, sharing the same UI module tree — no "plaintext first, then copy" step**. The shared UI work is done once and used by both.

### Parallel development

Both messengers progress in lockstep from Phase 0 onwards. The shared modules (`:domain`, `:viewmodel`, `:ui:cli`, `:ui:tui`, `:ui:gui`, `:network:ktor`) are populated incrementally by work that benefits both. Each messenger has its own `:storage:*` (`plain` or `encrypted`); the E2EE messenger adds `:crypto:libsignal`. The two composition roots (`:client:plaintext` and `:client:e2ee`) wire the right storage and crypto into the shared UI. E2EE-specific UI affordances (verification, safety numbers, identity rotation, recovery) are feature-flagged in the shared UI; `:client:e2ee` enables the flags, `:client:plaintext` leaves them off.

The per-messenger cost is roughly: shared UI (done once) + per-messenger storage + per-messenger server endpoints + (E2EE only) `:crypto:libsignal`. The total is less than building two separate UIs.

The plaintext messenger's plan (`docs/minimax-m3.md`) is the reference for the shared phase work (`:domain` DTOs, `:viewmodel` screens, `:ui:cli` command set, server auth/transport); this doc focuses on what's E2EE-specific and what differs from plaintext.

### Module layout (set up in Phase 0)

```
:server:plaintext    plaintext messenger's Ktor server
:server:e2ee         E2EE messenger's Ktor server
:domain              DTOs and use cases — shared by both messengers
:viewmodel           per-screen state holders — shared, with feature flags for E2EE-specific screens
:ui:cli              stdin/stdout client (readline loop, v1) — shared
:ui:tui              lanterna full-screen client (v1.5, added in Phase 4) — shared
:ui:gui              Compose Multiplatform Desktop (v2+, added in Phase 7) — shared
:network:ktor        Ktor client, envelope types, WebSocket — shared
:storage:plain       plaintext messenger's data layer (plain SQLite)
:storage:encrypted   E2EE messenger's data layer (encrypted SQLite)
:crypto:libsignal    E2EE-only: libsignal wrapper (X3DH, Double Ratchet, sender keys, identity)
:client:plaintext    composition root: wires :domain + :viewmodel + :ui:* + :network:ktor + :storage:plain
:client:e2ee         composition root: wires :domain + :viewmodel + :ui:* + :network:ktor + :storage:encrypted + :crypto:libsignal
```

The "encryption at the boundary" rule (stricter for E2EE): `:domain` exposes only the post-decryption DTOs — the same `Message`, `Conversation`, `User` types the plaintext messenger sees. Server-internal types (raw envelope bodies, ciphertext, auth claims, prekey bundles) live in `:network:ktor` and `:storage:encrypted` and never leak into `:domain`. The repository interfaces (`MessageRepository`, `ConversationRepository`, `AuthRepository`) live in `:domain`; the E2EE implementation lives in `:storage:encrypted`, the plaintext implementation in `:storage:plain`. Only `:crypto:libsignal` ever holds plaintext in conjunction with ciphertext — the boundary is the repository interface in `:domain`. If a DTO in `:domain` ever has to know whether a message is plaintext or ciphertext, the boundary is wrong.

E2EE-specific UI (verification badges, safety numbers, identity key rotation, recovery flow) is feature-flagged in `:ui:*` and `:viewmodel`: the flags are wired on in `:client:e2ee`, off in `:client:plaintext`. The shared UI code is the same composable in both — only the flags and the composition root differ.

### Phase 0 — Skeleton (2–3 days)
- Multi-module Gradle layout as above — every module is a stub with its own `build.gradle.kts` from day 1
- `:server:plaintext` and `:server:e2ee`: Ktor app skeleton, config, structured logging, basic `/metrics`
- All shared modules (`:domain`, `:viewmodel`, `:ui:cli`, `:ui:tui`, `:ui:gui`, `:network:ktor`) as empty stubs
- Per-messenger modules (`:storage:plain`, `:storage:encrypted`, `:crypto:libsignal`, `:client:plaintext`, `:client:e2ee`) as empty stubs
- PostgreSQL + Flyway baseline
- Health check, `/metrics`
- Caddy in front, HTTPS, domain
- **Bootstrap admin:** seed the first user via env var on first startup (e.g. `BOOTSTRAP_ADMIN_EMAIL` + `BOOTSTRAP_ADMIN_PASSWORD`). If `users` is empty on boot, create the admin and skip on subsequent starts. The admin cannot invite themselves, so this step is required.

### Phase 1 — Auth and transport (1 week, both messengers in parallel)
- `:domain`: `User`, `AuthSession`, `RefreshToken` DTOs; `AuthRepository` interface (register, login, logout, refresh, password reset) — shared
- `:network:ktor`: Ktor client wrapper, JSON envelope types, access/refresh token plumbing — shared
- `:viewmodel`: auth ViewModel — shared
- `:ui:cli`: `register <invite> <email> <password>`, `login <email> <password>`, `logout`, `whoami` — shared
- Server (`:server:e2ee`):
  - Shared: auth endpoints (registration, login, logout, password reset, refresh tokens, rate limiting)
  - E2EE-specific: identity key generation on registration; the server stores the public prekey bundle and identity fingerprint
- `:storage:plain`: user table — plaintext
- `:storage:encrypted`: user table + device key metadata — E2EE
- `:crypto:libsignal`: identity key generation on register — E-63
- `:client:plaintext`: wires `:storage:plain.AuthRepository` into `:ui:cli`
- `:client:e2ee`: wires `:storage:encrypted.AuthRepository` and `:crypto:libsignal` into `:ui:cli`
- Caddy + HTTPS in front
- Both messengers can register and log in via the same shared CLI commands

### Phase 2 — Basic 1:1 messaging + E2EE crypto foundation (2 weeks, both messengers in parallel)
- `:domain`: `Message`, `Conversation`, `MessageCursor` DTOs; `MessageRepository`, `ConversationRepository` interfaces — shared
- `:network:ktor`: WebSocket client wrapper, `last_event_id` plumbing, reconnection with exponential backoff — shared
- `:viewmodel`: `ConversationListViewModel`, `ChatViewModel` — shared, with feature flags for E2EE-specific behaviors
- `:ui:cli`: `list-conversations`, `send <user> <body>`, `recv` (block until new message), `history <user> [N]` — shared
- E2EE-specific CLI commands (feature-flagged): `prekey-refresh`, `verify <user>`, `safety-number <user>`
- Server (`:server:e2ee`):
  - Shared: WS gateway (MVP), message endpoints, conversation list, pagination
  - E2EE-specific: prekey bundle storage (E-48), envelope relay (E-49), encrypted attachment storage (E-50)
- `:storage:plain`: SQLite cache (sqldelight or sqlite-jdbc) for `messages`, `conversations`; `MessageRepository` and `ConversationRepository` implementations that mirror server state and reapply incoming WS events atomically — plaintext
- `:storage:encrypted`: SQLite cache (encrypted at rest via SQLCipher or whole-DB encryption with passphrase-derived key); `MessageRepository` and `ConversationRepository` implementations; encrypted local message history (E-65, E-68) — E2EE
- `:crypto:libsignal`: libsignal wrapper (X3DH, Double Ratchet, sender keys, identity key, session persistence) — E-60, E-64, E-66
- `:client:plaintext`: wires `:storage:plain.MessageRepository` into shared ViewModels
- `:client:e2ee`: wires `:storage:encrypted.MessageRepository` and `:crypto:libsignal` into shared ViewModels
- Prekey bundle fetch + X3DH first-contact handshake — E-67, E-69
- Send / receive end-to-end (both messengers, in parallel)
- **Unit tests against libsignal test vectors** (protocol conformance — non-negotiable)
- CLI smoke test (E2EE): generate keys, encrypt to self, decrypt, verify round-trip

**Stop here for 1–2 weeks of dogfooding** for each messenger. The shared CLI commands work for both; only the storage and crypto backing differs. Two CLI clients in two terminals, real chat (one plaintext, one E2EE), fix bugs and sharpen UX.

### Phase 3 — Real-time hardening (1 week, both messengers)
- `:viewmodel`: `PresenceViewModel` (per-contact online/offline + last seen + typing) — shared
- `:ui:cli`: show online state in `list-contacts`; show typing indicator while `recv` blocks — shared
- `:network:ktor`: client-side WS reconnection with `last_event_id`, exponential backoff — shared
- Server (`:server:e2ee`):
  - Shared: WS gateway Production (auth on upgrade, structured routing, heartbeat, graceful shutdown) — E-32
  - Shared: WS reconnection + offline message backlog (`last_event_id` contract — design this first)
  - E2EE: envelopes are already routed; no additional server work here

### Phase 4 — UX polish + TUI (2 weeks, both messengers)
- `:viewmodel`: add screens — edit/delete, reactions, read receipts, mentions, search, block, reply, forward — shared, with E2EE-specific feature flags
- `:ui:cli`: add commands (`edit <id> <body>`, `delete <id>`, `react <id> <emoji>`, `block <user>`, `search <query>`, `reply <id> <body>`, `forward <id> <user>`, `mark-read <id>`, etc.) — shared
- `:ui:tui`: NEW shared module. `lanterna` for full-screen chat, `Mordant` for any non-lanterna output, `Clikt` for command-and-args. Same `Message` DTOs, same `MessageRepository` — only the storage backing differs per messenger
- Both composition roots: add a second entry point that wires `:ui:tui` instead of `:ui:cli`
- Server (`:server:e2ee`):
  - Shared: edit/delete, read receipts, mentions, search, block, reply, forward, open invite creation to all users
  - E2EE: no additional server work; these features work the same way
- E2EE-specific TUI: passphrase prompt on launch (E-61)

### Phase 5 — Groups (1 week, both messengers)
- `:domain`: add `Group` / `GroupMember` / `GroupRole` DTOs (or extend `Conversation` with `kind=group` + `members`; same DTOs for both messengers, so the plaintext messenger's UI also sees them)
- `:storage:plain`: add `groups`, `group_members` tables; extend `MessageRepository` for group messages — plaintext
- `:storage:encrypted`: add `groups`, `group_members` tables; extend `MessageRepository` for group messages (sender keys, encrypted) — E2EE
- `:crypto:libsignal`: per-group sender key generation, sender key distribution — E-72, E-73
- `:ui:cli` and `:ui:tui`: add group commands / screens (create group, list members, add/remove, role management) — shared
- Server (`:server:e2ee`):
  - Shared: group chats (MVP): schema, CRUD, roles — E-27 (MVP)
  - Shared: group chats (Production): system messages, last-admin-leaves
  - E2EE-specific: group sender key distribution (server just forwards) — E-53
  - E2EE-specific: **re-keying on member add/remove** (the single most common "we shipped groups and they broke" bug in E2EE messengers) — E-27 (Production)
  - Test re-keying explicitly (E2EE): remove a member, verify they cannot decrypt subsequent messages. Test *all* corner cases (member re-add, two members removed at once, etc.)

### Phase 6 — Recovery (1 week, E2EE-specific; plaintext can add a lighter version if useful)
- `:domain`: add `EncryptedBackup` DTO (server stores opaque blob; client encrypts with passphrase-derived key)
- `:storage:encrypted`: add backup gen/restore tables (server-side encrypted blob reference + local encrypted backup data)
- `:network:ktor`: upload/download encrypted backup blob — shared
- `:ui:cli` and `:ui:tui`: add recovery flow (passphrase prompt on launch, restore prompt, passphrase change) — E2EE (feature-flagged)
- Server (`:server:e2ee`):
  - E2EE: server-side encrypted backup blob storage — E-52
  - E2EE: server-side encrypted backup generation (client encrypts `identity + sessions + recent_history` with passphrase-derived key, uploads) — E-70
  - E2EE: backup restore with passphrase — E-71
  - E2EE: passphrase change (re-encrypt and re-upload backup)

### Phase 7 — GUI v2.5 (3–4 weeks, both messengers)
- `:ui:gui`: NEW shared module. Compose Multiplatform Desktop, Material 3.
- Both composition roots: add a third entry point that wires `:ui:gui`
- Same `Message` DTOs as CLI and TUI; no changes to `:domain` or `:viewmodel`
- E2EE-specific GUI: verification screen, safety numbers, identity key rotation (feature-flagged)

### Phase 8 — Defer indefinitely
- MLS (sender keys are enough for 10–50 people)
- Federation (Matrix-style) — E-47
- Multi-device — out of scope (single device per user; per the project's decision)
- Custom emoji / sticker packs at scale
- Post-compromise security / deniability — these are properties of libsignal you get by default, not features you implement
- All Hardcore-tier items (multi-region, sharding, CQRS, etc.)

On-demand features (not deferred, but built only when asked — same shape as the plaintext plan's "Phase 7: Communication features (when asked)"):
- Web Push (VAPID) — when someone wants notifications while the app is closed — E-38
- 2FA (TOTP) — when an account gets phished — E-10
- Voice / video calls (DTLS-SRTP) — multi-week project, only if people ask — E-42
- Stickers, story, threads, polls, pinned messages — same "skip unless asked" reasoning as the plaintext messenger

### What to skip entirely
- **GUI for v1.** TUI is the realistic daily driver for a CLI community; GUI is a v2.5.
- **Multi-device.** Single device per user. Out of scope.
- **Voice / video calls.** Even without E2EE, calls are a separate project. With DTLS-SRTP, much bigger.
- **MLS.** Sender keys are simpler and adequate at 10–50 people.
- **Federation.** Same as plaintext: not justified.
- **Sticker packs, story, polls, threads.** Same reasoning as plaintext.

### Risks to flag early
1. **Module structure has to match the frontend note's layout from day 1.** Both messengers build against the same shared module tree; a `:domain` DTO that knows about ciphertext, or `:ui:cli` depending on `:server:e2ee`, will break the parallel approach. Stick to the "encryption at the boundary" rule; the wrong choice here means a rewrite of `:domain` and `:storage:encrypted`.
2. **Crypto integration is the most underestimated piece.** Test with libsignal test vectors *before* building anything UI. A subtle bug in the ratchet (e.g. wrong key ordering, off-by-one in message keys) is silent and catastrophic.
3. **X3DH first-contact handshake is harder than it looks.** Get the protocol design (prekey bundle format, identity key fingerprint on the wire, session state persistence) right *before* writing the CLI. The wrong choice means a rewrite of `:crypto:libsignal` later.
4. **Re-keying on group member add/remove.** The single most common "we shipped groups and they broke" bug in E2EE messengers. Test explicitly: kick a member, verify they cannot decrypt subsequent messages. Test *all* corner cases (member re-add, two members removed at once, etc.).
5. **Recovery story is critical, not optional.** Lose your device, lose your keys, lose your history. Ship the server-side encrypted backup blob in Phase 6, not v2.0; design the passphrase flow in Phase 2 so it doesn't have to be retrofitted.
6. **WebSocket reliability + envelope ordering.** The client may receive messages out of order, duplicate, or after a long delay. The libsignal ratchet handles out-of-order decryption, but the application layer (conversation list, read state) needs to be defensive about it.

### What "done" looks like
- **v1.0 (both messengers, ship to community):** Phases 0–3, ~4–6 weeks for one Kotlin dev (or faster with two devs in parallel). Both messengers' CLIs are working: plaintext 1:1 messaging + E2EE 1:1 messaging. The shared CLI commands work for both; only the storage and crypto backing differs. Stop here for a while.
- **v1.5 (TUI):** Phase 4, ~6–9 weeks total. Both messengers get the TUI from the same shared module.
- **v2.0 (groups + recovery):** Phases 5–6, ~10–13 weeks total. Real production messenger for both.
- **v2.5 (GUI):** Phase 7, ~13–17 weeks total. Both messengers get the GUI from the same shared module.
- **v3.0 (on-demand):** Phase 8 on-demand items, only what the community actually asks for.
