"""Every damage report the desk holds, with every revision of each of them.

A finding is not a row that gets updated. It is a card that gets re measured,
and the earlier measurement is the thing an engineer needs when the question is
how fast it is growing. So the register keeps every revision and hands back the
latest one unless somebody asks for the history.

Superseding is separate from re measuring and the two get confused. A re
measurement is the same damage looked at again. A supersession is this record
being closed because another record now describes the damage, which happens
when two cards turn out to be the same dent found twice, and it is the only way
a finding leaves the open list without being repaired.
"""

from __future__ import annotations

from collections.abc import Iterator
from datetime import date

from doublerbay.damage.finding import Finding, Geometry
from doublerbay.damage.kind import DamageKind
from doublerbay.errors import BadReading, NotOnFile
from doublerbay.ids import FindingId, Serial
from doublerbay.measure.usage import Usage


class DamageRegister:
    """The dent map, kept as a card per damage and a revision per look at it."""

    def __init__(self) -> None:
        self._cards: dict[FindingId, list[Finding]] = {}

    def raise_finding(self, finding: Finding) -> None:
        if finding.reference in self._cards:
            raise BadReading(f"{finding.reference} has already been raised")
        if finding.revision != 1:
            raise BadReading(f"{finding.reference} is raised at revision one")
        self._cards[finding.reference] = [finding]

    def latest(self, reference: FindingId) -> Finding:
        return self._revisions(reference)[-1]

    def history(self, reference: FindingId) -> tuple[Finding, ...]:
        """Every revision of one card, oldest first."""
        return tuple(self._revisions(reference))

    def re_measure(
        self, reference: FindingId, geometry: Geometry, on: date, at_usage: Usage
    ) -> Finding:
        current = self.latest(reference)
        if not current.is_open:
            raise BadReading(f"{reference} has been superseded and is not re measured")
        updated = current.re_measured(geometry, on, at_usage)
        self._cards[reference].append(updated)
        return updated

    def supersede(self, reference: FindingId, by: FindingId) -> Finding:
        if by not in self._cards:
            raise NotOnFile(f"{by} is not a finding on this register")
        current = self.latest(reference)
        if not current.is_open:
            raise BadReading(f"{reference} has already been superseded")
        closed = current.superseded(by)
        self._cards[reference].append(closed)
        return closed

    def on_tail(self, serial: Serial) -> tuple[Finding, ...]:
        return tuple(card for card in self._latest_cards() if card.serial == serial)

    def open_on(self, serial: Serial) -> tuple[Finding, ...]:
        return tuple(card for card in self.on_tail(serial) if card.is_open)

    def of_kind(self, kind: DamageKind) -> tuple[Finding, ...]:
        return tuple(card for card in self._latest_cards() if card.kind is kind)

    def found_between(self, start: date, end: date) -> tuple[Finding, ...]:
        """Cards raised in a window, taken on the day the damage was first found."""
        if end < start:
            raise BadReading(f"{end.isoformat()} is before {start.isoformat()}")
        raised = [revisions[0] for revisions in self._cards.values()]
        return tuple(
            sorted(
                (card for card in raised if start <= card.found_on <= end),
                key=lambda card: (card.found_on, str(card.reference)),
            )
        )

    def next_reference(self, year: int) -> FindingId:
        """The next unused number in a year, which is how the shop raises a card."""
        used = [reference.sequence for reference in self._cards if reference.year == year]
        return FindingId(year, max(used, default=0) + 1)

    def _revisions(self, reference: FindingId) -> list[Finding]:
        try:
            return self._cards[reference]
        except KeyError:
            raise NotOnFile(f"{reference} is not a finding on this register") from None

    def _latest_cards(self) -> list[Finding]:
        return sorted(
            (revisions[-1] for revisions in self._cards.values()),
            key=lambda card: str(card.reference),
        )

    def __iter__(self) -> Iterator[Finding]:
        return iter(self._latest_cards())

    def __len__(self) -> int:
        return len(self._cards)

    def __contains__(self, reference: object) -> bool:
        return reference in self._cards

    def __str__(self) -> str:
        openings = sum(1 for card in self._latest_cards() if card.is_open)
        return f"{len(self._cards)} cards, {openings} open"
