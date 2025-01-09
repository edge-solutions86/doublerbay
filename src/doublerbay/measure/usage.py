"""Flight cycles and flight hours, and why they are not one number.

A cycle is a pressurisation, which is what grows a crack in a fuselage skin. An
hour is time under load, which is what a wing cares about. Structural limits
come written against one or the other and often against both at once, so the
two are carried together and never collapsed.

They do not order. A tail can be ahead of another on cycles and behind it on
hours, which is exactly what a short haul aircraft looks like next to a long
haul one of the same age. Anything that wants to ask whether one usage has
reached another has to say which counter it means, or ask whether both have.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from functools import total_ordering

from doublerbay.errors import BadReading, WrongMeasure

MINUTES_PER_HOUR = 60


@total_ordering
class Cycles:
    """A count of pressurisations."""

    __slots__ = ("_count",)

    def __init__(self, count: int) -> None:
        if not isinstance(count, int) or isinstance(count, bool):
            raise WrongMeasure(f"cycles are whole, got {count!r}")
        if count < 0:
            raise BadReading("an aircraft cannot fly a negative cycle")
        self._count = count

    @property
    def count(self) -> int:
        return self._count

    def __add__(self, other: Cycles) -> Cycles:
        if not isinstance(other, Cycles):
            return NotImplemented
        return Cycles(self._count + other._count)

    def __sub__(self, other: Cycles) -> Cycles:
        if not isinstance(other, Cycles):
            return NotImplemented
        return Cycles(self._count - other._count)

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, Cycles):
            return NotImplemented
        return self._count == other._count

    def __lt__(self, other: Cycles) -> bool:
        if not isinstance(other, Cycles):
            return NotImplemented
        return self._count < other._count

    def __hash__(self) -> int:
        return hash(("Cycles", self._count))

    def __repr__(self) -> str:
        return f"Cycles({self._count})"

    def __str__(self) -> str:
        return f"{self._count} FC"


@total_ordering
class Hours:
    """Time under load, kept in whole minutes because the log book is."""

    __slots__ = ("_minutes",)

    def __init__(self, minutes: int) -> None:
        if not isinstance(minutes, int) or isinstance(minutes, bool):
            raise WrongMeasure(f"hours are held as whole minutes, got {minutes!r}")
        if minutes < 0:
            raise BadReading("an aircraft cannot fly a negative hour")
        self._minutes = minutes

    @classmethod
    def of_hours(cls, value: Decimal | int | str) -> Hours:
        scaled = Decimal(value) * MINUTES_PER_HOUR
        rounded = int(scaled.to_integral_value())
        if scaled != rounded:
            raise BadReading(f"{value} h is finer than a minute")
        return cls(rounded)

    @property
    def minutes(self) -> int:
        return self._minutes

    @property
    def hours(self) -> Decimal:
        return (Decimal(self._minutes) / MINUTES_PER_HOUR).quantize(Decimal("0.1"))

    def __add__(self, other: Hours) -> Hours:
        if not isinstance(other, Hours):
            return NotImplemented
        return Hours(self._minutes + other._minutes)

    def __sub__(self, other: Hours) -> Hours:
        if not isinstance(other, Hours):
            return NotImplemented
        return Hours(self._minutes - other._minutes)

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, Hours):
            return NotImplemented
        return self._minutes == other._minutes

    def __lt__(self, other: Hours) -> bool:
        if not isinstance(other, Hours):
            return NotImplemented
        return self._minutes < other._minutes

    def __hash__(self) -> int:
        return hash(("Hours", self._minutes))

    def __repr__(self) -> str:
        return f"Hours({self._minutes})"

    def __str__(self) -> str:
        return f"{self.hours} FH"


@dataclass(frozen=True, slots=True)
class Usage:
    """Both counters at one moment. Deliberately not orderable."""

    cycles: Cycles
    hours: Hours

    @classmethod
    def zero(cls) -> Usage:
        return cls(Cycles(0), Hours(0))

    def __add__(self, other: Usage) -> Usage:
        if not isinstance(other, Usage):
            return NotImplemented
        return Usage(self.cycles + other.cycles, self.hours + other.hours)

    def __sub__(self, other: Usage) -> Usage:
        if not isinstance(other, Usage):
            return NotImplemented
        return Usage(self.cycles - other.cycles, self.hours - other.hours)

    def reaches_either(self, target: Usage) -> bool:
        """True once either counter has caught the target, which is how limits fall due."""
        return self.cycles >= target.cycles or self.hours >= target.hours

    def reaches_both(self, target: Usage) -> bool:
        return self.cycles >= target.cycles and self.hours >= target.hours

    @property
    def average_leg(self) -> Decimal:
        """Hours per cycle, which is how the fleet tells its short haul tails apart."""
        if self.cycles.count == 0:
            raise ZeroDivisionError("no cycles flown, so there is no average leg")
        return (Decimal(self.hours.minutes) / MINUTES_PER_HOUR / self.cycles.count).quantize(
            Decimal("0.01")
        )

    def __str__(self) -> str:
        return f"{self.cycles} / {self.hours}"
