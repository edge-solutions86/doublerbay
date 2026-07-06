"""The checks that have to pass, written down once and run from two places.

A repository with a continuous integration file and a developer's habit is a
repository with two definitions of "green". They agree for about a month. Then
somebody adds a check to the workflow and nobody's laptop runs it, or somebody
adds one to their laptop and the workflow never sees it, and the argument
about which one is authoritative happens on the day it matters most.

So the gates are data. The workflow runs them by name and so does the command
line, and a test asserts the workflow file names every gate this module does.
Adding a gate here without adding it to the workflow fails that test, which is
the only way this stays true.

Nothing here shells out on import. The commands are strings until somebody
asks for them to run, so importing this module in a test costs nothing.
"""

from __future__ import annotations

import subprocess
from dataclasses import dataclass
from pathlib import Path

from doublerbay.errors import BadReading


@dataclass(frozen=True, slots=True)
class Gate:
    """One check, its command, and why it is worth the seconds it takes."""

    name: str
    command: tuple[str, ...]
    why: str

    def __post_init__(self) -> None:
        if not self.name.strip():
            raise BadReading("a gate has a name")
        if not self.command:
            raise BadReading(f"{self.name} has no command to run")

    @property
    def written(self) -> str:
        return " ".join(self.command)

    def __str__(self) -> str:
        return f"{self.name}: {self.written}"


GATES = (
    Gate(
        name="lint",
        command=("ruff", "check", "."),
        why="the rules the whole repository is written to",
    ),
    Gate(
        name="format",
        command=("ruff", "format", "--check", "."),
        why="a formatting argument in a review is an argument nobody needs",
    ),
    Gate(
        name="types",
        command=("mypy",),
        why="a measurement passed as the wrong unit is a type error here",
    ),
    Gate(
        name="tests",
        command=("pytest",),
        why="the suite, which is the only thing that says the rules are right",
    ),
    Gate(
        name="migrations",
        command=("alembic", "upgrade", "head"),
        why="a migration that does not apply is found at deploy time otherwise",
    ),
    Gate(
        name="rollback",
        command=("alembic", "downgrade", "base"),
        why="a migration nobody can undo is a migration nobody can deploy",
    ),
)


def named(name: str) -> Gate:
    """One gate by name, refusing one this repository does not have."""
    for gate in GATES:
        if gate.name == name:
            return gate
    every = ", ".join(one.name for one in GATES)
    raise BadReading(f"{name!r} is not a gate, it is one of {every}")


def names() -> tuple[str, ...]:
    return tuple(one.name for one in GATES)


def run(gate: Gate, where: Path | None = None) -> int:
    """Run one gate and hand back what it exited with."""
    return subprocess.run(gate.command, cwd=where, check=False).returncode


def run_all(where: Path | None = None) -> tuple[tuple[Gate, int], ...]:
    """Run every gate in order and keep what each one exited with.

    Every gate runs even after one fails. A run that stopped at the first
    failure would mean four rounds of push and wait to find four problems.
    """
    return tuple((gate, run(gate, where)) for gate in GATES)


def failed(results: tuple[tuple[Gate, int], ...]) -> tuple[Gate, ...]:
    return tuple(gate for gate, code in results if code != 0)


def workflow_path(root: Path) -> Path:
    """Where the workflow lives, so a test can read it rather than guess."""
    return root / ".github" / "workflows" / "ci.yml"


def gates_named_in(text: str) -> frozenset[str]:
    """Which gates a workflow file actually names, read off its own text."""
    return frozenset(one for one in names() if f"name: {one}" in text)
