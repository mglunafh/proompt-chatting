-- Step 2's schema: the subset of docs/general/notes-db-schema.md that this step's
-- features reach. The `Reduced:` clauses in docs/step2/features.md fix which columns
-- are here and which are not; a later step widens rather than revisits.
--
-- Foreign keys are NO ACTION to prevent hard-deletion and cascade deletions.
-- Accounts are disabled, invites revoked. Sessions are deleted, and nothing points at them.

create table users (
    id            bigserial primary key,
    username      text        not null,
    password_hash text        not null,               -- Argon2id, W-08
    is_admin      boolean     not null default false, -- AUTH-03 reduces the ordered role to a flag
    disabled      boolean     not null default false,
    last_seen_at  timestamptz,                        -- null until the first disconnect
    created_at    timestamptz not null default now()
);

-- SEC-05 puts username uniqueness here rather than in anything held in memory.
create unique index users_username_key on users (username);

create table sessions (
    id           bigserial primary key,
    token_hash   bytea       not null,                -- SHA-256 of the token, 32 bytes
    user_id      bigint      not null references users (id),
    created_at   timestamptz not null default now(),
    last_used_at timestamptz not null default now(),
    expires_at   timestamptz not null                 -- absolute: AUTH-11 has no sliding renewal
);

create unique index sessions_token_hash_key on sessions (token_hash);

-- Logout-everywhere, and the boot repair W-22 runs over a user's sessions.
create index sessions_user_id_idx on sessions (user_id);

create table invites (
    id         bigserial primary key,
    token_hash bytea       not null,                  -- the hash, never the raw code
    issued_by  bigint      not null references users (id),
    created_at timestamptz not null default now(),
    expires_at timestamptz not null,
    used_at    timestamptz,                           -- presence = redeemed; W-10 races on this
    revoked_at timestamptz                            -- presence = revoked, by MOD-06's disable
);

create unique index invites_token_hash_key on invites (token_hash);
create index invites_issued_by_idx on invites (issued_by);

-- The container behind messages. `direct` is the only kind in this step.
create table conversations (
    id         bigserial primary key,
    kind       text        not null,
    created_at timestamptz not null default now(),
    direct_lo  bigint      references users (id),
    direct_hi  bigint      references users (id),

    constraint conversations_kind_check
        check (kind in ('direct')),

    constraint conversations_direct_pair_check
        check ((kind = 'direct') = (direct_lo is not null and direct_hi is not null)),

    -- One spelling of a pair, so W-23's canonicalization is enforced rather than trusted.
    constraint conversations_direct_order_check
        check (direct_lo is null or direct_lo < direct_hi)
);

-- What stops two concurrent first-sends between one pair forking the history.
create unique index conversations_direct_pair_key
    on conversations (direct_lo, direct_hi) where kind = 'direct';

create table conversation_members (
    conversation_id bigint      not null references conversations (id),
    user_id         bigint      not null references users (id),
    joined_at       timestamptz not null default now(),

    primary key (conversation_id, user_id)
);

-- Listing one user's conversations reads this the other way round from the primary key.
create index conversation_members_user_id_idx on conversation_members (user_id);

create table messages (
    id              bigserial primary key,           -- fixes display order, and pages scrollback
    conversation_id bigint      not null references conversations (id),
    sender_id       bigint      not null references users (id),
    body            text        not null,
    created_at      timestamptz not null default now(),
    client_msg_id   text        not null
);

-- The send idempotency key: a resend finds the existing row rather than writing a second.
create unique index messages_sender_client_msg_id_key on messages (sender_id, client_msg_id);

-- MSG-03 pages `before` an id within one conversation, newest first.
create index messages_conversation_id_id_idx on messages (conversation_id, id desc);
