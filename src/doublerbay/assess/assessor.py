"""Walking one damage through the book and coming out with an answer.

Every rule runs, not just the ones up to the first failure. An engineer looking
at the trail wants to see that the depth was fine and the edge margin was not,
because that tells them where to put the repair. Stopping at the first no
throws away the half of the answer that is useful.

The disposition is the worst thing any rule said, which is the only combination
rule that is safe. Three rules saying allowable and one saying repair is a
repair. It is never an average and it is never the last one that ran.

When the book has no page at all for this combination the answer is not a
failure of the software, it is the manual running out, and that has its own
disposition. That is exactly the case that goes to the manufacturer, and it is
worth it landing there by itself rather than through an exception somebody
catches and turns into a shrug.
"""

from __future__ import annotations

from dataclasses import dataclass

from doublerbay.assess.placement import Placement, place
from doublerbay.assess.rules import CHECKS, Case
from doublerbay.assess.verdict import Disposition, Reason, Verdict
from doublerbay.damage.finding import Finding
from doublerbay.errors import NotOnFile
from doublerbay.fleet.catalogue import TailContext
from doublerbay.manual.book import Book
from doublerbay.manual.table import LimitRow


@dataclass(frozen=True, slots=True)
class Assessment:
    """One damage assessed, with everything that went into it."""

    finding: Finding
    placement: Placement
    verdict: Verdict
    row: LimitRow | None

    @property
    def disposition(self) -> Disposition:
        return self.verdict.disposition

    @property
    def needs_work(self) -> bool:
        return self.verdict.needs_work

    def __str__(self) -> str:
        return f"{self.finding.reference} on {self.placement.part}: {self.verdict}"


def _no_page(finding: Finding, placement: Placement, miss: NotOnFile) -> Verdict:
    return Verdict(
        Disposition.OUT_OF_LIMITS,
        (Reason("page", str(miss), False),),
        None,
        None,
    )


def assess(book: Book, tail: TailContext, finding: Finding) -> Assessment:
    """Walk one card through every rule the book has for it."""
    placement = place(tail, finding)
    try:
        row = book.limits_for(
            placement.chapter,
            tail.on,
            tail.aircraft,
            finding.kind,
            placement.panel_kind,
            placement.region,
        )
    except NotOnFile as miss:
        return Assessment(finding, placement, _no_page(finding, placement, miss), None)

    revision = book.revision_on(placement.chapter, tail.on).number
    case = Case(finding, placement, row.limits, book.cleanup.for_kind(finding.kind))

    reasons: list[Reason] = []
    worst = Disposition.ALLOWABLE
    for check in CHECKS:
        answer = check(case)
        if answer is None:
            continue
        reasons.append(answer.reason)
        if answer.outcome.worse_than(worst):
            worst = answer.outcome

    verdict = Verdict(worst, tuple(reasons), row.reference, revision)
    return Assessment(finding, placement, verdict, row)


def assess_all(
    book: Book, tail: TailContext, findings: tuple[Finding, ...]
) -> tuple[Assessment, ...]:
    """Assess a run of cards on one tail, in the order they were raised."""
    return tuple(
        assess(book, tail, finding)
        for finding in sorted(findings, key=lambda card: str(card.reference))
    )


def worst_disposition(assessments: tuple[Assessment, ...]) -> Disposition:
    """The worst answer across a set, which is what a panel or a tail reports."""
    if not assessments:
        return Disposition.ALLOWABLE
    return max((one.disposition for one in assessments), key=lambda answer: answer.rank)
