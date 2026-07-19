# Kotlin E2EE Messenger, general info

## Tech stack

Target: small communities (~20–50 users), single-instance server.
End-to-end encrypted: the server routes and stores only ciphertext.
The client may connect to multiple servers, with fully isolated
per-server identities and data.

### Server

- Kotlin 2.3.21 + kotlinx-coroutines 1.11.0
- Ktor Server 3.5.1 (Netty) — REST + WebSockets
- kotlinx.serialization — JSON envelopes carrying ciphertext payloads
- PostgreSQL — users, session tokens, pre-key directory, message queue
- Exposed + HikariCP — DB access and connection pooling
- Flyway — schema migrations
- HOCON (`application.conf`) — config with env-var overrides (`${?VAR}`)
- Opaque session tokens for auth
- No content logic: the server is a blind relay + public-key directory
  (identity keys, signed pre-keys, one-time pre-keys) + store-and-forward
  message queue

### Client (JVM, terminal-based)

- Ktor Client (CIO engine) with WebSocket support
- libsignal-client (`org.signal:libsignal-client`) — Signal Protocol:
  X3DH session setup, Double Ratchet, Sender Keys (groups), sealed sender;
  JVM bindings with bundled native libs; license AGPL-3.0
- Bouncy Castle — Argon2 (passphrase KDF), AES-GCM
- H2 (embedded, encrypted mode) — per-server local store: message history,
  key material, session token; full-text search over the encrypted file
- Clikt — CLI command/argument parsing
- Mordant — terminal rendering (colors, prompts, live status)
- kotlinx.coroutines — concurrent socket-read / stdin-write loops
- XDG Base Directory layout (`~/.local/share/<app>/servers/<host>/`)

### Infrastructure

- Docker Compose — app + PostgreSQL
- Caddy — reverse proxy with automatic TLS (WSS); access logs minimized
- Single instance: connection registry and message fan-out in memory,
  no Redis or external broker

### Implementation notes

- **Message queue, not archive.** The server's `message_queue` table is
  store-and-forward only: rows deleted on acknowledged delivery, plus a
  TTL (~30 days). Monotonic bigserial IDs serve as delivery cursors —
  `sync { afterId }` drains the queue, it is not a history API.
- **History lives on clients only.** Ratchet message keys are deleted
  after decryption, so re-downloaded ciphertext is undecryptable.
  Plaintext history exists exclusively in the client's local H2 store.
- **H2 password derivation.** The H2 file password is never the raw user
  passphrase: `Argon2(passphrase, per-server salt)` → 32 bytes → hex
  string. This bypasses H2's weak (fast-KDF) internal password handling;
  app-level re-encryption of private keys then becomes optional
  defense-in-depth rather than a necessity.
- **Per-server isolation.** Each server gets its own identity key pair
  (no reuse across servers — prevents cross-server correlation), pre-keys,
  sessions, token, history, and attachments under
  `servers/<canonical-host>/`. One passphrase unlocks all stores via
  per-server derivation (Argon2 with per-server salt, or HKDF from a
  master key with `info = server host`). Leaving a server = deleting one
  directory.
- **Search is client-side.** The server cannot index ciphertext; local
  search runs over the client's H2 full-text index. Page-level encryption
  keeps SQL/FTS working over data that is ciphertext at rest.
- **Attachments.** Encrypted client-side with a random content key; the
  server stores opaque blobs; the key travels inside the E2EE message.
- **Authorization rule.** No query may return data based solely on
  client-supplied IDs — every access is scoped by the authenticated user
  (queue reads by recipient, group operations by membership).
- **Metadata hardening.** Reduce what the server can observe:
  delete-on-delivery + TTL (no history to seize); no message-event
  logging; minimize Caddy/Ktor access logs; pad ciphertext envelopes to
  size buckets; typing indicators and presence sent as E2EE ephemeral
  messages; sealed sender so the server cannot build a sender→recipient
  graph. Remaining exposure: connection-level facts (online timing, IPs,
  traffic volume) — these cannot be hidden server-side; clients may use
  Tor/VPN. E2EE protects content, not the social graph — documented so it
  is never assumed otherwise.
