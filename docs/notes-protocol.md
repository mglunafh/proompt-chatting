# Protocol notes

- **Frames are sealed hierarchies with a `type` discriminator** — kotlinx.serialization polymorphism, in the shared protocol module compiled into both server and client.
- **Commands and events are separate hierarchies** — client→server and server→client, so a client cannot construct a server-only frame.
- **Version is checked once at the WebSocket upgrade** — a mismatch is rejected there, not carried on every frame.
- **Strictness is asymmetric** — the server rejects unknown keys, the client ignores them, so a newer server can add event fields without breaking older clients.
- **Errors are typed frames** — a rejected command says why and the connection survives.
- **Acks correlate by `client_msg_id`** — the send idempotency key doubles as the correlation key.
- **The heartbeat is an application frame, not protocol ping/pong** — it carries the user's head `change_seq` and a digest of the online set, and the server does work behind each tick (session re-check, `last_used_at` refresh), none of which a built-in ping affords.
- **The server ticks and the client replies** — the reply is what proves liveness, and a client that misses consecutive ticks is closed, dropping out of the presence count. The reply is exempt from the interaction rate limit, so a client cannot spend its own send budget staying alive.
- **One heartbeat interval fixes three numbers elsewhere** — offline-detection latency, the worst case on a missed revoke-close, and the accuracy of the boot last-seen repair. It is chosen once, here.
- **Reconnect is client-driven, with exponential backoff and jitter** — the server never invites a client back; a dropped socket is retried by the client, which resumes by presenting its sync cursor after the upgrade. Backoff with jitter is what keeps a server restart from returning as a reconnect stampede.
- **Only an application close code stops the reconnect loop** — an unrecognized code, a missing one, an abnormal `1006` and a graceful `1001` all mean retry with backoff. A client halts only on a code it knows in the 4000–4999 range, so a newer server can introduce one without stranding an older client.
- **Close codes mirror the upgrade's HTTP statuses** — `4401` for a session that is no longer valid, so the client discards its token and asks for credentials, and `4403` for a disabled account, where it discards the token and says so rather than prompting, since a password will not help. The upgrade rejects the same two cases as `401` and `403` ([03-authentication.md](03-authentication.md)), so a client too old to read the close code stops one reconnect later instead of looping.
- **`4409` session displaced** — another socket took over this session. Close-only, with no upgrade counterpart, because the displaced client's token is still valid: it stops, keeps the token, and offers a manual reconnect. Auto-retrying would evict the socket that just evicted it, and the two would trade places indefinitely.
