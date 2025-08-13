"""The repair as a record, which is what the next repair in that area has to work around.

A repair is not finished when the last rivet goes in. It becomes a permanent
feature of the aeroplane, and every assessment after it has to know it is
there: what skin it covers, how thick it made that area, when it went on, and
what it committed the operator to. A repair nobody recorded is a repair the
next engineer sizes a doubler straight over the top of.

Clearance is the rule that bites. Two repairs whose fastener patterns come
inside a few diameters of each other are feeding load into the same holes, and
neither one was sized for that. The clearance is measured between the patches
and not between the damages they cover, which is a bigger number than people
expect once both skirts are counted.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from doublerbay.errors import BadReading
from doublerbay.grid.surface import Offset
from doublerbay.ids import FindingId, PanelId, RepairId
from doublerbay.measure.length import Length
from doublerbay.measure.usage import Usage
from doublerbay.repair.scheme import RepairCategory, RepairScheme
from doublerbay.structure.footprint import DoublerForm, Footprint, InstalledDoubler

# Two repairs closer than this are sharing load paths neither was sized for.
# Stated in fastener diameters of the larger of the two patterns.
CLEARANCE_DIAMETERS = "4.0"


@dataclass(frozen=True, slots=True)
class RepairRecord:
    """One repair on one aeroplane, as the records office holds it."""

    reference: RepairId
    part: PanelId
    against: FindingId
    scheme: RepairScheme
    centre: Offset
    embodied: date
    at_usage: Usage
    removed: date | None = None

    def __post_init__(self) -> None:
        if self.removed is not None and self.removed < self.embodied:
            raise BadReading(f"{self.reference} was removed before it went on")

    @property
    def category(self) -> RepairCategory:
        return self.scheme.category

    @property
    def is_fitted(self) -> bool:
        return self.removed is None

    @property
    def needs_inspection(self) -> bool:
        return self.is_fitted and self.scheme.needs_inspection

    @property
    def footprint(self) -> Footprint:
        """The skin the patch covers, which is the doubler and not the damage."""
        return Footprint.square(self.centre, self.scheme.doubler.along, self.scheme.doubler.around)

    @property
    def required_clearance(self) -> Length:
        return self.scheme.doubler.size.diameters(CLEARANCE_DIAMETERS)

    def as_installed(self) -> InstalledDoubler:
        """The record as the structure definition wants to see it."""
        form = {
            "external doubler": DoublerForm.EXTERNAL,
            "flush doubler": DoublerForm.FLUSH,
            "internal doubler": DoublerForm.INTERNAL,
        }[self.scheme.form.value]
        return InstalledDoubler(
            part=str(self.reference),
            form=form,
            footprint=self.footprint,
            material=self.scheme.doubler.material,
            gauge=self.scheme.doubler.gauge,
            fitted=self.embodied,
        )

    def clears(self, other: RepairRecord) -> bool:
        """Whether the two patches are far enough apart to act independently."""
        needed = max(self.required_clearance, other.required_clearance)
        return self.footprint.clears(other.footprint, needed)

    def clearance_to(self, other: RepairRecord) -> Length:
        return self.footprint.clearance_to(other.footprint)

    def removed_on(self, day: date) -> RepairRecord:
        return RepairRecord(
            reference=self.reference,
            part=self.part,
            against=self.against,
            scheme=self.scheme,
            centre=self.centre,
            embodied=self.embodied,
            at_usage=self.at_usage,
            removed=day,
        )

    def __str__(self) -> str:
        state = "fitted" if self.is_fitted else "removed"
        return f"{self.reference} on {self.part}, {self.scheme.category}, {state}"


def clashes(fitted: tuple[RepairRecord, ...]) -> tuple[tuple[RepairRecord, RepairRecord], ...]:
    """Every pair of fitted repairs that is inside the clearance the other needs."""
    found = []
    live = [record for record in fitted if record.is_fitted]
    for index, first in enumerate(live):
        for second in live[index + 1 :]:
            if not first.clears(second):
                found.append((first, second))
    return tuple(found)
