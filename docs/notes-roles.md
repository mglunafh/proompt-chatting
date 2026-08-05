# Roles notes

## What it is

Who may do what, across two scopes that are decided separately and stored
separately: `users.server_role` on the account, and `conversation_members.role`
on the membership row. Both are ordered columns rather than permission sets,
because within each scope the powers nest. Their value sets are disjoint, so one
can never be mistaken for the other.

## What it solves

- **Delegating enforcement without delegating the keys** — someone has to be able to remove a bad actor without also being able to take over accounts.
- **A remedy short of banning** — without a middle role the only server-wide lever is disabling an account, which makes every infraction all-or-nothing.
- **Running a conversation without involving the server** — a group needs a member who can remove a disruptive participant or fix a wrong name, and that is not the server operator's job.
- **Keeping private conversations private** — a server role must not become a way to read or moderate a group chat its holder was never added to.

## Server scope

### Roles

- **Admin** — promote and demote, issue password reset tokens, create server channels and post in them, issue invites unconditionally, plus everything below.
- **Mod** — ban and unban, silence and unsilence server-wide or within a single public or server channel, moderate public channels and server channels.
- **User** — no server-wide powers; issues invites only when `allow_user_invites` is on, per [02-registration.md](02-registration.md).

A ban is the same act as disabling an account: it sets the disabled flag, deletes
the user's sessions, closes their sockets, and revokes their outstanding invites
([02-registration.md](02-registration.md)). There is no second, admin-only
version of it — `disabled` is the column's name, not a separate power. A user
retiring their own account takes the same path, differing only in the audit
action it writes.

`server_role` is read by joining `users` on the session lookup, so a REST request
always sees the current one. A WebSocket authenticates once at upgrade, so the heartbeat
re-check in [03-authentication.md](03-authentication.md) reads the role in the
same lookup it already makes for expiry and revocation, bounding a stale role to
one tick.

### What stays with admins

- **Password reset issuance** — the token sets a new password and revokes the user's sessions, so whoever issues one can become that user. A mod holding it is an admin under another name. Admins may issue one for each other, which escalates nothing and is what keeps a locked-out admin recoverable.
- **Promotion and demotion** — otherwise a mod manufactures an accomplice.
- **Unconditional invite issuance** — mods fall under `allow_user_invites` with regular users. Inviting extends the vouching chain rather than enforcing anything, so it tracks trust rather than the enforcement role.
- **Multi-use invites** — only an admin may set `max_uses` above one.
- **Revoking someone else's invite** — an issuer always revokes their own; anything further is an admin action, and only an admin lists invites they did not issue. Invites are the vouching chain rather than enforcement, so they follow trust rather than the enforcement role.
- **Posting in server channels** — the announcement voice belongs to whoever may create the channel.

### Protections

- **Nobody acts on an equal or higher role** — banning and demoting require the target's role to be strictly lower than the actor's, so a mod cannot ban a mod and no demotion war is possible. Reset issuance is exempt.
- **A holder may always act on themselves** — how the bootstrap admin retires itself ([02-registration.md](02-registration.md)), with last-active-admin protection still catching the last one.
- **The last active admin cannot be disabled, demoted or banned** — enforced server-side, per [02-registration.md](02-registration.md).
- **Not extended to mods** — the server functions with none, so there is nothing to lock out.
- **Removing a rogue admin takes the host** — no admin may demote or ban an equal. Admins run the box, so the admin-and-mod line is a security boundary and the admin-and-operator line is not.
- **Every promotion and demotion is audited** — the same record the audit chain already keeps for admin creation.

## Conversation scope

A role on the membership row, shared by group chats and channels. Server
channels carry none: membership is everyone and write access comes from the
server role, so the column would never be read there.

### Roles

- **Owner** — promote and demote, transfer ownership, archive the conversation, set a channel's post-at-top-level flag. Exactly one, the creator at first.
- **Officer** — kick, silence, edit the name and description, delete other members' messages, pin, add members, and in a channel toggle comments.
- **Member** — read, write, leave.

