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
- **Reactions as their own rows** — mirroring `message_reactions` ([notes-db-schema.md](notes-db-schema.md)), so a reaction does not rewrite the message.
- **Conversations and membership** — enough for the list, for group rosters, and for the mute level the badge filters on.
- **Users, id to name** — messages carry immutable IDs and usernames are renameable, so this is required, not an optimization. Kept current by the profile event, and repaired from the snapshot for a client that was away ([notes-user-presence.md](notes-user-presence.md)).
- **The cursors** — one sync cursor per device, plus the read cursor per conversation as last seen from the server.
- **The block list** — fetched at startup and on change, and held so the render filter works before that fetch returns ([notes-blocking.md](notes-blocking.md)).
- **The session token** — the raw value, held nowhere else, since the server keeps only its hash ([notes-authentication.md](notes-authentication.md)). The one thing here that cannot be refetched, and the reason the file is `0600`.

## The two edges

- **The sync cursor is the top** — the highest `change_seq` stored; above it, nothing has been fetched.
- **A per-conversation floor is the bottom** — the oldest message ID held. Without it, an evicted range renders as the start of the conversation, and the client cannot tell that from a conversation that really has no more history.
- **Crossing the floor is a server call** — history paged `before` the floor's ID, written back so the floor descends.

## Gotchas

- **The cursor is persisted only after the messages it covers** — catch-up only ever walks upward, so writing the cursor first and crashing leaves a hole nothing repairs.
- **Eviction trims oldest-first** — a per-conversation cap punches holes below the high-water mark, and `change_seq` is global, so catch-up walks straight past them.
- **Change events arrive for messages not held** — edits and deletions below the floor are dropped, not errors.
- **Search cannot go local** — a local index covers the window only, so results differ per device and miss the archive. Not a limit of SQLite's full-text support; the client does not hold the corpus.

## Attachments

The record and the bytes are cached separately: the record is derivable metadata
like everything else here, the bytes are large, immutable and disposable on
their own. Server-side definitions are in
[notes-attachments.md](notes-attachments.md).

```
$XDG_DATA_HOME/<app>/
└── cache.db                       session token, messages, attachment records, cursors

$XDG_CACHE_HOME/<app>/
├── tmp/
│   └── 019bd4c2-…                 one partial download, resumed by Range
└── blob/
    └── e3/b0/e3b0c44298fc1c14…    the download's ETag, hex, no extension

<destination>/
└── report.png                     what a save produces, under its original name
```

Only the third is user-facing. The cache is named by hash and evicted without
notice; a save is where the user is told to look.

The paths are XDG on Linux, and a client on macOS or Windows resolves that
platform's equivalent for each of the two roles, one durable and one disposable.
The split is what matters, not the spelling.

- **The record is mirrored, the bytes are not** — id, filename, size and sniffed type arrive with their message on the same write path. The server's bookkeeping fields stay server-side.
- **The attachment row is evicted with its message.**
- **A tombstone drops its attachment** — a row outliving the body renders a paperclip that 404s.
- **Bytes live in a content-addressed cache outside SQLite**, under `$XDG_CACHE_HOME`, keyed by the `ETag`. The tag is the blob hash, so a cached copy is self-verifying; the directory is size-capped, evicted LRU, and safe to delete.
- **A download lands in `tmp/` and is renamed into `blob/` when complete**, so a partial file is never served from cache and a dropped transfer resumes by `Range` against what is already there.
- **Nothing is fetched implicitly** — bytes move only on an explicit command.
- **A save resolves its destination in order** — an explicit path argument, a configured download directory, `XDG_DOWNLOAD_DIR`, then the working directory. No `~/Downloads` fallback, and the path written is reported back.
- **A save copies out of `blob/` when the bytes are cached**, and downloads only when they are not.
- **The saved path is kept as a hint** — path, size and mtime per attachment, in its own table not cascaded by eviction. A `stat` settles it in the common case; if size matches but mtime moved, the file is hashed against the blob key, which needs no server call. Any mismatch degrades to not knowing where the file went, never to an error.
- **Never overwrite, always rename** — `report (1).png`. The filename came from another member; with the basename stripping in [notes-validation.md](notes-validation.md), never escape the destination and never clobber inside it.
- **Saved files are `0600` and never executable.** Opening one is an explicit second command.
- **The idempotency key is persisted before the transfer starts**, or a crashed upload retries with a new key and leaves two records for one file. Whether it belongs in a general outbox is open.

## Consequences

- **Disposable, bar the session row** — schema changes are a drop and refetch, corruption is fixed by dropping the cached tables, and there is no migration to plan. Nothing in those tables is authoritative: what the server does not hold is a hint, and losing a hint costs a convenience rather than data. The token is the one value that cannot be refetched, so the remedy is dropping tables rather than the file — deleting the file works too and costs a login.
- **Droppable** — a build without it refetches a slice on connect, losing offline reading and paying bandwidth per reconnect, and losing nothing else.
- **The seam costs more to retrofit than the store** — one write path from socket to store to view. With it, the backing store is a file to swap; without it, adding a cache re-plumbs every read.
