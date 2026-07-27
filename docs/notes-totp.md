# TOTP notes

## Why the secret cannot be hashed

A password hash works because the server only needs to *verify*: hash the
submitted value, compare. TOTP is different — at every login the server
recomputes `HMAC-SHA1(secret, timestep)` itself, so it needs the actual secret
bytes back.

That rules out Argon2id and SHA-256 for this field, and makes the TOTP secret
the only long-lived recoverable credential in the system. Everything below
follows from that.

## Storage model

A separate table rather than columns on `users`:

```
totp_credentials(
  user_id,
  secret_ciphertext,   -- AES-GCM encrypted, never plaintext
  nonce,
  key_version,         -- which KEK encrypted this row
  enabled_at,          -- null until a live code confirms enrollment
  last_used_step       -- replay guard
)
```

- **Separate table** — 2FA is opt-in, so most users have no row rather than a table of nulls; the crypto blob stays out of the `users` row read on every request; and clearing 2FA (which an admin reset does) is a row delete.
- **Recovery codes are hashed, not encrypted** — verify-only, so they follow the session-token pattern in [03-authentication.md](03-authentication.md): high-entropy random plus a fast hash, single-use.

## Best practices

### Generation

- **160 bits from a CSPRNG** — RFC 4226 mandates at least 128 bits and recommends 160. That is 20 bytes, 32 base32 characters, and what authenticator apps expect. `SecureRandom`, never `Random`.
- **Longer is not better** — 256-bit secrets are harmless but buy nothing against HMAC-SHA1 and make manual entry worse, which matters when manual entry is the enrollment path.

### Storage

- **Envelope encryption** — the secret is encrypted with a data key; the data key is wrapped by a key-encryption key held outside the database (AWS KMS, GCP KMS, Azure Key Vault, or self-hosted HashiCorp Vault). Plaintext exists only in memory during verification.
- **AEAD ciphers only** — AES-256-GCM or ChaCha20-Poly1305, unique nonce per encryption. Raw AES-CBC without a MAC is a standard pentest finding.
- **Bind the ciphertext to the user** — pass `user_id` as additional authenticated data. Without it, an attacker with write access copies their own secret blob onto another row and generates valid codes for that account; the ciphertext is valid, just attached to the wrong person.
- **Version the key** — store which KEK encrypted each row so the key can be rotated and rows re-encrypted lazily.
- **Disk and database encryption do not substitute** — Postgres TDE and encrypted volumes protect against stolen hardware. They return decrypted plaintext to SQL injection, a compromised application-database connection, or a leaked `pg_dump`. Application-layer encryption is the control that applies here.
- **Strongest form: the application never sees the secret** — Vault's TOTP secrets engine stores the seed and verifies codes itself, so the app submits a code and receives valid/invalid.

### Verification

- **Rate limit and lock out** — six digits is a million possibilities, and a ±1 step window makes three codes valid at once. RFC 4226 §7.3 calls for throttling; typical practice is lockout after 5–10 failures.
- **Keep the window at ±1 step** — each extra step widens the guessing surface linearly. Correct NTP removes the need for more.
- **Record the consumed time step** and reject reuse, so an observed code dies at the end of its window.
- **Constant-time comparison** of the submitted code.

### Lifecycle

- **Confirm before activating** — persist as pending, require one live code, then set `enabled_at`. Stops a mistyped secret becoming a lockout.
- **Display once, never again** — no endpoint re-reveals the secret; re-enrollment mints a fresh one and invalidates the old.
- **Re-authenticate for sensitive changes** — disabling 2FA, regenerating recovery codes, changing a password. Otherwise a stolen session strips the second factor silently.
- **Audit every 2FA event** — enrollment, disable, recovery-code use. Most services also email the user; with no email in the model this collapses to the admin-visible audit chain from [02-registration.md](02-registration.md), which is weaker.
- **Keep the secret out of logs** — all container stdout ships to Loki, so a secret reaching a log line is stored a second time, unencrypted, under a different retention policy.

## Where production has moved

TOTP is phishing-vulnerable: a convincing fake login page relays the code to the
real server in real time, and the six digits authenticate the attacker. Serious
deployments have moved to WebAuthn/passkeys, where the credential is bound to
the origin and a relayed challenge fails.

That is unavailable here — WebAuthn needs a browser or platform authenticator
API, and every client is a JVM terminal app. TOTP is the strongest second factor
a console client can reach, not the strongest available.

## Proportionate for this project

Worth adopting at 10–50 users, self-hosted, single Postgres:

- 160-bit secret from `SecureRandom`
- AES-256-GCM, key from an env var, `user_id` as AAD, key-version column
- Verification rate limit with lockout, ±1 step window, consumed-step replay guard
- Pending-until-confirmed enrollment, never re-exposed
- Recovery codes hashed and single-use
- Secret never written to a log line

Worth skipping: KMS or Vault, per-record data keys, WebAuthn. If the env-var key
is outgrown, Vault's Transit engine is the natural upgrade and needs no schema
change beyond the `key_version` column.

The ceiling: with the key in an env var on the same host as the database,
whoever owns the host owns both. The control is aimed at leaked dumps and stolen
backups — the realistic threat for a server with `pg_dump` writing to local
disk. Keeping the key out of the backups is what makes it work at all.

## Open decisions

- **Where the encryption key lives** — env var alongside `BOOTSTRAP_ADMIN_*`, a mounted file, or something else. Determines the rotation procedure.
- **Rate-limit and lockout thresholds** for code verification, and whether they share machinery with the still-undecided general rate limiting in the Security area.
- **Re-authentication scope** — which operations demand a fresh factor, given there is no email channel to notify on.
- **Whether 2FA stays in scope at all** — three of the four source docs omit it entirely, and minimax-m3 schedules it last, "when asked".
