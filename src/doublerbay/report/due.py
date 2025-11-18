"""What falls due, and how close it is, ordered so the worst is at the top.

Every limit on this desk is against three clocks at once: flight cycles,
flight hours and the calendar. A deferral granted for five hundred cycles on
an aeroplane doing four a day and one granted for a hundred and eighty days on
an aeroplane sitting in a hangar are the same limit written two ways, and the
list has to sort them against each other honestly.

So everything gets converted to days at the tail's own recent rate, and the
smallest answer wins. That number is a forecast and it is wrong the moment the
aeroplane flies differently, which is exactly why it is recomputed on every
read and never stored.

A limit already passed sorts above everything and carries a negative margin,
because the desk needs the overrun in days rather than a flat "expired". An
aeroplane three days past a deferral and one eleven months past are different
conversations with the authority.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from decimal import Decimal
from enum import Enum

from doublerbay.disposition.deferral import Deferral
from doublerbay.errors import BadReading
from doublerbay.fleet.forecast import Forecast
from doublerbay.ids import FindingId, Serial
from doublerbay.measure.interval import DueAt
from doublerbay.measure.usage import Usage

# How far ahead the desk plans. Past this the rate is not worth trusting.
HORIZON_DAYS = 3650


class Clock(Enum):
    """Which of the three limits is the one that actually bites."""

    CYCLES = "flight cycles"
    HOURS = "flight hours"
    CALENDAR = "calendar"

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True, slots=True)
class Margin:
    """How long is left against one limit, in days, and which clock said so."""

    days: int
    clock: Clock

    @property
    def is_overrun(self) -> bool:
        return self.days < 0

    @property
    def is_due_now(self) -> bool:
        return self.days <= 0

    def __str__(self) -> str:
        if self.is_overrun:
            return f"{abs(self.days)} days over on {self.clock}"
        return f"{self.days} days on {self.clock}"


def days_to(due: DueAt, flown: Usage, rate: Forecast, today: date) -> Margin:
    """The tightest of the three clocks, expressed in days at this tail's rate.

    A limit with no calendar term still gets a day count, because the rate
    turns cycles into days. That is the whole point: it is the only way a
    cycles limit and a calendar limit can be put in one sorted list.
    """
    candidates: list[Margin] = []
    if due.cycles is not None:
        left = Decimal(due.cycles.count - flown.cycles.count)
        candidates.append(Margin(_as_days(left, rate.cycles_a_day), Clock.CYCLES))
    if due.hours is not None:
        left = Decimal(due.hours.minutes - flown.hours.minutes) / 60
        candidates.append(Margin(_as_days(left, rate.hours_a_day), Clock.HOURS))
    if due.on is not None:
        candidates.append(Margin((due.on - today).days, Clock.CALENDAR))
    if not candidates:
        raise BadReading("a due point with no clocks on it cannot be counted down")
    return min(candidates, key=lambda one: one.days)


def _as_days(left: Decimal, a_day: Decimal) -> int:
    """Turn a margin in cycles or hours into days at a rate, rounding down.

    Rounding down is deliberate on both sides of zero. Ten cycles left at
    three a day is three days, not four, and ten cycles over at three a day is
    four days over, not three. The error always lands against the aeroplane.
    """
    if a_day <= 0:
        return HORIZON_DAYS if left >= 0 else 0
    days = left / a_day
    whole = int(days)
    if days < 0 and days != whole:
        whole -= 1
    return min(whole, HORIZON_DAYS)


@dataclass(frozen=True, slots=True)
class DueItem:
    """One thing falling due on one tail, with the margin worked out."""

    serial: Serial
    against: FindingId
    what: str
    due: DueAt
    margin: Margin

    @property
    def is_overrun(self) -> bool:
        return self.margin.is_overrun

    def __str__(self) -> str:
        return f"{self.serial} {self.against} {self.what}: {self.margin}"


def deferral_due(deferral: Deferral, serial: Serial, flown: Usage, rate: Forecast, today: date):
    """One deferral turned into a line on the due list."""
    return DueItem(
        serial=serial,
        against=deferral.against,
        what=f"deferral, re inspect {deferral.re_inspect_with}",
        due=deferral.due,
        margin=days_to(deferral.due, flown, rate, today),
    )


def due_list(
    deferrals: tuple[Deferral, ...],
    serial: Serial,
    flown: Usage,
    rate: Forecast,
    today: date,
    within_days: int | None = None,
) -> tuple[DueItem, ...]:
    """Everything due on one tail, worst first, optionally cut at a horizon."""
    items = [deferral_due(one, serial, flown, rate, today) for one in deferrals]
    if within_days is not None:
        if within_days < 0:
            raise BadReading("a horizon is a count of days ahead")
        items = [one for one in items if one.margin.days <= within_days]
    return tuple(sorted(items, key=lambda one: (one.margin.days, str(one.against))))


def overrun(items: tuple[DueItem, ...]) -> tuple[DueItem, ...]:
    """The ones already past their limit, which is the grounding list."""
    return tuple(one for one in items if one.is_overrun)
