"""Choosing a repair, and the category it lands in, which is the part that costs money.

The category is not a description of quality. It is a commitment. Category A is
permanent and nobody looks at it again. Category B is permanent enough but has
to be inspected on a repeat interval for the rest of the aeroplane's life.
Category C is temporary and has to be taken off and done properly by a date.

So the category decides how much work the repair generates over twenty years,
and it is decided by where the repair is rather than how well it is made. A
doubler on the tension surface of a wing is category B however good it is,
because the surface it is on grows cracks. The same doubler on a tail cone is
category A.

The form is the other half. An external patch is quicker and it stands proud of
the skin, which the aerodynamicists will not have forward of the wing on the
upper surfaces, so those go flush and take twice as long.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

from doublerbay.assess.placement import Placement
from doublerbay.errors import BadReading
from doublerbay.grid.ata import Reference
from doublerbay.measure.fastener import FastenerSize
from doublerbay.measure.length import Length
from doublerbay.repair.doubler import DoublerPlan, size_doubler
from doublerbay.structure.build import Region
from doublerbay.structure.panel import PanelKind


class RepairCategory(Enum):
    """What the repair commits the operator to."""

    A = "permanent"
    B = "permanent with a repeat inspection"
    C = "temporary, to be replaced"

    @property
    def needs_inspection(self) -> bool:
        return self is not RepairCategory.A

    @property
    def is_permanent(self) -> bool:
        return self is not RepairCategory.C

    def __str__(self) -> str:
        return f"category {self.name}"


class RepairForm(Enum):
    """How the patch sits relative to the skin."""

    EXTERNAL = "external doubler"
    FLUSH = "flush doubler"
    INTERNAL = "internal doubler"

    @property
    def is_in_the_airflow(self) -> bool:
        return self is RepairForm.EXTERNAL

    def __str__(self) -> str:
        return self.value


# Panels where a patch standing proud of the skin is not acceptable, because of
# what it does to the airflow before it reaches the wing.
FLUSH_ONLY: frozenset[PanelKind] = frozenset(
    {PanelKind.CROWN_SKIN, PanelKind.WING_UPPER, PanelKind.WING_LOWER}
)

# Where a repair carries a repeat inspection whatever it is made of.
INSPECTED_KINDS: frozenset[PanelKind] = frozenset({PanelKind.WING_LOWER})
INSPECTED_REGIONS: frozenset[Region] = frozenset(
    {Region.FASTENER_ROW, Region.JOINT_BAND, Region.CUTOUT_CORNER}
)

# What the shop reaches for on skin unless the drawing says otherwise.
STANDARD_FASTENER = FastenerSize(5)


@dataclass(frozen=True, slots=True)
class RepairScheme:
    """One repair, as it would be drawn."""

    form: RepairForm
    category: RepairCategory
    doubler: DoublerPlan
    sealed: bool
    reference: Reference
    filler: Length | None = None

    def __post_init__(self) -> None:
        if self.filler is not None and self.filler.mils <= 0:
            raise BadReading("a filler has a thickness")
        if self.form is RepairForm.FLUSH and self.filler is None:
            raise BadReading("a flush repair needs a filler to make up the skin line")

    @property
    def needs_inspection(self) -> bool:
        return self.category.needs_inspection

    @property
    def fastener_count(self) -> int:
        return self.doubler.fastener_count

    def __str__(self) -> str:
        seal = ", sealed" if self.sealed else ""
        return f"{self.form}, {self.category}, {self.doubler}{seal}"


def form_for(placement: Placement) -> RepairForm:
    """External unless the panel will not have something standing proud of it."""
    return RepairForm.FLUSH if placement.panel_kind in FLUSH_ONLY else RepairForm.EXTERNAL


def category_for(placement: Placement, temporary: bool = False) -> RepairCategory:
    """A unless something about the place says otherwise, C if it is a stopgap."""
    if temporary:
        return RepairCategory.C
    if placement.panel_kind in INSPECTED_KINDS:
        return RepairCategory.B
    if placement.region in INSPECTED_REGIONS:
        return RepairCategory.B
    return RepairCategory.A


def needs_sealing(placement: Placement) -> bool:
    """A repair through a pressure boundary has to hold pressure when it is done."""
    return placement.panel_kind.is_pressurised


def select_scheme(
    along: Length,
    around: Length,
    placement: Placement,
    reference: Reference,
    size: FastenerSize | None = None,
    temporary: bool = False,
) -> RepairScheme:
    """Work out the repair for a hole of a given size at a given place."""
    size = size if size is not None else STANDARD_FASTENER
    form = form_for(placement)
    doubler = size_doubler(along, around, placement.thickness, placement.material, size)
    return RepairScheme(
        form=form,
        category=category_for(placement, temporary),
        doubler=doubler,
        sealed=needs_sealing(placement),
        reference=reference,
        filler=placement.thickness if form is RepairForm.FLUSH else None,
    )
