"""Taking a day of counter readings off the maintenance system, in bulk.

Counters arrive as a file once a night, one line per tail, and the file is
never entirely clean. A tail was sold last week and is still on the export. A
line has a blank hours column because a technician did not close the sector.
One reading runs a counter backwards because the replacement flight data unit
started from zero.

The wrong shape for this is all or nothing. A file of forty tails refused
because one line is wrong means thirty nine aeroplanes with stale counters,
and the desk works around it by editing the file, which is worse. So each line
is taken on its own and the result says exactly which ones did not land and
why.

The right shape is also not silence. A line that was skipped has to appear in
the answer with its reason, because a skipped line is a counter that will be
wrong tomorrow too.
"""

from __future__ import annotations

import csv
from dataclasses import dataclass
from datetime import date
from decimal import Decimal, InvalidOperation
from io import StringIO

from doublerbay.errors import BadReading, DoublerbayError
from doublerbay.fleet.utilisation import Snapshot
from doublerbay.ids import Serial
from doublerbay.measure.usage import Cycles, Hours, Usage
from doublerbay.store.unit import UnitOfWork

COLUMNS = ("serial", "taken", "cycles", "hours")


@dataclass(frozen=True, slots=True)
class Line:
    """One line off the file, as it was read."""

    at: int
    serial: str
    taken: str
    cycles: str
    hours: str

    def __str__(self) -> str:
        return f"line {self.at}, {self.serial} on {self.taken}"


@dataclass(frozen=True, slots=True)
class Refused:
    """A line that did not land, and what stopped it."""

    line: Line
    why: str

    def __str__(self) -> str:
        return f"{self.line}: {self.why}"


@dataclass(frozen=True, slots=True)
class Taken:
    """What one file did."""

    posted: tuple[Serial, ...]
    refused: tuple[Refused, ...]

    @property
    def read(self) -> int:
        return len(self.posted) + len(self.refused)

    @property
    def is_clean(self) -> bool:
        return not self.refused

    def __str__(self) -> str:
        if self.is_clean:
            return f"{len(self.posted)} readings posted"
        return f"{len(self.posted)} readings posted, {len(self.refused)} refused"


def lines_from(text: str) -> tuple[Line, ...]:
    """Read the file, refusing one whose header is not the one this desk sends."""
    reader = csv.DictReader(StringIO(text))
    header = tuple(reader.fieldnames or ())
    missing = [one for one in COLUMNS if one not in header]
    if missing:
        raise BadReading(f"the file is missing the {', '.join(missing)} column")
    return tuple(
        Line(
            at=at,
            serial=(row.get("serial") or "").strip(),
            taken=(row.get("taken") or "").strip(),
            cycles=(row.get("cycles") or "").strip(),
            hours=(row.get("hours") or "").strip(),
        )
        for at, row in enumerate(reader, start=2)
    )


def snapshot_from(line: Line) -> Snapshot:
    """Turn one line into a reading, refusing anything unreadable by name."""
    if not line.serial:
        raise BadReading("no serial on the line")
    try:
        taken = date.fromisoformat(line.taken)
    except ValueError:
        raise BadReading(f"{line.taken!r} is not a date") from None
    try:
        cycles = Cycles(int(line.cycles))
    except ValueError:
        raise BadReading(f"{line.cycles!r} is not a count of cycles") from None
    try:
        hours = Hours.of_hours(Decimal(line.hours))
    except (InvalidOperation, ValueError):
        raise BadReading(f"{line.hours!r} is not a count of hours") from None
    return Snapshot(taken, Usage(cycles, hours))


def post_all(unit: UnitOfWork, text: str) -> Taken:
    """Post every reading a file carries, keeping the ones that will not land.

    Each line gets its own savepoint. Without one, a refusal partway through
    leaves the session in a state where nothing else can be written, and the
    thirty nine good lines after it would be lost to the one bad one.
    """
    posted: list[Serial] = []
    refused: list[Refused] = []
    for line in lines_from(text):
        mark = unit.session.begin_nested()
        try:
            snapshot = snapshot_from(line)
            serial = Serial(line.serial)
            unit.aircraft.post_reading(serial, snapshot)
            unit.flush()
        except DoublerbayError as stopped:
            mark.rollback()
            refused.append(Refused(line, str(stopped)))
        else:
            mark.commit()
            posted.append(serial)
    return Taken(posted=tuple(posted), refused=tuple(refused))
