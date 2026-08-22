# Step 2 features

What the step delivers, in one sentence: **you have an account, you log in from
more than one place, and your messages are still there tomorrow.**
How each is built is in [todo.md](todo.md); the exclusion list at the
bottom is part of the scope rather than a footnote to it.

## From the feature set

### Core messaging

- **MSG-04 Live connection** — one WebSocket per session, authenticated at the upgrade, carrying typed client and server frames from a module both sides depend on, with the protocol version checked once at the upgrade rather than on every frame. The server ticks a heartbeat and the client's reply proves liveness; reconnect is client-driven with backoff and stops only on an application close code. A rejected client frame comes back as a typed error frame rather than a close. Reduced: the tick carries neither the head `change_seq` nor a roster digest, since neither catch-up nor reconciliation is in this step.
- **MSG-01 Direct messages** — a send names a recipient by username; the body is validated, written to Postgres against the `direct` conversation for that canonical pair, acknowledged to the sender with its server-assigned id and timestamp, and fanned out to every live session of both participants. A message to a disconnected user is durable but not delivered until they reconnect. Messages hang off a conversation container with membership rows even though `direct` is the only kind, which is a schema shape rather than extra scope. Reduced: no `change_seq`, so a dropped fan-out is recovered by paging history rather than by a cursor walk; no edit, delete, reply, forward, reaction or mention.
- **MSG-02 Conversation list** — the conversations a user belongs to, ordered by last activity with the last message attached, fetched over REST at startup. Rows carry their members' ids and usernames, which is what lets a client render a message that references users by id. Reduced: no unread counts and no mention tally, which are the layer over a read cursor this step does not have.
- **MSG-03 Message history** — scrollback fetched over REST in server-capped pages, keyed on the message id rather than an offset so a concurrent write cannot shift a page. Reduced: paged `before` an id only; the `after` a `change_seq` half of the same endpoint belongs to MSG-05.

### Authentication

- **AUTH-03 Admin bootstrapping** — the first admin is seeded at boot from a username variable and a mounted password secret, inserted only when that identity is absent, and retired manually through the disable below. Reduced: no audit row, and `is_admin` is a boolean rather than MOD-01's ordered role.
- **AUTH-02 Registration** — accounts are created by redeeming an admin-issued single-use invite, presented with a new username and password. Reduced: admin-only issuance, so no `allow_user_invites` flag and no quota; single-use only; no listing and no revocation; and the bare token rather than a link, which nothing in a JVM console client can open.
- **AUTH-04 Login** — verify the password, generate a 256-bit random token, insert a session row holding `hash(token)`, and return the raw token once. Reduced: no second factor, so a verified password issues a session directly.
- **AUTH-01 Opaque session tokens** — random strings looked up by SHA-256 hash in a `sessions` table rather than JWT, chosen for instant revocation. The connection registry is keyed by session and holds one socket each, so a user's socket count and session count are the same number. Reduced: the row carries no `client_label` and no `last_used_ip`, which are evidence for a session list this step does not render.
- **AUTH-05 Request authentication** — `Authorization: Bearer <token>` on REST, and on WebSocket validated once at the upgrade. Identity is the token throughout: nothing a client asserts about who it is reaches the server. Reduced: the heartbeat's session lookup joins nothing, since no role can change under an open socket.
- **AUTH-06 Logout and revocation** — logout deletes the session row, logout-everywhere deletes every row for the user, and both close the affected live sockets. Reduced: no session listing and no killing one by id.
- **AUTH-10 Multi-session** — one row per login, so a user holds concurrent sessions from two terminals at once. Half of what the step's sentence claims, and it costs nothing beyond AUTH-01.
- **AUTH-11 Token lifetime** — an absolute `expires_at` on the row, checked on every REST lookup and on each heartbeat tick, which is the only check that catches an expiry mid-connection. Reduced: no sliding renewal and no idle reaping.

### Users and presence

- **USR-04 Presence** — who is currently connected, per user rather than per session, counted off the connection registry and never persisted. Broadcast to every other connected session on the 0→1 and 1→0 edges only, with offline delayed by a short grace so a blip does not flicker every roster; the offline frame carries `last_seen_at`. Reduced: no declared status, and no profile-change broadcast, since nothing renameable exists yet.
- **USR-05 Presence snapshot** — the full roster pushed as the first frame after the upgrade, captured by the same registry operation that adds the socket. Entries carry a user id and a username: nothing renames in this step, so a client caches the mapping from the frames it already receives rather than resolving ids against an endpoint. Reduced: no digest on the heartbeat and no reconciliation.
- **USR-07 Last seen** — `last_seen_at` on the user row, written when a user's last socket drops and repaired at startup from the newest session activity, since a hard kill runs no disconnect handler. Means last connected rather than last active.

