# Kotlin Messenger

## Tech stack

Sized for a small community (~10–50 users): single server process, one Postgres
database, no high-availability / high-load / global-scale infrastructure.

- **Kotlin 2.3.21** — implementation language for server and client.
- **Gradle 9.5** — build tool with version catalog; Shadow plugin for the fat JAR.
- **Ktor 3.5.1** — server framework.
- **Netty** (`ktor-server-netty`) — engine hosting the Ktor app and WebSocket connections.
- **WebSockets** (Ktor plugin) — real-time transport for the live message stream.
- **REST over HTTP** — request/response surface for login, history backfill, uploads.
- **kotlinx-coroutines 1.11.0** — concurrency model.
- **kotlinx.serialization** — JSON encoding for WebSocket frames and REST bodies.
- **PostgreSQL 17** — database; named Docker volume for durability.
- **Exposed** — Kotlin SQL framework for database access.
- **Flyway** — schema migrations under version control.
- **In-process connection registry** (`ConcurrentHashMap<UserId, MutableSet<Session>>`) — message routing to online users, one set per user since a user may connect from several frontends at once; no broker needed.
- **In-memory ephemeral state** — presence and typing indicators, not persisted.
- **JWT** (Ktor `auth-jwt`) + **Argon2id** (`com.password4j:password4j`) — auth tokens and password hashing.
- **Koin** — dependency injection (optional).
- **Caddy** — reverse proxy terminating client↔server HTTPS/`wss://` with automatic Let's Encrypt certificates.
- **Docker Compose** — deployment with ~6 services: `db`, `server`, `proxy`, `prometheus`, `loki`, `grafana`.
- **`:protocol` Gradle module** — message DTOs shared by server and all clients.
- **`:client:api` Gradle module** — the `ChatEngine` interface and domain model; the only module the frontends depend on.
- **Ktor client** — `:client:core-plain` networking (WebSocket + REST); connects via `wss://`, not part of compose.
- **Clikt** — command parsing for the `:client:console` frontend.
- **Mosaic** — Compose-for-terminal UI for the `:client:tui` frontend.
- **Compose Desktop** — GUI for the deferred `:client:gui` frontend.
- **Testcontainers + kotest + Ktor `testApplication`** — integration tests against real Postgres.
- **Logback** (SLF4J) + **logstash-logback-encoder** — logging to stdout as structured JSON.
- **Micrometer** (`ktor-server-metrics-micrometer`, Prometheus registry) — in-app instrumentation exposing `GET /metrics`.
- **Prometheus** — scrapes and stores metrics time series.
- **Grafana Loki** — lightweight log store; container stdout shipped via the Docker Loki logging driver (no extra agent).
- **Grafana** — single dashboard over both data sources (Prometheus metrics + Loki logs).

## Features

Feature IDs (`F-NN`) are stable references, assigned in area-blocked ranges. The
two subsections below group the same set of features — first by area, then by
implementation difficulty.

### By area

#### Core messaging

- **F-01 Direct messages** — 1:1 conversation between two users.
- **F-02 Channel messages** — post to a named group channel.
- **F-03 Message durability** — write to Postgres before delivery; DB is source of truth.
- **F-04 Offline delivery** — store messages for offline recipients, deliver on reconnect.
- **F-05 History backfill** — fetch "everything since `seq` N" on (re)connect.
- **F-06 Edit own message** — amend a sent message.
- **F-07 Delete own message** — remove a sent message.
- **F-08 @mentions** — reference a user in a message.
- **F-09 Replies / threading** — reply to a specific message.

#### Users & presence

- **F-10 Registration** — create an account (username + password).
- **F-11 Login** — authenticate over REST, receive a JWT.
- **F-12 Authenticated WebSocket** — open the live connection with the JWT.
- **F-13 Logout / token invalidation** — end a session.
- **F-14 Online/offline presence** — show who is currently connected.
- **F-15 Typing indicators** — ephemeral "user is typing" signal.
- **F-16 User profile** — display name.
- **F-17 Avatar** — profile image.
- **F-18 Custom status** — free-text status message.
- **F-19 Block user** — suppress messages from a specific user.

#### Group / community management

