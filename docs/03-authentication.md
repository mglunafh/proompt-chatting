# Authentication

## Mechanism

Authentication uses opaque, database-backed session tokens rather than JWT.

- An **opaque token** is a random string with no embedded claims — a lookup key into server-side session state, meaningless on its own.
- Chosen for **instant revocation** (delete the row), **no signing keys or token crypto to misuse**, and because **multi-instance is an explicit non-goal** — so the statelessness a JWT would buy is worth nothing here.

## Sessions table

```
sessions(
  id,
  token_hash,       -- SHA-256 of the token; the lookup key
  user_id,
  created_at,
  last_used_at,
  expires_at,
  client_label      -- e.g. "console" / "tui"; lets a user see and kill individual sessions
)
```

- **SHA-256 hashing** — store `hash(token)`, never the raw token; a fast hash suffices because the token is 256-bit random with nothing to brute-force.
- **Multi-session** — one row per login, so a user may hold several concurrent sessions (e.g. console + TUI at once). This table holds credentials; the in-memory connection registry holds live sockets.
- **Registry keyed by session, one socket each** — a live socket carries the session `id` it was opened with and a session holds at most one, so the registry maps a session to a socket rather than to a set, and a user's socket count and session count are the same number. Revoking one session closes only its socket while a ban closes every socket of that user.

## Flows

- **Login (issue)** — verify the user's password, generate a 256-bit random token, insert a session row with `hash(token)` / `user_id` / expiry, and return the raw token once; the client persists it in its local store.
- **Authenticate (lookup)** — the client sends `Authorization: Bearer <token>` on REST requests and on the WebSocket upgrade; the server hashes it and looks up by `token_hash`; the request is valid if the row exists and is not expired, binding it to `user_id`. On WebSocket the token is validated once, at upgrade.
- **Re-upgrade replaces** — an upgrade presenting a token that already holds a live socket registers the new socket and then closes the old one, in that order. Rejecting the second would lock out the ordinary case, a client reconnecting after a blip while the server still holds its zombie socket, for up to a heartbeat timeout; registering first keeps the user's socket count off zero, so no presence edge fires and no offline grace starts on a session that never actually lapsed.
- **Sharing a token evicts, by design** — two clients holding the same token displace each other on every reconnect, in turn, indefinitely. A session is one client: the remedy is a second login, not a shared token.
- **Revoke** — logout deletes the row; logout-everywhere deletes all rows for `user_id`; ban/suspend disables the user, then deletes their sessions; a stolen token is killed by deleting its row. All are instant, single SQL statements.
- **List / kill own sessions** — a user lists their own rows (`id`, `client_label`, `created_at`, `last_used_at`; never the token, which exists only as a hash) and deletes one by `id`. Killing the current session is logout; killing another is how a user drops a device they no longer hold.
- **Close on revoke** — every revoke path also closes the affected sessions' live sockets through the in-memory registry. A socket is authenticated once, at upgrade, and never revisits the table, so deleting the row alone would leave it receiving fan-out until it happened to drop — the case revocation exists for. The close carries `4401`, or `4403` where the user was banned or disabled, so the client stops rather than reconnecting against a dead token.
- **Upgrade rejections are typed** — an unknown, expired or revoked token is rejected `401`, a disabled user `403`, a protocol version mismatch `426`, and a rate-limited client `429`. They pair with the close codes in [notes-protocol.md](notes-protocol.md), so a client learns the same fact whether it was already connected or reconnecting, and a client too old to read the close code still stops at the rejection.
- **Re-check on heartbeat** — each heartbeat tick looks up the socket's session row and closes the socket if the row is gone or `expires_at` has passed. This is the only check that catches expiry, which fires no event of its own, and it bounds a missed close to one heartbeat interval: a socket that registers just after a revoke swept the registry is caught on its next tick.

## Token lifetime considerations

- **Policy** — session tokens are long-lived, renewed on use (sliding expiry) with an absolute cap; "kill it now" cases are handled by revocation, not by short expiry. Example: renew on each authenticated use, absolute cap ~30 days.
- **The heartbeat counts as use** — a client holding only a WebSocket makes no authenticated REST requests, so without this a session in active use would expire mid-connection.
- **Idle timeout** — optionally reap sessions with no live socket, with a timeout measured in days rather than a short hard expiry. Example: log out a session after ~14 days with no activity. A connected session is demonstrably alive, so its idle clock starts when the socket drops.

These map onto the `last_used_at` and `expires_at` columns in the sessions table.
`last_used_at` has a second consumer: [04-user-presence.md](04-user-presence.md)
repairs last-seen from it at startup, which is what makes the heartbeat refresh
worth its writes.
