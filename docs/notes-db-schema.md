# Database schema notes

The server's tables in one place. This doc is authoritative for shape; the doc
that owns each feature is authoritative for why the table looks the way it does.
The client's SQLite store is a separate thing and stays in
[notes-client-cache.md](notes-client-cache.md).

The inventory is below, and each table's block follows it.

- **`users`** — accounts. Columns arrive from [03-authentication.md](03-authentication.md), [notes-roles.md](notes-roles.md), [notes-validation.md](notes-validation.md) and [04-user-presence.md](04-user-presence.md).
- **`sessions`** — one row per login ([03-authentication.md](03-authentication.md)).
- **`password_resets`** — admin-issued single-use reset tokens ([03-authentication.md](03-authentication.md)).
- **`invites`** — registration tokens and the vouching chain ([02-registration.md](02-registration.md)).
- **`totp_credentials`** — encrypted TOTP secrets ([notes-totp.md](notes-totp.md)).
- **`recovery_codes`** — hashed single-use codes behind a lost device ([notes-totp.md](notes-totp.md)).
- **`conversations`** — the one container behind direct messages, groups and channels ([notes-core-messaging.md](notes-core-messaging.md), [notes-community.md](notes-community.md)).
- **`conversation_members`** — membership, and with it the conversation role, the mute level, the read cursor and the `blocked` flag ([notes-core-messaging.md](notes-core-messaging.md), [notes-roles.md](notes-roles.md), [notes-notifications.md](notes-notifications.md), [notes-blocking.md](notes-blocking.md)).
- **`conversation_restrictions`** — kicks and silences, deliberately outside the membership row ([notes-roles.md](notes-roles.md)).
- **`messages`** — every message, post and comment ([notes-core-messaging.md](notes-core-messaging.md), [notes-community.md](notes-community.md)).
- **`message_mentions`** — a row per mentioned user ([notes-core-messaging.md](notes-core-messaging.md), [notes-notifications.md](notes-notifications.md)).
- **`message_reactions`** — one row per message, user and emoji ([feature-set.md](feature-set.md)).
- **`attachments`** — the record that describes a blob and authorizes access to it ([notes-attachments.md](notes-attachments.md)).
- **`blocks`** — who blocks whom ([notes-blocking.md](notes-blocking.md)).
- **`audit_log`** — who did what to whom and when ([feature-set.md](feature-set.md)).

## `users`

```
users(
  id,
  username,         -- unique index, not an application-level check
  display_name,     -- nullable; the username renders when absent
  bio,              -- nullable
  password_hash,    -- Argon2id
  server_role,      -- admin / mod / user, ordered
  disabled,         -- set by a ban or a retirement; sessions are deleted rather than marked
  status,           -- declared: available / away / do_not_disturb, sticky across disconnects
  silenced_until,   -- nullable; a server-wide silence, self-clearing
  last_seen_at,     -- last connected, repaired at boot from sessions.last_used_at
  created_at
)
```

## `sessions`

```
sessions(
  id,
  token_hash,       -- SHA-256 of the token; the lookup key
  user_id,
  created_at,
  last_used_at,
  last_used_ip,     -- nullable; who is using the token now
  expires_at,       -- sliding, renewed on use, with an absolute cap
  client_label      -- e.g. "console" / "tui"
)
```

## `password_resets`

```
password_resets(
  id,
  token_hash,       -- SHA-256 of the token; never the raw code
  user_id,          -- whose password it sets
  issued_by,        -- the admin; vouching chain
  created_at,
  expires_at,       -- hours, not days
  used_at           -- nullable; presence = redeemed
)
```

## `invites`

```
invites(
  id,
  token_hash,           -- store the hash, never the raw code
  issued_by,            -- vouching chain
  created_at,
  expires_at,           -- TTL in days; ceiling in 02-registration.md
  max_uses  default 1,  -- single-use by default; generalizes to N
  used_count default 0, -- the value the redeem races on
  revoked_at            -- nullable; presence = revoked
)
```

## `totp_credentials`

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

## `recovery_codes`

```
recovery_codes(
  user_id,      -- primary key with code_hash
  code_hash,    -- a fast hash; the code is high-entropy random
  used_at       -- nullable; presence = spent, and a code is spent once
)
```

## `conversations`

```
conversations(
  id,
  kind,               -- direct / group / channel; the one container's discriminator
  name,               -- null for direct, required and non-empty otherwise
  description,        -- nullable
  visibility,         -- channel only: public or private
  scope,              -- channel only: 'server' makes it a server channel
  comments_enabled,   -- the channel default a post overrides
  members_may_post,   -- channel only; false by default, so owner and officers post
  archived_at,        -- nullable; presence = read-only and out of the active list
  created_at,
  direct_lo,          -- direct only: least(a, b) of the member pair
  direct_hi           -- direct only: greatest(a, b); unique with direct_lo where kind = 'direct'
)
```

## `conversation_members`

```
conversation_members(
  conversation_id,
  user_id,              -- primary key with conversation_id
  role,                 -- owner / officer / member; never read in a server channel
  joined_at,            -- tenure, which is what succession promotes on
  mute_level,           -- all / mentions_only / none, default all
  last_read_message_id, -- the read cursor, set to the conversation head at join
  blocked               -- direct only; recomputed from blocks, never toggled
)
```

