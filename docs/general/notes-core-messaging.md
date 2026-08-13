# Core messaging notes

## What it is

The message model every conversation kind is built on: one container with a
`kind` discriminator, one message table keyed on it, and the ordering and
delivery rules that keep a client's copy matching the server's. Group chats and
channels add permissions and shape over this rather than a second message path.

## What it solves

- **A client that was away missing nothing** — one cursor covers inserts, edits, deletions and reactions, so catching up is a walk rather than a replay.
- **A dropped fan-out costing latency rather than data** — the database always holds the message and a client can always tell that it is behind.
- **Two clients of one user agreeing on what has been read** — the read position is server-side and shared, while what has been fetched stays per device.
- **A retried send not duplicating a message** — the sender's own key makes the insert idempotent.

## The container

- **One conversation container** — a `conversations` row with a `kind` discriminator (`direct` / `group` / `channel`), membership in `conversation_members`, messages keyed only by `conversation_id`. Group messaging is a permission layer over this, not a second message path.
- **A DM is a `direct` conversation identified by its canonical pair** — `direct_lo` and `direct_hi` on the conversation row ([notes-db-schema.md](notes-db-schema.md)), holding `least(a, b)` and `greatest(a, b)` under a unique index where `kind = 'direct'`, or concurrent first-sends fork the history.
- **DM conversations are created implicitly on first send** — no separate "open chat" call.
- **Self-DMs are allowed** — the pair permits `a = b`, and that conversation carries one membership row rather than two.

## Message identity and ordering

- **`bigserial` message IDs from the first migration** — immutable, fixing display order and the key scrollback pages against.
- **Every message row carries a `change_seq`** — bumped in the same transaction as anything that changes how the message renders: insert, edit, deletion, reaction. One cursor covers them all.
- **`change_seq` is assigned under an advisory transaction lock** — taken as the last statement before commit, on every path that bumps it, so numbers are handed out in commit order. Without it a lower number can commit after a higher one has already carried a client's cursor past it, losing the message silently.
- **References by immutable user ID** — usernames are renameable; clients resolve them locally.
- **Server-assigned timestamps** — `created_at` is set at insert, so a sender cannot backdate or post-date their own messages.
- **Every message has a sender** — `sender_id` is `NOT NULL`. Server-generated notices ride typed frames instead of message rows, so no read, edit, delete, pin, reaction or unread rule needs a sender-less case, and nothing renders without an author.

## Delivery

- **Persist before delivering** — the ack and the fan-out both follow the commit, so anything a client has seen is re-fetchable.
- **No permanent loss, detected within one heartbeat** — the database always holds the message and a client can always discover it is behind, so a dropped fan-out costs latency rather than data. This is what makes per-recipient acks and a server-side retransmit queue unnecessary.
- **Fan-out is per session, not per user** — every live session of every participant, including the sender's own.
- **A message for an unknown conversation triggers a list refetch** — the message envelope carries no conversation metadata.
- **Conversation metadata rides its own frame** — a rename, a description edit, or an archive or unarchive is pushed to the members as a typed frame rather than a message, so it stays out of history, out of unread counts, and out of the posts-only tally a channel keeps. A client that was offline for it learns the new value from the conversation list it refetches on reconnect.
- **Membership changes ride their own frame** — a join or a leave is pushed to the members as a typed frame and never persisted as a message. Live signal only: an offline client sees the resulting roster when it refetches, and there is no record in the conversation of who joined when. Buying that record back means a second history path, not a nullable `sender_id`.
- **History and catch-up are one endpoint** — paged `before` an `id` within a conversation, or `after` a `change_seq` across all of them.
- **The heartbeat carries the head `change_seq` visible to that user** — the maximum over the conversations they belong to, and a client below it pages from its cursor, recovering anything a failed fan-out dropped. Not the server-wide head, which advances with traffic in conversations they are not in and would leave every client permanently behind. Computed once per user per tick rather than per socket.
- **Catch-up pages in a loop until a page comes back short** — the cursor advances per page, so an interrupted walk resumes where it stopped instead of restarting. Nothing expires, so a client days behind walks the same path as one seconds behind, only longer; a message arriving live during the walk is applied by its ID, so page and live copy may land in either order.
- **Sends carry a client-generated key** — unique on `(sender_id, client_msg_id)`; a retry returns the existing message instead of inserting.

## Edits and deletions

- **The edit window is 24 hours, and the sender's alone** — long enough for a typo found the next morning, short enough that a message someone has read stops changing under them. A moderator deletes rather than rewrites.
- **An edit keeps the ID, `created_at` and position** — only the body and `edited_at` change, the body revalidated as a send is ([notes-validation.md](notes-validation.md)) and `change_seq` bumped.
- **Deletion clears the body and keeps the row** — a real `DELETE` is invisible to a `change_seq` walk, so the tombstone is what reaches an offline client, and replies still resolve against it. No window on it: an edit is bounded because it rewrites what was already read.

## Cursors

- **The sync cursor is per device, client-side** — the highest `change_seq` that device has stored, sent on reconnect. Never shared between a user's sessions, which hold separate local caches ([notes-client-cache.md](notes-client-cache.md)).
- **The read cursor is per user and conversation, server-side** — the last message read, feeding unread counts and read receipts, shared across the user's sessions. Advanced only by an explicit client call and never by delivery, or it collapses into the sync cursor and everything received counts as read.
- **The read cursor only moves forward** — an advance is a max against the stored value, since a user's sessions can mark read out of order.
- **It is initialized to the conversation head at join** — the row is created with membership, and group history has no per-member floor, so starting at zero would show a new member the whole channel as unread.
- **A cursor advance is fanned out to the user's own sessions** — the cursor is shared across them, so reading in one clears the unread state in the rest. One of the fan-outs aimed at a single user rather than at a conversation's members: per-user state set on one client and read on the rest, alongside a block change ([notes-blocking.md](notes-blocking.md)) and a mute change ([notes-notifications.md](notes-notifications.md)).
- **A member's read position is visible to the other members** — this is what "seen by" renders: a read receipt in a direct conversation, a list in a group. The cursor is shared state rather than private.

## Mentions

- **The reference is a token in the body** — the client composes `<@42>` where the sender picked a name, and each client resolves it at render from the id-to-name map it already keeps ([notes-client-cache.md](notes-client-cache.md)). A rename renders correctly and the server never scans for names.
- **Mentions are stored as rows** — `message_mentions` ([notes-db-schema.md](notes-db-schema.md)), derived by scanning the body for tokens in the transaction that inserts the message, so the tally in [notes-notifications.md](notes-notifications.md) aggregates at query time. An edit re-scans in its own transaction.
- **Resolve for rendering, membership for alerting** — a token renders for any valid ID but writes a row only where that ID is a current member at insert, since a forward or a saved message carries tokens into a conversation the mentioned users are not in.

