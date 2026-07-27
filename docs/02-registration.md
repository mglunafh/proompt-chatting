# Architectural decision regarding new accounts

## Bootstrapping the admin account

The first admin is seeded from `BOOTSTRAP_ADMIN_*` env vars, then retired once
real admins exist. Procedure:

1. Boot with the `BOOTSTRAP_ADMIN_*` env vars; the server seeds the account only if it does not already exist.
2. Log in as the bootstrap admin and create named, per-person admin accounts (one human per account, no shared logins).
3. Verify a replacement admin can log in and holds full admin rights.
4. Disable the bootstrap account (a deliberate admin action, not automatic).
5. Remove or rotate the env vars so the seed credential no longer exists anywhere.

Best practices and invariants:

- **Disable, don't delete** — deactivate with a flag to preserve foreign keys and the audit chain (the bootstrap admin is the root of "who created/promoted whom").
- **Last-active-admin protection** — the server refuses to disable, demote, or ban the last remaining active admin; enforced server-side, not in the UI.
- **Create-if-absent only** — the seed inserts when missing and never updates an existing row, so the env var can't become a permanent password backdoor.
- **Seed on a durable marker** — key off a `bootstrapped` flag or the bootstrap identity's existence, not "the `users` table is empty."
- **Retire manually** — do not auto-disable the bootstrap account when a second admin appears; retire it only after verifying the replacement.
- **Env-var hygiene** — use a strong random value, treat it as single-use, and delete it after retirement. Alternatively, have the server generate a random bootstrap password on first boot and print it to the logs once, keeping the secret out of the compose file entirely.

## Invite flow

Regular users are created by redeeming an invite. Procedure:

1. An issuer creates an invite; the server generates one high-entropy secret and returns it as both a bare token and a link (`https://host/invite/<token>`).
2. The issuer shares the token/link out-of-band with the invitee.
3. The invitee registers by presenting the token (paste the code, or open the link where a surface exists) along with their new username and password.
4. The server redeems the invite atomically, creates the account, records who redeemed it, and inserts the account's membership row in every server channel.
5. The invite is now consumed and cannot be reused.

One secret, two renderings — the link is the token wrapped in a URL. The link is
currently cosmetic: all frontends are JVM console/TUI, so nothing opens
`/invite/<token>` yet, and the bare token is the real artifact until a web/GUI
surface or a URL-scheme handler exists.

Invite record:

```
invites(
  id,
  token_hash,           -- store the hash, never the raw code
  issued_by,            -- vouching chain
  created_at,
  expires_at,           -- TTL (default 7 days)
  max_uses  default 1,  -- single-use by default; generalizes to N
  used_count default 0,
  redeemed_by,          -- who consumed it (join table if max_uses > 1)
  revoked_at            -- nullable; presence = revoked
)
```

Status is derived, not stored: `revoked_at` set → revoked; `used_count >= max_uses`
→ used; `expires_at < now()` → expired; else active.

Best practices and invariants:

- **Hash + high entropy** — store `hash(token)`, never the raw code (a DB leak must not yield live invites); use a 128+ bit URL-safe random value so it can't be guessed or enumerated.
- **Atomic single-use redeem** — enforce the use limit with a conditional update (`UPDATE ... WHERE token_hash = ? AND used_count < max_uses AND revoked_at IS NULL AND expires_at > now()`) and check rows-affected; never read-then-write, or two redeemers race.
- **Revocation + states** — the issuer or any admin revokes an invite that still has uses left, including a partly-used multi-use one; revoking stops further redemptions and undoes none of the past ones. Status is derived (active / used / expired / revoked), and the revocation is recorded in the audit chain.
- **Vouching chain** — record `issued_by` and `redeemed_by` so a bad actor can be traced to who invited them; pairs with the admin audit chain above.
- **Who can issue** — admins always; everyone else only when a server-side `allow_user_invites` flag is enabled (default admin-only), with a per-user quota on active invites to prevent mass-minting. Admins are exempt from the quota.
- **Multi-use invites are admin-only** — `max_uses > 1` is a secret that gets forwarded rather than handed to one person, so a non-admin issues single-use invites only.
- **TTL ceiling** — `expires_at` defaults to 7 days and cannot exceed a server-wide maximum of roughly 30, admins included, so no invite outlives the attention of whoever issued it.
- **Listing** — a caller lists the invites they issued; an admin lists every invite on the server, with `issued_by` on each row. Rows carry the derived status, counts and timestamps, and never a token, which exists only as a hash and cannot be shown again after issuance.
