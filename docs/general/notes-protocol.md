# Protocol notes

## What it is

The wire between server and client: how a frame is shaped, what the two sides
may send each other, and the three moments that are not ordinary traffic — the
upgrade, the heartbeat, and the close. The shared module holds the definitions,
so both sides compile against one copy.

## What it solves

- **A newer server not breaking an older client** — the strictness is deliberately lopsided, and every stopping condition is a code a client can fail to recognize without looping.
- **A dropped socket costing latency rather than state** — the client resumes by presenting its cursor, so nothing has to be replayed or acknowledged per recipient.
- **Liveness that does work while it proves liveness** — one tick carries the catch-up head and the roster digest, and drives the checks a built-in ping could not.

## Framing

- **Frames are sealed hierarchies with a `type` discriminator** — kotlinx.serialization polymorphism, in the shared protocol module compiled into both server and client.
- **Client frames and server frames are separate hierarchies** — client→server and server→client.
- **Version is checked once at the WebSocket upgrade** — a mismatch is rejected there, not carried on every frame.
- **Strictness is asymmetric** — the server rejects unknown keys, the client ignores them, so a newer server can add server frame fields without breaking older clients.
- **Frame and body caps are enforced before deserialization** — the transport limits in [notes-validation.md](notes-validation.md), which are what bound the work an unparsed frame can cost.
- **Errors are typed frames** — a rejected client frame says why and the connection survives. A breach of the interaction rate limit is one of them rather than a close, since closing turns a spamming client into a reconnect storm ([notes-rate-limiting.md](notes-rate-limiting.md)).
- **A dropped frame is the third outcome** — a typing indicator arriving faster than its floor is discarded with no answer at all, neither accepted nor rejected, because the reply would cost more than the frame ([notes-user-presence.md](notes-user-presence.md)).
- **Acks correlate by `client_msg_id`** — the send idempotency key doubles as the correlation key.

## After the upgrade

- **The presence snapshot is the first frame the server sends** — captured by the same registry operation that adds the socket, so no delta can slip between the two ([notes-user-presence.md](notes-user-presence.md)).
- **The client resumes by presenting its sync cursor**, and pages anything above it before the live stream means anything.

## The heartbeat

- **The heartbeat is an application frame, not protocol ping/pong** — it carries the user's head `change_seq` and a digest of the online set, and the server does work behind each tick (session re-check, `last_used_at` refresh), none of which a built-in ping affords.
- **The server ticks and the client replies** — the reply is what proves liveness, and a client that misses consecutive ticks is closed, dropping out of the presence count. The reply is exempt from the interaction rate limit, so a client cannot spend its own send budget staying alive.
- **One heartbeat interval fixes every number that depends on it** — offline-detection latency, the worst case on a missed revoke-close, how long a demoted mod keeps their powers on an open socket, and the accuracy of the boot last-seen repair. Anything bounded by a tick is bounded by this one choice, made here.

## Reconnect and close

- **Reconnect is client-driven, with exponential backoff and jitter** — the server never invites a client back; a dropped socket is retried by the client, which resumes by presenting its sync cursor after the upgrade. Backoff with jitter is what keeps a server restart from returning as a reconnect stampede.
- **Only an application close code stops the reconnect loop** — an unrecognized code, a missing one, an abnormal `1006` and a graceful `1001` all mean retry with backoff. A client halts only on a code it knows in the 4000–4999 range, so a newer server can introduce one without stranding an older client.
- **Close codes mirror the upgrade's HTTP statuses** — `4401` for a session that is no longer valid, so the client discards its token and asks for credentials, and `4403` for a disabled account, where it discards the token and says so rather than prompting, since a password will not help. The upgrade rejects the same two cases as `401` and `403` ([notes-authentication.md](notes-authentication.md)), so a client too old to read the close code stops one reconnect later instead of looping.
- **`4409` session displaced** — another socket took over this session. Close-only, with no upgrade counterpart, because the displaced client's token is still valid: it stops, keeps the token, and offers a manual reconnect. Auto-retrying would evict the socket that just evicted it, and the two would trade places indefinitely.
