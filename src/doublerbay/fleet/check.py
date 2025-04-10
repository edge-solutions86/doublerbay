"""Checks, and how the damage came to be found in the first place.

Where a finding comes from changes what can be believed about it. A walkaround
is a man on the ground looking up at a fuselage in the rain, and what he can
see is a dent big enough to throw a shadow. A hangar check has the aeroplane on
stands with the fairings off and a light held against the skin at an angle, and
that will find a scratch the walkaround would never have shown.

That matters twice. It decides whether a finding is a new damage or one that
has been there for years and was only now looked at properly, and it decides
what a repeat inspection can be written against, because an inspection is only
worth setting if the check it is set on could actually find the thing.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from enum import Enum

from doublerbay.errors import BadReading
from doublerbay.ids import Serial
from doublerbay.measure.interval import DueAt, Interval
from doublerbay.measure.usage import Usage


class CheckKind(Enum):
    """What the aeroplane was in when the damage was found."""

    WALKAROUND = "walkaround"
    TRANSIT = "transit"
    LINE = "line check"
    HANGAR = "hangar check"
    HEAVY = "heavy check"
    NDT_SWEEP = "ndt sweep"

    @property
    def access(self) -> int:
        """How much of the aeroplane is open, nought to four."""
        return _ACCESS[self]

    @property
    def is_scheduled(self) -> bool:
        return self is not CheckKind.WALKAROUND

    @property
    def can_find_fine_damage(self) -> bool:
        """Whether a scratch or a hairline crack would be seen at this level."""
        return self.access >= _ACCESS[CheckKind.HANGAR]

    def opens_more_than(self, other: CheckKind) -> bool:
        return self.access > other.access

    def __str__(self) -> str:
        return self.value


_ACCESS = {
    CheckKind.WALKAROUND: 0,
    CheckKind.TRANSIT: 1,
    CheckKind.LINE: 2,
    CheckKind.HANGAR: 3,
    CheckKind.HEAVY: 4,
    CheckKind.NDT_SWEEP: 4,
}


class DiscoverySource(Enum):
    """Who or what brought the damage to the desk."""

    WALKAROUND = "walkaround"
    SCHEDULED_CHECK = "scheduled check"
    CREW_REPORT = "crew report"
    GROUND_HANDLING = "ground handling report"
    LIGHTNING_REPORT = "lightning strike report"
    NDT = "non destructive test"

    @property
    def is_reported_by_a_person(self) -> bool:
        return self in (
            DiscoverySource.WALKAROUND,
            DiscoverySource.CREW_REPORT,
            DiscoverySource.GROUND_HANDLING,
            DiscoverySource.LIGHTNING_REPORT,
        )

    @property
    def carries_an_event(self) -> bool:
        """Sources that come with something that happened, and a time it happened at."""
        return self in (DiscoverySource.GROUND_HANDLING, DiscoverySource.LIGHTNING_REPORT)

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True, slots=True)
class CheckEvent:
    """One check on one tail, with the counters as they read at the time."""

    serial: Serial
    kind: CheckKind
    started: date
    finished: date
    at_usage: Usage

    def __post_init__(self) -> None:
        if self.finished < self.started:
            raise BadReading(f"{self.serial}: a check does not finish before it starts")

    @property
    def days(self) -> int:
        return (self.finished - self.started).days + 1

    def covers(self, day: date) -> bool:
        return self.started <= day <= self.finished

    def __str__(self) -> str:
        return f"{self.serial} {self.kind} {self.started.isoformat()}"


@dataclass(frozen=True, slots=True)
class CheckProgramme:
    """How often each kind of check comes round on this fleet."""

    intervals: dict[CheckKind, Interval]

    def __post_init__(self) -> None:
        for kind in (CheckKind.LINE, CheckKind.HANGAR, CheckKind.HEAVY):
            if kind not in self.intervals:
                raise BadReading(f"a programme sets an interval for the {kind}")

    def interval_for(self, kind: CheckKind) -> Interval:
        try:
            return self.intervals[kind]
        except KeyError:
            raise BadReading(f"this programme has no {kind} on it") from None

    def next_after(self, event: CheckEvent) -> DueAt:
        """When the same kind of check comes round again after this one."""
        return self.interval_for(event.kind).due_from(event.at_usage, event.finished)

    def deepest_by(self, due: DueAt, flown: Usage, on: date) -> CheckKind | None:
        """The most opened up check that is already due, which is what a finding rides on."""
        candidates = [kind for kind in self.intervals if due.is_due(flown, on)]
        if not candidates:
            return None
        return max(candidates, key=lambda kind: kind.access)

    def __str__(self) -> str:
        return ", ".join(
            f"{kind}: {interval}"
            for kind, interval in sorted(self.intervals.items(), key=lambda pair: pair[0].access)
        )
