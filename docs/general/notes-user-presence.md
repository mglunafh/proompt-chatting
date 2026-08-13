# Users and presence

## Presence model

- **Per user, not per session** — an update names a user and a state, never which client or how many; `client_label` stays an authentication concern.
- **Derived from the connection registry** — the registry in [notes-authentication.md](notes-authentication.md) is keyed by session and holds one socket each, so presence is the per-user count of live sockets: online at one or more, offline at zero. Counting sessions instead would give the same number.
- **Never persisted** — a restart starts everyone offline until they reconnect.
- **Edge-triggered** — 0→1 emits online and 1→0 emits offline; session churn in between emits nothing, so opening a TUI beside a running console is silent.
- **Revoke-closes are ordinary disconnects** — the socket closes on logout, ban and expiry decrement the count through the same path as any other drop, so no revoke path needs its own presence handling.

## Updates

- **Broadcast to every other connected session** — the roster is secret from nobody, since public channels are open to anyone and a server channel holds every account ([notes-community.md](notes-community.md)), and a change happens on connect and disconnect rather than per action. Conversation-scoped delivery would cost a membership lookup per change and a retroactive rule when someone joins a group, and would hide presence of users a member has not talked to yet.
- **The payload is a user ID and a state** — by ID, since usernames are renameable and clients resolve them locally ([notes-validation.md](notes-validation.md)).
- **The offline frame carries `last_seen_at`** — a client renders "last seen 20 minutes ago" from the frame instead of a follow-up fetch.
- **No self-echo on connect and disconnect** — a user's own sessions do not receive their own online and offline frames, which tell them nothing they do not already know. A declared-status change does reach them, since it is set on one client and has to show on the rest.
- **Offline is broadcast after a ~20s grace period**, cancelled by a reconnect inside the window, so a network blip does not flicker every roster. Its floor is a client's first reconnect attempt ([notes-protocol.md](notes-protocol.md)) — shorter than that and the blip it exists for fires the frame anyway. Detection is already delayed by the heartbeat timeout; the grace makes the delay deliberate rather than incidental.
- **The pending offline frame is a coroutine `Job` per user** — a `ConcurrentHashMap` of user to a job that delays and then broadcasts, cancelled outright by a reconnect. A delay to cancel rather than an entry to expire, so nothing else can fire the frame by evicting it.

## Snapshot at connect

- **Pushed as the first frame after upgrade** — the registry operation that adds the socket captures the broadcast state in the same step, live sockets and grace-window users together, so nothing can change between the snapshot and the first delta. Fetching over REST instead would leave a gap in which a change reaches every connected socket but not the one still registering, and the client would render that user wrong until their next transition, with neither side able to detect it.
- **The snapshot reports broadcast state, not the raw ref-count** — a user inside their offline grace window still reads as online, because no offline frame has fired and none will if they reconnect in time. Reporting zero live sockets as offline would latch a just-connected client to a value that no later frame corrects.
- **Reconciled on the heartbeat** — the heartbeat carries a digest of the online set, and a client whose roster disagrees asks for a fresh snapshot. This is the same shape as the head `change_seq` the heartbeat already carries for message catch-up, and it covers drift from any cause rather than only at startup.

## Declared status

- **A closed set** — available, away, do not disturb. The user sets it and the server never infers it: a live socket says nothing about whether anyone is at the keyboard, and a console client blocked on input cannot tell waiting from absent.
- **Independent of connectivity** — presence stays the live-socket count and status is a separate field; the two are set, stored and reasoned about apart from each other.
- **Persisted on the user row** — it belongs to the user rather than a session, so it must show on every client of theirs and survive a restart while they stay connected.
- **Sticky** — it holds across disconnects until the user changes it. A status that silently resets is worse than a stale one, since nothing tells the user it happened.
- **No functional consequence** — do not disturb is a label; it suppresses nothing. Whether it should gate delivery is a notification-policy question, and deciding it here would bury that policy in the presence model.
- **Carried on presence frames and in the snapshot** — a change rides the same channel as a presence change and reaches the setter's own sessions besides, and the roster pushed at connect includes each user's status, so a client is never left waiting for someone to change it.

## Profile changes

- **Broadcast like a status change** — username, display name and bio are user-row fields every client renders, and every client caches the id-to-name mapping ([notes-client-cache.md](notes-client-cache.md)), so a change goes out on the same channel and to the same audience as a presence frame, the setter's own sessions included. Without it a rename leaves every other client in the community drawing the old name.
- **A client that was away repairs on connect** — the snapshot carries each user's current name and display name beside their status, and anyone absent from it is resolved by the same user fetch that supplies their `last_seen_at`.

## Last seen

- **`last_seen_at` on the user row** — the offline frame reaches only clients connected to witness it and the snapshot carries the online roster, so without a stored value anyone who left before a client connected reads as offline with no timestamp.
- **Written when the ref-count hits zero** — stamped at the moment the last socket dropped, not when the grace period expires; the grace is a display rule about flicker, not a claim about when the user was last there.
- **Repaired at startup, not flushed periodically** — a hard kill runs no disconnect handler, so a boot query raises each user's `last_seen_at` to the newest `last_used_at` across their sessions where that is later. The registry is empty at boot, so every user is offline by definition and the repair is one statement, accurate to within one heartbeat.
- **Means last connected, never last active** — nothing in the model observes activity now that status is user-declared.
- **Read for offline users from the user record** — the snapshot carries only the online roster, so the timestamp for everyone else rides the user fetch.

## Typing indicators

- **Ephemeral** — never persisted and never replayed on catch-up; a reconnecting client starts with no indicators at all.
- **Scoped to the conversation** — delivered to the live sessions of that conversation's other members, unlike presence, which goes to everyone. No self-echo.
- **Re-asserted about every 4s while typing** — timer-driven rather than keystroke-driven: emit on the first keystroke, then at most once per interval while the input is non-empty and has changed since the last tick, so a fast typist and a slow one produce the same traffic. This interval is the value actually chosen; the expiry and the floor below are set against it.
- **Expired by the receiver after about 10s** — comfortably longer than the refresh interval, so one lost frame does not flicker the indicator, and a sender that crashes, drops or is revoked mid-word clears itself without cooperating.
- **No stop frame** — a message from that user clears their own indicator in that conversation, and pausing or abandoning a draft falls to the expiry. One frame type, fire and forget, with nothing to keep in sync.
- **Floored server-side** — frames arriving faster than roughly one per two seconds per conversation are dropped rather than counted against the interaction rate limit, so a misbehaving client cannot burn a real user's send budget with typing spam ([notes-rate-limiting.md](notes-rate-limiting.md)). The floor sits well under the refresh interval, so a well-behaved client never meets it.
- **The floor is an expiring map keyed `(sender, conversation)`** — Caffeine with `expireAfterWrite` at the floor interval, so a present key drops the frame and an absent one accepts it. The expiry is the rule itself, leaving no timestamps to compare and nothing to sweep.
- **Not sent in a server channel** — membership there is every account, so one indicator would tell everyone connected that an admin is typing. The one kind where scoping to the conversation bounds nothing.