- **E2EE consequences.** Mentions and formatting are parsed client-side
  before encryption; link previews are client-side and opt-in (fetching
  leaks the user's IP to the linked site); a new device starts with no
  history (v1 assumes a single device per user).

## Feature list

Each feature has a stable ID (`E-NN`) referenced in both groupings below.

### By area

#### Core messaging

- **E-01** 1:1 Double Ratchet messaging — the core loop: encrypt → queue → drain → decrypt
- **E-02** Group messaging (Sender Keys) — including sender-key distribution messages
- **E-03** Edit / delete — tombstone events inside E2EE payloads; easier than plaintext F-04: blind server + client-local history means no sync-semantics cost
- **E-04** Replies / quotes — `reply_to_id` inside payload
- **E-05** Reactions — encrypted reaction events

#### Users & presence

- **E-06** Registration + initial key upload — invite token + first pre-key bundle publish
- **E-07** Presence — E2EE'd and opt-in; presence is itself activity metadata
- **E-08** E2EE typing indicators — ephemeral encrypted events
- **E-09** E2EE read receipts — same channel as typing
- **E-10** Multiple server profiles — isolated directories, concurrent connections, per-server cursors

#### Group / community management

- **E-11** Invites, roles, kick/ban — the server never needs content access for these; kick/ban complete only with E-29 re-key (removal without re-keying is security theater)
- **E-12** Pinned messages, read-only channels — pin events are E2EE payloads referencing message IDs

#### Notifications & UX

- **E-13** Mentions — client-side parsing before encryption
- **E-14** Markdown rendering — client-only
- **E-15** Desktop notifications — removed from scope: OS-specific glue, no reliable focus detection in terminal (same reasoning as F-19); mention events (E-13) remain for client-side alerting
- **E-16** Local message cache — decrypted in memory for search/display
- **E-17** Line-based client v1 — TUI deferred until the protocol stabilizes
- **E-18** TUI — scrollback, input line, channel list, verification status indicators

#### Search & content

- **E-19** Local encrypted history + client-side FTS — page-level crypto keeps FTS working over ciphertext at rest
- **E-20** Client-side link previews (opt-in) — removed from scope: poor ROI at this scale (same as F-23); client-side fetch would at least mean no SSRF surface if ever revisited
- **E-21** Server-side search — not merely hard: impossible by design

#### Security

- **E-22** Per-server identity key generation + storage — libsignal generates, encrypted H2 stores
- **E-23** X3DH session establishment — libsignal does the crypto; we wire bundles to sessions
- **E-24** Pre-key bundle publish/fetch (key directory) — server endpoints + one-time pre-key replenishment
- **E-25** Post-quantum key agreement (PQXDH) — free with recent libsignal; just don't disable it; covers key agreement only — ratchet PQ coverage depends on pinned libsignal version
- **E-26** TOFU (trust-on-first-use) for identity keys — store key on first contact, flag changes
- **E-27** Safety number verification — libsignal fingerprints; in-person comparison is realistic at this scale
- **E-28** Key-change warnings — MITM tripwire; pairs with TOFU
- **E-29** Re-key on membership change — rotate sender key when someone leaves
- **E-30** Sealed sender — server cannot build a sender→recipient graph; three subsystems: server-issued sender certificates, delivery tokens, libsignal sealed-sender cipher; scope: hides sender from server in 1:1 delivery only; watch for creep toward Hard
- **E-31** Envelope padding to size buckets — metadata hardening
- **E-32** Queue TTL + delete-on-ack — no server-side message archive exists, period
- **E-33** Log/metadata hygiene — Caddy/Ktor log tuning; the biggest real-world metadata leak; recurring audit, not a one-off — includes dependency logging and prod log levels
- **E-34** Disappearing messages — timer in payload, clients enforce deletion; natural fit (no server archive)
- **E-35** Passphrase unlock — one passphrase, per-server derivation (Argon2 → H2 password)
- **E-36** Passphrase change — re-derive, re-encrypt stores; re-encrypt via copy + swap, not in place (crash mid-re-encrypt risks a corrupt store)
- **E-37** Encrypted key export/backup — device migration path; deferrable
- **E-38** Leave server / crypto-shred — delete directory (salt deletion suffices); client-side only — server-side account/pre-key deletion not covered by any feature (gap)
- **E-39** MLS migration — removed from scope; revisit only if Sender Keys ever become limiting
- **E-40** Cover traffic / timing resistance — the metadata layer that stays exposed

#### Admin / ops

- **E-41** Health endpoint, Postgres backups — backups contain only ciphertext + public keys

#### Transport resilience

- **E-42** Send/receive + acks + queue drain (`sync`) — the encrypted walking skeleton; Medium because the queue is the only delivery path (no history API fallback) and acks land only after decrypt + persist — contrast plaintext F-28/F-29 at Easy
- **E-43** Membership-scoped fan-out — server routes without reading; same authorization rule

#### Attachments

- **E-44** Encrypted attachments — random content key per file, key travels inside the E2EE message

#### Nice-to-have

- **E-45** Multi-device sync — the key-sync problem; v1 = single device per user, bridged later by encrypted key export
- **E-46** Voice/video — terminal client can't use it
- **E-47** Federation — multi-server client is not federation; contradicts the single-instance goal

### By difficulty

#### Trivial

- **E-25** Post-quantum key agreement (PQXDH)
- **E-26** TOFU (trust-on-first-use) for identity keys
- **E-31** Envelope padding to size buckets
- **E-32** Queue TTL + delete-on-ack
- **E-33** Log/metadata hygiene
- **E-38** Leave server / crypto-shred
- **E-41** Health endpoint, Postgres backups

#### Easy

- **E-03** Edit / delete
- **E-04** Replies / quotes
- **E-05** Reactions
- **E-06** Registration + initial key upload
- **E-07** Presence
- **E-08** E2EE typing indicators
- **E-09** E2EE read receipts
- **E-11** Invites, roles, kick/ban
- **E-12** Pinned messages, read-only channels
- **E-13** Mentions
- **E-14** Markdown rendering
- **E-16** Local message cache
- **E-17** Line-based client v1
- **E-22** Per-server identity key generation + storage
- **E-23** X3DH session establishment
- **E-27** Safety number verification
- **E-28** Key-change warnings
- **E-29** Re-key on membership change
- **E-34** Disappearing messages
- **E-35** Passphrase unlock
- **E-36** Passphrase change
- **E-43** Membership-scoped fan-out

#### Medium

- **E-01** 1:1 Double Ratchet messaging
- **E-02** Group messaging (Sender Keys)
- **E-10** Multiple server profiles
- **E-18** TUI
- **E-19** Local encrypted history + client-side FTS
- **E-24** Pre-key bundle publish/fetch (key directory)
- **E-30** Sealed sender
- **E-37** Encrypted key export/backup
- **E-42** Send/receive + acks + queue drain (`sync`)
- **E-44** Encrypted attachments

#### Hard

- _(none)_

#### Very hard

- _(none)_

#### Ignore

- **E-15** Desktop notifications
- **E-20** Client-side link previews (opt-in)
- **E-21** Server-side search
- **E-39** MLS migration
- **E-40** Cover traffic / timing resistance
- **E-45** Multi-device sync
- **E-46** Voice/video
- **E-47** Federation

## Implementation plan

Guiding principle: every milestone ends with a runnable, usable system —
vertical slices, not horizontal layers. The client carries the critical
path; the server stays thin.

Client architecture follows the frontend note
(docs/kimi-k3-frontend-note.md): frontends are thin renderers over
`:client:app-core` — feature logic lives in app-core, never in a frontend.
The E2EE engine is one implementation of the `:client:api` ChatEngine
contract, so the console/TUI frontends and app-core are shared with the
plaintext engine.

### Milestone 0 — Foundations

- Module layout (per frontend note): `:e2ee:server`, `:e2ee:protocol`
  (shared envelopes, version field, ciphertext payload slots); client
  modules (`:client:api`, `:client:app-core`, `:client:engine:e2ee`,
  `:client:frontend:console`) created at M1
- docker-compose (PostgreSQL + app), Flyway, HOCON config, health endpoint
- Server schema v0: `users`, `tokens`, `invites`
- WS connect + token auth, no crypto yet
- Exit: server runs migrations and answers `/health`; client connects and
  authenticates

### Milestone 1 — Encrypted 1:1 walking skeleton

Critical path: identity → key directory → sessions → ratchet → queue
drain.

- Spike first: libsignal-client hello-world (generate keys, encrypt,
  decrypt) before building anything around it — the JVM/native-lib
  integration is the riskiest unknown in the project, kill it early
- Client: identity key generation; encrypted H2 store + Argon2 passphrase
  unlock — with the per-server directory layout from day one (multi-server
  is a storage-structure decision, not a later feature)
- Client modules from day one (per frontend note): `:client:api`
  (ChatEngine contract — events, view models, Capabilities, EngineState;
  pure Kotlin, no toolkit/transport), `:client:app-core` (event→state
  reduction, user intents, unread counts; Flow-based),
  `:client:engine:e2ee` (ChatEngine over WS + libsignal + encrypted H2),
  `:client:frontend:console` (readln + Mordant) — the reference frontend
- Unlock goes through the generic contract — `EngineState = Locked | Ready
  | Failed` + `unlock(credentials: CharArray)` — the same api the
  plaintext engine uses for login; credentials zeroed after use
- Discipline: protocol types never enter UI code; frontends depend only on
  api/app-core types, engine impl wired in the frontend's `main`; expect
  `:client:api` churn until the TUI lands (M4)
- FakeEngine emitting scripted events — UI development and tests without
  a server
- Server: key directory endpoints (publish/fetch pre-key bundles,
  one-time pre-key replenishment)
- X3DH session setup; Double Ratchet 1:1 messages
- Server `message_queue`: bigserial cursors, per-recipient, acks,
  delete-on-ack, TTL
- Line-based client: register, unlock, send, receive, `sync`
- Decisions locked here: TOFU (record identity keys, no UI yet);
  at-least-once delivery + client dedupe by message ID; envelope padding
  (trivial now, painful to retrofit)
- Exit: two clients on two machines exchange E2EE messages; the server DB
  shows only ciphertext; a restarted client drains its queue; local
  history survives restarts

### Milestone 2 — Trust layer

Cheap features, outsized importance — this is what makes "E2EE" a claim
rather than a vibe. Deliberately before groups: group membership amplifies
MITM value.

- Safety numbers (fingerprint display, compare command)
- Key-change warnings (the TOFU tripwire) — warn-and-allow policy per
  frontend note; `/trust <contact>` resets after a verified change
- Verification status surfaced in the client
- Exit: two users verify in person; a server-side key swap (simulated
  MITM) is detected and loudly surfaced

### Milestone 3 — Group messaging

- Sender Keys: group sessions + distribution messages
- Membership management: roles, kick/ban — with re-key on member removal
  (removal without re-keying is security theater)
- Channels/groups API: server routes by membership, never touches content
- Edit/delete/replies/reactions/mentions as encrypted events
- Exit: a group chats end-to-end encrypted; removing a member provably
  rotates keys

### Milestone 4 — Community & client experience

- Invites, read-only channels, pinned messages
- E2EE typing indicators, read receipts, opt-in presence
- Disappearing messages (natural fit — no server archive exists)
- `:client:frontend:tui` — thin, time-boxed renderer over
  `:client:app-core`, with per-contact verification indicators; rendering
  only, no new logic — freezes `:client:api` (the line-based client stays
  fully functional)
- Local FTS search over H2
- Multiple simultaneous server profiles — live in `:client:app-core`, so
  every frontend gets them free (the M1 directory layout pays off here)

### Milestone 5 — Richness & hardening

- Encrypted attachments (content key inside the E2EE message)
- Sealed sender
- Metadata hygiene pass: Caddy/Ktor log audit, retention review
- Encrypted key export/backup — the bridge to future multi-device
- PQXDH verified enabled (should be free — confirm, don't assume)

### Continuous

- Postgres backups from the moment real data exists (they contain only
  ciphertext + public keys)
- Rate limiting — late, low priority in a trusted community
- Multi-device, cover traffic, MLS, desktop notifications, link previews — remain out of scope

### Risk notes

- libsignal JVM integration — neutralized by the M1 spike, before anything
  depends on it
- Ratchet vs. unreliable delivery — out-of-order/duplicate messages
  exercising skipped-key storage; this is what integration tests should
  hammer
- Key-management UX — passphrases, verification, key changes are where
  users actually feel E2EE; the crypto is the easy part
- M1 is the largest milestone by far; M2 is the smallest and the most
  defining
