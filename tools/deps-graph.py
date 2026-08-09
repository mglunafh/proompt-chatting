#!/usr/bin/env python3
"""Render a dependency graph of tasks and features from a deps.md source file.

    deps-graph.py init   docs/step2/deps.md
    deps-graph.py verify docs/step1/deps.md
    deps-graph.py build  docs/*/deps.md

`build` writes deps.html (interactive page) and deps-mermaid.md next to each
input. Node rows are derived, not declared: a task sits one row below its
deepest prerequisite, a feature one row below its deepest covering task.

Descriptions are optional, and come from an indented line under a node, or from
`- **ID Name** — description` bullets in the files named by ## Sources, by -d,
or by default in todo.md and features.md beside deps.md. A node with none falls
back to its name. Format reference: deps-format.md.

Standard library only.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

TEMPLATE_NAME = "graph-template.html"
FORMAT_DOC = "deps-format.md"
PROSE_FILES = ("todo.md", "features.md")
KINDS = ("task", "feature")

SKELETON = """\
# {title}

<!-- kind, ID, then the name shown on the node. An indented line under a
     node is its description, and wins over any prose file below. -->
## Nodes

task     W-01    First task
task     W-02    Second task
feature  ABC-01  First feature

<!-- A -> B means B waits on A. List several with commas. -->
## Order

W-01 -> W-02

<!-- FEATURE <- the tasks that complete it. Every feature needs one line. -->
## Coverage

ABC-01 <- W-02

<!-- Descriptions come from `- **ID Name** — description` bullets in todo.md
     and features.md beside this file, when those exist. To read from
     somewhere else, add a section listing the files in order, relative to
     this file:

     ## Sources

     ../shared/glossary.md

     A ## Sources section with no files listed reads no prose at all. -->
