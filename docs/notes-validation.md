# Validation notes

## Rules for names

### Username

```
^[a-z][a-z0-9_-]{2,31}$
```

- **Lowercase only** — removes the case-folding question and the `Alice`/`alice` ambiguity outright. Viable because a separate display name carries the human-readable form; without one this would be too harsh.
- **ASCII only** — kills confusables (Cyrillic `а` rendering as `a`), Unicode normalization, zero-width characters, and bidi overrides in a single rule rather than five.
- **Starts with a letter, no trailing separator, no consecutive separators** — so `a__b` and `a_b` cannot both exist, and `_alice` and `alice_` are not distinct accounts.
- **3 to 32 characters.**
- **Uniqueness enforced by a database unique index**, not an application-level check, or two concurrent registrations race past it.
- **Reserved names** — `admin`, `system`, `server`, `root`, `mod`, `moderator`, `support`, `everyone`, `here`, `bot`, plus any route segment that could collide if usernames ever appear in URLs: `invite`, `health`, `metrics`, `api`. Without this, someone registers `system` and their messages read as server output in a terminal client.
- **Survives command parsing** — every client is a CLI or TUI, so a name cannot contain spaces (ambiguous in `/msg alice hello`) and cannot begin with `-` (Clikt reads it as a flag).

Usernames can be changed. That imposes three requirements:

- **Every reference is by immutable user ID** — the username is a mutable attribute, never a foreign key. Messages, invites (`issued_by`, `redeemed_by`), sessions, and audit records all point at the ID, so a rename rewrites nothing and breaks nothing.
- **Renames are recorded in the audit log** — old value, new value, timestamp. This is what keeps history readable: a record from before a rename still resolves to the right person, and the name they held at the time is recoverable.
- **A freed name is held before it can be reclaimed** — otherwise someone renames away and a third party immediately takes the name, inheriting whatever recognition attached to it. A hold of roughly 30 days, plus a cooldown between a user's own renames, removes the churn.

A rename revalidates against the same pattern, reserved list, and unique index as
registration.

### Display name

The human-readable label shown in place of the username. Optional; when absent the
username is displayed instead.

- **Unicode allowed, normalized to NFC** — unlike the username this is not an identifier, so it need not be restricted to ASCII.
- **Trimmed, with internal whitespace runs collapsed** — these two are safe to apply silently; everything else below is rejected rather than mangled.
- **Rejected: C0 and C1 control characters** (0x00–0x1F, 0x7F, and U+0080–U+009F), **bidi overrides** (U+202A–U+202E, U+2066–U+2069), **and zero-width characters** (U+200B–U+200D, U+FEFF). C1 matters because U+009B is CSI, a single-character equivalent of `ESC [`. Bidi overrides reverse rendered text and zero-width characters create invisible distinctions between otherwise identical names.
- **Capped at 32 grapheme clusters and 128 bytes** — both, because a single grapheme cluster can carry unlimited combining marks. The byte cap is what stops Zalgo text; the grapheme cap is what makes the limit mean what a user expects.
- **Not unique**, and not subject to a reserved list — uniqueness and reservation belong to the identifier.
- **Freely changeable**, with no cooldown, since nothing is addressed by it.

### Group name

Groups are addressed by a server-generated immutable ID, which carries every foreign
key; the name is a label the client resolves against the user's own group list.

- **Required and non-empty** after trimming, unlike the display name — a group with no name is unusable in a picker.
- **Unicode allowed, normalized to NFC, trimmed, internal whitespace runs collapsed**, with the same rejections as the display name: C0 and C1 control characters, bidi overrides, zero-width characters.
- **Capped at 64 grapheme clusters and 256 bytes** — longer than a display name, since group names carry more description in practice.
- **Not unique.** Two groups may share a name; the client disambiguates against its cached list, showing a numbered picker when a prefix matches more than one. Global uniqueness would buy disambiguation the client already does better, with local context.
- **Freely renameable**, since nothing is addressed by it.

### Group description

Carried by group chats, channels and server channels alike, alongside the name.

- **Optional** — a blank value is stored as absent, not as an empty string.
- **Single line** — newlines rejected, since it renders on one row in a listing and in a conversation header.
- **Unicode allowed, normalized to NFC, trimmed, internal whitespace runs collapsed**, with the same rejections as the group name: C0 and C1 control characters, bidi overrides, zero-width characters.
- **Capped at 256 grapheme clusters and 1024 bytes** — four times the group name, on the same grapheme-plus-byte pairing, since the byte cap is what bounds combining marks.
- **Not unique and freely changeable**, like the name.

