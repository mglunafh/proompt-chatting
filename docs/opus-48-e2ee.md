# Kotlin Messenger — E2EE variant

## Tech stack

An end-to-end-encrypted messenger, sized for a small community (~10–50 users):
single server process, one Postgres database, no high-availability / high-load /
global-scale infrastructure.

The **server is a dumb relay**: it routes ciphertext it cannot read, serves a
public-key (prekey) directory, and stores encrypted blobs for offline delivery.
All cryptography lives **client-side, in `:client:core-e2ee`**; the server never links
a crypto library. Two decisions bound the design:

- **One device per user.** No multi-device, no device linking. A reinstall is a
  *fresh* identity — device loss means identity loss (contacts must re-verify)
  and history loss (absent an optional encrypted backup). Accepted tradeoff at
  this scale.
- **One client per user per machine.** `:client:core-e2ee` takes an exclusive lock on
  its keystore at startup; a second client for the same user refuses to start,
  so the single mutable Double Ratchet state always has one writer.

- **Kotlin 2.3.21** — implementation language for server and client.
- **Gradle 9.5** — build tool with version catalog; Shadow plugin for the fat JAR.
- **Ktor 3.5.1** — server framework.
- **Netty** (`ktor-server-netty`) — engine hosting the Ktor app and WebSocket connections.
- **WebSockets** (Ktor plugin) — real-time transport carrying opaque ciphertext frames.
- **REST over HTTP** — request/response surface for login, prekey publish/fetch, history backfill, encrypted-blob upload.
- **kotlinx-coroutines 1.11.0** — concurrency model.
- **kotlinx.serialization** — JSON encoding of the message envelope: routing metadata plus the ciphertext it wraps.
- **PostgreSQL 17** — database; stores ciphertext, prekey bundles, and metadata. Named Docker volume for durability.
- **Exposed** — Kotlin SQL framework for database access.
- **Flyway** — schema migrations under version control.
- **In-process connection registry** (`ConcurrentHashMap<UserId, Session>`) — routes envelopes to the online recipient; one session per user, since a user has a single device.
- **In-memory ephemeral state** — presence and typing indicators, not persisted.
- **JWT** (Ktor `auth-jwt`) + **Argon2id** (`com.password4j:password4j`) — transport auth tokens and account-password hashing (the account login, separate from the cryptographic identity keys below).
- **`libsignal` (`org.signal:libsignal-client`)** — the E2EE engine: X3DH handshake, Double Ratchet (1:1), Sender Keys (groups), prekey bundles, and safety-number generation. Rust core with JVM bindings; lives in `:client:core-e2ee`. The server never links it.
- **libsignal key stores** — `IdentityKeyStore` / `PreKeyStore` / `SignedPreKeyStore` / `SessionStore` implementations in `:client:core-e2ee`, backed by a single local keystore file (SQLite or flat file), single-writer under the startup lock.
- **`lazysodium-java` (libsodium)** — two supporting jobs: (1) derive the keystore-encryption key from the user passphrase (`crypto_pwhash`, Argon2id) and encrypt the keystore at rest with XChaCha20-Poly1305; (2) encrypt attachment blobs client-side, with the per-file key carried inside the encrypted message.
- **Server-side prekey directory** — Postgres tables + REST endpoints to publish and fetch each user's identity key, signed prekey, and one-time prekey bundles, and to signal one-time-prekey exhaustion for replenishment. One device per user keeps each directory entry a single bundle.
- **Startup keystore lock** — an exclusive OS file lock in the keystore directory; a second `:client:core-e2ee` for the same user detects it and refuses to start, guaranteeing a single writer to the mutable ratchet state.
- **BouncyCastle** — general X.509 / primitive support (likely already transitive); not the E2EE engine.
- **Koin** — dependency injection (optional).
- **Caddy** — reverse proxy terminating client↔server HTTPS/`wss://` with automatic Let's Encrypt certificates. Complements the E2EE layer: TLS protects routing metadata and the prekey exchange, the E2EE layer protects payloads.
- **Docker Compose** — deployment with ~6 services: `db`, `server`, `proxy`, `prometheus`, `loki`, `grafana`.
- **`:protocol` Gradle module** — envelope DTOs (routing metadata + ciphertext + prekey types) shared by server and all clients.
- **`:client:api` Gradle module** — the `ChatEngine` interface and domain model; the only module the frontends depend on, which keeps libsignal off their compile path entirely.
- **Ktor client** — `:client:core-e2ee` networking (WebSocket + REST); connects via `wss://`, not part of compose.
- **Clikt** — command parsing for the `:client:console` frontend.
- **Mosaic** — Compose-for-terminal UI for the `:client:tui` frontend.
- **Compose Desktop** — GUI for the deferred `:client:gui` frontend.
- **Testcontainers + kotest + Ktor `testApplication`** — integration tests against real Postgres.
- **Logback** (SLF4J) + **logstash-logback-encoder** — logging to stdout as structured JSON (metadata only; message contents never reach the server in the clear).
- **Micrometer** (`ktor-server-metrics-micrometer`, Prometheus registry) — in-app instrumentation exposing `GET /metrics`.
- **Prometheus** — scrapes and stores metrics time series.
- **Grafana Loki** — lightweight log store; container stdout shipped via the Docker Loki logging driver (no extra agent).
- **Grafana** — single dashboard over both data sources (Prometheus metrics + Loki logs).

