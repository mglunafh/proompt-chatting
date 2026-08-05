# Rate limiting notes

## Unauthenticated surfaces

Three routes take a credential from a caller with no session: login, invite
redemption ([02-registration.md](02-registration.md)) and password-reset
redemption ([03-authentication.md](03-authentication.md)). All three key the same
way, since there is no `user_id` to key on. Two keys are available and each is
broken alone:

- **Submitted username** — catches a targeted attack on one account, but lets anyone lock out any user they can name by deliberately failing. Lockout as denial of service.
- **Source IP** — catches credential stuffing, but rotating IPs evade it and NAT means several legitimate users can share one.

Use both, with different thresholds and different responses.

- **Count failures, not attempts, and reset on success** — a user with a working password never meets the limiter no matter how often they log in.
- **Prefer progressive delay to hard lockout** — the nth consecutive failure for a username sleeps roughly `2^n` seconds before responding, capped near a minute. This flattens an attacker's rate while a legitimate user who pauses and retries always gets in, and it has no lockout-DoS vector. That matters here specifically: [02-registration.md](02-registration.md) protects the last active admin from being disabled, but nothing stops someone jamming an admin's login with deliberate failures. Progressive delay makes that attack pointless.
- **Harder cap per IP** — around 20 failures in 15 minutes across all usernames, then reject outright for a window. No legitimate client does that. The ceiling tracks the concurrent Argon2id hashing the box can absorb, not an estimate of what guessing requires.
- **Argon2id is already a rate limiter, and that cuts both ways** — tuned to ~150ms, one connection can try at most ~6 guesses per second before anything is added, which is the real brake on guessing. It also makes unauthenticated login the most expensive operation an anonymous caller can trigger, so concurrent logins burn CPU that legitimate traffic needs. Protecting the CPU is the stronger argument for the per-IP limit.
- **Keep responses uniform** — same status, same message, and same timing for unknown user and wrong password, which means hashing against a dummy Argon2 hash when the user does not exist or the early return leaks existence. Throttle responses must not distinguish "this account exists and you are failing against it" from "your IP is blocked".
- **The two redemption routes take the per-IP cap and nothing else** — there is no username to key a progressive delay on, and the tokens are high-entropy enough that guessing is not the risk. The cap is there because both hash a new password with Argon2id on an anonymous request, which is the same CPU the login limit protects.

Where the counters live:

- **In-memory** — a Caffeine map of key to failure state with expiry-after-write. Simple and free, but resets on deploy.
- **Columns on `users`** (`failed_attempts`, `locked_until`) — survives restart and is admin-visible, but costs a write on every failed login and cannot express the IP dimension.

In-memory for both dimensions, plus a log line per failed login. The counter
does not need to be durable; the evidence does. Log lines already ship to Loki,
so that yields queries and Grafana alerting with no write on the failure branch.

## User interactions

Everything here is authenticated, so `user_id` comes from the session lookup and
there is no key ambiguity.

- **Token bucket, per user, per operation class** — N tokens refilling at R per second, each call takes one, empty bucket rejects. Suits chat because it permits bursts (three pasted messages in a second is normal) while capping the sustained rate. Fixed windows allow double the nominal rate across a window boundary and punish exactly that normal burst.

Classes want different numbers, so this is not one limiter:

- **Message send** — generous burst, on the order of 20 tokens refilling at 2/s. The anchor the other classes are set against.
- **History and search queries** — the expensive ones, much tighter.
- **Invite minting** — already covered by the per-user active-invite quota in [02-registration.md](02-registration.md), which is a stock limit rather than a rate. Leave it there.
- **Attachment upload and download** — a bucket each on the REST routes, numbers in [notes-attachments.md](notes-attachments.md). Downloads need one of their own because the per-user storage allowance counts stored bytes and never sees a read.
- **Reactions and cursor advances** — a class of their own, tighter than sends. Each is one small frame that bumps `change_seq` and fans out, so a toggle repeated in a loop is the cheapest thing to send and among the most expensive to serve; a send at least costs the sender a body.
- **Typing indicators are floored, not budgeted** — dropped above roughly one per two seconds per conversation instead of taking a token, so typing spam costs a misbehaving client its own frames rather than a real user's send budget ([04-user-presence.md](04-user-presence.md)).

Two enforcement points, not one:

- **REST routes** use Ktor's built-in `RateLimit` plugin — register a named limiter with a key extractor, wrap the routes, and get `429` with `Retry-After` handled.
- **WebSocket frames cannot** — the plugin sees one HTTP request, the upgrade, and never sees the frames after it. Per-message limiting lives inside the socket read loop, checking a bucket before dispatching each inbound frame.

Since message send is the main thing worth limiting and it is WebSocket-only,
most of the value is in the hand-rolled half; the plugin covers the REST surface
almost incidentally.

- **On breach, send an error frame rather than closing the socket** — a close triggers the client's reconnect logic, so a client that is spamming becomes a reconnect storm, which is worse than what was being limited. The frame is one of the typed errors in [notes-protocol.md](notes-protocol.md).
- **The heartbeat reply takes no token** — a client cannot spend its own budget staying alive, and a limiter that could close a socket by starving it would defeat the error-frame decision above.
- **Storage is a Caffeine cache of `user_id` to bucket with expiry-after-access**, so idle users evict themselves. This is correct only because there is a single server process; with two, buckets diverge and a shared store is required. Worth recording as an assumption rather than discovering it later.

## What they share

Little: different keys, different triggers, different durability requirements,
different failure responses. What can be shared is a thin internal interface
(`tryAcquire(key, class): Allowed | RetryAfter(duration)`) and Micrometer
counters tagged by limiter name, so throttle events reach the Grafana dashboard.
At 10–50 users that abuse signal is arguably worth more than the blocking.

Login limiting is a security control and goes in from the start — it is a small
amount of code and it covers every route that takes a credential without a
session.
Interaction limiting is resource protection, its value scales with user count,
and it can wait until messaging exists. Reserve the seam in the WebSocket read
loop now, because retrofitting a check into a dispatch path is more annoying
than leaving a no-op there.