"""

H1_RE = re.compile(r"^#\s+(?P<title>.+?)\s*$")
SECTION_RE = re.compile(r"^##+\s+(?P<name>.+?)\s*$")
FENCE_RE = re.compile(r"^\s*```")
NODE_RE = re.compile(r"^(?:[-*]\s+)?(?P<kind>\w+)\s+(?P<id>[^\s]+)\s+(?P<name>.*\S)\s*$")
ORDER_RE = re.compile(r"^(?P<src>[^\s>]+)\s*->\s*(?P<dsts>.+)$")
COVER_RE = re.compile(r"^(?P<dst>[^\s<]+)\s*<-\s*(?P<srcs>.+)$")
COMMENT_RE = re.compile(r"<!--.*?-->", re.DOTALL)

BULLET_RE = re.compile(
    r"^\s*[-*]\s+\*\*(?P<id>[A-Za-z0-9][\w.]*(?:-[\w.]+)*)\s+(?P<name>[^*]+?)\*\*"
    r"\s*(?:[—–]|--)\s*(?P<desc>.+?)\s*$"
)
TRAILING_IDS_RE = re.compile(r"\s*(?:[A-Z][A-Z0-9]*-\d+)(?:\s*,\s*[A-Z][A-Z0-9]*-\d+)*\s*\.\s*$")
LINK_RE = re.compile(r"\[([^\]]+)\]\([^)]*\)")


@dataclass
class Node:
    id: str
    kind: str
    name: str
    line: int
    desc: str = ""
    row: int = 1


@dataclass
class Edge:
    src: str
    dst: str
    line: int


@dataclass
class Graph:
    path: Path
    title: str = "Dependency map"
    nodes: dict[str, Node] = field(default_factory=dict)
    order: list[Edge] = field(default_factory=list)
    coverage: list[Edge] = field(default_factory=list)
    sources: list[str] = field(default_factory=list)
    sources_declared: bool = False
    problems: list[str] = field(default_factory=list)

    def tasks(self) -> list[Node]:
        return [n for n in self.nodes.values() if n.kind == "task"]

    def features(self) -> list[Node]:
        return [n for n in self.nodes.values() if n.kind == "feature"]


class SourceError(Exception):
    """The input could not be read far enough to be checked."""


# --------------------------------------------------------------------------- parse


def split_ids(text: str) -> list[str]:
    return [part.strip() for part in re.split(r"[,\s]+", text.strip()) if part.strip()]


def parse_deps(path: Path) -> Graph:
    try:
        raw = path.read_text(encoding="utf-8")
    except OSError as exc:
        raise SourceError(f"{path}: {exc.strerror or exc}") from exc

    graph = Graph(path=path)
    section = None
    current: Node | None = None

    # Blank the comments in place so reported line numbers still match the file.
    blanked = COMMENT_RE.sub(lambda m: "\n" * m.group(0).count("\n"), raw)

    for number, line in enumerate(blanked.splitlines(), start=1):
        text = line.strip()
        if not text or FENCE_RE.match(line):
            continue

        heading = SECTION_RE.match(text)
        if heading:
            section = heading.group("name").strip().lower()
            current = None
            if section == "sources":
                graph.sources_declared = True
            continue

        title = H1_RE.match(text)
        if title:
            graph.title = title.group("title").strip()
            continue

        where = f"{path}:{number}"

        if section == "nodes":
            # An indented line under a node is that node's description.
            if current is not None and line[:1].isspace():
                current.desc = f"{current.desc} {text}".strip()
                continue

            match = NODE_RE.match(text)
            if not match:
                graph.problems.append(f"{where}: cannot read node line: {text}")
                continue
            kind = match.group("kind").lower()
            node_id = match.group("id")
            if kind not in KINDS:
                graph.problems.append(f"{where}: unknown kind {kind!r}, expected task or feature")
                continue
            if node_id in graph.nodes:
                first = graph.nodes[node_id].line
                graph.problems.append(f"{where}: {node_id} already defined on line {first}")
                continue
            current = Node(
                id=node_id, kind=kind, name=match.group("name").strip(), line=number
            )
            graph.nodes[node_id] = current

        elif section == "sources":
            graph.sources.append(text)

        elif section == "order":
            match = ORDER_RE.match(text)
            if not match:
                graph.problems.append(f"{where}: cannot read order line: {text}")
                continue
            src = match.group("src")
            for dst in split_ids(match.group("dsts")):
                graph.order.append(Edge(src=src, dst=dst, line=number))

        elif section == "coverage":
            match = COVER_RE.match(text)
            if not match:
                graph.problems.append(f"{where}: cannot read coverage line: {text}")
                continue
            dst = match.group("dst")
            for src in split_ids(match.group("srcs")):
                graph.coverage.append(Edge(src=src, dst=dst, line=number))

        elif section is None:
            continue

    if not graph.nodes:
        graph.problems.append(f"{path}: no ## Nodes section, or it is empty")

    return graph


def clean_prose(text: str) -> str:
    text = LINK_RE.sub(r"\1", text)
    text = text.replace("`", "").replace("**", "").replace("*", "")
    text = TRAILING_IDS_RE.sub("", text).strip()
    # Bullets read as a clause after the bold name, so they open lower-case.
    if text[:1].islower():
        text = text[0].upper() + text[1:]
    return text


def read_descriptions(files: list[Path]) -> dict[str, str]:
    """Collect `- **ID Name** — description` bullets from the prose docs, first ID wins."""
    found: dict[str, str] = {}

    for source in files:
        if not source.is_file():
            continue

        current: str | None = None
        parts: list[str] = []

        def flush() -> None:
            if current and current not in found:
                found[current] = clean_prose(" ".join(parts))

        for line in source.read_text(encoding="utf-8").splitlines():
            bullet = BULLET_RE.match(line)
            if bullet:
                flush()
                current = bullet.group("id")
                parts = [bullet.group("desc")]
            elif current and line.startswith((" ", "\t")) and line.strip():
                parts.append(line.strip())
            elif not line.strip():
                flush()
                current, parts = None, []
        flush()

    return found


# ----------------------------------------------------------------------- validate


def validate(graph: Graph) -> tuple[list[str], list[str]]:
    errors = list(graph.problems)
    warnings: list[str] = []
    known = graph.nodes

    for label, edges in (("order", graph.order), ("coverage", graph.coverage)):
        for edge in edges:
            where = f"{graph.path}:{edge.line}"
            for end in (edge.src, edge.dst):
                if end not in known:
                    errors.append(f"{where}: {label} edge names {end}, which no node defines")

    for edge in graph.order:
        for end in (edge.src, edge.dst):
            if end in known and known[end].kind != "task":
                errors.append(
                    f"{graph.path}:{edge.line}: order edges join tasks, but {end} is a feature"
                )
        if edge.src == edge.dst:
            errors.append(f"{graph.path}:{edge.line}: {edge.src} depends on itself")

    for edge in graph.coverage:
        if edge.src in known and known[edge.src].kind != "task":
            errors.append(f"{graph.path}:{edge.line}: {edge.src} covers a feature but is not a task")
        if edge.dst in known and known[edge.dst].kind != "feature":
            errors.append(f"{graph.path}:{edge.line}: {edge.dst} is covered but is not a feature")

    for label, edges, arrow in (("order", graph.order, "->"), ("coverage", graph.coverage, "<-")):
        seen = set()
        for edge in edges:
            key = (edge.src, edge.dst)
            if key in seen:
                pair = f"{edge.dst} <- {edge.src}" if arrow == "<-" else f"{edge.src} -> {edge.dst}"
                warnings.append(f"{graph.path}:{edge.line}: {label} edge {pair} listed twice")
            seen.add(key)

    cycle = find_cycle(graph)
    if cycle:
        errors.append(f"{graph.path}: order edges form a cycle: {' -> '.join(cycle)}")

    covered = {edge.dst for edge in graph.coverage}
    for node in graph.features():
        if node.id not in covered:
            errors.append(f"{graph.path}:{node.line}: {node.id} is covered by no task")

    for node in graph.nodes.values():
        if not node.desc:
            warnings.append(f"{graph.path}:{node.line}: {node.id} has no description")

    return errors, warnings


def find_cycle(graph: Graph) -> list[str]:
    children: dict[str, list[str]] = {node_id: [] for node_id in graph.nodes}
    for edge in graph.order:
        if edge.src in children and edge.dst in children:
            children[edge.src].append(edge.dst)

    WHITE, GREY, BLACK = 0, 1, 2
    colour = {node_id: WHITE for node_id in children}
    trail: list[str] = []

    def walk(node_id: str) -> list[str]:
        colour[node_id] = GREY
        trail.append(node_id)
        for child in children[node_id]:
            if colour[child] == GREY:
                return trail[trail.index(child):] + [child]
            if colour[child] == WHITE:
                found = walk(child)
                if found:
                    return found
        trail.pop()
        colour[node_id] = BLACK
        return []

    for node_id in children:
        if colour[node_id] == WHITE:
            found = walk(node_id)
            if found:
                return found
    return []


# -------------------------------------------------------------------------- layout


def assign_rows(graph: Graph) -> None:
    """Longest-path layering: higher row means it can start sooner."""
    parents: dict[str, list[str]] = {node.id: [] for node in graph.tasks()}
    for edge in graph.order:
        if edge.dst in parents and edge.src in parents:
            parents[edge.dst].append(edge.src)

    depth: dict[str, int] = {}

    def task_depth(node_id: str, seen: frozenset[str] = frozenset()) -> int:
        if node_id in depth:
            return depth[node_id]
        if node_id in seen:
            return 1
        value = 1
        for parent in parents[node_id]:
            value = max(value, task_depth(parent, seen | {node_id}) + 1)
        depth[node_id] = value
        return value

    for node in graph.tasks():
        node.row = task_depth(node.id)

    covered: dict[str, list[str]] = {node.id: [] for node in graph.features()}
    for edge in graph.coverage:
        if edge.dst in covered and edge.src in depth:
            covered[edge.dst].append(edge.src)

    for node in graph.features():
        sources = covered[node.id]
        node.row = max((depth[t] for t in sources), default=0) + 1


def ordered_nodes(graph: Graph) -> list[Node]:
    return sorted(graph.nodes.values(), key=lambda n: (n.row, n.kind != "task", n.id))


# -------------------------------------------------------------------------- render


def render_html(graph: Graph, template: str) -> str:
    data = {
        "nodes": [
            {"id": n.id, "kind": n.kind, "row": n.row, "name": n.name, "desc": n.desc or n.name}
            for n in ordered_nodes(graph)
        ],
        "order": [[e.src, e.dst] for e in graph.order],
        "coverage": [[e.src, e.dst] for e in graph.coverage],
    }
    payload = json.dumps(data, ensure_ascii=False).replace("</", "<\\/")

    lines = template.splitlines()
    for index, line in enumerate(lines):
        if "/*__GRAPH_DATA__*/" in line:
            indent = line[: len(line) - len(line.lstrip())]
            lines[index] = f"{indent}const GRAPH = {payload}; /*__GRAPH_DATA__*/"
            break
    else:
        raise SourceError("template has no /*__GRAPH_DATA__*/ marker")

    return "\n".join(lines).replace("__TITLE__", graph.title) + "\n"


def mermaid_id(node_id: str) -> str:
    return re.sub(r"[^A-Za-z0-9]", "", node_id)


def mermaid_classes(graph: Graph, indent: str = "  ") -> list[str]:
    lines = [
        f"{indent}classDef task fill:#16a34a,stroke:#14532d,stroke-width:1px,color:#ffffff",
        f"{indent}classDef feature fill:#dc2626,stroke:#7f1d1d,stroke-width:1px,color:#ffffff",
    ]
    for kind in KINDS:
        members = [mermaid_id(n.id) for n in ordered_nodes(graph) if n.kind == kind]
        if members:
            lines.append(f"{indent}class {','.join(members)} {kind}")
    return lines


def mermaid_by_depth(graph: Graph) -> list[str]:
    """Every node in one tree, top to bottom, in the order the page lays them out."""
    lines = ["```mermaid", "graph TD"]

    for node in ordered_nodes(graph):
        lines.append(f"  {mermaid_id(node.id)}[{node.id} {node.name}]")
    lines.append("")

    for edge in graph.order:
        lines.append(f"  {mermaid_id(edge.src)} --> {mermaid_id(edge.dst)}")
    for edge in graph.coverage:
        lines.append(f"  {mermaid_id(edge.src)} -.-> {mermaid_id(edge.dst)}")
    lines.append("")

    lines.extend(mermaid_classes(graph))
    lines.append("```")
    return lines


def mermaid_grouped(graph: Graph) -> list[str]:
    """Tasks and features boxed apart, left to right."""
    lines = ["```mermaid", "graph LR", "  subgraph Tasks"]

    for node in graph.tasks():
        lines.append(f"    {mermaid_id(node.id)}[{node.id} {node.name}]")
    for edge in graph.order:
        lines.append(f"    {mermaid_id(edge.src)} --> {mermaid_id(edge.dst)}")

    lines.extend(["  end", "", "  subgraph Features"])
    for node in graph.features():
        lines.append(f"    {mermaid_id(node.id)}[{node.id} {node.name}]")
    lines.extend(["  end", ""])

    for edge in graph.coverage:
        lines.append(f"  {mermaid_id(edge.src)} -.-> {mermaid_id(edge.dst)}")
    lines.append("")

    lines.extend(mermaid_classes(graph))
    lines.append("```")
    return lines


def render_mermaid(graph: Graph) -> str:
    lines = [f"# {graph.title}", "", "## By depth", ""]
    lines.extend(mermaid_by_depth(graph))
    lines.extend(["", "## Grouped by kind", ""])
    lines.extend(mermaid_grouped(graph))
    return "\n".join(lines) + "\n"


# ----------------------------------------------------------------------------- cli


def prose_files(graph: Graph, override: list[Path] | None) -> list[Path]:
    """--description wins over ## Sources, which wins over the sibling defaults."""
    if override is not None:
        declared = override
    elif graph.sources_declared:
        declared = [graph.path.parent / name for name in graph.sources]
    else:
        return [graph.path.parent / name for name in PROSE_FILES]

    for source in declared:
        if not source.is_file():
            graph.problems.append(f"{graph.path}: prose file {source} does not exist")
    return declared


