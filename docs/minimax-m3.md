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

Each feature is listed under the difficulty of its **MVP** version; if it has a meaningfully harder **Production-ready** version, that one is listed in brackets and may appear again in a higher-difficulty section. Features without brackets are pure MVP at that difficulty (the Production version is not a separate tier).

### Trivial
- Health check endpoint
- User registration (MVP: validate invite token, insert user)
- Invites (admin: create / list / revoke; later: all users can create)
- Login → JWT access + refresh (MVP: bcrypt compare, return both tokens)
- Logout / token revocation (MVP: delete refresh token from store)
- Get / update own profile (MVP: text fields only)
- Create 1:1 chat (find-or-create, idempotent)
- Pagination of message history (keyset cursor)
- Online / offline presence (in-process map: add on connect, remove on disconnect)
- Last seen (write `last_seen_at` on disconnect, periodic flush to DB)
- Typing indicator (WS event, debounced on client)
- Backups: cron `pg_dump` to local + rclone to Backblaze B2
- Caddy + automatic Let's Encrypt in front of Ktor

### Easy
- User registration (Production: invite token + bound email + revocation + per-user rate limit on invite creation)
- Login → JWT access + refresh (Production: rate limit, constant-time response, breach check, refresh-token rotation)
- Logout / token revocation (Production: bump session-version claim, optional JTI blocklist for access tokens)
- Get / update own profile (Production: avatar upload, resize, public path)
- Send / fetch text messages (MVP: insert + return, fetch with cursor)
- Conversation list (MVP: list my conversations)
- Conversation list (Production: last-message preview, unread counts, pinned/muted/archived)
- Refresh token rotation (MVP: issue new access from valid refresh)
- Password reset via email (MVP: token in DB, reset endpoint)
- Read receipts (MVP: explicit mark-read endpoint, per-conversation last_read_id)
- Edit / delete own message (MVP: PATCH/DELETE with owner check, "delete for everyone" within time window)
- Edit / delete own message (Production: "(edited)" indicator, edit history table)
- Group chats (MVP: schema + CRUD endpoints, role on members)
- Group roles: owner / admin / member
- @mentions + unread counters (MVP: parse `@username` in body, count unread per conversation)
- @mentions + unread counters (Production: denormalized unread counter, notification fan-out on mention)
- Image / file attachment (MVP: write file to local FS, return id)
- WebSocket gateway (MVP: upgrade, register session, route by message type)
- Idempotency key on `POST /messages` (client UUID, PK conflict returns existing)
- Search in own chats (`ILIKE` is enough at this scale)
- Block / unblock user (MVP: blocks table, filter on read paths)
- Polls in groups (create / vote / close)
- Reply to message (in-conversation; show quoted block)
- Forward message (cross-conversation; show attribution)
- Emoji reactions (separate reactions table, unique on `(message_id, user_id, emoji)`)
- Pinned messages in groups
- 2FA (TOTP) (secret + QR + verify + recovery codes)
- Rate limiting (Caffeine counters, per-user + per-IP)
- Soft delete + "delete for everyone" within N minutes
- Simple admin role + kick/ban
- Voice messages (MVP: client records, uploads; server stores as-is — WebM/Opus plays in any modern browser)
- Link previews (MVP: parse OG tags on send, cache preview by URL hash)
- Server-side edit history + audit log (append-only tables; `GET /messages/:id/history` returns edit list)
- Story / status (24h ephemeral) (schema + `expires_at` index + cleanup cron)

### Medium
- User registration (Production)
- Login → JWT access + refresh (Production)
- Send / fetch text messages (Production: authz, body size, HTML escape, idempotency, WS fan-out, stable ordering)
- Refresh token rotation (Production: theft detection, reuse invalidates family)
- Password reset via email (Production: SMTP infra, token hashing, expiry + single-use, rate limit on reset endpoint)
- Read receipts (Production: per-recipient semantics in groups, "who read", aggregate unread counters)
- Image / file attachment (Production: mime sniff, size limit, auth-gated download, thumbnails, retry-safe upload)
- Voice / video calls (WebRTC P2P, Ktor as signaling server)
- Multi-device: one user, N active WS sessions, fan-out to all
- Web Push notifications (VAPID)
- Threads / reply-to-message (parent_id + recursive fetch, "x new replies" tracking)
- WebSocket reconnection + offline message backlog on reconnect
- Voice messages (Production: ffmpeg transcoding, waveform generation, async worker, multi-format storage)
- Sticker / custom emoji packs (pack + sticker tables, user subscriptions, moderation, image processing)

