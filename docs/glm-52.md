# GLM-52 Messenger

## Tech stack

### Server
- **Ktor (Netty engine)** — HTTP/WebSocket server
- **WebSockets** — real-time message delivery
- **PostgreSQL + Exposed** — persistence
- **HikariCP** — JDBC connection pooling
- **Flyway** — schema migrations
- **Kotlin Serialization (JSON)** — message encoding
- **JWT (ktor-server-auth-jwt)** — authentication
- **Gradle (Kotlin DSL)** — build tooling

### Deployment
- **Docker Compose** — orchestrates `postgres`, `server`, and the TLS reverse proxy
- **Caddy** — TLS termination with automatic Let's Encrypt certificates
- `.env` file for DB credentials, JWT secret, domain

### Shared
- **Kotlin Serialization** models reused across server and client to keep the wire protocol in sync

### Client (JVM console)
- **Ktor Client (CIO engine)** — WebSocket/HTTP client (`wss://` endpoint)
- **kotlinx-cli** — argument parsing and interactive options
- **BufferedReader input loop** — console interaction

## Features

Feature IDs are stable references used across both groupings below.

### By area

**Core messaging**
- F1 — One-to-one DMs
- F2 — Group chats / channels
- F3 — Message history (persisted in PostgreSQL)
- F4 — Offline message delivery (queue and deliver on connect)
- F5 — Message edit / delete (with tombstones)
- F6 — Message threading / replies
- F7 — Emoji reactions
- F8 — @mentions with notifications

**Users & presence**
- F9 — Registration / login
- F10 — Online / offline / away presence
- F11 — Last seen timestamps
- F12 — Typing indicators
- F13 — Read receipts ("seen" markers)
- F14 — User profile (display name, avatar, bio)
- F42 — WS heartbeat / keepalive (dead-socket detection drives presence pruning)

**Group / community management**
- F15 — Public and invite-only groups
- F16 — Invite links (with revocation)
- F17 — Group metadata (name, description, avatar)
- F18 — Roles: owner, admin, member; permission gating (promote/kick/mute)
- F19 — Member join/leave notifications
- F20 — Pinned messages in channels

**Notifications & UX**
- F21 — Desktop notifications on new messages
- F22 — Notification mute per-channel or globally
- F23 — Unread badge counts per chat
- F36 — Bookmarks / saved messages

**Search & content**
- F24 — Full-text search across messages
- F25 — Message pagination / infinite scroll (history loading)
- F26 — Code block formatting (monospace rendering in console)
- F27 — Markdown subset support (bold, italic, code)

**Security**
- F28 — JWT auth with token refresh
- F29 — Message content validation / length limits
- F30 — Rate limiting per user (prevent spam)

**Nice-to-have**
- F32 — Voice-message-like clips (binary blobs)
- F34 — Tags / topics for grouping channels
- F35 — Polls in group chats

**Admin / ops**
- F33 — Broadcast announcements to all members
- F37 — Server-side console health view (connected users, channels)
- F38 — Server graceful shutdown notification to clients
- F39 — User ban / suspension by admin

**Transport resilience**
- F40 — Auto-reconnect with exponential backoff
- F41 — Server queue TTL indicator (visible deadline for undelivered messages)

**Attachments**
- F31 — File / image sharing (upload via REST, reference in WS messages)

### By difficulty

**Trivial**
- F9 — Registration / login
- F11 — Last seen timestamps
- F19 — Member join/leave notifications
- F26 — Code block formatting
- F29 — Message content validation / length limits
- F40 — Auto-reconnect with exponential backoff
- F41 — Server queue TTL indicator
- F42 — WS heartbeat / keepalive

**Easy**
- F1 — One-to-one DMs
- F3 — Message history
- F7 — Emoji reactions
- F10 — Online / offline / away presence
- F12 — Typing indicators
- F14 — User profile
- F17 — Group metadata
- F20 — Pinned messages
- F22 — Notification mute
- F23 — Unread badge counts
- F25 — Message pagination
- F27 — Markdown subset support
- F28 — JWT auth with token refresh
- F33 — Broadcast announcements
- F36 — Bookmarks / saved messages
- F37 — Server console health view