- **F-20 Create channel** — start a new channel.
- **F-21 Join / leave channel** — manage own membership.
- **F-22 Rename / describe channel** — edit channel metadata.
- **F-23 Archive / delete channel** — retire a channel.
- **F-24 Channel roles** — owner / admin / member permissions.
- **F-25 Invite / add members** — bring users into a channel.
- **F-26 Kick / remove members** — remove users from a channel.
- **F-27 Private channels** — invite-only visibility.
- **F-28 Group DMs** — ad-hoc multi-user conversation without a formal channel.
- **F-29 Federation / multi-server** — *out of scope (single instance by design).*

#### Notifications & UX

- **F-30 Delivery receipts** — mark a message delivered to a recipient.
- **F-31 Read markers** — per-user, per-conversation read position.
- **F-32 Unread counts** — per-conversation unread tally.
- **F-33 Mention notifications** — highlight/alert on @mention.
- **F-34 Console bell / notification hook** — audible or terminal notification on new message.
- **F-35 Mute / notification preferences** — per-conversation mute.
- **F-36 Mobile push notifications** — *out of scope (console client only).*

#### Search & content

- **F-40 Full-text search** — search message history (Postgres `tsvector`).
- **F-41 Pinned messages** — pin important messages in a channel.
- **F-42 History export** — dump a channel's history to a file.
- **F-43 Link previews** — *out of scope (server-side fetching of arbitrary user URLs is an SSRF risk and needs an async fetch/cache pipeline; not worth it for a small trusted community).*

#### Security

- **F-50 TLS transport** — HTTPS/`wss://` via Caddy.
- **F-51 Password hashing** — Argon2id via password4j.
- **F-52 JWT refresh / rotation** — token renewal and rotation.
- **F-53 Password reset / change** — self-service credential change.
- **F-54 Account lockout** — throttle brute-force login attempts.
- **F-55 Audit log** — record security-relevant events.
- **F-56 End-to-end encryption** — *out of scope (TLS is sufficient for a trusted group).*
- **F-57 Rate limiting / anti-abuse** — *out of scope initially (trusted membership).*

#### Admin / ops

- **F-60 Admin user management** — list/manage accounts.
- **F-61 Ban / suspend user** — disable an account.
- **F-62 Database backups** — scheduled Postgres dumps.
- **F-63 Health / readiness endpoint** — liveness probe for compose.
- **F-64 Metrics** — Micrometer/Prometheus counters.
- **F-65 Structured logging** — Logback JSON logs.

#### Transport resilience

- **F-70 Auto-reconnect with backoff** — client reconnects after drops.
- **F-71 Heartbeat keepalive** — WebSocket ping/pong.
- **F-72 Message ordering** — per-conversation monotonic sequence numbers guarantee order.
- **F-73 Client ack + at-least-once** — per-recipient acks with server-side retransmit. *Optional add-on — see delivery model below.*
- **F-74 Duplicate suppression** — drop any message whose `seq` is ≤ the last-seen contiguous `seq`.
- **F-75 Resume from last ack** — explicit ack-driven resume handshake on reconnect. *Optional add-on — see delivery model below.*

**Delivery model (persist + reconcile).** The default model — chosen over a full
per-recipient ack protocol because the DB is the source of truth and it reuses
machinery we build anyway (F-72 ordering, F-71 heartbeat, F-05 backfill). A
*conversation* here is a DM or a channel:

- Each message carries a per-conversation monotonic `seq` (F-72); the client
  tracks its highest contiguous `seq` per conversation.
- **Gap detection:** on receiving `seq N`, if `N > lastSeen + 1` a message was
  missed — the client backfills the hole (F-05); if `N ≤ lastSeen` it is a
  duplicate and dropped (F-74).
- **Trailing-drop cover:** the heartbeat carries the conversation head `seq`, so
  a dropped *last* message is caught within one heartbeat and backfilled.
- **Dead/half-open connections:** heartbeat timeout → reconnect → resync.
- **Sender assurance:** a single round-trip — server persists and replies with
  the assigned `seq`; the sender retries with the same `client_msg_id`
  (idempotency key) if no reply.

Guarantee: **no permanent loss** (the DB always has the message and the client
can always detect it is behind). Full F-73 acks would only lower worst-case
recovery from ~1 heartbeat to ~1 ack-timeout — not worth the complexity at this
scale, so kept on the shelf.

#### Attachments

- **F-80 File / image upload** — store on filesystem, metadata row in DB.
- **F-81 File download** — retrieve an attachment.
- **F-82 Size / type limits** — enforce upload constraints.
- **F-83 Image thumbnails** — generate previews for images.

#### Nice-to-have

