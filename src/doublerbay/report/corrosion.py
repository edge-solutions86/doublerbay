"""The corrosion programme report, which is the one that goes outside.

This is not a count of corrosion. It is an argument that the programme is
working, and the number that carries the argument is the proportion of
findings above level one. A fleet with two hundred level one findings and
nothing above is a programme doing its job. A fleet with four findings, three
of them level two, is a programme that has stopped.

The awkward part is the denominator. Counting level twos alone makes a small
fleet look bad and a large one look fine, so the rate has to be per panel
inspected rather than per finding, and this desk does not hold the inspection
count. So the report carries the raw counts and the ratio it can honestly
compute, and it says which denominator it used rather than picking the
flattering one.

Repeat locations are the other half. A panel that has been graded three times
in five years is telling the desk something no single grading does, and it is
the input the programme review actually acts on.
"""

from __future__ import annotations

from collections import Counter
from dataclasses import dataclass
from datetime import date
from decimal import ROUND_HALF_UP, Decimal

from doublerbay.errors import BadReading
from doublerbay.ids import PanelId, Serial
from doublerbay.tolerance.corrosion import CorrosionEntry, CorrosionLevel

# A panel graded this many times is worth a look at the programme, not the panel.
REPEAT_THRESHOLD = 3
PLACES = Decimal("0.001")


@dataclass(frozen=True, slots=True)
class RepeatLocation:
    """A panel that has been graded more than once, and how often."""

    part: PanelId
    times: int
    first: date
    latest: date

    @property
    def is_persistent(self) -> bool:
        return self.times >= REPEAT_THRESHOLD

    @property
    def years_between(self) -> Decimal:
        days = Decimal((self.latest - self.first).days)
        return (days / Decimal(365)).quantize(PLACES, rounding=ROUND_HALF_UP)

    def __str__(self) -> str:
        return f"{self.part} graded {self.times} times over {self.years_between} years"


@dataclass(frozen=True, slots=True)
class ProgrammeReport:
    """One tail's corrosion programme picture over a window."""

    serial: Serial
    since: date
    until: date
    by_level: tuple[tuple[CorrosionLevel, int], ...]
    repeats: tuple[RepeatLocation, ...]

    @property
    def total(self) -> int:
        return sum(count for _, count in self.by_level)

    @property
    def above_level_one(self) -> int:
        return sum(count for level, count in self.by_level if level is not CorrosionLevel.ONE)

    @property
    def is_coping(self) -> bool:
        """Whether the programme is holding, which is nothing above level one."""
        return self.above_level_one == 0

    @property
    def escalation_rate(self) -> Decimal:
        """The share of findings above level one, over findings as the denominator."""
        if self.total == 0:
            return Decimal(0)
        return (Decimal(self.above_level_one) / Decimal(self.total)).quantize(
            PLACES, rounding=ROUND_HALF_UP
        )

    @property
    def denominator(self) -> str:
        """What the rate is over, said out loud rather than assumed."""
        return "findings graded, not panels inspected"

    @property
    def persistent(self) -> tuple[RepeatLocation, ...]:
        return tuple(one for one in self.repeats if one.is_persistent)

    def count_at(self, level: CorrosionLevel) -> int:
        return dict(self.by_level).get(level, 0)

    def __str__(self) -> str:
        state = "coping" if self.is_coping else f"{self.above_level_one} above level one"
        return f"{self.serial}: {self.total} graded, {state}"


def repeats_in(entries: tuple[CorrosionEntry, ...]) -> tuple[RepeatLocation, ...]:
    """Panels graded more than once, most often first."""
    seen: dict[PanelId, list[date]] = {}
    for one in entries:
        seen.setdefault(one.part, []).append(one.found_on)
    found = [
        RepeatLocation(part=part, times=len(days), first=min(days), latest=max(days))
        for part, days in seen.items()
        if len(days) > 1
    ]
    found.sort(key=lambda one: (-one.times, str(one.part)))
    return tuple(found)


def programme_report(
    serial: Serial, entries: tuple[CorrosionEntry, ...], since: date, until: date
) -> ProgrammeReport:
    """Build one tail's programme picture over a window."""
    if until < since:
        raise BadReading("a reporting window ends after it starts")
    inside = tuple(one for one in entries if since <= one.found_on <= until)
    counted = Counter(one.level for one in inside)
    by_level = tuple(sorted(counted.items(), key=lambda pair: pair[0].value))
    return ProgrammeReport(
        serial=serial,
        since=since,
        until=until,
        by_level=by_level,
        repeats=repeats_in(inside),
    )
