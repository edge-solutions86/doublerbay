"""Flying with the damage, and the limit that says for how long.

A deferral is a decision to leave damage on an aeroplane that is going to keep
flying, and it is only defensible with a limit on it. So many cycles, so many
hours, so many days, whichever comes first, and a re inspection at the end of
it. A deferral without a limit is not a deferral, it is a decision nobody
wrote down.

The limit is the tightest of three things and the third is the one people
forget. The book's own deferral allowance, whatever the engineer set on this
particular card, and the next scheduled check the aeroplane is going into,
because there is no sense deferring past an opportunity to fix it.

An expired deferral is not an error. It is an aeroplane flying with damage
outside its limit, which is a grounding item, and the sweep that finds them is
the most important report on the desk.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from doublerbay.errors import BadReading
from doublerbay.ids import FindingId
from doublerbay.measure.interval import DueAt, Interval
from doublerbay.measure.usage import Usage
from doublerbay.tolerance.method import InspectionMethod

# What the book allows without an engineer setting something tighter.
STANDARD_DEFERRAL = Interval.parse("500 FC / 750 FH / 180 DY")


@dataclass(frozen=True, slots=True)
class Deferral:
    """A decision to keep flying with a damage, and the limit on it."""

    against: FindingId
    granted: date
    at_usage: Usage
    limit: Interval
    re_inspect_with: InspectionMethod
    by: str
    reason: str = ""

    def __post_init__(self) -> None:
        if not self.by.strip():
            raise BadReading(f"{self.against}: a deferral is granted by somebody")

    @property
    def due(self) -> DueAt:
        return self.limit.due_from(self.at_usage, self.granted)

    def is_expired(self, flown: Usage, today: date) -> bool:
        return self.due.is_due(flown, today)

    def days_left(self, today: date) -> int | None:
        return self.due.remaining_days(today)

    def tightened_to(self, limit: Interval, by: str, reason: str = "") -> Deferral:
        """A shorter limit than the one granted, which an engineer may always set."""
        return Deferral(
            against=self.against,
            granted=self.granted,
            at_usage=self.at_usage,
            limit=limit,
            re_inspect_with=self.re_inspect_with,
            by=by,
            reason=reason or self.reason,
        )

    def __str__(self) -> str:
        return f"{self.against} deferred to {self.due}, {self.re_inspect_with}"


def limit_for(
    engineer_set: Interval | None = None,
    next_check: date | None = None,
    granted: date | None = None,
) -> Interval:
    """The tightest of the book, the engineer, and the next check the tail goes into."""
    limit = engineer_set if engineer_set is not None else STANDARD_DEFERRAL
    if next_check is None or granted is None:
        return limit
    days = (next_check - granted).days
    if days < 1:
        raise BadReading("a deferral to a check that has already started is not a deferral")
    if limit.days is not None and limit.days <= days:
        return limit
    return Interval(cycles=limit.cycles, hours=limit.hours, months=None, days=days)


def grant(
    against: FindingId,
    granted: date,
    at_usage: Usage,
    by: str,
    method: InspectionMethod = InspectionMethod.DVI,
    engineer_set: Interval | None = None,
    next_check: date | None = None,
    reason: str = "",
) -> Deferral:
    """Grant a deferral with the tightest limit the three sources allow."""
    return Deferral(
        against=against,
        granted=granted,
        at_usage=at_usage,
        limit=limit_for(engineer_set, next_check, granted),
        re_inspect_with=method,
        by=by,
        reason=reason,
    )


def expired(deferrals: tuple[Deferral, ...], flown: Usage, today: date) -> tuple[Deferral, ...]:
    """Every deferral the aeroplane has flown past, which is a grounding list."""
    return tuple(one for one in deferrals if one.is_expired(flown, today))