## `conversation_restrictions`

```
conversation_restrictions(
  conversation_id,  -- primary key with user_id
  user_id,
  kicked_at,        -- nullable; read by the join path and the read grant, nowhere else
  kicked_by,
  silenced_until,   -- nullable and self-clearing; an indefinite silence is a far date
  silenced_by
)
```

## `messages`

```
messages(
  id,                 -- bigserial; fixes display order and is the key scrollback pages against
  conversation_id,
  sender_id,          -- not null; server notices ride typed events instead of sender-less rows
  body,               -- cleared on delete, the row surviving as a tombstone
  created_at,         -- server-assigned at insert, so a sender cannot backdate
  edited_at,          -- nullable; presence = edited
  deleted_at,         -- nullable; presence = tombstone
  change_seq,         -- bumped by insert, edit, delete and reaction, under an advisory transaction lock
  client_msg_id,      -- unique with sender_id; the send idempotency key and the ack correlation key
  reply_to_id,        -- nullable; quoting only, and must carry this row's parent_post_id
  forwarded_from_id,  -- nullable; the source a forward or a saved copy came from, across conversations
  parent_post_id,     -- nullable; containment, non-null only in a channel and only above a post
  comments_enabled,   -- nullable; overrides the channel default, null = inherit
  attachment_id       -- nullable; one per message
)
```

## `message_mentions`

```
message_mentions(
  message_id,   -- primary key with user_id
  user_id       -- the mentioned member; the tally aggregates these at query time
)
```

## `message_reactions`

```
message_reactions(
  message_id,   -- primary key with user_id and emoji
  user_id,
  emoji,        -- from the server-side allowlist
  created_at
)
```

## `attachments`

```
attachments(
  id,
  owner_id,             -- every authorization and quota decision reads this row
  conversation_id,      -- the destination, carried directly so download need not reach through the message
  blob_key,             -- SHA-256 of the contents; also the ETag
  size_bytes,
  sniffed_type,         -- canonical
  declared_type,        -- what the client claimed, kept for comparison
  filename,             -- display label only; never locates a file
  idempotency_key,      -- unique with owner_id, never alone
  created_at,           -- with owner_id, the index the rolling allowance sums over
  unreferenced_since,   -- nullable; cleared when a message references it
  variant_key           -- nullable until a variant exists
)
```

## `blocks`

```
blocks(
  blocker_id,   -- primary key with blocked_id, and the prefix scan for a caller's list
  blocked_id,   -- no index of its own; nothing asks who blocks a given user
  created_at,
  check blocker_id <> blocked_id
)
```

## `audit_log`

```
audit_log(
  id,
  created_at,
  actor_id,           -- not null; the seed row names the account it creates
  caused_by,          -- nullable, to audit_log.id; set on every consequence of another row
  action,             -- the verb; the set is enumerated in feature-set.md
  target_user_id,     -- nullable; who it was done to
  conversation_id,    -- nullable; the scope of a conversation-scope action
  message_id,         -- nullable; set by a moderator deletion
  invite_id,          -- nullable; issuance, redemption and revocation
  old_value,          -- nullable; the transition, where the action is one
  new_value
)
```

- **Typed nullable foreign keys rather than `target_type` and `target_id`** — the records are read alongside the rows they describe, which polymorphic ids give up. The row is sparse; at this size that is cheaper than losing referential integrity.
- **Scope and target are separate** — a kick carries both, `target_user_id` for who and `conversation_id` for where, so "everything done to this user" and "this conversation's history" stay different queries.
- **`old_value` and `new_value` rather than a blob** — most audited actions are a transition, and [notes-validation.md](notes-validation.md) reads the rename hold and the per-user cooldown off these columns, so the old username has to be indexable rather than buried.
- **`actor_id` is never null** — the seed row names the account it creates, since the credential that authorized the insert is that identity. Self-referential on purpose, and what keeps every audit query free of an actorless case.
- **A cascade points at what caused it** — `caused_by` is null on anything a person did directly and set on every consequence, so one ban and the successions, auto-archives and invite revocations under it are a single query rather than a guess from matching timestamps.
- **A cascade also carries the triggering actor** — the admin who banned the owner. Paired with `caused_by` that claims no more than it should: the row says who acted and which act it followed, not that they chose which officer succeeded.
- **The action name says what happened, never how it was reached** — `ownership.succeeded` and `ownership.transferred` are different acts and stay different verbs, but whether a row was automatic is read off `caused_by`.
- **Insert-only and never trimmed** — the vouching chain reads invite redemptions here and the rename hold reads renames here, so a retention policy would break both silently.
- **Nothing it points at is ever hard-deleted** — accounts are disabled, conversations archived, messages tombstoned, invites revoked. That invariant is what lets the foreign keys be real ones.
- **Indexes** — `(target_user_id, created_at)`, `(conversation_id, created_at)`, `(action, old_value)` for the rename hold, and `caused_by` for a triggering row's consequences.
