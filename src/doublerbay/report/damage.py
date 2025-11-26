"""The open damage summary, which is the page the morning meeting looks at.

Counting damage is less obvious than it sounds. A card can be re measured four
times and it is still one damage. Two cards raised by two shifts on the same
dent are two cards and one damage, and the desk only finds that out when
somebody supersedes one of them. So the counts here are over cards the desk
still owns, and a superseded card is not one of them.

Grouping is by kind and by chapter rather than by panel. A panel level count
runs to four hundred rows on a narrowbody and nobody reads it. Chapter and
kind fit on a page and answer the question the meeting is actually asking,
which is whether anything has moved since last week.

Nothing here reaches for the manual. This is a count of what was found, not of
what it was assessed as. Mixing the two produces a report that changes when a
revision lands without a single new damage having been found, and then nobody
trusts any of it.
"""

from __future__ import annotations

from collections import Counter
from dataclasses import dataclass
from datetime import date, timedelta

from doublerbay.damage.finding import Finding
from doublerbay.damage.kind import DamageKind, DamageOrigin
from doublerbay.errors import BadReading
from doublerbay.grid.location import FuselagePoint
from doublerbay.ids import Serial

FUSELAGE_CHAPTER = 53
WING_CHAPTER = 57


def chapter_of(finding: Finding) -> int:
    """Which chapter a card files under, taken off where it is."""
    return FUSELAGE_CHAPTER if isinstance(finding.point, FuselagePoint) else WING_CHAPTER


@dataclass(frozen=True, slots=True)
class DamageCount:
    """Open cards on one tail, counted the three ways the meeting asks for."""

    serial: Serial
    on: date
    total: int
    by_kind: tuple[tuple[DamageKind, int], ...]
    by_chapter: tuple[tuple[int, int], ...]
    by_origin: tuple[tuple[DamageOrigin, int], ...]
    raised_recently: int

    @property
    def is_clean(self) -> bool:
        return self.total == 0

    @property
    def worst_kind(self) -> DamageKind | None:
        """The kind there is most of, or nothing if the tail is clean."""
        return self.by_kind[0][0] if self.by_kind else None

    def of_kind(self, kind: DamageKind) -> int:
        return dict(self.by_kind).get(kind, 0)

    def in_chapter(self, chapter: int) -> int:
        return dict(self.by_chapter).get(chapter, 0)

    def __str__(self) -> str:
        if self.is_clean:
            return f"{self.serial}: nothing open"
        return f"{self.serial}: {self.total} open, worst {self.worst_kind}"


def _ranked(counted: Counter, key=str) -> tuple:
    """Most first, and ties broken by name so two runs agree."""
    return tuple(sorted(counted.items(), key=lambda pair: (-pair[1], key(pair[0]))))


def count_open(
    serial: Serial, findings: tuple[Finding, ...], on: date, recent_days: int = 30
) -> DamageCount:
    """Count what is open on one tail as of a day."""
    if recent_days < 1:
        raise BadReading("a recent window is at least a day")
    since = on - timedelta(days=recent_days)
    live = tuple(one for one in findings if one.superseded_by is None and one.found_on <= on)
    return DamageCount(
        serial=serial,
        on=on,
        total=len(live),
        by_kind=_ranked(Counter(one.kind for one in live), key=lambda kind: kind.value),
        by_chapter=_ranked(Counter(chapter_of(one) for one in live), key=str),
        by_origin=_ranked(Counter(one.origin for one in live), key=lambda origin: origin.value),
        raised_recently=sum(1 for one in live if one.found_on >= since),
    )


@dataclass(frozen=True, slots=True)
class FleetDamage:
    """The same count across the register, tail by tail and in total."""

    on: date
    tails: tuple[DamageCount, ...]

    @property
    def total(self) -> int:
        return sum(one.total for one in self.tails)

    @property
    def clean_tails(self) -> tuple[Serial, ...]:
        return tuple(one.serial for one in self.tails if one.is_clean)

    @property
    def worst_tail(self) -> DamageCount | None:
        return self.tails[0] if self.tails and not self.tails[0].is_clean else None

    def for_tail(self, serial: Serial) -> DamageCount:
        for one in self.tails:
            if one.serial == serial:
                return one
        raise BadReading(f"{serial} is not in this report")

    def __str__(self) -> str:
        return f"{len(self.tails)} tails, {self.total} open on {self.on.isoformat()}"


def count_fleet(
    holdings: dict[Serial, tuple[Finding, ...]], on: date, recent_days: int = 30
) -> FleetDamage:
    """Count the whole register, worst tail first."""
    counts = [count_open(serial, found, on, recent_days) for serial, found in holdings.items()]
    counts.sort(key=lambda one: (-one.total, one.serial.number))
    return FleetDamage(on=on, tails=tuple(counts))