## Feature list

Feature IDs (`E-NN`) are stable references, assigned in area-blocked ranges with
gaps left for later insertions. The two subsections below group the same set of
features — first by area, then by implementation difficulty.

### By area

#### Core messaging

- **E-01 Direct messages** — encrypted 1:1 over a Double Ratchet session.
- **E-02 Channel / group messages** — encrypted group send via Sender Keys.
- **E-03 Message durability (store-and-forward)** — server persists ciphertext *transiently*, only until the recipient's device acks receipt, then deletes it; the DB is the source of truth for delivery, never for content or history. The client's local decrypted store is the real history.
- **E-04 Offline delivery** — hold ciphertext for offline recipients, deliver on reconnect.
- **E-05 Missed-message delivery** — on reconnect, drain the queue of ciphertext buffered while offline (up to the last acked `seq`); not arbitrary history re-fetch, since acked messages are already gone from the server.
- **E-06 Edit own message** — an edit is a new encrypted message referencing the original id.
- **E-07 Delete own message** — tombstone; server drops the ciphertext, clients render the deletion.
- **E-08 @mentions** — client-side, over decrypted content.
- **E-09 Replies / threading** — parent id travels inside the encrypted payload.

#### Users & presence

- **E-10 Registration** — create an account (username + password).
- **E-11 Login** — authenticate over REST, receive a JWT.
- **E-12 Authenticated WebSocket** — open the live connection with the JWT.
- **E-13 Logout / token invalidation** — end a transport session.
- **E-14 Online/offline presence** — who is currently connected (server-visible metadata).
- **E-15 Typing indicators** — ephemeral signal; leaks metadata to the server unless itself encrypted.
- **E-16 User profile** — display name.
- **E-17 Avatar** — profile image.
- **E-18 Custom status** — free-text status.
- **E-19 Block user** — suppress a specific user.

#### Group / community management

- **E-20 Create channel** — start a new channel.
- **E-21 Join / leave channel** — manage own membership; triggers a group re-key (E-55).
- **E-22 Rename / describe channel** — edit channel metadata.
- **E-23 Archive / delete channel** — retire a channel.
- **E-24 Channel roles** — owner / admin / member permissions.
- **E-25 Invite / add members** — new member receives the group key; re-key (E-55).
- **E-26 Kick / remove members** — must re-key so a removed member can't read future messages (post-compromise security); a real crypto cost, not just a DB delete.
- **E-27 Private channels** — invite-only visibility.
- **E-28 Group DMs** — ad-hoc multi-user conversation without a formal channel.

#### Notifications & UX

- **E-30 Delivery receipts** — mark a message delivered to a recipient.
- **E-31 Read markers** — per-user, per-conversation read position.
- **E-32 Unread counts** — per-conversation unread tally.
- **E-33 Mention notifications** — alert on @mention (computed client-side).
- **E-34 Console bell / notification hook** — terminal notification on new message.
- **E-35 Mute / notification preferences** — per-conversation mute.

#### Search & content

- **E-40 Client-side search** — over locally decrypted history; the server cannot index content.
- **E-41 Pinned messages** — pin important messages in a channel.
- **E-42 History export** — decrypted dump from a client to a file.

#### Cryptography & key management

