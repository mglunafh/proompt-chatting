# Search notes

## ILIKE

### Implementation

Nothing is added to the schema. The endpoint runs one query against `messages`,
joined to `conversation_members` on the caller so the scan never leaves the
conversations they belong to:

```sql
SELECT m.id, m.conversation_id, m.sender_id, m.body, m.created_at
FROM messages m
JOIN conversation_members cm
  ON cm.conversation_id = m.conversation_id
 AND cm.user_id = $caller
WHERE m.body ILIKE $pattern ESCAPE '\'
  AND m.id < $cursor
ORDER BY m.id DESC
LIMIT $page;
```

The server builds `$pattern` from the user's string: escape `%`, `_` and the
backslash itself, then wrap the result in `%…%`. Skipping the escape hands every
user a wildcard, and a query of a single `%` returns their whole history a page
at a time. The membership join is what bounds the work — without it the
predicate is evaluated over every message on the instance, including
conversations the caller cannot read. Case folding comes from `ILIKE`, so there
is no collation to configure, though its behaviour on non-ASCII text follows the
database locale.

### Pros

- **Nothing to build** — it works on day one, with no migration to sequence against anything else.
- **Predictable semantics** — a substring is a substring; there is nothing to explain to a user about why a result matched.
- **Highlighting is free** — the client knows the exact matched span and can render it without asking the server.
- **No language configuration** — a mixed-language community behaves the same as a monolingual one.
- **No write cost** — sends and edits do no index maintenance.

### Cons

- **It matches inside words** — "cat" hits "concatenate", "category" and "advocate".
- **No stemming** — "running" does not find "ran" or "runs".
- **No multi-word queries** — either the exact substring including its spaces, or an AND of several `ILIKE`s assembled by hand.
- **No relevance ranking** — recency is the only order available.
- **Cost grows with history** — every query is a scan, so the ceiling moves as the archive does.

## tsvector

A Postgres column type holding a document already broken into searchable form: a
sorted list of distinct lexemes — normalized words — with the positions where
each occurred. `to_tsvector('english', 'The cats were running quickly')` yields
`'cat':2 'quick':5 'run':4`, having stemmed each word, dropped the stopwords, and
kept positions so phrase queries can check adjacency. The `simple` config skips
stemming and stopword removal, lowercasing and tokenizing only, which keeps
word-boundary matching without committing to a language. Queries are the
companion `tsquery` type and `@@` matches the two; a GIN index over the column is
an inverted index from lexeme to rows, which is why a lookup replaces a scan.

### Implementation

A generated column holds the parsed body and a GIN index covers it. Both are
added once, and Postgres maintains the column itself on insert and edit:

```sql
ALTER TABLE messages
  ADD COLUMN body_tsv tsvector
  GENERATED ALWAYS AS (to_tsvector('simple', body)) STORED;

CREATE INDEX messages_body_tsv_idx ON messages USING gin (body_tsv);
```

The query keeps the same membership join, cursor and page limit as the `ILIKE`
route; only the predicate changes:

```sql
WHERE m.body_tsv @@ websearch_to_tsquery('simple', $query)
```

`websearch_to_tsquery` is what lets the wire stay a plain string: it parses
quoted phrases, `or`, and a leading `-` for negation out of ordinary text, and
never raises on malformed input. `to_tsquery` would instead force clients to
construct operator syntax and the server to validate it. The text search config
named in the column and in the query must be the same one, or the index is
skipped and matching silently changes.

`ts_rank` and `ts_headline` sit on top of this, for relevance ordering and
server-rendered match context. Neither is needed to ship the route.

### Pros

- **Word-boundary matching** — no more hits inside longer words.
- **Multi-word and phrase queries** work without hand assembly.
- **Cost is independent of history size** — an index lookup rather than a scan.
- **Stemming and ranking are available** if the config is chosen for them.

### Cons

- **It commits to a language** — `english` stems badly for a mixed-language community, while `simple` keeps indexing and word boundaries but gives up stemming. `simple` is the safer default unless the community is known to be monolingual.
- **Every write maintains the index** — inserts and edits pay for it in the same transaction.
- **Highlighting moves server-side** — a stemmed match does not correspond to the string the user typed, so the client can no longer find the span itself.
- **Stemming surprises people** — a user who typed an exact word and gets its inflections back has to be told why.

## Choosing

- **Start with `ILIKE`** — the upgrade is additive: add the column and index, swap the predicate, leave the wire shape alone.
- **The trigger to upgrade is semantics, not latency** — complaints about matching inside words and about multi-word queries arrive long before the scan is slow. A small community's history runs to roughly 100 MB of body text, comfortably in page cache.
- **`pg_trgm` is the middle option** — a GIN trigram index makes `ILIKE '%term%'` indexed, keeping substring semantics and adding fuzzy matching with no language choice, at the cost of a bulky index. It answers a slow scan, not poor semantics.

## The contract that keeps the choice reversible

- **Scope is the caller's conversations** — the same membership predicate history paging uses, so a member added later matches messages sent before they joined, and a non-member matches nothing. Narrower than reading on purpose: a public channel is readable without joining, but its bodies enter no one's search until they join it.
- **Ordering is recency** — message ID descending. Relevance ranking exists only under `tsvector`, so leaving it out now makes adding it later additive rather than breaking.
- **Paging is keyed on message ID**, as in history, and a page may span conversations.
- **The query is a plain string** — never `tsquery` operators, or clients start depending on the mechanism.

## What neither route changes

- **Deleted messages never match** — a tombstone keeps its row with the body cleared.
- **Edits need no extra handling** — a scan reads the current body, and a generated column is maintained in the same transaction as the edit.
- **Abuse is already covered** — search is a query endpoint, so the per-user token bucket in front of query endpoints applies.
