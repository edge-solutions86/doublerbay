"""Assessing a panel, which is not the same as assessing the cards on it one at a time.

Three of the book's rules only exist once every card on the panel is on the
table at once. Two damages inside a clear separation are one damage. A bay
holds so many and no more. A panel holds so many and no more. A desk working
card by card passes all three without ever seeing them.

The order matters and it is the order the book states. Cluster first, because
clustering changes how many damages there are, and a panel with eight cards on
it may be over the panel count at eight and inside it at five once three of
them have been taken together. Then count the bays, then count the panel.

What comes out is the per card answer raised where a panel rule bit it, with
the panel rule in that card's own trail. A card that was allowable on its own
and is not once its neighbours are counted should say so on its own face.
"""

from __future__ import annotations

from collections import Counter
from dataclasses import dataclass

from doublerbay.assess.assessor import Assessment, assess
from doublerbay.assess.verdict import Disposition, Reason
from doublerbay.damage.cluster import Cluster, Placed, clusters
from doublerbay.damage.finding import Finding
from doublerbay.fleet.catalogue import TailContext
from doublerbay.grid.location import FuselagePoint
from doublerbay.ids import FindingId, PanelId
from doublerbay.manual.book import Book
from doublerbay.manual.interaction import CountLimits


@dataclass(frozen=True, slots=True)
class PanelAssessment:
    """Every card on one panel, assessed together."""

    part: PanelId
    assessments: tuple[Assessment, ...]
    groups: tuple[Cluster, ...]

    @property
    def card_count(self) -> int:
        return len(self.assessments)

    @property
    def damage_count(self) -> int:
        """How many damages the book counts here, which is groups and not cards."""
        return len(self.groups)

    @property
    def disposition(self) -> Disposition:
        if not self.assessments:
            return Disposition.ALLOWABLE
        return max((one.disposition for one in self.assessments), key=lambda answer: answer.rank)

    def for_card(self, reference: FindingId) -> Assessment:
        for one in self.assessments:
            if one.finding.reference == reference:
                return one
        raise KeyError(reference)

    def __str__(self) -> str:
        return (
            f"{self.part}: {self.card_count} cards in {self.damage_count} groups, "
            f"{self.disposition}"
        )


def _bay_key(tail: TailContext, finding: Finding) -> str:
    point = finding.point
    if isinstance(point, FuselagePoint):
        return str(tail.barrel.bay_at(point))
    return str(tail.wing_box.grid_for(point.side).bay_of(point))


def _raise_with(one: Assessment, reason: Reason, to: Disposition) -> Assessment:
    """Put a panel rule into a card's own trail, and raise its answer if it bit."""
    verdict = one.verdict.with_reason(reason)
    if to.worse_than(verdict.disposition):
        verdict = verdict.raised_to(to)
    return Assessment(one.finding, one.placement, verdict, one.row)


def _cluster_reasons(
    answers: dict[FindingId, Assessment], groups: tuple[Cluster, ...], counts: CountLimits
) -> None:
    for group in groups:
        if group.is_single:
            continue
        detail = f"assessed with {group.size - 1} other damage inside the clear separation"
        over = counts.cluster_exceeded(group.size)
        for member in group.members:
            reference = member.finding.reference
            answers[reference] = _raise_with(
                answers[reference],
                Reason("group", detail, not over),
                Disposition.REPAIRABLE if over else Disposition.ALLOWABLE,
            )


def _bay_reasons(
    answers: dict[FindingId, Assessment],
    per_bay: Counter[str],
    bays: dict[FindingId, str],
    counts: CountLimits,
) -> None:
    if counts.per_bay is None:
        return
    for reference, bay in bays.items():
        found = per_bay[bay]
        over = counts.bay_exceeded(found)
        answers[reference] = _raise_with(
            answers[reference],
            Reason("bay count", f"{found} in {bay} against a limit of {counts.per_bay}", not over),
            Disposition.REPAIRABLE if over else Disposition.ALLOWABLE,
        )


def _panel_reasons(
    answers: dict[FindingId, Assessment], part: PanelId, damages: int, counts: CountLimits
) -> None:
    if counts.per_panel is None:
        return
    over = counts.panel_exceeded(damages)
    detail = f"{damages} on {part} against a limit of {counts.per_panel}"
    for reference, one in list(answers.items()):
        answers[reference] = _raise_with(
            one,
            Reason("panel count", detail, not over),
            Disposition.REPAIRABLE if over else Disposition.ALLOWABLE,
        )


def assess_panel(book: Book, tail: TailContext, findings: tuple[Finding, ...]) -> PanelAssessment:
    """Assess a set of cards that all sit on one panel."""
    if not findings:
        raise ValueError("a panel assessment needs at least one card")

    first = assess(book, tail, findings[0])
    part = first.placement.part
    answers: dict[FindingId, Assessment] = {findings[0].reference: first}
    placed = [Placed(findings[0], first.placement.centre)]
    bays = {findings[0].reference: _bay_key(tail, findings[0])}

    for finding in findings[1:]:
        one = assess(book, tail, finding)
        if one.placement.part != part:
            raise ValueError(f"{finding.reference} is not on {part}")
        answers[finding.reference] = one
        placed.append(Placed(finding, one.placement.centre))
        bays[finding.reference] = _bay_key(tail, finding)

    groups = clusters(placed, book.interaction.separation)
    counts = book.interaction.counts

    _cluster_reasons(answers, groups, counts)
    _bay_reasons(answers, Counter(bays.values()), bays, counts)
    _panel_reasons(answers, part, len(groups), counts)

    ordered = tuple(answers[finding.reference] for finding in findings)
    return PanelAssessment(part, ordered, groups)


def assess_by_panel(
    book: Book, tail: TailContext, findings: tuple[Finding, ...]
) -> tuple[PanelAssessment, ...]:
    """Split a tail's open cards by panel and assess each panel as a whole."""
    grouped: dict[PanelId, list[Finding]] = {}
    for finding in findings:
        part = assess(book, tail, finding).placement.part
        grouped.setdefault(part, []).append(finding)
    return tuple(
        assess_panel(book, tail, tuple(cards)) for _, cards in sorted(grouped.items(), key=str)
    )
