"""The manual as a document, with revisions that come into force on a date.

A structural repair manual is not a fixed thing. It is reissued every few
months, limits move, pages get withdrawn, and the revision that answers for a
damage is the one that was in force when the damage was dispositioned, not the
one on the shelf today. Reassessing an old finding against today's book is how
a repair that was correct becomes a finding in an audit.

So a revision has an issue date and an effective date and they are not the same
day. The book arrives, the operator has a window to work it into the
maintenance programme, and until the effective date the previous revision is
still the one that governs. Anything that reads a limit reads it as of a date.
"""

from __future__ import annotations

from bisect import bisect_right
from dataclasses import dataclass, field
from datetime import date

from doublerbay.errors import BadReading, NotOnFile
from doublerbay.fleet.model import Effectivity
from doublerbay.grid.ata import Chapter, Reference


@dataclass(frozen=True, slots=True)
class Revision:
    """One issue of the manual, and the day it starts to govern."""

    number: int
    issued: date
    effective: date

    def __post_init__(self) -> None:
        if self.number < 1:
            raise BadReading("revisions are numbered from one")
        if self.effective < self.issued:
            raise BadReading(f"revision {self.number} takes effect before it was issued")

    @property
    def lead_days(self) -> int:
        """How long the operator had to work it in before it started to govern."""
        return (self.effective - self.issued).days

    def in_force_on(self, day: date) -> bool:
        """Whether this revision had started by a day, ignoring anything later."""
        return day >= self.effective

    def __str__(self) -> str:
        return f"rev {self.number} of {self.effective.isoformat()}"


@dataclass(frozen=True, slots=True)
class Page:
    """One subject in the manual, at one revision, for one set of tails."""

    reference: Reference
    title: str
    revision: int
    effectivity: Effectivity = field(default_factory=Effectivity.all_aircraft)
    withdrawn_at: int | None = None

    def __post_init__(self) -> None:
        if not self.title.strip():
            raise BadReading(f"{self.reference} has a title")
        if self.revision < 1:
            raise BadReading(f"{self.reference} is issued at a revision")
        if self.withdrawn_at is not None and self.withdrawn_at <= self.revision:
            raise BadReading(f"{self.reference} cannot be withdrawn before it was issued")

    def is_live_at(self, revision: int) -> bool:
        """Whether the page is in the book at a given revision number."""
        if revision < self.revision:
            return False
        return self.withdrawn_at is None or revision < self.withdrawn_at

    def __str__(self) -> str:
        return f"{self.reference} {self.title}"


@dataclass(frozen=True, slots=True)
class Manual:
    """One chapter of the book, with its revision history."""

    chapter: Chapter
    revisions: tuple[Revision, ...]

    def __post_init__(self) -> None:
        if not self.revisions:
            raise BadReading(f"chapter {self.chapter} has at least one revision")
        numbers = [revision.number for revision in self.revisions]
        effective = [revision.effective for revision in self.revisions]
        if numbers != sorted(numbers) or len(set(numbers)) != len(numbers):
            raise BadReading("revisions are listed in order with no repeats")
        if effective != sorted(effective) or len(set(effective)) != len(effective):
            raise BadReading("revisions take effect in order with no two on one day")

    @property
    def latest(self) -> Revision:
        return self.revisions[-1]

    @property
    def first(self) -> Revision:
        return self.revisions[0]

    def in_force_on(self, day: date) -> Revision:
        """The revision that governed on a day, which is the last one to have started."""
        starts = [revision.effective for revision in self.revisions]
        index = bisect_right(starts, day) - 1
        if index < 0:
            raise NotOnFile(f"chapter {self.chapter} had no revision in force on {day.isoformat()}")
        return self.revisions[index]

    def numbered(self, number: int) -> Revision:
        for revision in self.revisions:
            if revision.number == number:
                return revision
        raise NotOnFile(f"chapter {self.chapter} has no revision {number}")

    def issued(self, revision: Revision) -> Manual:
        """The chapter with one more revision on it."""
        return Manual(self.chapter, (*self.revisions, revision))

    def __str__(self) -> str:
        return f"chapter {self.chapter}, {self.latest}"
