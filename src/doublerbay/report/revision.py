"""What a new revision of the manual does to the cards already on the register.

A revision lands and the desk has a problem nobody schedules time for: every
open card was dispositioned against the old book, and some number of them
would be dispositioned differently now. Nothing tells anybody which ones. The
cards do not change, so no report moves, and the first sign of it is an audit
asking why a card is being flown against a limit that was withdrawn.

So this reassesses every open card twice, once as of a day before the revision
and once as of a day after it, and lists the ones where the answer moved. The
list is usually short, which is the point: an engineer can work through nine
cards in an afternoon and cannot work through four hundred.

It moves in both directions on purpose. A revision that relaxes a limit turns
somebody's grounded aeroplane back into a flying one, and that is worth an
afternoon too.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from doublerbay.assess.assessor import assess
from doublerbay.assess.verdict import Disposition
from doublerbay.errors import BadReading, DoublerbayError
from doublerbay.fleet.catalogue import TypeCatalogue
from doublerbay.ids import FindingId, Serial
from doublerbay.manual.book import Book
from doublerbay.store.context import context_for
from doublerbay.store.unit import UnitOfWork


@dataclass(frozen=True, slots=True)
class Moved:
    """One card whose answer is different under the new book."""

    reference: FindingId
    serial: Serial
    was: Disposition
    now: Disposition

    @property
    def got_worse(self) -> bool:
        return self.now.rank > self.was.rank

    @property
    def got_better(self) -> bool:
        return self.now.rank < self.was.rank

    def __str__(self) -> str:
        way = "worse" if self.got_worse else "better"
        return f"{self.reference} on {self.serial}: {self.was.value} to {self.now.value} ({way})"


@dataclass(frozen=True, slots=True)
class Sweep:
    """What one revision did to the cards already open."""

    before: date
    after: date
    looked_at: int
    moved: tuple[Moved, ...]
    unreadable: tuple[FindingId, ...]

    @property
    def worse(self) -> tuple[Moved, ...]:
        return tuple(one for one in self.moved if one.got_worse)

    @property
    def better(self) -> tuple[Moved, ...]:
        return tuple(one for one in self.moved if one.got_better)

    @property
    def is_quiet(self) -> bool:
        return not self.moved and not self.unreadable

    def __str__(self) -> str:
        if self.is_quiet:
            return f"{self.looked_at} cards looked at, nothing moved"
        parts = []
        if self.worse:
            parts.append(f"{len(self.worse)} worse")
        if self.better:
            parts.append(f"{len(self.better)} better")
        if self.unreadable:
            parts.append(f"{len(self.unreadable)} could not be assessed")
        return f"{self.looked_at} cards looked at, {', '.join(parts)}"


def _answer(unit: UnitOfWork, book: Book, catalogue: TypeCatalogue, finding, on: date):
    """What the book makes of one card on one day, or nothing if it cannot say."""
    try:
        tail = context_for(unit, catalogue, finding.serial, on)
        return assess(book, tail, finding).disposition
    except DoublerbayError:
        return None


def sweep(
    unit: UnitOfWork,
    book: Book,
    catalogue: TypeCatalogue,
    before: date,
    after: date,
) -> Sweep:
    """Reassess every open card on both sides of a revision and list what moved."""
    if after <= before:
        raise BadReading("the day after a revision comes after the day before it")

    moved: list[Moved] = []
    unreadable: list[FindingId] = []
    looked_at = 0
    for aircraft in unit.aircraft.in_service_on(after):
        for finding in unit.findings.open_on(aircraft.serial):
            looked_at += 1
            was = _answer(unit, book, catalogue, finding, before)
            now = _answer(unit, book, catalogue, finding, after)
            if was is None or now is None:
                unreadable.append(finding.reference)
                continue
            if was is not now:
                moved.append(Moved(finding.reference, finding.serial, was, now))

    moved.sort(key=lambda one: (-one.now.rank, str(one.reference)))
    return Sweep(
        before=before,
        after=after,
        looked_at=looked_at,
        moved=tuple(moved),
        unreadable=tuple(sorted(unreadable, key=str)),
    )


def worse_on(sweep_of: Sweep, serial: Serial) -> tuple[Moved, ...]:
    """The cards on one tail that the revision made worse."""
    return tuple(one for one in sweep_of.worse if one.serial == serial)
