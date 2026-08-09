# Community notes

## What it is

The membership layer over the one conversation container in
[notes-core-messaging.md](notes-core-messaging.md), and the three kinds built on
it: group chats, channels, and server channels. The container, its `kind`
discriminator and everything keyed on `conversation_id` belong to core
messaging. This is the layer that decides who is in a conversation, who may
write to it, and what shape the writing takes.

Server-wide is a flag on a channel rather than a fourth kind, and comments are
messages in the same table rather than a second entity. Nearly every property
below follows from those two facts.

## What it solves

- **Talking to a set of people rather than a pair** — a conversation whose membership the creator chooses and can extend later.
- **Addressing an audience that was never invited** — a channel author writes to readers who found the channel themselves, and reader responses do not become the feed.
- **A notice nobody can miss** — an announcement reaches every account, including the ones that would have declined to join the channel carrying it.
- **Reading a feed without every reply arriving as news** — a busy comment thread should not make a channel look like it has hundreds of things to read.

## Group chats

- **A name and a description** — name rules in [notes-validation.md](notes-validation.md).
- **Invite-only** — a user sees a group iff they hold a membership row. No listing, no discovery, no join request, and no public/private flag to carry.
- **Created by any user** — the creator adds registered users, who become members at once rather than receiving an invitation to accept or decline.
- **A member may leave at will** — their own row, deleted by themselves. A sole owner transfers ownership first, per [notes-roles.md](notes-roles.md).
- **No per-member history floor** — a member added later reads everything said before they joined. Chosen over a `joined_at_message_id` floor: the newcomer gets context, and every history query stays free of a per-member predicate.
- **Messages stay flat** — no containment pointer is set in a group, so `reply_to_id` quotes an earlier message without creating structure.

## Channels

- **A name and a description** — name rules in [notes-validation.md](notes-validation.md).
- **Public or private** — `visibility` on the conversation. A public channel is listed and its posts readable by anyone, and a user who wants to take part inserts their own membership; a private one is invisible to non-members and entered only by invitation. The author adds members directly under either.
- **Created by any user**, public or private, the creator becoming its owner ([notes-roles.md](notes-roles.md)). Only a server channel takes an admin, since its membership is everyone.
- **Membership is what allows writing** — a non-member of a public channel reads it and nothing more: no posting, no commenting, no read cursor, and no live delivery, since fan-out follows membership. A mod or admin moderating a public channel is the exception and does so without joining ([notes-roles.md](notes-roles.md)).
- **Joining and reading both check for a kick** — a `conversation_restrictions` row with `kicked_at` set blocks the self-insert and the read, which is the only thing that puts a kicked user below the stranger baseline here ([notes-roles.md](notes-roles.md)). The two checks are the only places that record is consulted.
- **Channels are found by name** — the listing matches a query against the name and description of public channels, a lookup over conversation rows rather than message bodies. Message search stays inside the caller's own conversations ([notes-search.md](notes-search.md)).
- **A member may leave at will** — their own row, deleted by themselves. A sole owner transfers ownership first, per [notes-roles.md](notes-roles.md).
- **No per-member history floor** — a member who joins later reads every post and comment made before them.
- **Two levels, never three** — a post is a message with no parent; a comment is a message pointing at a post; nothing hangs below a comment.
- **`parent_post_id` is containment** — nullable, non-null only in a channel. The referenced row must be in the same conversation and must itself have `parent_post_id IS NULL`, which is where the depth cap is enforced.
- **`reply_to_id` is presentation** — quoting only, unchanged from core messaging and available in every kind. A comment may quote another comment without adding a level, because containment is the other column.
- **A quote stays inside its post** — `reply_to_id` must carry the same `parent_post_id` as the message quoting it, both null or both the same post. Otherwise a quoted line renders pointing at something outside the view the reader is in.
- **Neither pointer cascades** — a deleted post survives as the tombstone core messaging already leaves, so its comments stay addressable with no orphan rule of their own.
- **Comments are disabled per channel and per post** — `comments_enabled` on the conversation is the default, and the same column on the message overrides it, null meaning inherit. One permission check on insert, taken only when `parent_post_id` is non-null. A channel that disables them everywhere is a read-only announcement feed, so that is not a separate kind either.
- **Only posts count as unread** — the per-conversation aggregate in [notes-notifications.md](notes-notifications.md) takes a `parent_post_id IS NULL` predicate, and a comment never raises the count. A comment addressed to a member reaches them through the mention tally instead, which is already counted apart.
- **Who may post at top level is a per-channel flag** — `members_may_post`, false by default so that owner and officers post and members comment; only the owner changes it. Roles in [notes-roles.md](notes-roles.md).

## Server channels

A channel with `scope = 'server'`, its membership and write access fixed rather
than managed:

- **Membership is every account** — a real row per user, inserted in the same transaction that creates the account and backfilled for existing users when the channel is created. Rows rather than an implied membership, because fan-out, the read cursor's initialization and the heartbeat's head `change_seq` are all computed from membership and would each need a special case otherwise.
- **Membership is insert-only** — there is no leave, so there is no rejoin and no question of what a returning member's read cursor holds.
- **No per-member history floor** — an account created after the channel reads everything announced before it existed.
- **Mute is the only relief** — the three-level per-conversation setting already on the membership row, unchanged.
- **Posted to by server admins** — top-level posts are guarded by the server-wide admin role that already exists, so this kind needs no per-conversation roles and can ship before they are designed.
- **Comments follow the channel rules** — the same per-channel default and per-post override, and where they are enabled any member may leave one. Commenting is the only thing a member writes here, which is what keeps the kind free of a role column.