**Medium**
- F2 — Group chats / channels
- F4 — Offline message delivery
- F5 — Message edit / delete (with tombstones)
- F6 — Message threading / replies
- F8 — @mentions with notifications
- F13 — Read receipts
- F15 — Public and invite-only groups
- F16 — Invite links (with revocation)
- F24 — Full-text search across messages
- F30 — Rate limiting per user
- F31 — File / image sharing
- F34 — Tags / topics for grouping channels
- F38 — Server graceful shutdown notification
- F39 — User ban / suspension by admin

**Hard**
- F18 — Roles: owner, admin, member; permission gating

**Very hard**
- (none)

**Ignore**
- F21 — Desktop notifications (impractical in a pure JVM console client)
- F32 — Voice-message-like clips (binary audio does not fit the console scope)
- F35 — Polls in group chats (out of scope for a minimal community messenger)

## Implementation plan

Each milestone is independently shippable. After M2 there is a usable toy messenger, after M6 a real one, and M7/7.5 turn it into something trustworthy for a community.

### M0 — Scaffolding & infra
- Gradle multi-module per `docs/glm-52-frontend-note.md`: `:shared`, `:shared-protocol`, `:backend:api` (+ `FakeChatClient`), `:backend:plain`, `:frontend:console`, `:client-app`, `:server`
- Shared serialization model skeletons (`:shared-protocol`)
- `ChatClient` interface in `:backend:api` (baseline methods only)
- Ktor server boots, `/health` endpoint
- `docker-compose.yml` with `postgres`, `server`, `caddy` (self-signed certs initially)
- Flyway baseline migration
- CI: `./gradlew build` + tests (per-module, independent)

### M1 — Auth & user foundation
- Register / login endpoints (REST)
- JWT issue + refresh, password hashing (Argon2 or PBKDF2)
- WS connect handshake with JWT auth
- User table, basic profile (display name)
- Features: F9, F28, F14

### M2 — DM core (vertical slice)
- One-to-one WS message exchange between online clients
- Messages stored in PostgreSQL, delivered via WS
- `GET /messages?chatId=...` history endpoint with pagination
- Client: login, list users, open DM, send/receive, scroll history
- Features: F1, F3, F25

### M3 — Presence & real-time polish
- Online/offline/away presence on connect/disconnect, driven by WS heartbeat
- Typing indicators
- Last seen timestamps
- Offline message delivery (queue table, flush on connect)
- Auto-reconnect with exponential backoff
- Server queue TTL indicator (visible deadline for undelivered messages)
- Features: F4, F10, F11, F12, F40, F41, F42

### M4 — Group chats
- Group CRUD, add/remove members
- WS fan-out to multiple members
- Group metadata (name, description)
- Member join/leave notifications
- Public vs invite-only groups, invite links with revocation
- Features: F2, F15, F16, F17, F19

### M5 — Message actions
- Edit message (with history row)
- Delete with tombstones
- Threading / replies
- Emoji reactions
- Read receipts (seen markers)
- Features: F5, F6, F7, F13

### M6 — Discovery & search
- @mentions with notifications
- Full-text search across messages (Postgres `tsvector`)
- Pinned messages in channels
- Unread badge counts
- Notification mute per-channel
- Features: F8, F20, F22, F23, F24

### M7 — Roles & admin
- Roles: owner / admin / member
- Permission gating for promote / kick / mute
- User ban / suspension
- Features: F18, F39

### M7.5 — Hardening & ops
- Rate limiting per user
- Message content validation / length limits
- Broadcast announcements
- Server graceful shutdown notification
- Server health view (connected users, channels)
- Code block + markdown rendering in client
- Bookmarks / saved messages
- Features: F26, F27, F29, F30, F33, F36, F37, F38

### Later / optional
- Deferred until core is stable:
  - F31 — File / image sharing
  - F34 — Tags / topics for grouping channels