### Session `client_label`

A short tag the client sets at login, stored on the session row in
[03-authentication.md](03-authentication.md). Multi-session means a user can hold
several concurrent sessions, and the list-and-revoke flow needs something to tell
them apart — without it the list is a set of rows differing only by timestamp.
Typical values are `console`, `tui`, or something user-supplied like `work laptop`.

```
^[A-Za-z0-9 -]{1,64}$
```

- **ASCII letters, digits, spaces, and dashes only**
- **Optional**; when absent the session is identified by its timestamps alone.
- **Trimmed, with internal whitespace runs collapsed**, since spaces are permitted inside the value.
- **Self-reported, so it is not evidence** — a stolen token used from an attacker's client sends whatever label that client chooses, including one imitating a legitimate session. The trustworthy columns in that view are the server-observed ones: `created_at`, `last_used_at`, and the login IP.

## Size and content limits

### Message body

- **8 KiB, measured in bytes** — long enough for a pasted stack trace, short enough that no single row holds a novel. Bytes rather than characters, since bytes are what the transport and the database actually cost.
- **Not normalized** — unlike names, a message body is reproduced verbatim. NFC normalization would silently alter pasted code and diffs, so bodies are validated and stored byte-for-byte.
- **C0 and C1 control characters rejected except `\n` and `\t`** — 0x00–0x1F, 0x7F, and U+0080–U+009F. This is the escape-sequence rule: a body carrying raw ANSI is interpreted by the terminal rather than displayed, which lets a message move the cursor, clear the screen, or overwrite the lines above it to fake output from another user. C0 covers ESC at 0x1B; C1 covers U+009B, which is CSI on its own and reaches the same result without an ESC byte.
- **`\r\n` collapsed to `\n` before validating**, and a bare `\r` rejected. Carriage return is the overwrite primitive, so it has no legitimate use once line endings are normalized.
- **Rejected when empty after trailing whitespace is stripped** — a whitespace-only message is a rendering artifact, not a message.
- **Capped at 100 lines** — within 8 KiB a body can still hold thousands of newlines and scroll a terminal client's history off the screen. Cheaper to bound here than to make every client collapse long messages.
- **Bidi overrides are allowed**, unlike in names, because mixed-direction text is legitimate in a message. The Trojan Source risk they carry is a rendering concern and belongs to the client, which must display them inertly rather than obeying them.

### Transport caps

- **WebSocket `maxFrameSize` of 64 KiB** — set on Ktor's WebSockets plugin. Must exceed the message body cap, since the frame also carries the JSON envelope; the gap absorbs that without letting the two limits be confused.
- **HTTP request body of 256 KiB** for REST endpoints, attachments excluded.
- **Transport caps are enforced before deserialization** — they are the only limits that apply before the server allocates anything, which is what makes them the DoS control. Field-level limits run after, on a payload already known to be bounded.
- **Unknown JSON keys are rejected**, which is kotlinx.serialization's default. `ignoreUnknownKeys` stays off on the server's inbound path, so a client-version mismatch fails loudly instead of silently dropping fields.
- **Limits are also expressed as database check constraints**, so a bug in the validation layer cannot write a row the rest of the system assumes is bounded.

## Rendering untrusted text in clients

Server-side rejection protects the database and any future non-terminal consumer. It
does not protect the terminal, because a client's untrusted input is the socket, not
the person typing on the other end:

- A compromised or hostile server sends whatever it wants, and every client renders it.
- Rows written before a validation rule existed are still in the database.
- Much of what a client renders never passed through body validation — attachment filenames, error strings, server-generated notices.

Rules:

- **One choke point** — a single function every inbound string passes through before it reaches Mordant or Mosaic. Sanitizing at each render site is how one path ends up forgotten.
- **Allowlist, not denylist** — keep printable characters, `\n`, and `\t`; replace everything else.
- **Replace visibly rather than dropping silently** — render a stripped escape as `␛` or U+FFFD, so a crafted message is visibly crafted. Silent removal makes it read as innocent.
- **Measure width in display columns, not code points** — wide CJK characters, emoji, and combining marks break TUI layout otherwise. Not a security property, but it belongs at the same choke point.

Log output needs no equivalent handling. The structured logging decision means
logstash-logback-encoder emits JSON, and JSON string escaping neutralizes both
newlines and ESC; Grafana renders in a browser, where the sequences are inert.