- **F-90 Emoji reactions** — react to a message.
- **F-91 Message formatting** — markdown rendering.
- **F-92 Slash commands** — client-side command parsing.
- **F-93 Edit history** — keep prior versions of edited messages.
- **F-94 Themes** — client color/appearance config.

### By difficulty

#### Trivial

- **F-16 User profile**
- **F-18 Custom status**
- **F-22 Rename / describe channel**
- **F-51 Password hashing**
- **F-63 Health / readiness endpoint**
- **F-65 Structured logging**

#### Easy

- **F-01 Direct messages**
- **F-02 Channel messages**
- **F-06 Edit own message**
- **F-07 Delete own message**
- **F-08 @mentions**
- **F-10 Registration**
- **F-11 Login**
- **F-14 Online/offline presence** — note: one user may be connected from several frontends at once, so track a per-user connection ref-count (online = ≥1 live connection) and lean on F-71 heartbeat + timeout to detect dead connections.
- **F-15 Typing indicators**
- **F-17 Avatar**
- **F-20 Create channel**
- **F-21 Join / leave channel**
- **F-23 Archive / delete channel**
- **F-32 Unread counts** — depends on F-31 read markers (Medium); implement after it, since it counts messages past the read position F-31 supplies.
- **F-33 Mention notifications**
- **F-34 Console bell / notification hook**
- **F-41 Pinned messages**
- **F-50 TLS transport**
- **F-71 Heartbeat keepalive**
- **F-74 Duplicate suppression** — drop any message whose `seq` is ≤ the last-seen contiguous `seq`; a plain comparison plus a unique `(sender, client_msg_id)` key server-side.
- **F-80 File / image upload**
- **F-81 File download**
- **F-82 Size / type limits**
- **F-90 Emoji reactions**
- **F-91 Message formatting** — note: server just stores raw text (trivial); the work is client-side rendering, which differs per frontend and is fiddliest in `:client:tui` (parse markdown → terminal styling). Plain `:client:console` renders it as-is.

#### Medium

- **F-03 Message durability**
- **F-04 Offline delivery** — depends on F-03 + F-05; once messages are stored and backfill exists this reduces to "send what was missed on reconnect." Easy in effort, but cannot land before them.
- **F-05 History backfill**
- **F-09 Replies / threading** — scope-dependent: a flat `parent_id` reply-to is Easy; nested thread views with reply counts are Medium.
- **F-12 Authenticated WebSocket**
- **F-13 Logout / token invalidation** — a stateless JWT can't be revoked in place; a minimal short-expiry + client-discard version ships in M1, while real invalidation (server-side denylist or revocable refresh-token table) arrives with F-52.
- **F-19 Block user** — scope-dependent: client-side filtering of a blocked user is Easy; server-side per-viewer filtering of a shared channel stream is Medium.
- **F-24 Channel roles**
- **F-25 Invite / add members** — scope-dependent: an admin adding users directly is Easy; a full invite flow (pending invites, accept/decline, invite links) is Medium.
- **F-26 Kick / remove members** — depends on F-24; given roles it is delete-membership + drop the live session. Easy after F-24.
- **F-27 Private channels** — depends on F-20; mostly a visibility flag threaded through listing/join checks. Easy after channels exist.
- **F-28 Group DMs** — depends on F-20; a thin variant of a channel (nameless, fixed membership) once channels exist.
- **F-30 Delivery receipts** — needs a per-recipient message ack (the recipient confirms receipt), which the persist+reconcile model doesn't provide; ships with the optional ack path (F-73), not the base delivery model.
- **F-31 Read markers**
- **F-35 Mute / notification preferences** — a per-(user, conversation) flag checked in the notify path. Easy in effort.
- **F-40 Full-text search**
- **F-42 History export** — scope-dependent: query + serialize is Easy; streaming large exports with access control nudges Medium.
- **F-52 JWT refresh / rotation**
- **F-53 Password reset / change** — caveat: password *change* (verify old, rehash) is Easy; password *reset* for a forgotten password needs an out-of-band channel (email) that is **not** in the stack — a missing dependency, not just Medium.
- **F-54 Account lockout** — failed-attempt tracking + cooldown; Easy-to-Medium, and the same territory as the F-57 rate-limiting we chose to ignore.
- **F-55 Audit log** — an append-only table plus call sites at each event; Easy in effort, spread across the codebase.
- **F-60 Admin user management** — CRUD over `users` behind an admin check. Easy after auth exists.
- **F-61 Ban / suspend user** — a disabled flag + deny-login + disconnect live sessions; couples with F-13 for the session kill.
- **F-62 Database backups** — a scheduled `pg_dump` container; ops scripting, not application code.
- **F-64 Metrics** — the Micrometer plugin + a few counters is Easy; the Medium part is standing up Prometheus (infra, not code).
- **F-70 Auto-reconnect with backoff**
- **F-72 Message ordering**
- **F-83 Image thumbnails**
- **F-92 Slash commands** — a client-side parser dispatching to `:client:api` calls (Clikt already present). Easy in effort.
- **F-93 Edit history** — write a version row on edit; view prior versions. Easy in effort.
- **F-94 Themes** — client config + a color scheme; nothing in `:client:console`, a palette in `:client:tui`. Easy in effort.

