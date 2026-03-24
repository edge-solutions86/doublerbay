"""One card's whole story, gathered from every register that touched it.

A damage card leaves a trail in five places. The card itself carries its
revisions. The disposition carries the moves. The deferral register knows what
it was flying against. The order register knows whether it went out to the
manufacturer. The repair register knows what finally answered it.

Nobody can hold five queries in their head at half past six, and the question
they are actually asking is always the same one: what happened to this, in
order. So this assembles the five into one list, sorted by the day each thing
happened, and says which register each line came off.

Ties are broken by the register rather than left to chance. A card raised and
dispositioned on the same day should read raised first, because it was, and a
sort that put them the other way round would have somebody reading the story
backwards.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from doublerbay.errors import DoublerbayError
from doublerbay.ids import FindingId, Serial
from doublerbay.store.unit import UnitOfWork

# Which register a line came off, and the order they read in on one day.
ORDER = ("card", "disposition", "deferral", "engineering", "repair")


@dataclass(frozen=True, slots=True)
class Entry:
    """One thing that happened to one card."""

    on: date
    register: str
    what: str

    @property
    def rank(self) -> int:
        return ORDER.index(self.register) if self.register in ORDER else len(ORDER)

    def __str__(self) -> str:
        return f"{self.on.isoformat()} {self.register}: {self.what}"


@dataclass(frozen=True, slots=True)
class Story:
    """Everything that happened to one card, oldest first."""

    reference: FindingId
    serial: Serial
    entries: tuple[Entry, ...]

    @property
    def opened(self) -> date:
        return self.entries[0].on

    @property
    def latest(self) -> date:
        return self.entries[-1].on

    @property
    def days_open(self) -> int:
        return (self.latest - self.opened).days

    def from_register(self, register: str) -> tuple[Entry, ...]:
        return tuple(one for one in self.entries if one.register == register)

    def __str__(self) -> str:
        return (
            f"{self.reference} on {self.serial}: "
            f"{len(self.entries)} entries over {self.days_open} days"
        )


def _card_entries(unit: UnitOfWork, reference: FindingId) -> list[Entry]:
    """One line per revision, saying what was measured."""
    entries = []
    for one in unit.findings.history(reference):
        what = "raised" if one.revision == 1 else f"measured again, revision {one.revision}"
        entries.append(Entry(one.found_on, "card", f"{what}, {one.geometry}"))
        if one.superseded_by is not None and one.revision == max(
            other.revision for other in unit.findings.history(reference)
        ):
            entries.append(Entry(one.found_on, "card", f"superseded by {one.superseded_by}"))
    return entries


def _disposition_entries(unit: UnitOfWork, reference: FindingId) -> list[Entry]:
    """One line per move, with who made it."""
    try:
        record = unit.dispositions.get(reference)
    except DoublerbayError:
        return []
    return [
        Entry(move.on, "disposition", f"{move.was} to {move.now} by {move.by}")
        for move in record.history
    ]


def _deferral_entries(unit: UnitOfWork, reference: FindingId) -> list[Entry]:
    """The live deferral, if there is one, and what it is due against."""
    try:
        deferral = unit.deferrals.get(reference)
    except DoublerbayError:
        return []
    return [
        Entry(
            deferral.granted,
            "deferral",
            f"granted by {deferral.by} to {deferral.due}, {deferral.re_inspect_with}",
        )
    ]


def _order_entries(unit: UnitOfWork, reference: FindingId) -> list[Entry]:
    """A line for each order raised, and a second when it was answered."""
    entries = []
    for order in unit.orders.against(reference):
        entries.append(Entry(order.raised, "engineering", f"{order.reference} raised"))
        if order.sent is not None:
            entries.append(Entry(order.sent, "engineering", f"{order.reference} sent"))
        if order.answered is not None:
            entries.append(Entry(order.answered, "engineering", f"{order.reference} {order.state}"))
    return entries


def _repair_entries(unit: UnitOfWork, reference: FindingId) -> list[Entry]:
    """A line when a repair went on, and another when it came off."""
    entries = []
    for repair in unit.repairs.against(reference):
        entries.append(
            Entry(repair.embodied, "repair", f"{repair.reference} fitted, {repair.scheme.category}")
        )
        if repair.removed is not None:
            entries.append(Entry(repair.removed, "repair", f"{repair.reference} removed"))
    return entries


def story_of(unit: UnitOfWork, reference: FindingId) -> Story:
    """Assemble one card's whole story, oldest first."""
    finding = unit.findings.latest(reference)
    entries = (
        _card_entries(unit, reference)
        + _disposition_entries(unit, reference)
        + _deferral_entries(unit, reference)
        + _order_entries(unit, reference)
        + _repair_entries(unit, reference)
    )
    entries.sort(key=lambda one: (one.on, one.rank, one.what))
    return Story(reference=reference, serial=finding.serial, entries=tuple(entries))
