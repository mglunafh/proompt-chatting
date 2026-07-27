# Client cache notes

## What it is

A SQLite file on each client holding the messages that client has already
received, so the recent past can be read without asking the server for it.
Every row in it is a copy of something Postgres still holds; the server stays
authoritative and owns the archive.

It is not a replica. The client keeps a contiguous top slice of history — a
window — and the rest of the conversation exists only server-side. Nearly every
property below follows from that one fact.

## What it solves

- **A restarted client renders immediately** — scrollback comes off disk instead of a blank window waiting on a fetch.
- **Reconnect fetches only what changed** — the client presents the highest `change_seq` it has stored and pages up from there, rather than pulling a recent slice every time it connects. Without a cache there is nothing to resume from and the cursor has no meaning.
- **Scrolling back stays local** — until the reader passes the oldest message held.
- **Reading survives a disconnect** — the window renders whether or not the server is reachable.

None of it is free: the window has edges, and the gotchas are what happens at
them.

## Contents

- **Messages in current state** — an edit overwrites the body and raises `change_seq` in place; never an event log.
- **Reactions as their own rows** — `(message_id, user_id, emoji)`, so a reaction does not rewrite the message.
- **Conversations and membership** — enough for the list and for group rosters.
- **Users, id to name** — messages carry immutable IDs and usernames are renameable, so this is required, not an optimization.
- **The cursors** — one sync cursor per device, plus the read cursor per conversation as last seen from the server.

## The two edges

- **The sync cursor is the top** — the highest `change_seq` stored; above it, nothing has been fetched.
- **A per-conversation floor is the bottom** — the oldest message ID held. Without it, an evicted range renders as the start of the conversation, and the client cannot tell that from a conversation that really has no more history.
- **Crossing the floor is a server call** — history paged `before` the floor's ID, written back so the floor descends.

## Gotchas

- **The cursor is persisted only after the messages it covers** — catch-up only ever walks upward, so writing the cursor first and crashing leaves a hole nothing repairs.
- **Eviction trims oldest-first** — a per-conversation cap punches holes below the high-water mark, and `change_seq` is global, so catch-up walks straight past them.
- **Change events arrive for messages not held** — edits and deletions below the floor are dropped, not errors.
- **Search cannot go local** — a local index covers the window only, so results differ per device and miss the archive. Not a limit of SQLite's full-text support; the client does not hold the corpus.

## Consequences

- **Disposable** — schema changes are a drop and refetch, corruption is fixed by deleting the file, and there is no migration to plan. The only store here whose data is fully derivable.
- **Droppable** — a build without it refetches a slice on connect, losing offline reading and paying bandwidth per reconnect, and losing nothing else.
- **The seam costs more to retrofit than the store** — one write path from socket to store to view. With it, the backing store is a file to swap; without it, adding a cache re-plumbs every read.
