# Authentication

## Mechanism

Authentication uses opaque, database-backed session tokens rather than JWT.

- An **opaque token** is a random string with no embedded claims — a lookup key into server-side session state, meaningless on its own.
- Chosen for **instant revocation** (delete the row), **no signing keys or token crypto to misuse**, and because **multi-instance is an explicit non-goal** — so the statelessness a JWT would buy is worth nothing here.

## Sessions

The `sessions` record is in [notes-db-schema.md](notes-db-schema.md).

- **SHA-256 hashing** — store `hash(token)`, never the raw token; a fast hash suffices because the token is 256-bit random with nothing to brute-force.
- **Multi-session** — one row per login, so a user may hold several concurrent sessions (e.g. console + TUI at once). This table holds credentials; the in-memory connection registry holds live sockets.
- **`last_used_ip` is the only evidence in the session list** — an `inet` column, where the others are two timestamps and a `client_label` the client picks ([notes-validation.md](notes-validation.md)), so without it the list answers how many sessions exist and not which one is not the user's. Written in the same UPDATE as `last_used_at`, so it costs no write of its own and it names whoever is using the token now rather than whoever logged in: a token stolen after login and replayed elsewhere moves the address, which a login-time column would miss.
- **It is evidence only if the proxy is pinned** — the socket peer is Caddy, so the value comes from `X-Forwarded-For`, and a server that trusts that header from anything but its own proxy lets a client choose the column outright. Trusting no other hop is the precondition for storing it at all.
- **Registry keyed by session, one socket each** — a live socket carries the session `id` it was opened with and a session holds at most one, so the registry maps a session to a socket rather than to a set, and a user's socket count and session count are the same number. Revoking one session closes only its socket while a ban closes every socket of that user.

## Flows

- **Login (issue)** — verify the user's password, generate a 256-bit random token, insert a session row with `hash(token)` / `user_id` / expiry, and return the raw token once; the client persists it in its local store.
- **Authenticate (lookup)** — the client sends `Authorization: Bearer <token>` on REST requests and on the WebSocket upgrade; the server hashes it and looks up by `token_hash`; the request is valid if the row exists and is not expired, binding it to `user_id`. On WebSocket the token is validated once, at upgrade.
- **Re-upgrade replaces** — an upgrade presenting a token that already holds a live socket registers the new socket and then closes the old one, in that order. Rejecting the second would lock out the ordinary case, a client reconnecting after a blip while the server still holds its zombie socket, for up to a heartbeat timeout; registering first keeps the user's socket count off zero, so no presence edge fires and no offline grace starts on a session that never actually lapsed.
- **Sharing a token evicts, by design** — two clients holding the same token displace each other on every reconnect, in turn, indefinitely. A session is one client: the remedy is a second login, not a shared token.
- **Revoke** — logout deletes the row; logout-everywhere deletes all rows for `user_id`; a ban disables the user, then deletes their sessions; a stolen token is killed by deleting its row. All are instant, single SQL statements.
- **List / kill own sessions** — a user lists their own rows (`id`, `client_label`, `created_at`, `last_used_at`, `last_used_ip`; never the token, which exists only as a hash) and deletes one by `id`. Killing the current session is logout; killing another is how a user drops a device they no longer hold.
- **Close on revoke** — every revoke path also closes the affected sessions' live sockets through the in-memory registry. A socket is authenticated once, at upgrade, and never revisits the table, so deleting the row alone would leave it receiving fan-out until it happened to drop — the case revocation exists for. The close carries `4401`, or `4403` where the user was banned or disabled, so the client stops rather than reconnecting against a dead token.
- **Upgrade rejections are typed** — an unknown, expired or revoked token is rejected `401`, a disabled user `403`, a protocol version mismatch `426`, and a rate-limited client `429`. The first two pair with the close codes `4401` and `4403` in [notes-protocol.md](notes-protocol.md), so a client learns the same fact whether it was already connected or reconnecting, and a client too old to read the close code still stops at the rejection. The other two need no counterpart: the version is checked once at upgrade and cannot change under an open socket, and a rate-limited client on one gets an error frame rather than a close ([notes-rate-limiting.md](notes-rate-limiting.md)).
- **Re-check on heartbeat** — each heartbeat tick looks up the socket's session row and closes the socket if the row is gone or `expires_at` has passed. This is the only check that catches expiry, which fires no frame of its own, and it bounds a missed close to one heartbeat interval ([notes-protocol.md](notes-protocol.md)): a socket that registers just after a revoke swept the registry is caught on its next tick. The same lookup joins `users` for `server_role`, so a demotion reaches a socket that authenticated once at upgrade within one tick rather than lasting the connection ([notes-roles.md](notes-roles.md)).