One enum rather than one per kind: a channel needs everything a group chat needs
plus two more, so a second enum would duplicate six permissions to express two.

Nobody acts on an equal or higher role here either: an officer kicks, silences or
demotes ordinary members only, and acting on an officer takes the owner.

### Posting in a channel

- **A per-channel flag, not a permission** — owner-and-officers or all members, defaulting to owner-and-officers. This is what makes one channel an announcement feed and another a place anyone starts topics, without adding a kind, mirroring the comments flag in [notes-community.md](notes-community.md).
- **Only the owner sets it** — flipping it changes what the channel is for, which is structural like deleting or transferring it, rather than the moderation act that toggling comments is.

### Succession

- **A sole owner cannot leave** — they transfer ownership or archive the conversation first. The only exception to leaving at will.
- **A disabled owner is replaced automatically** — a ban or disable promotes the longest-tenured officer, or the longest-tenured member if there are none, skipping accounts that are themselves disabled. Without it a private conversation is stuck ownerless, since no server role can reach in to appoint anyone. A conversation left with no active member is archived.

### Membership changes

- **Owner and officers add members** — an ordinary member cannot.

### Archiving

- **Archived rather than deleted** — setting `archived_at` makes a conversation read-only, keeps its history for the members who were in it, and takes it out of the active list. Nothing is destroyed, matching disable-don't-delete for accounts and the tombstone a deleted message leaves.
- **The owner archives and unarchives** — and for a server channel, which carries no conversation role, an admin does both.
- **Read-only covers the message stream, not `archived_at` itself** — otherwise archiving would be one-way and nothing could bring the conversation back.
- **The change rides the conversation metadata event** — members learn at once rather than keeping a composer over a conversation that no longer takes messages ([notes-core-messaging.md](notes-core-messaging.md)).

### Audit

- **Every conversation-scope action is recorded** — kicks, silences, role changes, ownership transfers, metadata edits, archiving, and a moderator deleting someone else's message, in the same table as the server-scope events, carrying the conversation id.

## Remedies

Three steps against a member, in order of weight: silence takes writing, kick
takes reading, ban takes the account.

Both conversation-scope remedies live in `conversation_restrictions`
([notes-db-schema.md](notes-db-schema.md)), one row per `(conversation_id,
user_id)` and kept out of `conversation_members`. That buys two things: a
membership row still means exactly what it means in
[notes-core-messaging.md](notes-core-messaging.md), so no fan-out, history,
search or heartbeat predicate changes; and neither remedy is cleared by the
member leaving.

- **Silence** — a `silenced_until` timestamp, nullable and self-clearing, so a timeout needs no follow-up call and an indefinite silence is a far date. Read on the send path, which loads the membership row for the mute level and joins the restriction beside it — one join, and the price of keeping the remedies off that row.
- **Who may silence** — owners and officers within their own conversation, and mods within any public channel or server channel, both writing the same `conversation_restrictions` row. A mod may also silence server-wide, where it sits on the user row instead and reaches everything at once.
- **A server-wide silence applies everywhere, private conversations included** — an account-level restriction like a ban, not a server role reaching into a conversation. Nothing is read or moderated.
- **Kick** — deletes the membership row and sets `kicked_at`. The record is consulted in two places rather than six: the read grant and the join path.
- **A kick persists until it is cleared** — an owner or officer adding the user back clears the record and inserts a fresh membership row, whose read cursor starts at the conversation head like any new member's.
- **The kick only bites in a public channel** — everywhere else, losing the membership row already ends reading and re-entry needs an owner or officer. In a public channel, where reading and joining are otherwise open, it is what puts a kicked user below the stranger baseline.
- **Server channels have no kick** — membership there is insert-only, so silence is the only remedy short of a ban.

## Composition

- **A server role reaches public conversations only** — a mod or admin may moderate a public channel or a server channel without being a member: delete messages, pin and silence, plus kick in a public channel. A server channel has no kick, since membership there is insert-only.
- **Group chats and private channels are out of reach** — no server role grants membership or moderation there, and the account-level ban stays the remedy.