- **E-50 Identity key generation** — long-term identity keypair created on first run.
- **E-51 Prekey publishing** — upload signed prekey + one-time prekeys to the directory.
- **E-52 X3DH session establishment** — fetch a contact's bundle and set up a session.
- **E-53 Double Ratchet messaging** — per-message forward-secret ratcheting for 1:1.
- **E-54 Sender Keys** — group session-key distribution for channel/group encryption.
- **E-55 Group re-keying on membership change** — rotate the group key on add/remove/leave.
- **E-56 One-time prekey replenishment** — detect exhaustion, top up the directory.
- **E-57 Safety-number verification** — fingerprint compare, out-of-band, for MITM protection.
- **E-58 Identity-key-change detection** — warn when a contact's identity key changes.
- **E-59 Local keystore protection** — at-rest encryption (passphrase-derived key) + single-writer startup lock.

#### Account & transport security

- **E-60 TLS transport** — HTTPS/`wss://` via Caddy (protects metadata + prekey exchange).
- **E-61 Account password hashing** — Argon2id via password4j.
- **E-62 JWT refresh / rotation** — transport-token renewal.
- **E-63 Account password reset / change** — self-service credential change.
- **E-64 Keystore passphrase change** — re-encrypt the local keystore under a new passphrase (distinct secret from the account password).
- **E-65 Account lockout** — throttle brute-force login attempts.
- **E-66 Audit log** — metadata-only security events (no message content exists to log).

#### Admin / ops

- **E-70 Admin user management** — list/manage accounts.
- **E-71 Ban / suspend user** — disable an account.
- **E-72 Database backups** — scheduled Postgres dumps (ciphertext only).
- **E-73 Health / readiness endpoint** — liveness probe for compose.
- **E-74 Metrics** — Micrometer/Prometheus counters.
- **E-75 Structured logging** — Logback JSON logs.

#### Transport resilience

- **E-80 Auto-reconnect with backoff** — client reconnects after drops.
- **E-81 Heartbeat keepalive** — WebSocket ping/pong; carries conversation head `seq`.
- **E-82 Message ordering** — per-conversation monotonic `seq`.
- **E-83 Duplicate suppression** — drop any `seq` ≤ last-seen contiguous.
- **E-84 Persist + reconcile delivery** — gap detection → backfill on holes; guarantees no permanent loss. The ratchet tolerates out-of-order arrival via skipped-message keys, so reconcile composes with encryption cleanly.

#### Attachments

- **E-90 Encrypted file / image upload** — encrypt client-side; server stores only the blob.
- **E-91 Encrypted file download** — fetch blob, decrypt with the per-file key from the message.
- **E-92 Size / type limits** — enforce upload constraints (on ciphertext size; type client-enforced).
- **E-93 Thumbnails** — generated client-side and sent as separate encrypted blobs.

#### Nice-to-have

- **E-100 Emoji reactions** — encrypted reaction payload.
- **E-101 Message formatting** — markdown, rendered client-side.
- **E-102 Slash commands** — client-side command parsing.
- **E-103 Edit history** — keep prior versions of edited messages.
- **E-104 Themes** — client appearance config.
- **E-105 Encrypted history backup** — optional encrypted backup so a reinstall can restore history; directly softens the device-loss tradeoff.

#### Out of scope

- **E-110 Multi-device / device linking** — excluded by the one-device-per-user decision.
- **E-111 Federation / multi-server** — single instance by design.
- **E-112 Mobile push notifications** — JVM clients only; push could carry only "you have a message," never content.
- **E-113 Link previews** — server can't fetch (SSRF) and can't see URLs anyway under E2EE.
- **E-114 Sealed sender / metadata privacy** — hides sender identity from the server; advanced, possible future.

### By difficulty

#### Trivial

- **E-16 User profile**
- **E-18 Custom status**
- **E-22 Rename / describe channel**
- **E-61 Account password hashing**
- **E-73 Health / readiness endpoint**
- **E-75 Structured logging**

#### Easy

