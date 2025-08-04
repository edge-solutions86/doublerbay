"""Sizing a doubler, which comes down to putting the load back where the metal was.

The skin that has been cut out was carrying load. The doubler has to pick that
load up on one side of the hole, carry it across, and put it back down on the
other side, and the only things carrying it into and out of the doubler are the
fasteners. So the count is not a matter of taste: it is the load the removed
strip carried, divided by what one fastener will take in shear, rounded up, on
each side.

Everything else follows from the count. The fasteners go in rows at the pitch
the standard practices set, the rows sit at the row spacing, and the doubler is
the hole plus enough skirt to hold all of them with edge distance left over. A
doubler drawn to look right and then filled with fasteners is how a repair ends
up with three rows where it needed five.
"""

from __future__ import annotations

import math
from dataclasses import dataclass
from decimal import Decimal

from doublerbay.errors import BadReading
from doublerbay.measure.fastener import (
    MIN_PITCH_D,
    MIN_ROW_SPACING_D,
    PREFERRED_EDGE_DISTANCE_D,
    FastenerSize,
)
from doublerbay.measure.gauge import one_step_heavier
from doublerbay.measure.length import MILS_PER_INCH, Length
from doublerbay.structure.material import Material

# Single shear allowable per fastener, in pounds, for the protruding head rivets
# the shop uses on skin repairs. Off the standard practices chapter.
SHEAR_ALLOWABLE: dict[int, int] = {
    3: 217,
    4: 388,
    5: 596,
    6: 862,
    8: 1524,
    10: 2400,
    12: 3456,
}

# A doubler goes on one gauge heavier than the skin it covers, so the repair is
# never the weakest thing in the joint.
KSI = 1000


def shear_allowable(size: FastenerSize) -> int:
    try:
        return SHEAR_ALLOWABLE[size.dash]
    except KeyError:
        raise BadReading(f"no shear allowable on file for a {size}") from None


def carried_load(thickness: Length, width: Length, material: Material) -> int:
    """The load the strip of skin being cut out was carrying, in pounds."""
    if thickness.mils <= 0 or width.mils <= 0:
        raise BadReading("a strip of skin has a thickness and a width")
    area = Decimal(thickness.mils) * Decimal(width.mils) / Decimal(MILS_PER_INCH) ** 2
    return int((area * material.ultimate_ksi * KSI).to_integral_value())


def fasteners_each_side(
    thickness: Length, width: Length, material: Material, size: FastenerSize
) -> int:
    """How many fasteners it takes to get that load into the doubler, each side."""
    return max(math.ceil(carried_load(thickness, width, material) / shear_allowable(size)), 1)


@dataclass(frozen=True, slots=True)
class DoublerPlan:
    """A patch, its fasteners, and how they are laid out."""

    gauge: Length
    material: Material
    along: Length
    around: Length
    size: FastenerSize
    rows_each_side: int
    per_row: int
    pitch: Length
    row_spacing: Length
    edge_distance: Length

    def __post_init__(self) -> None:
        if self.rows_each_side < 1:
            raise BadReading("a doubler has at least one row of fasteners each side")
        if self.per_row < 2:
            raise BadReading("a row of one fastener is a hinge, not a joint")

    @property
    def fastener_count(self) -> int:
        """Every hole in the patch, four sides of it."""
        return self.rows_each_side * self.per_row * 4

    @property
    def skirt(self) -> Length:
        """How far the doubler reaches past the hole on one side."""
        return self.edge_distance + self.row_spacing * (self.rows_each_side - 1)

    def __str__(self) -> str:
        return (
            f"{self.gauge.inches} {self.material}, {self.along.inches} by {self.around.inches}, "
            f"{self.fastener_count} off {self.size}"
        )


def size_doubler(
    along: Length,
    around: Length,
    skin: Length,
    material: Material,
    size: FastenerSize,
) -> DoublerPlan:
    """Work out a doubler for a hole of a given size in a skin of a given gauge."""
    if along.mils <= 0 or around.mils <= 0:
        raise BadReading("a doubler covers a hole with a size")

    gauge = one_step_heavier(skin).nominal
    pitch = size.diameters(MIN_PITCH_D)
    row_spacing = size.diameters(MIN_ROW_SPACING_D)
    edge = size.diameters(PREFERRED_EDGE_DISTANCE_D)

    needed = fasteners_each_side(skin, max(along, around), material, size)
    per_row = max(int(max(along, around).mils // pitch.mils) + 1, 2)
    rows = max(math.ceil(needed / per_row), 1)

    skirt = edge + row_spacing * (rows - 1)
    return DoublerPlan(
        gauge=gauge,
        material=material,
        along=along + skirt * 2,
        around=around + skirt * 2,
        size=size,
        rows_each_side=rows,
        per_row=per_row,
        pitch=pitch,
        row_spacing=row_spacing,
        edge_distance=edge,
    )