def load(path: Path, override: list[Path] | None = None) -> Graph:
    graph = parse_deps(path)
    descriptions = read_descriptions(prose_files(graph, override))
    for node in graph.nodes.values():
        # An inline description in deps.md beats anything found in a prose file.
        if not node.desc:
            node.desc = descriptions.get(node.id, "")
        node.desc = clean_prose(node.desc) if node.desc else ""
    assign_rows(graph)
    return graph


def report(errors: list[str], warnings: list[str]) -> None:
    sys.stdout.flush()
    for warning in warnings:
        print(f"  warning  {warning}")
    for error in errors:
        print(f"  error    {error}", file=sys.stderr)


def suggested_title(path: Path) -> str:
    name = path.parent.name
    pretty = re.sub(r"(?<=[A-Za-z])(?=\d)", " ", name).replace("-", " ").replace("_", " ").strip()
    if not pretty or pretty in {".", "..", "docs"}:
        return "Dependency map"
    return f"{pretty[:1].upper()}{pretty[1:]} dependency map"


def run_init(paths: list[Path], force: bool) -> int:
    status = 0
    for path in paths:
        if path.exists() and not force:
            print(f"error    {path} already exists, pass --force to overwrite", file=sys.stderr)
            status = 1
            continue
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(SKELETON.format(title=suggested_title(path)), encoding="utf-8")
        print(f"wrote    {path}")
    return status