- **E-01 Direct messages** — depends on E-53 (the established session).
- **E-02 Channel / group messages** — depends on E-54 (group key).
- **E-06 Edit own message**
- **E-07 Delete own message**
- **E-08 @mentions**
- **E-10 Registration**
- **E-11 Login**
- **E-14 Online/offline presence**
- **E-15 Typing indicators**
- **E-17 Avatar**
- **E-20 Create channel**
- **E-21 Join / leave channel** — the membership op is easy; the re-key it triggers is E-55.
- **E-23 Archive / delete channel**
- **E-30 Delivery receipts** — nearly free: reuses the per-recipient receipt ack that E-03 store-and-forward already requires to delete.
- **E-32 Unread counts** — depends on E-31 read markers.
- **E-33 Mention notifications**
- **E-34 Console bell / notification hook**
- **E-35 Mute / notification preferences**
- **E-50 Identity key generation** — libsignal generates and persists the keypair.
- **E-56 One-time prekey replenishment**
- **E-60 TLS transport**
- **E-81 Heartbeat keepalive**
- **E-83 Duplicate suppression**
- **E-92 Size / type limits** — size on ciphertext is trivial; type must be client-enforced since the server can't sniff.
- **E-100 Emoji reactions**
- **E-101 Message formatting**

#### Medium

- **E-03 Message durability (store-and-forward)**
- **E-04 Offline delivery**
- **E-05 Missed-message delivery**
- **E-09 Replies / threading**
- **E-12 Authenticated WebSocket**
- **E-13 Logout / token invalidation**
- **E-19 Block user**
- **E-24 Channel roles**
- **E-25 Invite / add members** — couples with E-55 re-key.
- **E-26 Kick / remove members** — correctness depends on E-55 re-keying every remaining member.
- **E-27 Private channels**
- **E-28 Group DMs**
- **E-31 Read markers**
- **E-51 Prekey publishing**
- **E-52 X3DH session establishment**
- **E-53 Double Ratchet messaging** — libsignal owns the ratchet; the work is wiring encrypt/decrypt into the send path and persisting session state.
- **E-57 Safety-number verification**
- **E-58 Identity-key-change detection**
- **E-59 Local keystore protection**
- **E-62 JWT refresh / rotation**
- **E-63 Account password reset / change** — reset for a forgotten password needs an out-of-band channel (email) that is not in the stack.
- **E-64 Keystore passphrase change**
- **E-65 Account lockout**
- **E-66 Audit log**
- **E-70 Admin user management**
- **E-71 Ban / suspend user**
- **E-72 Database backups**
- **E-74 Metrics**
- **E-80 Auto-reconnect with backoff**
- **E-82 Message ordering**
- **E-84 Persist + reconcile delivery**
- **E-90 Encrypted file / image upload**
- **E-91 Encrypted file download**
- **E-93 Thumbnails**
- **E-102 Slash commands**
- **E-103 Edit history**
- **E-104 Themes**

#### Hard

- **E-54 Sender Keys** — group session management: distribute a sender key to each member over pairwise sessions, admit new joiners mid-stream.
- **E-55 Group re-keying on membership change** — correctness-critical (post-compromise security), must reach every remaining member on each change.
- **E-105 Encrypted history backup** — a security-sensitive backup format plus its own key/recovery management and restore flow.

#### Very hard

- *None intrinsic.* The primitive cryptography is handled by libsignal; the Hard items above are integration and correctness work, not protocol implementation. Anything genuinely very-hard (metadata privacy, multi-device) sits in *Out of scope*.

#### Ignore

- **E-110 Multi-device / device linking**
- **E-111 Federation / multi-server**
- **E-112 Mobile push notifications**
- **E-113 Link previews**
- **E-114 Sealed sender / metadata privacy**

Excluded by the one-device-per-user decision, the small-community scope, or (as
with E-113) a cost that outweighs its value here — rather than by difficulty
alone.

## Implementation plan

Each milestone is a vertical slice that ends with something runnable and
demoable. Client development is folded into every milestone rather than tracked
separately.

The first milestone that sends a message
already sends ciphertext. Encryption determines the message envelope, the
durability model (store-and-forward, E-03), and the client keystore — retrofitting
those three would touch every layer, so cryptographic foundations land *before*
messaging rather than after.

**The crypto dependency chain fixes the order:**

```
identity keys + keystore  →  prekey directory  →  X3DH + Double Ratchet (1:1)  →  Sender Keys (groups)
```

The last link is load-bearing: a group sender key (E-54) is distributed to each
member *over* their pairwise Double Ratchet session, so group encryption cannot
precede solid 1:1. This is why channels land late here rather than early.

### Frontend architecture

Three JVM frontends share a single engine and differ only in presentation. The
engine is split into an **interface** and an implementation, so the UI is written
once against the interface and never against a concrete engine — see
`opus-48-frontend-note.md` for the full module layout:

