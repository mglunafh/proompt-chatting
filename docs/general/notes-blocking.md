# Blocking notes

A personal mute: a member marks another and stops seeing what they write, so a
shared channel stays usable when one member is intolerable and a direct exchange
can be ended outright. It is a preference held by the blocker, never announced to
the blocked user, and it carries no authority — abuse is answered by the role ladder
in [notes-roles.md](notes-roles.md), not by this.

## The table

The `blocks` record is in [notes-db-schema.md](notes-db-schema.md).

- **The primary key serves both reads** — a point lookup on the pair, a prefix scan on `blocker_id` for the caller's list.
- **No index on `blocked_id`**, since nothing asks who blocks a given user. That query is the moderation aggregate we declined, and leaving it unindexed keeps it from being added casually.
- **No history and no audit rows** — unblocking deletes the row.

Alongside it sits a denormalized `blocked` column on `conversation_members`.

- **It means delivery in this `direct` conversation is suppressed**, in either direction, and it is set on both members' rows.
- **`blocks` stays the source of truth**; this column exists so the send path never queries `blocks` at all.
- **It is recomputed, never toggled** — two point lookups on `blocks` after any change to the pair, since A unblocking B must not clear a block B holds on A.
- **Nothing sets it on a group or channel row.** Those conversations are never filtered server-side.

## Routes

- **`GET /blocks`** — the caller's list, a prefix scan on `blocker_id`.
- **`PUT /blocks/{user_id}`** — idempotent; blocking twice is not an error.
- **`DELETE /blocks/{user_id}`** — idempotent likewise.

All three act on the caller's own rows only. None of them emits anything to the
blocked user: no frame, no notification, no change to what they can read.

## Blocking, in order

1. **Insert into `blocks`**, or return unchanged if the row is there.
2. **Look up the `direct` conversation** for the canonical pair. If there is none, stop — there is no membership row to mark, and the check at creation will cover the first send.
3. **Recompute `blocked`** from the two `blocks` lookups and write it to both membership rows, in the same transaction as the insert.

Unblocking is the same three steps with a delete, and the recompute in step 3 is
what keeps a reciprocal block intact.

## The send path

- **An existing DM costs nothing.** The recipient's membership row is already loaded to read the mute level in [notes-notifications.md](notes-notifications.md); `blocked` is a column on that row. Set, the send is refused.
- **Implicit conversation creation queries `blocks` once**, before the `conversations` insert — otherwise a blocked first send leaves an empty `direct` conversation behind. That path already does inserts and a unique-index check, and runs once per pair rather than per message.
- **Group and channel sends are not checked**, so fan-out stays one broadcast and the unread count stays block-unaware.
- **A refusal is generic** — "cannot send to this user", the same answer a disabled account gives.

## The client

- **Fetches `GET /blocks` at startup** and after any change of its own; without the list there is nothing to filter against.
- **A change fans out to the blocker's own sessions** — the list is per user and set from one client, so the others learn of it the way a read-cursor advance reaches them ([notes-core-messaging.md](notes-core-messaging.md)). Without it a second client renders the blocked sender until it restarts.
- **Search results pass the same filter** — the server returns them unfiltered ([notes-search.md](notes-search.md)), so a blocked sender's match renders as the collapsed marker rather than a body.
- **Presence is not filtered** — a blocked user still appears in the roster. The block governs what they write, not whether they exist.
- **A mention from a blocked sender does not alert** — the tally still counts it, as the unread count does, but nothing is raised. Alerting is attention and counting is state, the split mute already makes ([notes-notifications.md](notes-notifications.md)).
- **Filters at render, not at write** — a channel message from a blocked sender is stored whole in `cache.db` and drawn as a collapsed marker.
- **Unblocking is a re-render.** The bodies were never discarded, so nothing is refetched.
- **Unread counts are untouched** — the row is present, so a client recount matches the server's.
- **A marker rather than a silent gap**, since an unexplained hole in scrollback reads as a bug.

## Consequences

- **A blocked user can still add you to a group** — adding checks nothing outside a direct conversation, and refusing would tell them they are blocked. Their messages there render as the collapsed marker like any other.
- **The block is not invisible, only unattributed** — a blocked user who sends learns the send failed, in the words a disabled account produces. What is hidden is why.
- **Quoted replies and forwards leak** — a reply renders the quoted line and a forward copies the body with attribution. Accepted.
- **A blocked channel message is still delivered, stored and counted.** Client-side filtering is a display rule, not suppression.
- **`blocked` is a cache and can be rebuilt** from `blocks` for every `direct` conversation, which is the repair if the two ever disagree.
- **Moving channel filtering to the server later** means bodyless rows in the client cache and a fetch-by-id route for unblock, neither of which exists today.
