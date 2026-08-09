# deps.md format

A `deps.md` file is the input to `deps-graph.py`. It names the tasks and features of one
step, states which tasks wait on which, and states which tasks complete each feature.
Everything else the outputs need — row positions, the Mermaid diagrams, the interactive
page — is derived from it.

## Commands

```sh
deps-graph.py init   docs/step2/deps.md      # write a commented skeleton
deps-graph.py verify docs/step2/deps.md      # check the input, write nothing
deps-graph.py build  docs/*/deps.md          # write deps.html and deps-mermaid.md
```

`build` runs the same checks as `verify` and refuses to write when any of them fail.
Both exit non-zero on an error and zero on warnings alone.

- `-d PATH`, `--description PATH` — read descriptions from this file; repeatable, and
  overrides the `## Sources` section.
- `--template PATH` — page template, `graph-template.html` beside the script by default.
- `--out-dir PATH` — write the outputs here instead of beside each input.
- `--force` — `init` only: overwrite an existing file.

## Shape of the file

The first `#` heading is the title, used for the page heading, the `<title>` and the
heading of `deps-mermaid.md`. Each `##` heading opens a section, and section names are
matched case-insensitively. Blank lines, ```` ``` ```` fences and `<!-- comments -->` are
ignored wherever they appear, so a section may be fenced for the benefit of a Markdown
renderer without changing how it parses.

## Nodes

One node per line: kind, ID, then the name shown on the node.

```
task     W-01    Build scaffolding
feature  MSG-04  Live connection
```

- Kind is `task` or `feature`. Nothing else is accepted.
- The ID is any run of non-space characters, and must be unique across the file.
- The name is the rest of the line.
- An **indented** line under a node is that node's description, continuing over as many
  indented lines as needed. It wins over any description found in a prose file.

```
task     W-01    Build scaffolding
  The shared module, the catalog entries and the
  application plugin on both sides.
```

## Order

Which tasks wait on which. `A -> B` means B cannot start until A is done.

```
W-02 -> W-03, W-04, W-08
```

Both ends must be tasks. Several targets may share one line, separated by commas or
spaces.

## Coverage

Which tasks complete a feature, written from the feature's side.

```
MSG-04 <- W-02, W-05, W-08
```

The left side must be a feature and the right side tasks. Every feature needs at least
one such line. A task may cover nothing.

## Sources

Where descriptions are read from, in order, relative to the `deps.md` file.

```
todo.md
../shared/glossary.md
```

- Omit the section entirely and `todo.md` and `features.md` beside `deps.md` are read
  when they exist, and silently skipped when they do not.
- Include the section with no files listed to read no prose at all.
- A file listed here, or passed with `-d`, must exist — a missing one is an error rather
  than a silent fallback.

Descriptions are read from bullets of the form:

```
- **W-02 Protocol module** — the command and event hierarchy as sealed types.
```

The ID must match a node. The first file to define an ID wins. Backticks, emphasis and
Markdown links are stripped, a trailing list of feature IDs is dropped, and the opening
letter is capitalised, since these bullets read as a clause after the bold name. A node
with no description anywhere falls back to its name as hover text.

## Derived rows

Row numbers are computed, never written down:

- A task sits one row below its deepest prerequisite; a task with none sits on row 1.
- A feature sits one row below the deepest task that covers it.

So a node's row is the earliest point at which it could be reached, and reading the page
downward reads the work in schedule order.

## What the checks catch

Errors, which stop `build`:

- a node ID defined twice, an unknown kind, or a line that cannot be read
- an edge naming an ID that no node defines
- an order edge touching a feature, or a coverage edge with its ends the wrong way round
- a task that depends on itself, or a cycle among the order edges (the loop is printed)
- a feature that no task covers
- a prose file that is named but missing

Warnings, which do not:

- a node with no description
- the same edge listed twice