## Password change

- **The current password is required** — verified before the new hash is written, so a session someone else is holding cannot replace the credential silently. This is the only place a password is checked outside login.
- **The new password is validated before hashing** — the rules in [notes-validation.md](notes-validation.md), identical at every point a password is set.
- **Hashed fresh** — Argon2id via password4j with a new salt and whatever cost the server is tuned to now, so a change also migrates a row hashed under older parameters.
- **Every other session is revoked, the caller's survives** — deleting all of them would log the user out of the client they just used. The rest go through the revoke path above, sockets included, since changing a password is what a user does when they believe a token leaked.

## Password reset

There is no email in the model, so nothing self-service exists: a user who cannot
log in asks an admin, who issues a token and hands it over the same way they hand
over an invite. The `password_resets` record is the invite record with the
multi-use machinery removed, in
[notes-db-schema.md](notes-db-schema.md).

- **Admins issue, and only against a lower role** — a redeemed token makes the holder that user, so issuance sits with promotion and demotion under the rule that nobody acts on an equal or higher role ([notes-roles.md](notes-roles.md)).
- **Hashed, high entropy, shown once** — a 256-bit random value stored as `hash(token)` and returned raw at issuance, never again, as with invites.
- **Short TTL** — hours rather than the invite's days ([notes-registration.md](notes-registration.md)). A reset is handed over in a conversation already happening, so an unredeemed one should expire before anyone forgets it exists.
- **A new token supersedes the outstanding one** — at most one live reset per user, so an admin reissuing after a failed hand-off does not leave two working tokens in circulation.
- **Redeemed by the same atomic conditional update as an invite** — `UPDATE ... WHERE token_hash = ? AND used_at IS NULL AND expires_at > now()`, acting on rows-affected, so two redeemers cannot race and a spent token cannot be replayed.
- **The new password is validated and hashed as on a change** — the same rules in [notes-validation.md](notes-validation.md), a new salt, and the cost the server is tuned to now.
- **Redeeming revokes every session** — all of them, unlike a change: the holder could not authenticate to begin with, so there is no current session to preserve, and any that do exist are a reason the reset was needed.
- **No public endpoint** — nothing accepts a username and issues, mails or displays a token. Issuance is an authenticated admin action and the only way one is created.
- **Issuance and redemption are audited** — who issued it, for whom, and when it was consumed, in the chain that already carries invites and admin promotion.

## Handling a password in a client

- **Prompted, never an argument** — a Clikt option or an environment variable lands in `ps` output and shell history.
- **Read without echo**, so it stays out of the terminal's scrollback.
- **Typed twice wherever it is set** — registration, change and reset. A typo is undone only by an admin-issued reset token.
- **Never logged**, on either side — container stdout ships to Loki.

## Token lifetime considerations

- **Policy** — session tokens are long-lived, renewed on use (sliding expiry) with an absolute cap; "kill it now" cases are handled by revocation, not by short expiry. Example: renew on each authenticated use, absolute cap ~30 days.
- **The heartbeat counts as use** — a client holding only a WebSocket makes no authenticated REST requests, so without this a session in active use would expire mid-connection.
- **Idle timeout** — optionally reap sessions with no live socket, with a timeout measured in days rather than a short hard expiry. Example: log out a session after ~14 days with no activity. A connected session is demonstrably alive, so its idle clock starts when the socket drops.

These map onto the `last_used_at` and `expires_at` columns in the sessions table.
`last_used_at` has a second consumer: [notes-user-presence.md](notes-user-presence.md)
repairs last-seen from it at startup, which is what makes the heartbeat refresh
worth its writes.
