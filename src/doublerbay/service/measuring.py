"""Measuring a damage again, and what the new numbers oblige the desk to do.

A re measurement is not an edit. The old numbers stay, because the whole
argument for leaving a crack alone rests on how fast it was growing, and that
argument disappears the moment somebody overwrites the first reading. So every
look adds a revision and nothing is ever replaced.

What the new numbers oblige is the part worth writing down. A damage that has
grown past what it was deferred against is a damage the deferral no longer
covers, whatever the limit says, and the desk has to know that on the day the
mechanic writes the card rather than at the next audit. So a re measurement
re assesses, compares the two answers, and says out loud when the answer got
worse.

It does not act on that by itself. Tightening a deferral or grounding an
aeroplane is an engineer's decision, and a service that made it automatically
would be a service engineers learn to work around.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from doublerbay.assess.assessor import assess
from doublerbay.assess.verdict import Disposition
from doublerbay.damage.finding import Finding, Geometry
from doublerbay.errors import BadReading, DoublerbayError
from doublerbay.fleet.catalogue import TypeCatalogue
from doublerbay.ids import FindingId
from doublerbay.manual.book import Book
from doublerbay.measure.usage import Usage
from doublerbay.store.context import context_for
from doublerbay.store.unit import UnitOfWork


@dataclass(frozen=True, slots=True)
class ReMeasured:
    """One card looked at again, and how the answer moved."""

    finding: Finding
    was: Disposition
    now: Disposition
    still_deferred: bool

    @property
    def revision(self) -> int:
        return self.finding.revision

    @property
    def got_worse(self) -> bool:
        return self.now.rank > self.was.rank

    @property
    def outgrew_its_deferral(self) -> bool:
        """Whether the card is still flying against a limit it no longer fits."""
        return self.still_deferred and self.got_worse

    def __str__(self) -> str:
        moved = f"{self.was.value} to {self.now.value}" if self.got_worse else str(self.now.value)
        return f"{self.finding.reference} revision {self.revision}, {moved}"


def re_measure(
    unit: UnitOfWork,
    book: Book,
    catalogue: TypeCatalogue,
    reference: FindingId,
    geometry: Geometry,
    on: date,
    flown: Usage | None = None,
) -> ReMeasured:
    """Record a new set of measurements and say how the disposition moved."""
    current = unit.findings.latest(reference)
    if on < current.found_on:
        raise BadReading(f"{reference} cannot be measured before it was found")
    if current.superseded_by is not None:
        raise BadReading(f"{reference} was superseded, measure {current.superseded_by} instead")

    before = _disposition(unit, book, catalogue, current, current.found_on)
    at = flown if flown is not None else unit.aircraft.log_for(current.serial).at(on)
    updated = current.re_measured(geometry, on, at)
    unit.findings.record_revision(updated)
    after = _disposition(unit, book, catalogue, updated, on)
    unit.flush()
    return ReMeasured(
        finding=updated,
        was=before,
        now=after,
        still_deferred=_is_deferred(unit, reference),
    )


def _disposition(
    unit: UnitOfWork, book: Book, catalogue: TypeCatalogue, finding: Finding, on: date
) -> Disposition:
    """What the book makes of this card on a day."""
    tail = context_for(unit, catalogue, finding.serial, on)
    return assess(book, tail, finding).disposition


def _is_deferred(unit: UnitOfWork, reference: FindingId) -> bool:
    try:
        unit.deferrals.get(reference)
    except DoublerbayError:
        return False
    return True


def history_of(unit: UnitOfWork, reference: FindingId) -> tuple[Finding, ...]:
    """Every revision of one card, oldest first."""
    return unit.findings.history(reference)


def grew_between(unit: UnitOfWork, reference: FindingId) -> bool:
    """Whether the worst measurement on the card is not the first one.

    Deliberately crude. It answers whether anything moved at all, which is the
    question that decides if the growth report is worth opening. How much it
    moved depends on the kind and belongs with the kind.
    """
    revisions = history_of(unit, reference)
    if len(revisions) < 2:
        return False
    first = revisions[0].geometry
    return any(one.geometry != first for one in revisions[1:])