- **`:protocol`** — envelope DTOs (routing metadata + ciphertext + prekey types),
  shared by server and every client.
- **`:client:api`** — the `ChatEngine` interface plus the domain model
  (`Message`, `ChatEvent`, ids). No implementation, no dependencies beyond
  coroutines. Inbound events are a `Flow` of *already-decrypted* domain objects;
  outbound actions are suspend functions. Two members exist for this variant that
  a plaintext engine leaves null: contact verification (E-57/E-58) and the
  keystore passphrase unlock.
- **`:client:core-e2ee`** — the implementation: Ktor WebSocket connection,
  reconnect/backoff/heartbeat, auth/session, token persistence, and local state
  (last-seen `seq` per conversation, unread counts, current-conversation
  context). **All cryptography lives here** — libsignal sessions, the encrypted
  keystore and its startup lock, encrypt-on-send / decrypt-on-receive — as does
  the decrypted local message history, which is the system's real history store.
- **`:client:console`** — minimal line-based frontend (Clikt + `println`);
  scriptable and CI-friendly.
- **`:client:tui`** — rich terminal UI (Mosaic).
- **`:client:gui`** — deferred Compose Desktop frontend.

Rule: **a frontend depends on `:client:api` and nothing else** — not on a core
implementation, not on `:protocol`, not on libsignal. It only renders decrypted
events from the engine and turns input into engine calls. Here the module
boundary does more than organise code: because no frontend has a dependency path
to `:client:core-e2ee`, "a frontend never sees a key" is enforced by the build
graph rather than by discipline.

This is also why client features need no separate plan: each is the client half
of a server feature, built in the same milestone and added behind `:client:api`
so all frontends inherit it. The interface exists from **M0**, before any UI is
written — its cost is one file, while retrofitting it after three frontends are
written against a concrete class means touching all three.

### M0 — Runnable skeleton

- Server: Netty engine, **E-73** health endpoint, minimal Docker Compose.
- Client: establish the full module split now — `:client:api` with a first
  `ChatEngine` method, `:client:core-e2ee` implementing it against `/health`,
  `:client:console` rendering the result. Small as this is, it puts the interface
  boundary in place before any UI exists, which is the point.
- **Done when:** `docker compose up` serves `/health`, the console client reaches
  it, and `:client:console` compiles with no dependency on `:client:core-e2ee`.

### M1 — Accounts & transport auth

- Server: **E-10**, **E-11**, **E-13**, **E-61**; Postgres + Exposed + Flyway,
  first migration creates `users`. No cryptography yet — this is the account
  layer that later carries the key material.
  **E-13** ships minimal here — short-lived tokens discarded client-side;
  revocable denylist / refresh rotation arrives with E-62 (M8).
- Client-core: auth/session API + on-disk token persistence. Console: register /
  login / logout commands.
- **Done when:** a user can register and log in from the console and receive a
  token.

### M2 — Key-management foundation

The milestone with no analog in a plaintext design, and the one that makes
everything after it possible.

- Server: prekey directory — tables and REST endpoints to publish and fetch
  identity keys, signed prekeys, and one-time prekey bundles, plus exhaustion
  signalling.
- Client-core: **E-50** identity keypair generated on first run, **E-59**
  encrypted keystore with the single-writer startup lock, **E-51** prekey
  publishing, **E-56** replenishment. Registration now also triggers keygen.
- Frontend: give the milestone something demoable — a console command that prints
  your own identity fingerprint and fetches and displays another user's prekey
  bundle. This is not throwaway: it is the seed of the E-57 verification UI in M5,
  and it exercises `:client:api` for a non-messaging call.
- **Done when:** a user's keys generate, lock at rest, their prekey bundle is
  published and fetchable by another user, and the console can show both
  fingerprints. Starting a second client for the same user fails on the lock.

### M3 — Encrypted durable 1:1 (core)

- Server: **E-12**, **E-03** store-and-forward (persist ciphertext, delete on
  receipt-ack), **E-81** heartbeat. The server never sees plaintext and never
  links libsignal.
- Client-core: **E-52** X3DH session establishment, **E-53** Double Ratchet
  encrypt/decrypt in the send/receive path, session-state persistence, **E-01**
  DMs, local decrypted history. Console: basic interactive send/receive.
