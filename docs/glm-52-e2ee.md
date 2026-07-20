# GLM-52 E2EE Messenger

## Tech stack

### Server
- **Ktor (Netty engine)** — HTTP/WebSocket server
- **WebSockets** — real-time message delivery
- **PostgreSQL + Exposed** — persistence (per-server user identity keys, prekeys, transient message queue)
- **HikariCP** — JDBC connection pooling
- **Flyway** — schema migrations
- **Kotlin Serialization (JSON)** — wire protocol envelope encoding
- **JWT (ktor-server-auth-jwt)** — user-to-server transport auth
- **Gradle (Kotlin DSL)** — build tooling

### Shared
- **Kotlin Serialization** models reused across server and client to keep the wire protocol in sync

### Client (JVM console)
- **Ktor Client (CIO engine)** — WebSocket/HTTP client (`wss://` endpoint); can connect to multiple servers / communities
- **kotlinx-cli** — argument parsing
- **BufferedReader input loop** — console interaction

### Client crypto
- **libsignal (org.signal:libsignal)** — Signal Protocol: X3DH, Double Ratchet, Sesame
- **Per-server identity keys** — distinct identity key pair per server/community the client connects to (no cross-server correlation)
- **Per-server prekeys and ratchet sessions** — all session state scoped by `serverId/chatId`
- **JDK KeyStore (PKCS12)** — stores identity, prekey, and ratchet session material; unlocked with an Argon2id-derived key from the user passphrase (Argon2 library TBD)
- **H2 (encrypted mode)** — local decrypted message history, keyed by an Argon2id-derived DB key held in the keystore

### Deployment
- **Docker Compose** — orchestrates `postgres`, `server`, and the TLS reverse proxy
- **Caddy** — TLS termination with automatic Let's Encrypt certificates
- `.env` file for DB credentials, JWT secret, domain

## Features

Feature IDs (E1–E54) are stable references used across both groupings below.

### By area

**Core messaging**
- E1 — One-to-one DMs (one ratchet session per pair)
- E2 — Group chats / channels (pairwise fan-out, one ciphertext per recipient)
- E3 — Local message history (encrypted H2; nothing on server after delivery)
- E4 — Offline delivery via transient server queue
- E5 — Message edit (new ciphertext referencing original)
- E6 — Soft delete (retract to peers) / local-only delete
- E7 — Threading / replies (envelope metadata)
- E8 — Emoji reactions
- E9 — @mentions with notifications (client parses decrypted text)
- E10 — Disappearing messages (client-side timer deletes from local H2 after TTL)
- E11 — Send / delivered / read receipts (end-to-end signals)
- E50 — Pending outbox (compose while offline, flush on reconnect)
- E51 — Message dedup by envelope ID (idempotency on reconnect replay)

**Users & presence**
- E12 — Registration / login (user-to-server JWT; server never learns long-term identity)
- E13 — User profile (display name, avatar, bio) E2E-encrypted to contacts
- E14 — Online / offline / away presence (server-side routing metadata)
- E15 — Last seen timestamps
- E16 — Typing indicators (ephemeral, not persisted)
- E52 — WS heartbeat / keepalive (dead-socket detection drives presence pruning)

**Group / community management**
- E17 — Public and invite-only groups
- E18 — Invite links with revocation
- E19 — Group metadata (name, description, avatar) E2E-encrypted; rotates on membership change
- E20 — Roles: owner / admin / member; permission gating
- E21 — Member join / leave notifications
- E22 — Pinned messages (signed by admin)

**Notifications & UX**
- E31 — Per-channel / global notification mute
- E32 — Unread badge counts (client-side tally)
- E33 — Bookmarks / saved messages (local-only)

**Search & content**
- E34 — Full-text search across local history (H2 query; no server search)
- E35 — Message pagination (client-side scrolling through local DB)
- E36 — Markdown subset rendering (bold, italic, code)
- E37 — Code block formatting (monospace in console)

**Security**
- E23 — Safety number display and out-of-band verification
- E24 — Identity key change alerts (TOFU mismatch warnings); triggers a fresh X3DH handshake and ratchet session reset
- E25 — Key fingerprint display per contact
- E29 — Trust-on-first-use indicators (unverified / verified / mismatch)
- E30 — Out-of-band QR key verification exchange (small community in-person)