def run(
    paths: list[Path],
    template_path: Path,
    out_dir: Path | None,
    build: bool,
    descriptions: list[Path] | None,
) -> int:
    status = 0

    for path in paths:
        try:
            graph = load(path, descriptions)
        except SourceError as exc:
            print(f"error    {exc}", file=sys.stderr)
            status = 1
            continue

        errors, warnings = validate(graph)
        rows = max((n.row for n in graph.nodes.values()), default=0)
        edges = len(graph.order) + len(graph.coverage)
        print(f"{path}: {len(graph.nodes)} nodes, {edges} edges, {rows} rows")
        report(errors, warnings)

        if errors:
            status = 1
            continue
        if not build:
            continue

        try:
            template = template_path.read_text(encoding="utf-8")
            html = render_html(graph, template)
        except (OSError, SourceError) as exc:
            print(f"error    {template_path}: {exc}", file=sys.stderr)
            status = 1
            continue

        destination = out_dir or path.parent
        destination.mkdir(parents=True, exist_ok=True)
        html_path = destination / "deps.html"
        mermaid_path = destination / "deps-mermaid.md"
        html_path.write_text(html, encoding="utf-8")
        mermaid_path.write_text(render_mermaid(graph), encoding="utf-8")
        print(f"  wrote    {html_path}")
        print(f"  wrote    {mermaid_path}")

    return status


