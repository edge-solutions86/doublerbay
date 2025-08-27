"""Corrosion levels, which are a programme classification and not a measurement.

The corrosion prevention and control programme grades every finding into one of
three levels, and the grading is not about how bad it looks. Level one is
corrosion the programme is coping with. Level two is corrosion the programme is
not coping with, either because it is past the allowable limits or because it
has come back in the same place between two inspections. Level three is
corrosion the operator judges to be an airworthiness concern now.

The distinction that matters is between one and two, and the trap is that the
same finding is level one the first time and level two the second, with nothing
about the metal having changed. So the history of that location is an input to
the classification, and a desk that grades each finding on its own can never
produce a level two.

Level three is reportable. The authority hears about it and so does the type
certificate holder, and there is a clock on it.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, timedelta
from enum import Enum

from doublerbay.damage.corrosion import Corrosion
from doublerbay.errors import BadReading
from doublerbay.ids import PanelId

# How long the operator has to report a level three finding.
REPORT_WITHIN_DAYS = 3


class CorrosionLevel(Enum):
    """How the programme grades a corrosion finding."""

    ONE = 1
    TWO = 2
    THREE = 3

    @property
    def is_reportable(self) -> bool:
        """Level three goes to the authority and to the manufacturer."""
        return self is CorrosionLevel.THREE

    @property
    def needs_programme_review(self) -> bool:
        """Anything above level one says the programme is not coping."""
        return self is not CorrosionLevel.ONE

    @property
    def is_worse_than_expected(self) -> bool:
        return self.needs_programme_review

    def worse_than(self, other: CorrosionLevel) -> bool:
        return self.value > other.value

    def __str__(self) -> str:
        return f"level {self.value}"


@dataclass(frozen=True, slots=True)
class CorrosionContext:
    """What the programme knows about this place, which the metal does not say."""

    part: PanelId
    within_limits: bool
    found_here_before: bool
    urgent_airworthiness_concern: bool = False

    def __str__(self) -> str:
        history = "seen here before" if self.found_here_before else "first time here"
        return f"{self.part}, {history}"


def level_for(finding: Corrosion, context: CorrosionContext) -> CorrosionLevel:
    """Grade one corrosion finding against the programme rules."""
    if context.urgent_airworthiness_concern:
        return CorrosionLevel.THREE
    if context.within_limits and not finding.exfoliating:
        return CorrosionLevel.ONE
    if context.found_here_before:
        return CorrosionLevel.TWO
    return CorrosionLevel.ONE


@dataclass(frozen=True, slots=True)
class CorrosionEntry:
    """One graded finding, with what the grading commits the operator to."""

    part: PanelId
    level: CorrosionLevel
    found_on: date

    @property
    def must_be_reported(self) -> bool:
        return self.level.is_reportable

    @property
    def report_by(self) -> date | None:
        if not self.must_be_reported:
            return None
        return self.found_on + timedelta(days=REPORT_WITHIN_DAYS)

    def is_overdue(self, today: date) -> bool:
        by = self.report_by
        return by is not None and today > by

    def __str__(self) -> str:
        return f"{self.part} {self.level} on {self.found_on.isoformat()}"


def grade(finding: Corrosion, context: CorrosionContext, found_on: date) -> CorrosionEntry:
    """Grade a finding and hand back the programme entry it produces."""
    stain_outside_limits = (
        finding.is_surface_only
        and not context.within_limits
        and not context.urgent_airworthiness_concern
    )
    if stain_outside_limits:
        raise BadReading(f"{context.part}: a surface stain cannot be outside the allowable limits")
    return CorrosionEntry(context.part, level_for(finding, context), found_on)
