# Attachment notes

## What it is

A file uploaded by a member and referenced from a message, so a conversation can
carry something other than text. It is the first feature where the server holds
state outside Postgres: the bytes live on disk, the description of them lives in
a table, and the two can disagree. Nearly every rule below exists to keep them
from disagreeing, or to make the disagreement harmless.

Three objects, never one:

- **The bytes** — an opaque blob under a server-generated key.
- **The record** — a row in `attachments` ([notes-db-schema.md](notes-db-schema.md)). There is no separate checksum: the blob key is the hash. The filename is a display label: it is what a client saves the file as and what the message renders, and it never locates a file or determines a type; its rules are in [notes-validation.md](notes-validation.md). Every authorization and quota decision is made against this row, never against the file.
- **The reference** — a nullable attachment id on the message row, one per message for now. A record can exist with no message pointing at it, and several records can point at one blob, but a referenced record always names bytes that are still there.

## What it solves

- **Sharing something that is not a message body** — a screenshot, a log file, a patch.
- **Keeping large payloads off the WebSocket**, which is a poor transport for them for the reasons under Transport.

## Transport

- **Upload and download are REST; the socket carries only the id.** The WebSocket is one multiplexed connection per user carrying small JSON frames and a heartbeat the client has to answer, so a multi-megabyte binary on it blocks every other frame for that user — including the heartbeat reply, which would drop a large upload's own sender out of the presence count. It also defeats backpressure and cannot be resumed.
- **Raw request body, not `multipart/form-data`** — `POST /attachments` with the file as the body, so the size cap is one byte counter in one copy loop. Multipart can be accepted alongside it later without touching the record or the download path.
- **Metadata rides in request headers, not the query string** — `Content-Disposition: attachment; filename*=UTF-8''…`, `Content-Type`, and the idempotency key. A filename in the URL lands in every proxy access log and needs an encoding rule the header already has.
- **Download is `GET /attachments/{id}`, streamed** — the id is the whole address, and the record supplies the name the client saves under.
- **`Range` is supported on download** (Ktor's `PartialContent`), so a large file survives a dropped link without starting over. Resumable upload is the harder half and stays out until file sizes justify it.

## Storage

Bytes are reached through one narrow interface, `AttachmentStore`, and nothing
outside it knows where a file sits or what it is called:

- `put(stream, limit) → (key, size, sniffed type)` — consumes the stream, enforces the byte cap, and returns everything the copy loop learned on the way past: where it stored the bytes, how many there were, and what they turned out to be.
- `open(key, range) → stream` — opens a whole blob or a byte range of one.
- `delete(key)` — removes a blob and its variants.

Everything below is that interface's only implementation: a local filesystem in
a named Docker volume mounted at `ATTACHMENT_ROOT`
(`/var/lib/chat/attachments`), owned by the server's user and shared with no
other container.

```
/var/lib/chat/attachments/
├── tmp/
│   └── 019bd4c2-…                     one in-progress upload, named by a server-minted id
├── blob/
│   └── e3/b0/e3b0c44298fc1c14…        SHA-256 of the contents, hex, no extension
└── derived/
    └── <variant>/e3/b0/e3b0c44298fc1c14…  variants keyed by their source blob's hash
```

- **The key is the SHA-256 of the contents.** Identical bytes are stored once however many members upload them or times a message is forwarded, a re-upload after a timeout resolves to the same path so the write is idempotent by construction, and a stored blob is immutable — which is what lets a ranged download resume without an `If-Range` check.
- **Two levels of two hex characters**, so no directory grows past a handful of entries and `rsync`, `find` and a manual `ls` stay usable.
- **No extension, and no user-controlled path component ever.** The path comes entirely from the hash; the original filename lives in the record. Nothing on disk can be inferred from, or made to collide by, what a client sent.
- **`tmp/` sits under the same root** because the last step is a rename and a rename is atomic only within one filesystem. Temporaries in `/tmp` turn that move into a cross-device copy, reintroducing the partial-file window the rename exists to close.
- **A rename onto an existing key is a no-op, not a conflict** — two members uploading the same file produce identical bytes, so whichever lands second drops its temp file and takes the blob already there, touching its mtime on the way past. The touch is what keeps a long-dormant blob from being swept out from under the record about to name it.
- **Directories `0700`, files `0600`, never executable.** The volume holds nothing but opaque blobs.
- **Uploads are refused below a free-space floor**, with `507` rather than a full disk discovered mid-write.
- **`derived/<variant>/` is fixed now and stays empty until a variant exists.** Variants keyed by their source blob's hash means generating them later adds files and no migration, and deleting a blob deletes its variants by the same key.

Backups do not come from `pg_dump`: the volume is synced separately, excluding
`tmp/`, and since a blob's contents can never change under its key, an
incremental sync only ever transfers additions — **provided it compares names
and sizes rather than timestamps**, since reuse moves a blob's mtime without
touching a byte of it. A matching name is a matching hash, which is a stronger
guarantee than any timestamp comparison.

- **Sync the volume after the dump, never before** — the captured blob set then covers every captured record, so a restore holds surplus bytes rather than records pointing at bytes that were never taken.
- **Restore in the same order the lifecycle writes: blobs, then records.**
- **The sweep stays inert until both halves are in.** It deletes blobs no record names, which is precisely what a restored volume looks like beside an empty or older database — pointed at a half-restored system it does exactly what it is told and removes the backup.

Two rejected alternatives, since both come up. Bytes in Postgres `bytea` buys
transactional writes and one consistent backup, at the cost of bloating every
dump, amplifying WAL, and holding a pooled connection for the length of a slow
client's transfer. A MinIO container buys an S3 API `AttachmentStore` already
abstracts and presigned uploads whose benefit does not exist at this scale, for
a seventh service with its own credentials, healthcheck and backup target. If
the bytes ever must leave the box — multi-instance deployment, the app host
ceasing to be the data host, or attachments outgrowing the disk — the move is a
hosted S3-compatible bucket, not a self-hosted object store.

## Limits

Five numbers, all configuration rather than invariants, taken into consideration
when moving files:

- **Per-file cap — 25 MB.** Covers a screenshot, a log tarball or a short recording without argument, and bounds what one request can cost.
- **Per-user allowance — 100 MB per rolling 24 hours.** Four files at the per-file cap, comfortably past normal use and low enough that one member's runaway client cannot fill the volume between checks.
- **Free-space floor — 2 GB or 10% of the volume, whichever is larger.** Under it, uploads are refused and everything else keeps working.
- **Upload rate — a burst of 5 per user, refilling at 1 per minute.** What closes the abort-and-retry hole below.
- **Download rate — a burst of 30 per user, refilling at 1 per second.** Loose enough that opening a scrollback full of attachments never meets it.

Where each is enforced, in the same order:

- **Per-file cap — inside `put`**, which counts as it writes and aborts the moment the count passes the number it was handed. `Content-Length` is worth an early rejection and nothing more, being a number the client invented; Caddy's `request_body max_size` sits in front as a backstop the server never relies on.
- **Per-user allowance — one query before the body is read**, `SUM(size_bytes)` over the owner's records inside the window, on an index of `(owner_id, created_at)`. Never a stored counter: it drifts, is lost on restart, and has to be reconciled against this sum anyway.
- **Free-space floor — checked before each upload**, answered with `507`.
- **Upload rate — a per-user token bucket on the endpoint**, the same shape as the interaction limiting in [notes-rate-limiting.md](notes-rate-limiting.md).
- **Download rate — a second bucket of the same shape**, on the download route. It bounds requests, not bytes: at the per-file cap a full burst is still several hundred megabytes, so it is a brake on looping clients rather than a bandwidth budget. Charging a rolling byte total against the record's size would bound that too, at the cost of a write on every read — not worth it until reads are actually the problem.

Consequences worth stating, since none of the five numbers covers them:

- **The handler picks the number, the store enforces it.** `min(perFileCap, remainingAllowance)` goes into `put`, so a breach of either dies mid-stream rather than after the bytes are on disk, and which of the two was hit is the handler's to report — the store was given one number and knows nothing about quotas.
- **Rolling window, not calendar day.** A midnight reset permits the whole allowance at 23:59 and again at 00:01, double the intended burst at the worst time to notice.
- **Concurrent uploads can both pass the pre-check**, overshooting the allowance by at most the per-file cap times a member's concurrent uploads. Either re-check the sum after inserting the record under a lock on the user row, or accept the overshoot — at this size it is tens of megabytes and the lock is optional, but it is a decision rather than an accident.
- **Aborted uploads are free**, since the allowance counts stored bytes: a client can push to just under the cap, abort, and repeat. The token bucket is what closes this, optionally with an in-memory ledger charging bytes received rather than bytes kept.
- **The floor is the only per-box limit.** Allowances bound one member's accident and nothing bounds the sum of everyone's, so the floor deserves an alert well above the point where it starts rejecting.
- **`GET /attachments/quota` returns cap, allowance, used and reset time**, so a client refuses locally before opening a socket and the TUI can show what is left. The client check is UX; the server enforces independently.

## Record lifecycle

An attachment passes through three states, each a legitimate place to stop:

- **Stored** — a blob exists under its hash and nothing in the database names it. It is unreachable: no id has been handed out, so no request can ask for it and only the sweep can find it.
- **Claimed** — a record names the blob, its uploader, and the conversation it is destined for. The uploader holds the id and can send it; no other member knows it exists.
- **Referenced** — a message names the record. Every member of that conversation can download it, and it lives as long as the message does.

Uploading, in order:

1. **Authenticate and read the headers** — the original filename and declared type off `Content-Disposition` and `Content-Type`. If the key names an existing record of this uploader — the pair is unique on `(owner_id, idempotency_key)`, never the key alone — return that record and read no body: the retry is finished. Scoping it to the owner is what stops one member's key colliding with another's and handing back a record id, which is the capability to reference the file.
2. **Decide the limit before reading a byte** — `min(perFileCap, remainingAllowance)`. A spent allowance is `429` with `Retry-After`, free space under the floor is `507`, and neither has cost a transfer.
3. **`put` streams the body into `tmp/<upload id>`**, hashing and counting as it writes, sniffing the first buffer, and aborting the moment the count passes the limit. The upload id is minted by the server for this one transfer; the client's idempotency key never reaches a path, being exactly the user-controlled component the layout forbids.
4. **`put` renames the temp file to `blob/<hash>`** — or, if that key already exists, drops the temp file and takes the blob that is there, touching it. → *Stored*
5. **Insert the record** with the fields listed above — the three `put` returned, the conversation and sanitized filename from the request, the declared type for comparison, the idempotency key — and return its id. → *Claimed*
6. **The client sends a message naming the id.** The server accepts it only if the record exists, belongs to the sender, names the conversation the message is going to, and is not already referenced. This is an authorization check, not a consistency one: without it a member attaches someone else's file, or a record becomes referenced from a second conversation whose membership never granted access, which silently voids the download check that reads the conversation off the record. Accepting it clears `unreferenced_since`. → *Referenced*

The order never reverses. Each step above fails toward something nothing points
at, which the sweep reclaims; the reverse order fails toward a pointer at
something absent, which a member sees as a broken attachment.

Afterwards, deleting a message clears the reference, stamps `unreferenced_since`
again, and the record drops back to *Claimed*, with the tombstone behaving
exactly as it does for text. The blob
outlives it: it is reclaimed when no record names it, not when a record is
deleted, since content addressing means several records legitimately share one
blob and forwarding a screenshot twice must survive deleting one copy.

Forwarding is what mints that second record. The server clones the row — same
blob key, the forwarding member as owner, the destination conversation, and
already referenced by the forwarded message — so no bytes move and no upload
happens, the key being the hash. The clone counts against the forwarder's
allowance like any other record, since every quota decision is made against a
row. Deleting either copy leaves the other whole, which is the case content
addressing exists for.

## The sweep

A job on a timer, and the only thing in the system that deletes an attachment:
`delete(key)` has exactly one caller. Every other path adds or unreferences, so
nothing has to get deletion right except this.

It exists because of the write order. Bytes before record before reference means
every interruption — a crash, a closed socket, a member who uploads and then
changes their mind — leaves something nothing points at, rather than a pointer
at something absent. That trade is only cheap if a janitor makes the leftovers
disappear, and this is it.

A pass, hourly, bounded in how much it will do and restartable at any point —
crashing halfway leaves work for the next one and nothing else. Each floor below
is a minimum, the cadence adding up to one more interval before anything is
actually reclaimed:

1. **Skip the pass, logging why, unless all of the following hold:**
   - no restore marker is set (see the backup ordering under Storage),
   - no other pass is in flight,
   - every candidate query below returned without error — a failure means skip, never "nothing names this blob",
   - the `attachments` table is non-empty, or `blob/` is empty too, or an operator has cleared the guard — an empty table beside a full volume is what a half-restored system looks like and equally what deleting the last attachment looks like, and the pass cannot tell them apart,
   - no class's candidate set exceeds a fifth of that class's population.
2. **Unlink every file in `tmp/` with an mtime older than one hour.** Filesystem only — an abandoned upload leaves no other trace, so `tmp/` is enumerated, not queried. The hour keeps the pass clear of transfers still running.
3. **Delete records whose `unreferenced_since` is more than 24 hours old.** The stamp is set when the record is inserted and cleared when a message references it, so the clock measures how long a record has actually been unreferenced rather than how old it is — a year-old attachment freed by a message deletion gets the same 24 hours as one uploaded this morning. Each row is deleted inside a transaction that re-reads its reference first and skips the row if a send landed between the selecting query and the delete. The 24 hours is a UX number rather than a safety one: it is how long a half-composed message may sit before its attachment evaporates.
4. **Walk `blob/`, and for each blob with an mtime older than one hour:** take a Postgres advisory lock on the hash, confirm under that lock that no record names the key, unlink the `derived/` variants, then unlink the blob. Running after step 3 means a record reclaimed there frees its blob in the same pass. `put` takes the same lock when it reuses an existing blob, which is what makes confirm-then-unlink safe against an upload deduplicating onto the blob being collected; the one-hour floor covers the gap between a blob being written and its record existing, and reuse touches the mtime so a long-dormant blob claimed by a new record is never eligible. Variants go before the blob, so an interrupted delete leaves them missing — they regenerate — rather than orphaned from a source that is gone.
5. **Log and export counts per class.** A pass reclaiming steadily more than it used to is the first symptom of a leak in the upload path, which is otherwise silent.

## Download

`GET /attachments/{id}`, in order:

1. **Authenticate the session** — a Bearer lookup, as on any REST route.
2. **Load the record by id**, or answer 404.
3. **Authorize against the record's state.** A `Claimed` record is readable by its owner alone; a `Referenced` one by any current member of the conversation the record names, which is why the record carries that conversation directly instead of reaching it through the message. Membership is evaluated per request, so a kick ends access at once, and deleting the message — which drops the record back to `Claimed` — revokes everyone but the uploader.
4. **Open the blob** with `open(key, range)`. A record whose blob is gone is the broken link the write order exists to prevent: answer 404, log it loudly, and count it.
5. **Stream the response** — `application/octet-stream`, `Content-Disposition: attachment; filename*=UTF-8''…` carrying the sanitized filename under the same encoding the upload header uses, `X-Content-Type-Options: nosniff`, `Content-Length`, `Accept-Ranges: bytes`, and `ETag` set to the blob hash. A ranged request answers `206` with `Content-Range`; an unsatisfiable one, `416`.

Properties this buys, and the ones it needs alongside:

- **Every refusal is a 404.** A 403 on a record the caller may not read distinguishes it from one that does not exist, which makes the id space enumerable by anyone with a session.
- **Caching comes free from content addressing.** The `ETag` is the hash and the bytes under a key never change, so `Cache-Control: private, immutable` with a long max-age is correct by construction and lets a client `304` out of re-fetching. Without it a TUI re-downloads the same screenshot every time scrollback renders it.
- **The stored content type is never echoed back**, only `application/octet-stream`. This is the primary control against an uploaded file executing in a browser or GUI client, and unlike type checking it survives polyglots that are valid in two formats at once.
- **Downloads carry their own rate limit**, because the per-user allowance counts stored bytes and never sees a read: a member looping over one 25 MB file is otherwise unmetered.
- **Bytes already fetched outlive the access check.** Authorization is per request, so a kick stops the next download, while `immutable` with a long max-age and the client's own blob cache ([notes-client-cache.md](notes-client-cache.md)) keep what was already fetched readable. Revocation bounds reach, not recall.

## Typing

Sniffing is reading a file's leading bytes and matching them against known
signatures, rather than believing the type the client declared.

What it solves:

- **A declared type is a guess or a lie** — clients derive it from the extension, and an upload piped from stdin has neither an extension nor a name. Sniffing is what makes the type on the record worth storing.
- **It is the precondition for anything that acts on the type.** Nothing should hand bytes to a decoder on a client's say-so, and an allowlist filtering on declared types filters nothing.
- **It costs almost nothing here.** The sample is the first buffer the copy loop has already read, so there is no second pass and no reopening the file.

What it does not solve:

- **It is not malware detection.** A genuine PNG is still arbitrary content, and a genuine archive may be a `.jar`.
- **It is not what stops an upload from executing.** A polyglot — one file that is both a valid GIF and valid HTML — sniffs as the first and runs as the second. The `octet-stream` / `attachment` / `nosniff` headers on download are the control, and they hold whether or not the sniff was right.
- **It cannot separate zip-based formats.** `.docx`, `.xlsx`, `.jar`, `.apk` and a plain archive all open with `PK\x03\x04`.
- **It cannot type a format that has no signature** — SVG is XML, and plain text, CSV and source code are indistinguishable from one another.

The policy today: the sniffed type is canonical on the record, the declared type
is stored beside it, and a disagreement between them is recorded rather than
rejected — nothing on the server acts on the type, so there is nothing to reject
for. The pair is also the evidence for whether an allowlist could be switched on
later without breaking anyone.

Detection is **`tika-core`**: one detector call over the first buffer, no
configuration. Take `tika-core` alone — `tika-parsers` pulls in POI, PDFBox and
the document-parsing CVE surface for extraction this server never performs.

Typing becomes load-bearing the moment a rendering client, server-side
processing of a file's contents, or a type allowlist arrives. Until then it is
labelling.