- Cross-cutting: add Micrometer instrumentation (**E-74**) and JSON logging
  (**E-75**) now, for eyes on the WebSocket and delivery layers.
- **Done when:** two clients exchange end-to-end-encrypted DMs in real time; the
  server holds only ciphertext, deleted once receipt is acked.

### M4 — Offline, reconnect & reconcile (stand up the TUI)

- Server: **E-04** offline queue, **E-05** missed-message delivery, **E-14**
  presence, **E-82** per-conversation `seq`.
- Client-core: **E-80** reconnect/backoff, **E-83** dedup, **E-84** gap detection
  → re-pull of unacked messages, resync on reconnect — all in core, so every
  frontend inherits correct delivery. The ratchet's skipped-message keys absorb
  out-of-order arrival.
- Frontend: introduce **`:client:tui`** (Mosaic) as the second frontend, written
  against `:client:api` only. This is the milestone that *validates* the
  interface — a second UI is what exposes anything the engine leaked or failed to
  expose, and it is far cheaper to find that here than after three frontends
  exist. It also solves render-while-typing.
- Cross-cutting: stand up Prometheus / Loki / Grafana, so there are dashboards to
  debug delivery against. The server sees only ciphertext and metadata, so this
  is where reconnect and gap-healing behaviour becomes observable at all.
- **Done when:** messaging an offline user delivers on their reconnect; killing
  the connection mid-conversation loses and duplicates nothing; console and TUI
  run off the same engine with no UI code shared between them and no engine code
  duplicated into either.

### M5 — Trust & everyday UX

- Client-core: **E-57** safety-number verification and **E-58** identity-key-change
  detection — both need only M3 sessions, and both must exist before the system
  is trusted outside a private network (M7).
- Server + core: **E-08**, **E-15**, **E-30**–**E-35**. **E-30** delivery receipts
  are nearly free here, reusing the per-recipient receipt ack that E-03 already
  requires in order to delete ciphertext.
- Frontends: verification UI in console and TUI — this is where `:client:api`'s
  verification member becomes non-null and the UIs light up the screens they
  would otherwise omit; **E-34** bell (console); TUI renders typing, read state,
  and unread badges.
- **Done when:** two users can verify each other out-of-band, are warned on key
  change, and typing / read state / unread badges / mention alerts all work.

### M6 — Channels & groups (the hard crypto)

Isolated deliberately: **E-54** and **E-55** are the plan's Hard items, and both
build on the pairwise sessions from M3.

- Client-core: **E-54** Sender Keys — distribute a group sender key to each
  member over their pairwise session, admit joiners mid-stream; **E-55** re-key
  on every membership change. **E-26** removal is only correct if the re-key
  reaches every remaining member.
- Server: **E-02**, **E-20**–**E-28** channel and membership management (metadata
  only; group content stays opaque).
- Frontends: channel switching (console commands; TUI channel pane).
- **Done when:** users create channels and hold group conversations, and a
  removed member provably cannot read messages sent after removal.

### M7 — Public deployment & TLS

- Infra: **E-60** TLS via Caddy; finalize the ~6-service compose file. TLS
  protects routing metadata and the prekey exchange; the E2EE layer protects
  payloads.
- Client-core: `wss://` trust, server-URL config, token-expiry re-auth.
- **Done when:** the community connects over `wss://` to a real domain.

### M8 — Content, attachments, admin, backup (optional GUI)

- Server + core: **E-06**, **E-07**, **E-09**, **E-16**–**E-19**,
  **E-40**–**E-42**, **E-90**–**E-93** encrypted attachments, **E-62**–**E-66**,
  **E-70**–**E-72**, nice-to-haves **E-100**–**E-104**.
- **E-105 encrypted history backup** — the only answer to device loss, but Hard
  and security-sensitive; kept here at low priority rather than in the MVP.
- Optional: **`:client:gui`** (Compose Desktop) against `:client:api`. By this
  point the engine surface has been exercised by two frontends for six
  milestones, so the GUI is presentation work only — no engine changes should be
  needed to add it.

### MVP line

**M0 → M5** yields a working end-to-end-encrypted 1:1 messenger with verified
contacts, offline delivery, and reliable reconnect — the honest E2EE MVP.
**M6** adds channels as the hard stretch, **M7** ships it publicly, **M8** is
ongoing enrichment. Natural "1.0" candidates: end of **M5** (1:1, private) or end
of **M7** (groups, public).