### Roles and moderation

- **MOD-06 Ban** — reduced to an admin disable, where an admin disables an account and re-enables it: the flag is set, sessions deleted, live sockets closed and unredeemed invites revoked. The server refuses to disable its last active admin. It is in the step because AUTH-03 seeds a credential whose retirement procedure requires exactly this, and without it the seed password is permanent. Reduced: admins only, no mod role, no silence, no kick, no audit row.

### Security

- **SEC-02 Credential rate limiting** — the two routes that take a secret without a session: login and invite redemption. Failures are counted per source IP on both and per username on login, keyed on failures rather than attempts, so a user with a working password never meets it. Reduced: no password-reset route to cover, and the IP is the socket peer, since no proxy is pinned in front of the server yet.
- **SEC-06 Password rules** — length bounds and a blocklist, with no character classes to satisfy; the one field whose raw value never reaches the database. Reduced: registration is the only place a password is set.
- **SEC-05 Name and label rules** — the pattern, length cap and character rejections for usernames, whose uniqueness is enforced by a unique index rather than by anything held in memory. Reduced: usernames alone, as they are the only named thing that exists.
- **SEC-07 Message body limits** — size and line caps, and rejection of the C0 and C1 control characters that carry terminal escape sequences, applied on the way in so nothing that fails them is ever stored. Reduced: the caps are constants in the shared module rather than configuration.
- **SEC-08 Transport caps** — WebSocket frame and HTTP body size, enforced before deserialization, which is what bounds the work an unparsed frame can cost. Reduced: constants in the shared module, as above.
- **SEC-09 Client-side sanitization** — a client renders untrusted text inertly rather than obeying it, since its untrusted input is the socket rather than the person typing. Covers message bodies arriving from a history page as well as from a live frame, and usernames arriving on presence frames and conversation rows.

### Admin and ops

- **OPS-01 Health endpoint** — `GET /health`, a liveness probe for Docker Compose. No auth, no dependencies.
- **OPS-07 Server configuration** — settings declared once each with both spellings, a type and a default, resolved at boot from system properties, the environment, a file named by `-Dserver.config`, then the one packaged in the jar, each value given directly or as a path to a file holding it. Reduced: the database settings alone, since nothing else is configurable yet; no reload and no remote configuration.

## Step-local

- **ST2-01 Client token store** — the session token persisted to a file with owner-only permissions, so a restart resumes without a password. The token alone: MSG-06's store holds the message cache beside it and is not in this step.
- **ST2-02 Client account commands** — `register`, `login` and `logout`, with passwords prompted and read without echo rather than taken as arguments, and typed twice where one is set.
- **ST2-03 Client commands** — two sigils divide the grammar a typed line is read under: `@` addresses a recipient, `/` addresses the client. `/help` names what can be typed, `/exit` closes the connection, `/list` prints the conversation list, `/history` pages the current conversation backwards, and `/who` prints the roster.

## Not in this step

Written down because a frozen scope needs its exclusions beside its inclusions,
or the boundary only exists in someone's head.

- **Messaging** — MSG-05 offline catch-up, MSG-06 local history cache, MSG-07 edit, MSG-08 delete, MSG-09 reply, MSG-10 forward, MSG-11 reactions, MSG-12 mentions, MSG-13 read cursor. The read cursor is out on the same rule as the rest: with no unread count to feed, it is a column and an endpoint with no consumer inside the step.
- **Notifications** — NTF-01 unread counts and NTF-02 mute, the layer that reads the cursor, which arrive with it.
- **Users** — USR-01 profiles, USR-02 blocking, USR-03 profile-change broadcast, USR-06 declared status, USR-08 typing indicators.
- **Everything above a DM** — all of COM, and all of MOD beyond the disable AUTH-03's retirement procedure requires.
- **Auth beyond a first login** — AUTH-07 self-retirement, AUTH-08 password change, AUTH-09 password reset, and the session list AUTH-01's dropped columns exist for.
- **Whole sections** — SRC search, ATT attachments, SEC-10 the audit log, OPS-02 through OPS-06, and everything under Nice-to-have.
- **Frontend** — the TUI, and the engine interface module the frontend TODO describes. Both belong to the step that has a second frontend and the state worth rendering in it.
