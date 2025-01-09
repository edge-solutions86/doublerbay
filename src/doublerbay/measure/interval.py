"""Limits written against three clocks at once.

Nothing structural is limited by a single number. A repeat inspection reads
three thousand cycles or four thousand five hundred hours or twenty four
months, whichever comes first, and a deferral reads ten days or fifty cycles.
An interval carries whichever of the three clocks the manual set and stays
silent on the others.

Turning an interval into a date and two counter readings is the only place the
three clocks meet. After that a due list only ever asks whether any of them has
been reached, which keeps the whichever comes first rule in one function rather
than in every caller that wanted to know.
"""

from __future__ import annotations

import re
from calendar import monthrange
from dataclasses import dataclass
from datetime import date, timedelta
from decimal import Decimal

from doublerbay.errors import BadReading
from doublerbay.measure.usage import Cycles, Hours, Usage

_TERM = re.compile(r"^(\d+)\s*(FC|FH|MO|DY)$", re.IGNORECASE)


def add_months(start: date, months: int) -> date:
    """Calendar arithmetic that lands on the last day of a short month."""
    total = start.month - 1 + months
    year = start.year + total // 12
    month = total % 12 + 1
    day = min(start.day, monthrange(year, month)[1])
    return date(year, month, day)


@dataclass(frozen=True, slots=True)
class Interval:
    """How much flying, or how much time, a limit allows."""

    cycles: Cycles | None = None
    hours: Hours | None = None
    months: int | None = None
    days: int | None = None

    def __post_init__(self) -> None:
        clocks = (self.cycles, self.hours, self.months, self.days)
        if all(clock is None for clock in clocks):
            raise BadReading("an interval has to be against at least one clock")
        if self.cycles is not None and self.cycles.count < 1:
            raise BadReading("an interval in cycles counts at least one")
        if self.hours is not None and self.hours.minutes < 1:
            raise BadReading("an interval in hours counts at least one minute")
        if self.months is not None and self.months < 1:
            raise BadReading("an interval in months counts at least one")
        if self.days is not None and self.days < 1:
            raise BadReading("an interval in days counts at least one")

    @classmethod
    def parse(cls, text: str) -> Interval:
        """Read the form the manual prints, terms separated by a slash."""
        cycles: Cycles | None = None
        hours: Hours | None = None
        months: int | None = None
        days: int | None = None
        terms = [part.strip() for part in text.split("/") if part.strip()]
        if not terms:
            raise BadReading(f"{text!r} carries no interval")
        for term in terms:
            match = _TERM.match(term)
            if match is None:
                raise BadReading(f"{term!r} is not an interval term")
            count, unit = int(match.group(1)), match.group(2).upper()
            if unit == "FC":
                cycles = Cycles(count)
            elif unit == "FH":
                hours = Hours.of_hours(count)
            elif unit == "MO":
                months = count
            else:
                days = count
        return cls(cycles=cycles, hours=hours, months=months, days=days)

    def scaled(self, factor: Decimal | str) -> Interval:
        """An escalation or a cut, applied to every clock the interval names."""
        ratio = Decimal(factor)
        if ratio <= 0:
            raise BadReading("an interval cannot be scaled to nothing")
        return Interval(
            cycles=Cycles(int((self.cycles.count * ratio).to_integral_value()))
            if self.cycles is not None
            else None,
            hours=Hours(int((self.hours.minutes * ratio).to_integral_value()))
            if self.hours is not None
            else None,
            months=int((self.months * ratio).to_integral_value())
            if self.months is not None
            else None,
            days=int((self.days * ratio).to_integral_value()) if self.days is not None else None,
        )

    def due_from(self, flown: Usage, on: date) -> DueAt:
        """Where the limit lands, given where the tail is now."""
        by_date: date | None = None
        if self.months is not None:
            by_date = add_months(on, self.months)
        if self.days is not None:
            candidate = on + timedelta(days=self.days)
            by_date = candidate if by_date is None else min(by_date, candidate)
        return DueAt(
            cycles=flown.cycles + self.cycles if self.cycles is not None else None,
            hours=flown.hours + self.hours if self.hours is not None else None,
            on=by_date,
        )

    def __str__(self) -> str:
        parts = []
        if self.cycles is not None:
            parts.append(f"{self.cycles.count} FC")
        if self.hours is not None:
            parts.append(f"{self.hours.hours} FH")
        if self.months is not None:
            parts.append(f"{self.months} MO")
        if self.days is not None:
            parts.append(f"{self.days} DY")
        return " / ".join(parts)


@dataclass(frozen=True, slots=True)
class DueAt:
    """The three readings a limit falls due on, any of which is enough."""

    cycles: Cycles | None = None
    hours: Hours | None = None
    on: date | None = None

    def is_due(self, flown: Usage, today: date) -> bool:
        if self.cycles is not None and flown.cycles >= self.cycles:
            return True
        if self.hours is not None and flown.hours >= self.hours:
            return True
        return self.on is not None and today >= self.on

    def remaining_cycles(self, flown: Usage) -> Cycles | None:
        if self.cycles is None:
            return None
        if flown.cycles >= self.cycles:
            return Cycles(0)
        return self.cycles - flown.cycles

    def remaining_days(self, today: date) -> int | None:
        if self.on is None:
            return None
        return max((self.on - today).days, 0)

    def __str__(self) -> str:
        parts = []
        if self.cycles is not None:
            parts.append(f"{self.cycles.count} FC")
        if self.hours is not None:
            parts.append(f"{self.hours.hours} FH")
        if self.on is not None:
            parts.append(self.on.isoformat())
        return " or ".join(parts) if parts else "no limit"