**Admin / ops**
- E38 — User ban / suspension by admin (behavioral gate; no content visibility)
- E39 — Rate limiting per user (metadata only)
- E40 — Server graceful shutdown notification
- E41 — Server-side console health view (connected users, queue depth, cert expiry)
- E42 — Broadcast announcements (E2E to all members individually)

**Transport resilience**
- E53 — Auto-reconnect with exponential backoff
- E54 — Server queue TTL indicator (visible deadline for undelivered messages)

**Attachments**
- E43 — File / image sharing with TTL and size cap (server holds ciphertext transiently)
- E44 — "View once" messages (delete on read, both sides)
- E48 — Image preview via external OS viewer (TUI shows a stub/thumbnail; on interact, client writes decrypted bytes to a temp file and launches `xdg-open` / `open` / viewer; temp file on `tmpfs` to avoid plaintext leaking to disk; viewer trust is a minor concern with rare image-format exploits)
- E49 — Attachment download progress indicator

**Nice-to-have**
- E45 — Desktop notifications on new messages
- E46 — Voice-message clips
- E47 — Polls in group chats

### By difficulty

**Trivial**
- E12 — Registration / login (user JWT)
- E15 — Last seen timestamps
- E16 — Typing indicators
- E21 — Member join / leave notifications
- E32 — Unread badge counts
- E35 — Message pagination (client-side)
- E37 — Code block formatting
- E40 — Server graceful shutdown notification
- E52 — WS heartbeat / keepalive
- E53 — Auto-reconnect with exponential backoff
- E54 — Server queue TTL indicator

**Easy**
- E1 — One-to-one DMs (libsignal does the heavy lifting)
- E3 — Local message history
- E4 — Offline delivery via transient queue
- E7 — Threading / replies
- E8 — Emoji reactions
- E9 — @mentions (client parse)
- E14 — Online / offline / away presence
- E22 — Pinned messages
- E25 — Key fingerprint display
- E29 — TOFU indicators
- E31 — Notification mute
- E33 — Bookmarks
- E36 — Markdown subset
- E42 — Broadcast announcements
- E43 — File / image sharing with TTL
- E44 — "View once" messages
- E48 — Image preview via external OS viewer
- E49 — Attachment download progress indicator
- E51 — Message dedup by envelope ID

**Medium**
- E2 — Group chats (pairwise fan-out)
- E5 — Message edit
- E6 — Soft delete / retract
- E10 — Disappearing messages
- E11 — Read receipts
- E13 — E2E-encrypted profile
- E17 — Public / invite-only groups
- E18 — Invite links with revocation
- E19 — Group metadata E2E-encrypted with rotation
- E23 — Safety number display
- E30 — QR key verification
- E34 — Full-text search local
- E38 — User ban / suspension
- E39 — Rate limiting
- E41 — Server health view
- E50 — Pending outbox (compose offline, flush on reconnect)

**Hard**
- E20 — Roles + permission gating (server + E2E signing)
- E24 — Identity key change alerts (detection and handling); triggers a fresh X3DH + ratchet session reset

**Very hard**
- (none)

**Ignore**
- E45 — Desktop notifications (impractical in a pure JVM console client)
- E46 — Voice-message clips (binary audio does not fit the console scope)
- E47 — Polls in group chats (out of scope for a minimal community messenger)

## Implementation plan

