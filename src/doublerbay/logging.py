"""What the service writes down about itself, and what it deliberately does not.

Two things get logged and nothing else does. Every refusal, because a refusal
is the service telling somebody no and the desk will eventually want to know
how often and to whom. And every write that changes what an aeroplane is
allowed to do, because those are the ones an audit asks about.

Reads are not logged. A read of the due list happens forty times a morning and
logging it buries the two lines that matter under two hundred that do not.

Nothing here writes a measurement, a serial or a key. The serial is the one
that looks harmless and is not: a log line naming the aeroplane and the
disposition is the finding, sitting in a file with weaker access rules than
the database it came out of. So a line names the reference and the kind of
thing that happened, and whoever needs the detail reads the record.
"""

from __future__ import annotations

import json
import logging
from dataclasses import dataclass
from datetime import UTC, datetime

NAME = "doublerbay"

# What a line can carry. Anything not on this list does not go in a log.
ALLOWED = frozenset({"event", "reference", "kind", "role", "outcome", "detail"})
# What must never go in one, even if somebody passes it.
NEVER = frozenset({"serial", "key", "holder", "measurements", "depth", "registration"})


@dataclass(frozen=True, slots=True)
class Line:
    """One thing worth writing down."""

    event: str
    reference: str | None = None
    kind: str | None = None
    role: str | None = None
    outcome: str | None = None
    detail: str | None = None

    def __post_init__(self) -> None:
        if not self.event.strip():
            raise ValueError("a log line says what happened")

    def fields(self) -> dict[str, str]:
        """Only the fields that carry something, in a fixed order."""
        held = {
            "event": self.event,
            "reference": self.reference,
            "kind": self.kind,
            "role": self.role,
            "outcome": self.outcome,
            "detail": self.detail,
        }
        return {name: value for name, value in held.items() if value}

    def __str__(self) -> str:
        return " ".join(f"{name}={value}" for name, value in self.fields().items())


def scrubbed(fields: dict[str, str]) -> dict[str, str]:
    """Drop anything this service will not put in a log file.

    A caller passing a serial is not doing anything malicious. They are
    debugging, and the line they add outlives the afternoon they added it in.
    """
    return {name: value for name, value in fields.items() if name in ALLOWED}


def as_json(line: Line, at: datetime | None = None) -> str:
    """One line as json, which is what a log shipper wants."""
    when = at if at is not None else datetime.now(UTC)
    return json.dumps({"at": when.isoformat(), **scrubbed(line.fields())}, sort_keys=False)


def logger() -> logging.Logger:
    """The one logger this package writes to."""
    return logging.getLogger(NAME)


def write(line: Line, at: datetime | None = None) -> None:
    """Write one line, at info because none of this is a problem by itself."""
    logger().info(as_json(line, at))


def refused(reference: str, kind: str, why: str, role: str | None = None) -> Line:
    """The line for a request the service said no to."""
    return Line(
        event="refused", reference=reference, kind=kind, role=role, outcome="no", detail=why
    )


def decided(reference: str, kind: str, role: str) -> Line:
    """The line for a write that changes what an aeroplane may do."""
    return Line(event="decided", reference=reference, kind=kind, role=role, outcome="yes")


def configure(level: int = logging.INFO) -> logging.Logger:
    """Attach one handler, once, without touching the root logger.

    Adding a handler to root would put this service's lines through whatever
    the host application has configured, and take that application's lines
    through this one. A named logger with propagate off keeps both.
    """
    held = logger()
    held.setLevel(level)
    held.propagate = False
    if not held.handlers:
        handler = logging.StreamHandler()
        handler.setFormatter(logging.Formatter("%(message)s"))
        held.addHandler(handler)
    return held