EPILOG = f"""\
sections of a deps.md file:

  ## Nodes      task|feature  ID  Name
                  an indented line under a node is its description
  ## Order      A -> B, C            B and C wait on A
  ## Coverage   FEATURE <- T1, T2    the tasks that complete FEATURE
  ## Sources    path/to/prose.md     where descriptions are read from
                  (default: {', '.join(PROSE_FILES)} beside deps.md)

full format: {FORMAT_DOC} beside this script
"""


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="deps-graph.py",
        description="Render task and feature dependency graphs from deps.md.",
        epilog=EPILOG,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "command",
        choices=("build", "verify", "init"),
        help="write the outputs, check the input only, or write a skeleton deps.md",
    )
    parser.add_argument("paths", nargs="+", type=Path, metavar="DEPS_MD")
    parser.add_argument(
        "-d",
        "--description",
        action="append",
        type=Path,
        dest="descriptions",
        metavar="PROSE_MD",
        help="prose file to read descriptions from; repeatable, overrides ## Sources",
    )
    parser.add_argument(
        "--template",
        type=Path,
        default=Path(__file__).resolve().parent / TEMPLATE_NAME,
        help=f"page template (default: {TEMPLATE_NAME} beside this script)",
    )
    parser.add_argument(
        "--out-dir",
        type=Path,
        default=None,
        help="write outputs here instead of beside each input",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="init: overwrite an existing deps.md",
    )
    args = parser.parse_args(argv)

    if args.command == "init":
        return run_init(args.paths, args.force)

    return run(
        args.paths,
        args.template,
        args.out_dir,
        build=args.command == "build",
        descriptions=args.descriptions,
    )


if __name__ == "__main__":
    sys.exit(main())