### Hard
- WebSocket gateway (Production: auth on upgrade, structured routing, heartbeat, reconnection with last-event-id, backpressure, multi-device fan-out, graceful shutdown)
- Group chats (Production: WS fan-out to N members, system messages, last-admin-leaves, kick + re-invite, denormalized last_message)
- Link previews (Production: SSRF protection — block private IP ranges, DNS-pinning, isolated fetch worker, redirect refusal)
- Federation (Matrix-style) — only if explicitly desired

### Hardcore
- Multi-region, sharding, CQRS, zero-knowledge architecture, ML spam, media servers (LiveKit/mediasoup), custom load balancer. None justified at 10–50 users.

--- 

## Implementation plan

Guiding principles: foundation first (schema and auth before anything else); security at launch, not later (Production auth bundled with MVP); smallest usable messenger, then iterate; defer polish.

### Phase 0 — Skeleton (1 day)
- Gradle (Kotlin DSL), Ktor app, config
- PostgreSQL + Flyway baseline
- Health check, structured logging, basic `/metrics`
- Caddy in front, HTTPS, domain
- **Bootstrap admin:** seed the first user via env var on first startup (e.g. `BOOTSTRAP_ADMIN_EMAIL` + `BOOTSTRAP_ADMIN_PASSWORD`). If `users` is empty on boot, create the admin and skip on subsequent starts. The admin cannot invite themselves, so this step is required.

### Phase 1 — Auth and transport (2–3 days, security-critical, all-or-nothing)
- Invites (admin: create / list / revoke; bound-email)
- User registration (MVP + Production together: validate token, insert user, bound email, revocation, per-user rate limit on invite creation)
- Login → JWT access + refresh (MVP + Production: rate limit, constant-time, breach check)
- Refresh token rotation (MVP + Production: theft detection, family invalidation)
- Logout / token revocation (MVP + Production: session version bump)
- Password reset via email (MVP, then Production with SMTP)
- Rate limiting on auth endpoints
- Caddy + HTTPS in front of Ktor

### Phase 2 — Basic 1:1 messaging (1 week)
- Schema: `conversations` (kind=direct), `conversation_members`, `messages`
- Create 1:1 chat (find-or-create, idempotent)
- Send / fetch text messages (MVP + Production together: auth, body size, escape, idempotency, stable ordering)
- Pagination (keyset cursor)
- WebSocket gateway (MVP: upgrade, register session, route by type)
- Conversation list (MVP)
- Online / offline presence + last seen + typing indicator
- Profile (text + avatar)

**Stop here for a week of dogfooding** before going further.

### Phase 3 — Real-time hardening (1 week)
- WebSocket gateway (Production: auth on upgrade, structured routing, heartbeat, graceful shutdown)
- WS reconnection + offline message backlog (`last_event_id` contract — design this first)
- Multi-device: one user, N active sessions, fan-out to all

### Phase 4 — UX polish (1 week, pick from the list as needed)
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
- Group chats (MVP: schema, CRUD, role on members)
- Group roles: owner / admin / member
- Group chats (Production: WS fan-out, system messages, last-admin-leaves, kick + re-invite, denormalized last_message)

### Phase 6 — Rich media (1–2 weeks, on demand)
- Image / file attachment (MVP)
- Image / file attachment (Production: mime sniff, auth-gated download, thumbnails)
- Voice messages (MVP: store WebM/Opus as-is)
- Voice messages (Production: ffmpeg transcoding, waveform, async worker)
- Link previews (MVP: OG-tag fetch + cache)
- Link previews (Production: SSRF protection — isolated fetch worker, IP-range block, DNS-pinning)

### Phase 7 — Communication features (when asked)
- Web Push (VAPID)
- 2FA (TOTP)
- Voice / video calls (WebRTC P2P)

### Phase 8 — Defer indefinitely
- Polls, pinned messages, threads, story / status, sticker packs, server-side edit history + audit log, federation
- All Hardcore

### What to skip entirely
- Federation (Matrix-style)
- Sticker / custom emoji packs (use emoji)
- Story / status
- Server-side edit history + audit log (unless legal/compliance need)
- All Hardcore

### Risks to flag early
1. **WS reconnection contract.** Decide `last_event_id` semantics, replay window, and behavior for 8-hour-offline users *before* writing the WS code. The wrong choice here means a rewrite.
2. **Multi-device fan-out.** Get it right in Phase 3; otherwise you'll fix fan-out bugs under load once users have 2+ devices.
3. **Last-admin-leaves in groups.** The single most common "we shipped groups and they broke" bug. Test it explicitly in Phase 5.

### What "done" looks like
- **v1 (ship to community):** Phases 0–3, ~2–3 weeks for one Kotlin dev.
- **v1.5 (after dogfooding):** Phases 4–5, another ~2–3 weeks.
- **v2 (on demand):** Phases 6–7.
