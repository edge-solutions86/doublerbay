"""Where a repeat interval comes from, which is a crack growth argument and not a guess.

A repair that needs inspecting needs a number, and the number is not picked. It
falls out of three things: how long a crack can get before the structure stops
carrying its load, how small a crack the chosen method can be relied on to
find, and how fast a crack grows there. The flying between those two lengths is
the whole opportunity to catch it, and the interval is that divided by a safety
factor so the crack gets looked at at least twice on its way up.

Which is why a more sensitive method buys a longer interval. Eddy current finds
a crack at fifty thousandths where a detailed visual needs half an inch, and
those extra four hundred and fifty thousandths of growth are months of flying.
The expensive inspection is often the cheaper programme.

The threshold is the flying before the first one. A crack has to start before
it can grow, and initiation takes longer than growth does, so the threshold is
a multiple of the repeat rather than the same number.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from decimal import Decimal

from doublerbay.errors import BadReading, NotOnFile
from doublerbay.measure.interval import DueAt, Interval
from doublerbay.measure.length import Length
from doublerbay.measure.usage import Cycles, Usage
from doublerbay.repair.scheme import RepairCategory
from doublerbay.structure.panel import PanelKind
from doublerbay.tolerance.method import InspectionMethod

# A crack is looked at at least twice between being findable and being critical.
SAFETY_FACTOR = 2

# Initiation takes longer than growth, so the first look is further out.
THRESHOLD_FACTOR = 2

# How long a temporary repair may stay on before it is replaced properly.
TEMPORARY_LIMIT_MONTHS = 12


@dataclass(frozen=True, slots=True)
class GrowthBasis:
    """What a crack does in one kind of structure."""

    critical: Length
    growth_per_thousand: Length

    def __post_init__(self) -> None:
        if self.critical.mils <= 0:
            raise BadReading("a critical crack length is a length")
        if self.growth_per_thousand.mils <= 0:
            raise BadReading("a crack that does not grow needs no inspection")

    def growth_cycles(self, from_size: Length) -> Cycles:
        """Flying between a crack being findable and being critical."""
        if from_size >= self.critical:
            raise BadReading(
                f"{from_size.inches} is already at or past the critical {self.critical.inches}"
            )
        run = self.critical - from_size
        cycles = Decimal(run.mils) / Decimal(self.growth_per_thousand.mils) * 1000
        return Cycles(int(cycles.to_integral_value()))

    def __str__(self) -> str:
        return f"critical {self.critical.inches}, {self.growth_per_thousand.inches} per 1000 FC"


# Growth rates by the kind of skin the repair is on. The lower wing surface is
# the fast one because it spends the flight in tension.
BASIS: dict[PanelKind, GrowthBasis] = {
    PanelKind.PRESSURISED_SKIN: GrowthBasis(Length.of_inches("6.000"), Length.of_inches("0.100")),
    PanelKind.CROWN_SKIN: GrowthBasis(Length.of_inches("6.000"), Length.of_inches("0.080")),
    PanelKind.BELLY_SKIN: GrowthBasis(Length.of_inches("6.000"), Length.of_inches("0.090")),
    PanelKind.UNPRESSURISED_SKIN: GrowthBasis(Length.of_inches("8.000"), Length.of_inches("0.040")),
    PanelKind.WING_UPPER: GrowthBasis(Length.of_inches("6.000"), Length.of_inches("0.050")),
    PanelKind.WING_LOWER: GrowthBasis(Length.of_inches("4.000"), Length.of_inches("0.150")),
}


def basis_for(kind: PanelKind) -> GrowthBasis:
    try:
        return BASIS[kind]
    except KeyError:
        raise NotOnFile(f"no crack growth data on file for {kind}") from None


@dataclass(frozen=True, slots=True)
class InspectionSchedule:
    """When a repair gets looked at, how, and how often after that."""

    method: InspectionMethod
    threshold: Interval
    repeat: Interval | None

    @property
    def is_repeating(self) -> bool:
        return self.repeat is not None

    def first_due(self, flown: Usage, embodied: date) -> DueAt:
        return self.threshold.due_from(flown, embodied)

    def next_due(self, flown: Usage, last_done: date) -> DueAt:
        if self.repeat is None:
            raise BadReading("this schedule has no repeat, it is a replacement limit")
        return self.repeat.due_from(flown, last_done)

    def __str__(self) -> str:
        if self.repeat is None:
            return f"{self.method} once by {self.threshold}"
        return f"{self.method} at {self.threshold} then every {self.repeat}"


def schedule_for(
    category: RepairCategory, kind: PanelKind, method: InspectionMethod
) -> InspectionSchedule | None:
    """Work out the inspection programme a repair of this category carries."""
    if not category.needs_inspection:
        return None
    if category is RepairCategory.C:
        return InspectionSchedule(
            method,
            Interval(months=TEMPORARY_LIMIT_MONTHS),
            None,
        )
    growth = basis_for(kind).growth_cycles(method.finds_down_to)
    repeat = Cycles(growth.count // SAFETY_FACTOR)
    if repeat.count < 1:
        raise BadReading(f"{method} leaves no room for a repeat interval on {kind}")
    return InspectionSchedule(
        method,
        Interval(cycles=Cycles(repeat.count * THRESHOLD_FACTOR)),
        Interval(cycles=repeat),
    )
