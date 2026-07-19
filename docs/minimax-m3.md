# Kotlin messenger, general info

## Tech stack

**Core**
- Kotlin (JVM) + Coroutines
- Ktor — lightweight async web framework, WebSocket support
- Gradle (Kotlin DSL) — build

**Persistence**
- PostgreSQL — primary store (run in Docker)
- Exposed or jOOQ — type-safe SQL access
- Flyway — schema migrations
- Caffeine — in-process cache (presence, rate limit counters)
- Redis — optional, only if scaling beyond a single Ktor instance

**Real-time / Messaging**
- Ktor WebSockets — client connections
- In-process pub/sub for fan-out (single instance)

**Auth / Security**
- JWT (access + refresh) — nimbus-jose-jwt
- bcrypt — password hashing
- Ktor Auth — auth pipeline
- Web Push + VAPID — browser notifications

**Media / Files**
- Local filesystem — attachments and avatars
- WebRTC P2P — voice / video calls (Ktor as signaling server)

**Observability**
- Logback with JSON encoder — structured logs
- Micrometer + Prometheus + Grafana — metrics and dashboards

**Build / Quality**
- detekt / ktlint — static analysis
- Kotest + MockK + Testcontainers — testing

**Deploy**
- Docker + Docker Compose
- Caddy (or nginx) — reverse proxy + automatic HTTPS (Let's Encrypt)
- Nightly `pg_dump` + rclone to Backblaze B2 — backups

---

## Features

Each feature has a stable ID (`F-NN`) so it can be referenced from other docs
(plaintext vs E2EE, implementation plan, etc.). The two subsections below list
the **same** set of features, organised two ways: first by area, then by
difficulty. Where a feature has an MVP version and a meaningfully harder
**Production-ready** version, the Production version is listed at a higher
difficulty (and may be deferred or skipped for v1).

### By area

#### Core messaging
- **F-11** Send / fetch text messages
- **F-12** Create 1:1 chat (find-or-create, idempotent)
- **F-13** Conversation list
- **F-14** Pagination of message history (keyset cursor)
- **F-15** Read receipts
- **F-16** Edit / delete own message
- **F-17** Reply to message (in-conversation; show quoted block)
- **F-18** Forward message (cross-conversation; show attribution)
- **F-19** Emoji reactions (separate table, unique on `(message_id, user_id, emoji)`)
- **F-20** Idempotency key on `POST /messages` (client UUID, PK conflict returns existing)
- **F-21** Soft delete + "delete for everyone" within N minutes
- **F-22** Server-side edit history + audit log (append-only tables; `GET /messages/:id/history`)
- **F-26** Threads / reply-to-message (parent_id + recursive fetch, "x new replies")

#### Users & presence
- **F-02** User registration
- **F-03** Invites (admin: create / list / revoke; later: all users can create)
- **F-06** Get / update own profile
- **F-25** Block / unblock user
- **F-33** Online / offline presence (in-process map: add on connect, remove on disconnect)
- **F-34** Last seen (write `last_seen_at` on disconnect, periodic flush to DB)
- **F-35** Typing indicator (WS event, debounced on client)

#### Group / community management
- **F-27** Group chats
- **F-28** Group roles: owner / admin / member
- **F-29** Pinned messages in groups
- **F-30** Polls in groups (create / vote / close)
- **F-47** Federation (Matrix-style)

#### Notifications & UX
- **F-24** @mentions + unread counters
- **F-38** Web Push notifications (VAPID)

#### Search & content
- **F-23** Search in own chats (`ILIKE` is enough at this scale)

#### Security
- **F-04** Login (JWT access + refresh)
- **F-05** Logout / token revocation
- **F-07** Password reset via email
- **F-08** Refresh token rotation
- **F-09** Rate limiting (Caffeine counters, per-user + per-IP)
- **F-10** 2FA (TOTP) (secret + QR + verify + recovery codes)

#### Admin / ops
- **F-01** Health check endpoint
- **F-31** Simple admin role + kick/ban
- **F-45** Backups: cron `pg_dump` to local + rclone to Backblaze B2
- **F-46** Caddy + automatic Let's Encrypt in front of Ktor

#### Transport resilience
- **F-32** WebSocket gateway
- **F-36** WebSocket reconnection + offline message backlog

#### Attachments
- **F-39** Image / file attachment
- **F-40** Voice messages (client records, uploads; server stores as-is — WebM/Opus plays in any modern browser)

#### Nice-to-have
- **F-42** Voice / video calls (WebRTC P2P, Ktor as signaling server)
- **F-43** Sticker / custom emoji packs
- **F-44** Story / status (24h ephemeral) (schema + `expires_at` index + cleanup cron)

### By difficulty

#### Trivial
- **F-01** Health check endpoint
- **F-02** User registration — MVP: validate invite token, insert user
- **F-03** Invites — MVP: admin can create / list / revoke
- **F-04** Login → JWT access + refresh — MVP: bcrypt compare, return both tokens
- **F-05** Logout / token revocation — MVP: delete refresh token from store
- **F-06** Get / update own profile — MVP: text fields only
- **F-12** Create 1:1 chat (find-or-create, idempotent)
- **F-14** Pagination of message history (keyset cursor)
- **F-33** Online / offline presence (in-process map: add on connect, remove on disconnect)
- **F-34** Last seen (write `last_seen_at` on disconnect, periodic flush to DB)
- **F-35** Typing indicator (WS event, debounced on client)
- **F-45** Backups: cron `pg_dump` to local + rclone to Backblaze B2
- **F-46** Caddy + automatic Let's Encrypt in front of Ktor

#### Easy
- **F-02** User registration — Production: invite token + bound email + revocation + per-user rate limit on invite creation
- **F-04** Login → JWT access + refresh — Production: rate limit, constant-time response, breach check, refresh-token rotation
- **F-05** Logout / token revocation — Production: bump session-version claim, optional JTI blocklist for access tokens
- **F-06** Get / update own profile — Production: avatar upload, resize, public path
- **F-07** Password reset via email — MVP: token in DB, reset endpoint
- **F-08** Refresh token rotation — MVP: issue new access from valid refresh
- **F-09** Rate limiting (Caffeine counters, per-user + per-IP)
- **F-10** 2FA (TOTP) (secret + QR + verify + recovery codes)
- **F-11** Send / fetch text messages — MVP: insert + return, fetch with cursor
- **F-13** Conversation list (MVP: list my conversations; Production: last-message preview, unread counts, pinned/muted/archived)
- **F-15** Read receipts — MVP: explicit mark-read endpoint, per-conversation last_read_id
- **F-16** Edit / delete own message (MVP: PATCH/DELETE with owner check, "delete for everyone" within time window; Production: "(edited)" indicator, edit history table)
- **F-17** Reply to message (in-conversation; show quoted block)
- **F-18** Forward message (cross-conversation; show attribution)
- **F-19** Emoji reactions (separate table, unique on `(message_id, user_id, emoji)`)
- **F-20** Idempotency key on `POST /messages` (client UUID, PK conflict returns existing)
- **F-21** Soft delete + "delete for everyone" within N minutes
- **F-22** Server-side edit history + audit log
- **F-23** Search in own chats (`ILIKE` is enough at this scale)
- **F-24** @mentions + unread counters (MVP: parse `@username` in body, count unread per conversation; Production: denormalized unread counter, notification fan-out on mention)
- **F-25** Block / unblock user (blocks table, filter on read paths)
- **F-27** Group chats — MVP: schema + CRUD endpoints, role on members
- **F-28** Group roles: owner / admin / member
- **F-29** Pinned messages in groups
- **F-30** Polls in groups (create / vote / close)
- **F-31** Simple admin role + kick/ban
- **F-32** WebSocket gateway — MVP: upgrade, register session, route by message type
- **F-39** Image / file attachment — MVP: write file to local FS, return id
- **F-40** Voice messages — MVP: client records, uploads; server stores as-is (WebM/Opus)
- **F-44** Story / status (24h ephemeral) (schema + `expires_at` index + cleanup cron)

#### Medium
- **F-07** Password reset via email — Production: SMTP infra, token hashing, expiry + single-use, rate limit on reset endpoint
- **F-08** Refresh token rotation — Production: theft detection, reuse invalidates family
- **F-11** Send / fetch text messages — Production: authz, body size, HTML escape, idempotency, WS fan-out, stable ordering
- **F-15** Read receipts — Production: per-recipient semantics in groups, "who read", aggregate unread counters
- **F-26** Threads / reply-to-message (parent_id + recursive fetch, "x new replies" tracking)
- **F-36** WebSocket reconnection + offline message backlog on reconnect
- **F-38** Web Push notifications (VAPID)
- **F-39** Image / file attachment — Production: mime sniff, size limit, auth-gated download, thumbnails, retry-safe upload
- **F-40** Voice messages — Production: ffmpeg transcoding, waveform generation, async worker, multi-format storage
- **F-42** Voice / video calls (WebRTC P2P, Ktor as signaling server)
- **F-43** Sticker / custom emoji packs (pack + sticker tables, user subscriptions, moderation, image processing)

#### Hard
- **F-27** Group chats — Production: WS fan-out to N members, system messages, last-admin-leaves, kick + re-invite, denormalized last_message
- **F-32** WebSocket gateway — Production: auth on upgrade, structured routing, heartbeat, reconnection with last-event-id, backpressure, graceful shutdown

#### Ignore
- **F-47** Federation (Matrix-style) — only if explicitly desired; not justified at 10–50 users

--- 

## Implementation plan

Guiding principles: foundation first (schema and auth before anything else); security at launch, not later (Production auth bundled with MVP); smallest usable messenger, then iterate; defer polish; **keep the module layout aligned with the frontend note from day 1, and develop the E2EE messenger (`docs/minimax-m3-e2ee.md`) in parallel**. Both messengers share the same UI module tree from Phase 0 — there is no "plaintext first, then copy" step. The shared UI work is done once and used by both.

### Module layout (set up in Phase 0)

```
:server:plaintext    Ktor server (plaintext messenger)
:domain              DTOs and use cases — the same types the E2EE messenger will see
:viewmodel           per-screen state holders
:ui:cli              stdin/stdout client (readline loop, v1)
:ui:tui              lanterna full-screen client (v1.5, added in Phase 4)
:ui:gui              Compose Multiplatform Desktop (v2+, added on demand in Phase 7)
:network:ktor        Ktor client, envelope types, WebSocket
:storage:plain       local SQLite cache for the plaintext messenger
:client:plaintext    composition root: wires :domain + :viewmodel + :ui:* + :network:ktor + :storage:plain
```

The "encryption at the boundary" rule: `:domain` exposes only user-facing types
(`Message`, `Conversation`, `User`) — the same DTOs the E2EE messenger will see.
Server-internal types (raw envelope bodies, ciphertext, auth claims) live in
`:network:ktor` and `:storage:plain` and never leak into `:domain`. The
repository interfaces (`MessageRepository`, `ConversationRepository`,
`AuthRepository`) live in `:domain`; the plaintext implementation lives in
`:storage:plain`; the E2EE implementation will land in `:storage:encrypted`
later. If a DTO in `:domain` ever has to know whether a message is plaintext
or ciphertext, the boundary is wrong.

### Phase 0 — Skeleton (1 day)
- Multi-module Gradle layout as above — every module is a stub with its own `build.gradle.kts` from day 1
- `:server:plaintext`: Ktor app, config, structured logging, basic `/metrics`
- PostgreSQL + Flyway baseline
- Health check, `/metrics`
- Caddy in front, HTTPS, domain
- **Bootstrap admin:** seed the first user via env var on first startup (e.g. `BOOTSTRAP_ADMIN_EMAIL` + `BOOTSTRAP_ADMIN_PASSWORD`). If `users` is empty on boot, create the admin and skip on subsequent starts. The admin cannot invite themselves, so this step is required.

### Phase 1 — Auth and transport (1 week, security-critical, all-or-nothing)
- `:domain`: `User`, `AuthSession`, `RefreshToken` DTOs; `AuthRepository` interface (register, login, logout, refresh, password reset)
- `:network:ktor`: Ktor client wrapper, JSON envelope types, access/refresh token plumbing
- `:ui:cli`: readline loop; commands `register <invite> <email> <password>`, `login <email> <password>`, `logout`, `whoami`; keep JWT + refresh token in memory for now
- `:client:plaintext`: composition root wires `AuthRepository` (server-backed in Phase 1) into `:ui:cli`
- Server (`:server:plaintext`):
  - Invites (admin: create / list / revoke; bound-email)
  - User registration (MVP + Production together: validate token, insert user, bound email, revocation, per-user rate limit on invite creation)
  - Login → JWT access + refresh (MVP + Production: rate limit, constant-time, breach check)
  - Refresh token rotation (MVP + Production: theft detection, family invalidation)
  - Logout / token revocation (MVP + Production: session version bump)
  - Password reset via email (MVP, then Production with SMTP)
  - Rate limiting on auth endpoints
- Caddy + HTTPS in front of Ktor

### Phase 2 — Basic 1:1 messaging (1.5 weeks)
- `:domain`: `Message`, `Conversation`, `MessageCursor` DTOs; `MessageRepository`, `ConversationRepository` interfaces
- `:network:ktor`: WebSocket client wrapper, `last_event_id` plumbing
- `:storage:plain`: SQLite cache (sqldelight or sqlite-jdbc) for `messages`, `conversations`; `MessageRepository` and `ConversationRepository` implementations that mirror server state and reapply incoming WS events atomically
- `:viewmodel`: `ConversationListViewModel`, `ChatViewModel`
- `:ui:cli`: commands `list-conversations`, `send <user> <body>`, `recv` (block until new message), `history <user> [N]`
- `:client:plaintext`: composition root wires repositories into ViewModels
- Server (`:server:plaintext`):
  - Schema: `conversations` (kind=direct), `conversation_members`, `messages`
  - Create 1:1 chat (find-or-create, idempotent)
  - Send / fetch text messages (MVP + Production together: auth, body size, escape, idempotency, stable ordering)
  - Pagination (keyset cursor)
  - WebSocket gateway (MVP: upgrade, register session, route by type)
  - Conversation list (MVP)
  - Online / offline presence + last seen + typing indicator
  - Profile (text + avatar)

**Stop here for a week of dogfooding** before going further: two CLI clients in
two terminals, real chat, fix bugs and sharpen UX. The CLI command set at this
point should match `docs/minimax-m3-e2ee.md` Phase 3 (`login`, `send`, `recv`,
`list-contacts`, `list-conversations`) so that the E2EE port reuses the same
shape — only the repository backing changes.

### Phase 3 — Real-time hardening (1 week)
- `:viewmodel`: add `PresenceViewModel` (per-contact online/offline + last seen + typing)
- `:ui:cli`: show online state in `list-contacts`; show typing indicator while `recv` blocks
- `:network:ktor`: client-side WS reconnection with `last_event_id`, exponential backoff
- Server (`:server:plaintext`):
  - WebSocket gateway (Production: auth on upgrade, structured routing, heartbeat, graceful shutdown)
  - WS reconnection + offline message backlog (`last_event_id` contract — design this first)

### Phase 4 — UX polish (2 weeks, pick from the list as needed)
- `:viewmodel`: add screens — edit/delete, reactions, read receipts, mentions, search, block, reply, forward. All reuse the existing `Message` / `Conversation` DTOs from `:domain`; no new DTOs needed unless a feature genuinely needs a new field
- `:ui:cli`: add commands (`edit <id> <body>`, `delete <id>`, `react <id> <emoji>`, `block <user>`, `search <query>`, `reply <id> <body>`, `forward <id> <user>`, `mark-read <id>`, etc.)
- `:ui:tui`: NEW module. `lanterna` for full-screen chat, `Mordant` for any non-lanterna output, `Clikt` for command-and-args. Same `Message` DTOs, same `MessageRepository` — only the view layer changes
- `:client:plaintext`: add a second entry point that wires `:ui:tui` instead of `:ui:cli` (no other change)
- Server (`:server:plaintext`):
  - Edit / delete (MVP + Production)
  - Read receipts (MVP, then Production)
  - Conversation list (Production: pinned, muted, last-message preview)
  - @mentions + unread counters (MVP, then Production)
  - Search (`ILIKE`)
  - Block / unblock user
  - Simple admin role + kick/ban
  - Reply to message, Forward message, Emoji reactions
  - Open invite creation to all users (toggle on `users.can_create_invites`)

### Phase 5 — Groups (1 week)
- `:domain`: add `Group` / `GroupMember` / `GroupRole` DTOs (or extend `Conversation` with `kind=group` + `members`; keep the same DTOs for both messengers so the E2EE port reuses them)
- `:storage:plain`: add `groups`, `group_members` tables; extend `MessageRepository` for group messages
- `:ui:cli` and `:ui:tui`: add group commands / screens (create group, list members, add/remove, role management)
- Server (`:server:plaintext`):
  - Group chats (MVP: schema, CRUD, role on members)
  - Group roles: owner / admin / member
  - Group chats (Production: WS fan-out, system messages, last-admin-leaves, kick + re-invite, denormalized last_message)

### Phase 6 — Rich media (1–2 weeks, on demand)
- `:domain`: add `Attachment` DTO (id + URL + mime + size + thumbnail URL); no plaintext/ciphertext difference at this layer
- `:storage:plain`: add `attachments` table
- `:network:ktor`: support `multipart/form-data` uploads
- `:ui:cli` and `:ui:tui`: add upload/send attachment commands
- Server (`:server:plaintext`):
  - Image / file attachment (MVP)
  - Image / file attachment (Production: mime sniff, auth-gated download, thumbnails)
  - Voice messages (MVP: store WebM/Opus as-is)
  - Voice messages (Production: ffmpeg transcoding, waveform, async worker)

### Phase 7 — Communication features (when asked)
- 2FA (TOTP) — adds a step in `:ui:cli` and `:ui:tui` after password; `AuthRepository.login` returns a "needs-2fa" intermediate state
- Web Push (VAPID) — server-side subscription endpoint; the client adds a one-time "subscribe" call after login. Minimal change outside `:server:plaintext` and `:network:ktor`
- Voice / video calls (WebRTC P2P) — only if asked; needs a new `:ui:gui` module (Compose Multiplatform Desktop) since TUI cannot host WebRTC. Defer until TUI is solid

### Phase 8 — Defer indefinitely
- Polls, pinned messages, threads, story / status, sticker packs, server-side edit history + audit log, federation
- All Hardcore-tier items (multi-region, sharding, CQRS, zero-knowledge, ML spam, media servers, custom load balancer)

### What to skip entirely
- Federation (Matrix-style)
- Sticker / custom emoji packs (use emoji)
- Story / status
- Server-side edit history + audit log (unless legal/compliance need)
- All Hardcore-tier items

### Risks to flag early
1. **Module structure has to be right from day 1.** A `Message` DTO that knows about server-internal types (or a `:server:plaintext` module that depends on `:ui:cli`) will make the later E2EE port expensive. Stick to the "encryption at the boundary" rule; the wrong choice here means a rewrite of `:domain` and `:storage:*`.
2. **WS reconnection contract.** Decide `last_event_id` semantics, replay window, and behavior for 8-hour-offline users *before* writing the WS code. The wrong choice here means a rewrite.
3. **Last-admin-leaves in groups.** The single most common "we shipped groups, and they broke" bug. Test it explicitly in Phase 5.
4. **Local cache + server consistency.** The `:storage:plain` cache is a mirror of the server. When a message arrives over WS, the cache and the ViewModel must update atomically (single write path through the repository, not a parallel DB write and event publish). The wrong choice here leads to flicker, missing messages, or duplicate messages on the client.

### What "done" looks like
- **v1 (ship to community):** Phases 0–3, ~4–5 weeks for one Kotlin dev. Server + working CLI + 1:1 messaging. Stop here for a week of dogfooding (two CLI clients in two terminals). The E2EE messenger is in lockstep: see `docs/minimax-m3-e2ee.md` for the parallel v1.
- **v1.5 (TUI):** Phase 4, ~6–7 weeks total. The CLI is replaced by a full-screen TUI; same server, same `:domain` DTOs, same repositories. Both messengers get the TUI from the same shared module.
- **v2.0 (groups):** Phase 5, ~7–8 weeks total.
- **v2.5 (rich media + 2FA + Web Push):** Phases 6–7, ~10–12 weeks total.
- **Next — iterate on both messengers:** see `docs/minimax-m3-e2ee.md`. The shared UI, `:domain`, `:viewmodel`, and `:network:ktor` are reused; each messenger grows on its own storage and (for E2EE) crypto.