Each milestone is independently shippable. The order differs from the plaintext plan because crypto setup has to land before any messaging works, and verification/key hygiene is elevated before roles/admin (in an E2EE messenger, verifying your contacts' keys is what makes the crypto story meaningful).

### M0 — Scaffolding & infra
- Gradle multi-module per `docs/glm-52-frontend-note.md`: `:shared`, `:shared-protocol`, `:backend:api` (+ `FakeChatClient`), `:backend:e2ee`, `:frontend:console`, `:client-app`, `:server`
- Wire-protocol envelope models (`kotlinx.serialization`) in `:shared-protocol`
- `ChatClient` interface in `:backend:api` (baseline methods only); `E2eeCapabilities` interface stubbed
- Ktor server boots, `/health` endpoint
- `docker-compose.yml`: `postgres`, `server`, `caddy` (self-signed certs initially)
- Flyway baseline migration
- CI: `./gradlew build` + tests (per-module, independent)

### M1 — Auth & crypto foundation
- Server tables: `users`, `identity_keys`, `signed_prekeys`, `one_time_prekeys`
- Endpoints: `POST /keys/identity`, `POST /prekeys`, `GET /prekeys/{userId}`, `POST /prekeys/claim`
- Client first-run onboarding: generate per-server identity key pair, upload to chosen server, generate + upload prekey batch
- PKCS12 keystore on disk, unlocked with an Argon2id-derived key from user passphrase (Argon2 lib TBD)
- JWT issue/refresh for user-to-server transport auth
- WS connect handshake with JWT
- Features: E12

### M2 — DM core (vertical slice proving the crypto)
- X3DH session setup using fetched prekey bundles
- Double Ratchet message exchange over WS between two online clients
- Server transient `message_queue` table; delivery + ACK + delete flow
- Client local H2 (encrypted), with DB key held in keystore — store decrypted messages, chat list
- `GET /queue` backlog fetch on reconnect; offline delivery
- Message dedup by envelope ID (ratchet can replay on reconnect)
- Client: login, list users, open DM, send/receive, scroll local history
- Features: E1, E3, E4, E35, E51

### M3 — Presence, typing, outbox
- Online/offline/away presence on connect/disconnect, driven by WS heartbeat
- Last seen timestamps
- Typing indicators (ephemeral queue messages, not persisted)
- Pending outbox: compose while offline, flush on reconnect
- Auto-reconnect with exponential backoff
- Server queue TTL indicator (visible deadline for undelivered messages)
- Features: E14, E15, E16, E50, E52, E53, E54

### M4 — Groups (pairwise fan-out)
- Group CRUD, add/remove members
- Pairwise fan-out: one ciphertext per recipient per message
- Group metadata (name, description, avatar) E2E-encrypted with a shared session key that rotates on membership change
- Public vs invite-only groups, invite links with revocation
- Member join/leave notifications
- Pinned messages (signed by an admin)
- Features: E2, E17, E18, E19, E21, E22

### M5 — Message actions
- Edit (new ciphertext referencing original; tombstone the old locally)
- Soft delete (retract signal to peers) / local-only delete
- Threading / replies (envelope metadata)
- Emoji reactions
- Send / delivered / read receipts (end-to-end signals)
- Disappearing messages (client-side timer deletes from local H2 after TTL)
- Features: E5, E6, E7, E8, E10, E11

### M6 — Verification & key hygiene
- Safety number display and out-of-band verification
- Identity key change alerts (TOFU mismatch warnings) — UI blocks sending until user acks
- Key fingerprint display per contact
- Trust-on-first-use indicators (unverified / verified / mismatch)
- Out-of-band QR key verification exchange
- Per-chat session reset
- Features: E23, E24, E25, E29, E30

### M7 — Roles & admin
- Roles: owner / admin / member
- Permission gating (server enforces *who can send/mutate*, E2E signing enforces *who can mutate group metadata*)
- User ban / suspension by admin (behavioral gate; admin cannot read content)
- Rate limiting per user (metadata only)
- Broadcast announcements (E2E to all members individually)
- Features: E20, E38, E39, E42

### M7.5 — Contact & UX polish
- E2E-encrypted profile (display name, avatar, bio) to contacts
- @mentions with notifications (client parses decrypted text)
- Unread badge counts (client-side tally)
- Per-channel / global notification mute
- Bookmarks / saved messages (local-only)
- Full-text search across local history (H2 query)
- Markdown subset rendering (bold, italic, code)
- Code block formatting (monospace in console)
- Server graceful shutdown notification
- Server-side console health view (connected users, queue depth, cert expiry)
- Features: E9, E13, E31, E32, E33, E34, E36, E37, E40, E41

### M8 — Attachments
- File / image sharing: client encrypts file with a random 256-bit symmetric key K (AES-256-GCM), uploads ciphertext blob to server's attachment endpoint with TTL, then sends a normal E2EE ratchet message whose decrypted payload contains `{blob_id, key: K, filename, mime, size}`. The ratchet protects K — only the recipient can recover it
- For groups: one K reused for the blob, but K encrypted once per recipient (pairwise fan-out, same as text). Storage stays at one ciphertext blob + N tiny key-delivery messages
- Server holds two ciphertexts: large blob (own TTL, e.g. 24 hours) + tiny ratchet message in transient queue (deleted on ACK)
- Size cap (e.g. 25 MB)
- "View once" messages (delete on read, both sides)
- Attachment download progress indicator
- Features: E43, E44, E49

### Later / optional
- E48 — Image preview via external OS viewer — easy, defer until client platform settles
- E45 — Desktop notifications — ignore (console client)
- E46 — Voice-message clips — ignore
- E47 — Polls — ignore