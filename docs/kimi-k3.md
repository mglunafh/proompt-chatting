# Kotlin Messenger, general info

## Tech stack

Target: small communities (~20–50 users), single-instance deployment.
No distributed/high-availability requirements.

### Server

- Kotlin 2.3.21 + kotlinx-coroutines 1.11.0
- Ktor Server 3.5.1 (Netty) — REST + WebSockets
- kotlinx.serialization — JSON message envelopes
- PostgreSQL — users, conversations, message history
- Exposed + HikariCP — DB access and connection pooling
- Opaque session tokens (stored in DB) for auth — trivial to revoke
- Server-authoritative monotonically increasing message IDs —
  double as sync cursors ("give me everything after ID N") for
  offline catch-up on reconnect
- Attachments on local disk, served by Ktor

### Client (JVM, terminal-based)

- Ktor Client (CIO engine) with WebSocket support
- Clikt — CLI command/argument parsing
- Mordant — terminal rendering (colors, prompts, live status)
- kotlinx.coroutines — concurrent socket-read / stdin-write loops

### Infrastructure

- Docker Compose — app + PostgreSQL
- Caddy — reverse proxy with automatic TLS (WSS)
- Single instance: presence tracking and message fan-out in memory
  (ConcurrentHashMap of sessions / SharedFlow), no Redis or external broker

### Explicit non-goals

- Horizontal scaling, high availability
- Redis or any external message broker
- Object storage / CDN for media

## Feature list

Each feature has a stable ID (`F-NN`) referenced in both groupings below.

### By area

#### Core messaging

- **F-01** JSON protocol envelopes (sealed classes + `type` discriminator) — foundation for everything
- **F-02** 1:1 messages with persistence — schema: users, conversations, conversation_members, messages
- **F-03** History fetch with pagination — keyset pagination by message ID
- **F-04** Edit / delete messages — decide early: tombstone vs hard delete; sync semantics (tombstone stream) are the real cost, not the endpoints; resolution path: tombstones + mutation-sequence cursor alongside the ID cursor (decide in M2)
- **F-05** Replies / quotes — `reply_to_id` column
- **F-06** Reactions
- **F-07** Threads — removed from scope; real costs were multiplied read cursors (F-11), thread-aware sync (F-29), TUI (F-21) pushed toward Hard; F-05 covers the lightweight need; soft no, revisit only on actual demand

#### Users & presence

- **F-08** Auth: register/login + opaque session tokens — bcrypt hashes, token table
- **F-09** Presence (online/offline broadcast) — in-memory map; ref-count multiple sessions per user (offline = zero sessions)
- **F-10** Typing indicators — ephemeral events, never persisted
- **F-11** Read receipts (last-read cursor per user per conversation)

#### Group / community management

- **F-12** Group conversations / channels — reuses the conversation model
- **F-13** Invite tokens (admin-generated) — gates registration
- **F-14** Roles (admin/mod/member) + kick/ban — permission checks on a few endpoints; decide bootstrap of first admin; server-wide role scope suffices
- **F-15** Read-only announcement channels — one permission bit; depends on F-14 roles
- **F-16** Pinned messages — one flag + one endpoint; v1: refetch on open, no live event; permission depends on F-14

#### Notifications & UX

- **F-17** Mentions (`@user`) + notification events — parse server-side, emit typed event
- **F-18** Markdown rendering — client-only
- **F-19** Desktop notifications — removed from scope: OS-specific glue, no reliable focus detection in terminal, manual per-OS testing; mention events (F-17) remain for client-side alerting
- **F-20** Local history cache
- **F-21** TUI v1: scrollback + input line + channel switching — hardest client part; Mordant helps; risk is scope creep, not unknowns — v1 excludes threads (F-07)

#### Search & content

- **F-22** Full-text search — Postgres tsvector (ILIKE suffices at this scale)
- **F-23** Link previews — removed from scope: difficulty was entirely the SSRF-hardening tail, poor ROI for a text-only preview at this scale

#### Security

- **F-24** Rate limiting / anti-spam — low priority for small trusted communities
- **F-25** E2E encryption — own project; breaks server-side search/catch-up; TLS + server trust suffices for small trusted communities

#### Admin / ops

- **F-26** Postgres backup cron
- **F-27** Health endpoint + basic metrics

#### Transport resilience

- **F-28** WS lifecycle: auth handshake, heartbeat, reconnect — Ktor provides ping/pong; real work is client-side backoff + resync (F-29) orchestration
- **F-29** Offline catch-up ("everything after ID N") — falls out of monotonic message IDs; covers messages only until the M2 tombstone decision extends sync to edits/deletes

#### Attachments

- **F-30** Attachments (upload/download to disk) — REST upload, message references file ID; download needs auth + membership check, size limits, filename sanitization

#### Nice-to-have

- **F-31** Voice/video — terminal client can't use it
- **F-32** Federation / multi-instance — contradicts single-instance goal

### By difficulty

#### Trivial

