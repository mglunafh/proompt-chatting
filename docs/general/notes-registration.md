# Registration

## Bootstrapping the admin account

The first admin is seeded at boot — the username from an environment variable,
the password from a mounted secret — then retired once real admins exist.
Procedure:

1. Boot with `BOOTSTRAP_ADMIN_USERNAME` set and the password mounted as a Docker secret, read through `BOOTSTRAP_ADMIN_PASSWORD_FILE`; the server seeds the account only if it does not already exist.
2. Log in as the bootstrap admin, invite each replacement by the flow below, and promote them once they have redeemed — one human per account, no shared logins.
3. Verify a replacement admin can log in and holds full admin rights.
4. Disable the bootstrap account (a deliberate admin action, not automatic).
5. Remove the secret file and the variable so the seed credential no longer exists anywhere.

Best practices and invariants:

- **Disable, don't delete** — deactivate with a flag to preserve foreign keys and the audit chain (the bootstrap admin is the root of "who created/promoted whom").
- **Last-active-admin protection** — the server refuses to disable, demote, or ban the last remaining active admin; enforced server-side, not in the UI.
- **Create-if-absent only** — the seed inserts when missing and never updates an existing row, so the credential cannot become a permanent password backdoor.
- **Seed on the bootstrap identity's existence**, not on "the `users` table is empty" — the table is non-empty the moment anyone registers, and a retired bootstrap account must not be reseeded by the next boot.
- **Retire manually** — do not auto-disable the bootstrap account when a second admin appears; retire it only after verifying the replacement.
- **Secret hygiene** — a strong random value, treated as single-use and deleted after retirement. It arrives as a Docker Compose secret like every other value the server takes, so it is never in the compose file itself.

## Leaving

- **A user retires their own account** — the same row change a ban makes: `disabled` set, sessions deleted, live sockets closed and outstanding invites revoked, through the one path in [notes-roles.md](notes-roles.md) rather than a second one.
- **Confirmed with the current password** — the guard a password change already takes ([notes-authentication.md](notes-authentication.md)), since a borrowed terminal is otherwise one call from retiring the account.
- **Audited as its own action** — a retirement and a ban write different actions, so the record distinguishes leaving from being removed.
- **Nothing of theirs is removed** — messages, memberships and the vouching chain survive per disable-don't-delete above, and an admin re-enables the account, which is what makes it retirement rather than deletion.

## Invite flow

Regular users are created by redeeming an invite. Procedure:

1. An issuer creates an invite; the server generates one high-entropy secret and returns it as both a bare token and a link (`https://host/invite/<token>`).
2. The issuer shares the token/link out-of-band with the invitee.
3. The invitee registers by presenting the token (paste the code, or open the link where a surface exists) along with their new username and password, both validated against the rules in [notes-validation.md](notes-validation.md).
4. The server redeems the invite atomically, creates the account, records who redeemed it, and inserts the account's membership row in every server channel.
5. The invite is now consumed and cannot be reused.

One secret, two renderings — the link is the token wrapped in a URL. The link is
currently cosmetic: all frontends are JVM console/TUI, so nothing opens
`/invite/<token>` yet, and the bare token is the real artifact until a web/GUI
surface or a URL-scheme handler exists.

The `invites` record is in [notes-db-schema.md](notes-db-schema.md).

Status is derived, not stored: `revoked_at` set → revoked; `used_count >= max_uses`
→ used; `expires_at < now()` → expired; else active.

Best practices and invariants:

- **Hash + high entropy** — store `hash(token)`, never the raw code (a DB leak must not yield live invites); use a 128+ bit URL-safe random value so it can't be guessed or enumerated.
- **Atomic single-use redeem** — enforce the use limit with a conditional update (`UPDATE ... WHERE token_hash = ? AND used_count < max_uses AND revoked_at IS NULL AND expires_at > now()`) and check rows-affected; never read-then-write, or two redeemers race.
- **Revocation + states** — the issuer or any admin revokes an invite that still has uses left, including a partly-used multi-use one; revoking stops further redemptions and undoes none of the past ones. Status is derived (active / used / expired / revoked), and the revocation is recorded in the audit chain.
- **A ban revokes the issuer's outstanding invites** — disabling an account revokes every invite of theirs with uses left, in the same transaction. Past redemptions stand; the accounts they created are judged on their own conduct.
- **Vouching chain** — `issued_by` on the invite, and each redemption in the audit chain, which already records them. That covers `max_uses > 1` without a second shape: the invite row carries who may vouch and how many uses are left, the audit rows carry who took them.
- **Who can issue** — admins always; everyone else only when a server-side `allow_user_invites` flag is enabled (default admin-only), with a per-user quota on active invites to prevent mass-minting. Admins are exempt from the quota.
- **Multi-use invites are admin-only** — `max_uses > 1` is a secret that gets forwarded rather than handed to one person, so a non-admin issues single-use invites only.
- **TTL ceiling** — `expires_at` cannot exceed a server-wide maximum of roughly 30 days, admins included, so no invite outlives the attention of whoever issued it. Example: a 7-day default.
- **Listing** — a caller lists the invites they issued; an admin lists every invite on the server, with `issued_by` on each row. Rows carry the derived status, counts and timestamps, and never a token, which exists only as a hash and cannot be shown again after issuance.