#### Hard

- **F-73 Client ack + at-least-once** — deferred optional add-on: per-recipient message acks with server-side retransmit, cutting worst-case recovery to one ack-timeout.

#### Very hard

- **F-75 Resume from last ack** — deferred optional add-on: an explicit ack-driven resume handshake on reconnect; correct only when acks, ordering, and dedup all compose, which is where the subtle bugs live.

#### Ignore

- **F-29 Federation / multi-server**
- **F-36 Mobile push notifications**
- **F-43 Link previews** — SSRF risk + async fetch/cache pipeline; complexity not worth it here.
- **F-56 End-to-end encryption**
- **F-57 Rate limiting / anti-abuse**

Excluded by the small-community scope — genuinely very-hard, scale-oriented, or
(as with F-43) a security/complexity cost that outweighs the value at this size —
rather than by difficulty alone.

## Milestones

Each milestone is a vertical slice that ends with something runnable and
demoable. Ordering front-loads the correctness core (durable messaging) and
isolates delivery-reliability work (sequence numbers + gap detection) into its
own late milestone. Client development is folded into every milestone rather
than tracked separately.

### Frontend architecture

Three JVM frontends share a single engine and differ only in presentation. The
engine is split into an **interface** and an implementation, so the UI is written
once against the interface and never against a concrete engine:

- **`:protocol`** — message DTOs, shared by server and every client.
- **`:client:api`** — the `ChatEngine` interface plus the domain model
  (`Message`, `ChatEvent`, ids). `ChatEvent` is the engine's own domain event,
  one layer above the wire and not a server frame. No implementation, no
  dependencies beyond coroutines. Inbound events are a `Flow`; outbound actions
  are suspend functions.
- **`:client:core-plain`** — the implementation: Ktor WebSocket connection,
  reconnect/backoff/heartbeat, auth/session, token persistence, and local state
  (last-seen `seq` per conversation, unread counts, current-conversation
  context). All client-side reliability logic — reconnect/backoff (F-70), gap
  detection with backfill, and dedup (F-74) — lives here, once.
- **`:client:console`** — minimal line-based frontend (Clikt + `println`);
  scriptable and CI-friendly.
- **`:client:tui`** — rich terminal UI (Mosaic).
- **`:client:gui`** — deferred Compose Desktop frontend.

Rule: **a frontend depends on `:client:api` and nothing else** — not on a core
implementation, not on `:protocol`, not on the network. It only renders events
from the engine and turns input into engine calls. This is why client features
need no separate plan: each is the client half of a server feature, built in the
same milestone and added behind `:client:api` so all frontends inherit it.

The interface exists from **M0**, before any UI is written. Its cost is one file;
retrofitting it after three frontends are written against a concrete class means
touching all three. It also keeps the door open for a second engine
implementation to drop in with the frontends untouched — see
`opus-48-frontend-note.md`.

### M0 — Runnable skeleton

- Server: Netty engine, **F-63** health endpoint, minimal Docker Compose.
- Client: establish the full module split now — `:client:api` with a first
  `ChatEngine` method, `:client:core-plain` implementing it against `/health`,
  `:client:console` rendering the result. Small as this is, it puts the interface
  boundary in place before any UI exists, which is the point.
- **Done when:** `docker compose up` serves `/health`, the console client reaches
  it, and `:client:console` compiles with no dependency on `:client:core-plain`.

### M1 — Accounts & auth

- Server: **F-10**, **F-11**, **F-13**, **F-51**; Postgres + Exposed + Flyway,
  first migration creates `users`. **F-13** ships minimal here — short-lived
  tokens discarded client-side; revocable denylist / refresh rotation arrives
  with F-52 (M8).
