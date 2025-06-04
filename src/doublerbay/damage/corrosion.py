"""Corrosion, which is measured as a patch and judged on what is under it.

A dent is a shape. Corrosion is a stain with metal missing under it, and the
depth is not known until somebody has taken the product off and put a depth
gauge in the worst of it. So a corrosion report carries the area it covers, the
worst depth found once it was cleaned up, and two flags that change everything
about the answer.

Pitting means the loss is not spread evenly and the average depth is
meaningless, so the worst pit governs. Exfoliation means it is running along
the grain and lifting the metal in layers, which no amount of surface blending
reaches, and it goes to a repair whatever the depth says. Those two are why
corrosion cannot be graded off a depth alone.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal

from doublerbay.errors import BadReading
from doublerbay.grid.surface import Offset
from doublerbay.measure.length import MILS_PER_INCH, Length
from doublerbay.measure.ratio import Taper
from doublerbay.structure.footprint import Footprint


def square_inches(along: Length, around: Length) -> Decimal:
    """Area in square inches, to two places, from two lengths in mils."""
    return (Decimal(along.mils) * Decimal(around.mils) / Decimal(MILS_PER_INCH) ** 2).quantize(
        Decimal("0.01")
    )


@dataclass(frozen=True, slots=True)
class Corrosion:
    """A corroded patch, with the worst depth found in it once it was cleaned."""

    along: Length
    around: Length
    worst_depth: Length
    pitted: bool = False
    exfoliating: bool = False

    def __post_init__(self) -> None:
        if self.along.mils <= 0 or self.around.mils <= 0:
            raise BadReading("corrosion covers an area")
        if self.worst_depth.mils < 0:
            raise BadReading("a corrosion depth does not run backwards")
        if self.exfoliating and self.worst_depth.mils == 0:
            raise BadReading("exfoliation is not a surface stain")

    @property
    def area(self) -> Decimal:
        return square_inches(self.along, self.around)

    @property
    def is_surface_only(self) -> bool:
        """Product taken off and no metal loss under it, which is the easy answer."""
        return self.worst_depth.mils == 0 and not self.exfoliating

    @property
    def reaches_below_the_surface(self) -> bool:
        return not self.is_surface_only

    @property
    def needs_a_repair_whatever_the_depth(self) -> bool:
        """Exfoliation runs along the grain and blending does not reach it."""
        return self.exfoliating

    @property
    def governing_depth(self) -> Length:
        """The number the tables read, which is the worst pit and never an average."""
        return self.worst_depth

    def blend_area(self, taper: Taper) -> Decimal:
        """How much skin the cleanup covers once it is feathered out all round."""
        runout = taper.runout(self.worst_depth)
        return square_inches(self.along + runout * 2, self.around + runout * 2)

    def footprint(self, centre: Offset) -> Footprint:
        return Footprint.square(centre, self.along, self.around)

    def limit_depth(self) -> Length | None:
        """The worst pit, which is the only depth corrosion is judged on."""
        return self.worst_depth

    def worse_than(self, other: Corrosion) -> bool:
        if self.exfoliating != other.exfoliating:
            return self.exfoliating
        return self.worst_depth > other.worst_depth

    def __str__(self) -> str:
        head = f"{self.area} sq in, {self.worst_depth.inches} deep"
        marks = [
            word for word, on in (("pitted", self.pitted), ("exfoliating", self.exfoliating)) if on
        ]
        return f"{head}, {' and '.join(marks)}" if marks else head