- **F-01** JSON protocol envelopes
- **F-09** Presence
- **F-10** Typing indicators
- **F-13** Invite tokens
- **F-15** Read-only announcement channels
- **F-16** Pinned messages
- **F-26** Postgres backup cron
- **F-27** Health endpoint + basic metrics

#### Easy

- **F-02** 1:1 messages with persistence
- **F-03** History fetch with pagination
- **F-05** Replies / quotes
- **F-06** Reactions
- **F-08** Auth: register/login + opaque session tokens
- **F-11** Read receipts
- **F-12** Group conversations / channels
- **F-14** Roles + kick/ban
- **F-17** Mentions + notification events
- **F-18** Markdown rendering
- **F-20** Local history cache
- **F-22** Full-text search
- **F-24** Rate limiting / anti-spam
- **F-28** WS lifecycle: auth handshake, heartbeat, reconnect
- **F-29** Offline catch-up
- **F-30** Attachments

#### Medium

- **F-04** Edit / delete messages
- **F-21** TUI v1

#### Hard

- _(none)_

#### Very hard

- _(none)_

#### Ignore

- **F-07** Threads
- **F-19** Desktop notifications
- **F-23** Link previews
- **F-25** E2E encryption
- **F-31** Voice/video
- **F-32** Federation / multi-instance

## Implementation plan

Guiding principle: every milestone ends with a runnable, usable system —
vertical slices, not horizontal layers.

Client architecture follows the frontend note
(docs/kimi-k3-frontend-note.md): frontends are thin renderers over
`:client:app-core` — feature logic lives in app-core, never in a frontend,
so all current and future frontends (console, TUI, maybe GUI) stay in sync
feature-wise by construction, and a future E2EE engine can reuse
everything above `:client:api`.

### Milestone 0 — Foundations

- Shared protocol module: sealed-class envelopes (kotlinx.serialization)
  used by both server and client — single source of truth for the wire
  format; include a protocol version field from day one
- docker-compose.yml — PostgreSQL + app service
- Schema migrations with Flyway from the very first table
  (not Exposed `SchemaUtils`)
- Health endpoint, HOCON config, Hikari pool
- Exit: server starts, connects to Postgres, runs a migration, answers `/health`

### Milestone 1 — Walking skeleton: 1:1 chat

- Schema v1: `users`, `tokens`, `conversations`, `conversation_members`,
  `messages` (bigserial IDs = monotonic cursor)
- Register/login → opaque token; WS `/ws` authenticated by token
- `send_message` → persist → fan out → `message_received`; ack to sender
- Catch-up: `sync { afterId }` on reconnect
- History: keyset pagination
- Client modules from day one (per frontend note): `:client:api`
  (ChatEngine contract — events, view models, Capabilities, EngineState;
  pure Kotlin, no toolkit/transport), `:client:app-core` (event→state
  reduction, user intents, unread counts; Flow-based),
  `:client:engine:plaintext` (ChatEngine over WS + JSON),
  `:client:frontend:console` (readln + Mordant) — the console frontend is
  the reference implementation and *is* the client until a concrete need
  arises
- Line-based scope only (no TUI yet): login, send, print incoming,
  `/history`, `/sync`
- Discipline: protocol types never enter UI code; frontends depend only on
  api/app-core types, engine impl wired in the frontend's `main`; expect
  `:client:api` churn until the TUI lands (M3)
- FakeEngine emitting scripted events — UI development and tests without
  a server
- Integration tests with Testcontainers against real Postgres
- Key decision: at-least-once delivery + client-side dedupe by message ID —
  makes reconnect/catch-up idempotent
- Exit: two terminals chat; a restarted client catches up cleanly —
  this is already a messenger

### Milestone 2 — Group chat & social basics

- Channels/groups (M1 data model already supports them)
- Presence, typing indicators (ephemeral, in-memory)
- Read receipts (last-read cursor)
- Edit/delete — decide tombstone vs hard delete here; affects sync semantics
- Exit: a group can use it day-to-day

### Milestone 3 — Terminal TUI (client-only)

- Deliberately scheduled after the protocol stabilizes, so the UI isn't
  chasing wire-format changes
- `:client:frontend:tui` — thin, time-boxed renderer over
  `:client:app-core` (Mordant only); rendering only, no new logic — this
  is the acid test of the M1 module split and freezes `:client:api`
- Scope: scrollback pane, input line, channel list, status markers
- Markdown rendering; optional local cache
- Medium effort, but fully decoupled from server work

### Milestone 4 — Community management

- All easy and independent, any order: invites, roles + kick/ban,
  read-only channels, pinned messages, mentions, reactions, replies

### Milestone 5 — Richness

- Attachments, full-text search

### Continuous

- Postgres backups from the moment M1 holds real data
- Rate limiting — late, low priority for trusted communities
- Threads, link previews, desktop notifications, E2EE, federation — remain out of scope

### Risk notes

- Reconnect/catch-up edge cases — mitigated by the at-least-once delivery +
  dedupe decision (M1) and integration tests
- TUI scope creep — the line-based client stays fully functional and the
  TUI is time-boxed, so the TUI never blocks server work