- Client-core: auth/session API + on-disk token persistence. Console: register /
  login / logout commands.
- **Done when:** a user can register and log in from the console and receive a
  token.

### M2 — Durable 1:1 messaging (core)

- Server: **F-12**, **F-03**, **F-01**, **F-71**.
- Client-core: WebSocket lifecycle, heartbeat, inbound-message `Flow`, send API,
  last-seen tracking. Console: basic interactive send/receive.
- Cross-cutting: add Micrometer instrumentation (**F-64**) and JSON logging
  (**F-65**) now, for eyes on the WebSocket layer.
- **Done when:** two logged-in clients exchange messages in real time, all
  persisted.

### M3 — Offline delivery & reconnect (stand up the TUI)

- Server: **F-04**, **F-05**, **F-14**.
- Client-core: **F-70** reconnect/backoff, backfill-from-last-seen, presence
  state.
- Frontend: introduce **`:client:tui`** (Mosaic) as the second frontend, written
  against `:client:api` only. This is the milestone that *validates* the
  interface — a second UI is what exposes anything the engine leaked or failed to
  expose, and it is far cheaper to find that here than after three frontends
  exist. It also solves render-while-typing.
- **Done when:** message an offline user, they reconnect and receive it; presence
  is visible; console and TUI run off the same engine with no UI code shared
  between them and no engine code duplicated into either.

### M4 — Channels & community

- Server: **F-02**, **F-20**–**F-28**.
- Client-core: channel model, membership, current-channel context. Frontends:
  channel switching (console commands; TUI channel pane).
- **Done when:** users create channels, manage membership, and hold group
  conversations.

### M5 — Presence signals & notifications

- Server: **F-08**, **F-15**, **F-31**–**F-35**.
- Client-core: typing send/receive, read markers, unread counts. Frontends:
  **F-34** bell (console); TUI renders typing, read state, and unread badges.
- **Done when:** typing indicators, read state, unread badges, and mention alerts
  all work.

### M6 — Transport resilience

Delivery model: **persist + reconcile** (per-conversation `seq` + gap detection +
heartbeat head-seq), not a full per-recipient ack protocol — see the
Transport-resilience section. This reuses F-72/F-71/F-05, so the milestone is a
reconnect-and-reconcile handler rather than a hand-rolled delivery protocol.

- Server: **F-72** per-conversation sequence numbers; heartbeat carries
  conversation head `seq`; sender-persistence ack (server replies with assigned
  `seq`).
- Client-core: gap detection → backfill (F-05) on holes, **F-74** dedup by
  monotonic id, resync on reconnect — all in core, so every frontend inherits
  correct delivery.
- Deferred optional add-ons — **F-73** full ack / at-least-once, **F-75**
  explicit resume, and **F-30** delivery receipts (which need the same
  per-recipient ack): built only if a few-second worst-case recovery, or true
  delivery receipts, prove worth the complexity.
- Cross-cutting: stand up Prometheus / Loki / Grafana for dashboards to debug the
  delivery guarantees.
- **Done when:** killing the connection mid-conversation loses and duplicates
  nothing — gaps heal on the next message, a dropped last message within one
  heartbeat.

### M7 — Public deployment & TLS

- Infra: **F-50** TLS via Caddy; finalize the ~6-service compose file.
- Client-core: `wss://` trust, server-URL config, token-expiry re-auth.
- **Done when:** the community connects over `wss://` to a real domain.

### M8 — Content, attachments, admin (optional GUI)

- Server: **F-06**, **F-07**, **F-09**, **F-40**–**F-42**, **F-80**–**F-83**,
  **F-16**–**F-19**, **F-52**–**F-55**, **F-60**–**F-62**, nice-to-haves
  **F-90**–**F-94**.
- Client-core + frontends: rendering for the above; **F-92** slash commands
  (console), **F-94** themes (TUI/GUI).
- Optional: **`:client:gui`** (Compose Desktop) against `:client:api`. By this
  point the engine surface has been exercised by two frontends for six
  milestones, so the GUI is presentation work only — no engine changes should be
  needed to add it.

### MVP line

**M0 → M4** yields a working group messenger for a trusted community (behind a
VPN/LAN). **M5–M6** harden it, **M7** ships it publicly, **M8** is ongoing
enrichment. Natural "1.0" candidates: end of **M4** (private) or end of **M7**
(public).
