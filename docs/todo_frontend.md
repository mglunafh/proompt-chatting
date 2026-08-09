# Frontend TODO — splitting the client

## The problem

The project may grow a second variant: an E2EE messenger alongside the plaintext
one. The two differ in the server, in the client's transport, and in client-side
storage and crypto. What the user sees does not differ: a conversation list, a
message with a sender and a timestamp, sending, presence, typing, unread counts.

The goal is to write the console, TUI and eventual GUI frontends once and run
them against either variant, without a frontend knowing which it is talking to.
Today the client is a single module with no seam between "talks to the server"
and "renders to the user", so every frontend written before that seam exists has
to be touched when it is introduced.

The variant-specific user-facing surface is small: contact verification (safety
numbers, key-change warnings) and a keystore passphrase unlock at startup.
Everything else is the same call from the UI's point of view.

## Things that could be done

- **Engine interface module** — one interface plus the domain model it exposes
  (event stream, send, login, history paging), in its own module with no
  transport, crypto or UI dependencies. Frontends depend on it and nothing else;
  each variant supplies an implementation. Costs one file today and is the item
  with the worst reversal cost, since retrofitting it means touching every
  frontend already written.
- **Fake engine emitting scripted events** — ships alongside the interface. Lets
  frontend work start with no server running and makes frontend tests
  independent of transport.
- **Express variant differences as nullable members and events** — verification
  metadata that is null in plaintext, an engine lifecycle state covering
  locked/ready/failed, a key-change event. A frontend shows a surface only when
  it is present, which is one condition per UI. The alternatives are a
  capability object the UI queries, or separate per-variant builds of the same
  UI behind compile-time flags.
- **A presentation layer between frontends and the engine** — unread counts,
  conversation-list ordering, event-to-state reduction, notification decisions.
  Genuinely shared across frontends, but its shape is not knowable until a
  second frontend exists; extracting it from a working console client is
  cheaper than guessing at it now.
- **Composition-root placement** — either `main` in each frontend module, which
  is simplest but puts a concrete engine on that module's compile path, or thin
  per-frontend app modules that depend on a frontend and an engine, which moves
  the guarantee from discipline into the build graph at the cost of more
  modules.
- **Keep protocol and wire types out of UI code from the start** — independent
  of every decision above, and free while the client is small.

## Open questions

- **Where the modules live in the step-based layout** — whether the E2EE variant
  is a new step or a sibling of the existing one, which fixes the module paths
  before anything is written down.
- **Whether the presentation layer is its own module, and when it appears.**
- **Whether console and TUI share rendering code** — the tech stack names a
  line-based styling library for one and a full-screen Compose-for-terminal
  renderer for the other. Appending to a stream and owning a redrawn screen may
  share state without sharing a renderer.
- **Search under E2EE** — server-side content search is unavailable, and a local
  index covers only the cached window, so results would be per-device and would
  miss the archive. Either the E2EE client holds the full corpus, which
  contradicts the window-with-a-floor cache model, or search returns partial
  results and the incompleteness is carried on the result. A storage decision,
  but the one that reaches a type the UI renders.
