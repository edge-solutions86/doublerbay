"""Taking a card in, which is more than one write and always has been.

Raising a damage card writes the card and opens its disposition. That much was
already true. What was not handled anywhere is that a corrosion card is also a
corrosion programme finding, and the programme grades it at the moment it is
raised, against the history of that panel on that aeroplane.

Doing the grading later does not work. The level depends on whether this place
has corroded before, and "before" means before this finding, so a batch job
run at the end of the month grades six findings against each other in whatever
order it happens to read them. The answer has to be computed once, in the
transaction that takes the card.

The route and the command line both come through here. Neither of them knows
that a corrosion card writes three rows and a dent writes two, and neither of
them should.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from doublerbay.assess.placement import place
from doublerbay.damage.corrosion import Corrosion
from doublerbay.damage.finding import Finding
from doublerbay.damage.kind import DamageKind
from doublerbay.disposition.state import DispositionRecord
from doublerbay.errors import BadReading
from doublerbay.fleet.catalogue import TailContext, TypeCatalogue
from doublerbay.ids import FindingId
from doublerbay.manual.book import Book
from doublerbay.store.unit import UnitOfWork
from doublerbay.tolerance.corrosion import CorrosionContext, CorrosionEntry, grade


@dataclass(frozen=True, slots=True)
class Intake:
    """What taking one card in produced."""

    reference: FindingId
    graded: CorrosionEntry | None = None

    @property
    def went_on_the_programme(self) -> bool:
        return self.graded is not None

    @property
    def must_be_reported(self) -> bool:
        return self.graded is not None and self.graded.must_be_reported

    @property
    def report_by(self) -> date | None:
        """When a reportable finding has to be out, if this one is reportable."""
        return self.graded.report_by if self.graded is not None else None

    def __str__(self) -> str:
        if self.graded is None:
            return f"raised {self.reference}"
        return f"raised {self.reference}, {self.graded.level}"


def tail_context(unit: UnitOfWork, catalogue: TypeCatalogue, finding: Finding) -> TailContext:
    """The aeroplane as it stood on the day the card was raised."""
    aircraft = unit.aircraft.get(finding.serial)
    return TailContext(
        aircraft=aircraft,
        on=finding.found_on,
        flown=unit.aircraft.log_for(finding.serial).at(finding.found_on),
        definition=catalogue.definition_for(aircraft.series),
    )


def within_limits(book: Book, tail: TailContext, finding: Finding) -> bool:
    """Whether the book lets this stand as it is, which is a level one input.

    A missing page counts as outside limits. The programme cannot claim it is
    coping with something it has no allowable for.
    """
    from doublerbay.assess.assessor import assess

    return assess(book, tail, finding).verdict.is_allowable


def take_in(
    unit: UnitOfWork,
    book: Book,
    catalogue: TypeCatalogue,
    finding: Finding,
    urgent: bool = False,
) -> Intake:
    """Write the card, its disposition, and its programme entry if it has one."""
    unit.findings.raise_finding(finding)
    unit.dispositions.save(DispositionRecord.raised(finding.reference))
    if finding.kind is not DamageKind.CORROSION:
        if urgent:
            raise BadReading("only a corrosion finding is graded as an airworthiness concern")
        return Intake(finding.reference)

    tail = tail_context(unit, catalogue, finding)
    placement = place(tail, finding)
    geometry = finding.geometry
    if not isinstance(geometry, Corrosion):
        raise BadReading(
            f"{finding.reference} is filed as corrosion without corrosion measurements"
        )
    entry = grade(
        geometry,
        CorrosionContext(
            part=placement.part,
            within_limits=within_limits(book, tail, finding),
            found_here_before=unit.corrosion.has_form_here(
                finding.serial, placement.part, finding.found_on
            ),
            urgent_airworthiness_concern=urgent,
        ),
        finding.found_on,
    )
    unit.corrosion.record(entry, finding.serial, finding.reference)
    return Intake(finding.reference, entry)


def next_reference(unit: UnitOfWork, on: date) -> FindingId:
    """The next card number for the year a card was found in."""
    return unit.findings.next_reference(on.year)
