# Notification notes

## What it is

Everything derived from the read cursor that tells a user something wants their
attention: per-conversation unread counts, an aggregate badge across them, and a
separate tally for mentions, plus the per-conversation setting that decides how
much of it reaches them. The cursor itself belongs to core messaging; this is
the layer that reads it.

## What it solves

- **Which conversation to open** — a member returning to a client with several conversations needs to know where the new material is without opening each one.
- **Not missing a message addressed to you** — a mention inside a busy conversation is otherwise indistinguishable from the traffic around it.
- **A conversation that alerts more than it is worth** — a member of a high-traffic group they cannot leave needs to quiet it without going blind to the messages naming them.

## Unread counts

- **Unread means past the cursor** — messages in the conversation with an ID above the member's `last_read_message_id`.
- **A member's own sends never count** — the cursor advances only on an explicit call, so without this a user's own message marks the conversation unread, and does so across their other sessions where no such call is coming.
- **Tombstones never count** — there is nothing left to read, and the unread would be uncleared permanently.
- **The conversation list returns the count** — computed at query time, one aggregate per conversation against the caller's cursor. This is the call a fresh client makes at startup, so an empty cache still shows correct numbers.
- **No counter column** — nothing is maintained on the send path. A denormalized counter costs a write per member per message, goes wrong on deletion, and needs a backfill whenever membership changes.
- **The client recounts between fetches** — on an incoming message and on a cursor advance, by counting rows in its cache rather than incrementing. Incrementing from events double-counts a message that also arrives through catch-up.
- **The badge counts conversations, not messages** — the number of conversations holding anything unread. Summing the per-conversation counts produces a large number that says nothing about what to do next.
- **Mentions are tallied apart** — folded into the unread count, a mention among a few hundred channel messages is invisible, which is the case the tally exists for.

## Mention alerting

- **The server knows the mention set either way** — server-side parsing derives it and client-sent ranges arrive as validated entities, so alerting does not wait on how core messaging settles mention storage.
- **The mention rides on the message** — a list of user IDs on the envelope, with the client alerting when it finds itself and highlighting from the same list. The mentioned member is already a member of the conversation, so the message reaches every one of their sessions; a dedicated mention frame would duplicate a delivery that has already happened and would need a catch-up path of its own.
- **Mentions are also rows** — `(message_id, user_id)`, written in the transaction that inserts the message. The tally is computed like the unread count, at query time against the caller's cursor, and an envelope field cannot be aggregated.
- **A self-mention does not alert** — the row may exist so the reference still highlights, but it stays out of the tally, as a member's own sends stay out of the unread count.
- **`@all` and `@here` are not mentions here** — expanded at send time they mark every conversation as mentioning everyone, which empties the tally of meaning, and they need a permission model that belongs to group management.

## Mute

- **Three levels** — `all`, `mentions_only`, `none`.
- **A column on the membership row** — per `(member, conversation)`, defaulting to `all`, leaving with the membership.
- **Read on the send path** — it arrives with the membership the fan-out already loads, so the check adds no query.
- **`mentions_only` alerts off the envelope** — the mention set is already on the message.
- **`none` silences mentions too** — otherwise it is `mentions_only` again.
- **The per-conversation count ignores the level** — mute governs attention, not counting, so unmuting needs no repair.
- **Muted conversations drop out of the badge** — a filter on the existing query, not new state.
- **Except a `mentions_only` conversation holding an unread mention** — it counts; that mention is what the level exists to let through.
- **Never a delivery filter** — a muted conversation receives, stores and catches up unchanged.
- **Server-side** — set from one client, it holds on every other.

## Deferred

- **A global default the conversation overrides** — an account-level level, with the per-conversation column nullable against it.
- **Timed mute** — `muted_until` rather than a level that stays set; composable, but it puts a clock on the send path.
- **What a client does on an alert** — a bell, a highlighted row in the conversation list, both or neither. Client polish rather than a feature.
- **What a client shows when its cache holds less than the unread region** — a capped display; client polish, not a property of the feature.
