"""A dent, measured the way the manual wants it measured.

Three numbers and two questions. The depth is taken from a straight edge laid
across the dent, the two diameters are taken along the body and around it, and
then somebody has to say whether the bottom of it is smooth and whether there
is a crack in it. The last two are not measurements and they are what most
dispositions actually turn on, because a smooth shallow dent is very often
allowable as it stands and a dent with a sharp bottom never is.

The ratio the limit tables are written against is the depth over the smaller of
the two diameters, not the larger. A long shallow trough that is narrow across
is graded on the narrow direction, because that is where the metal is being
worked hardest, and taking the wide direction gives an answer that clears
something it should not.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal

from doublerbay.errors import BadReading
from doublerbay.grid.surface import Offset
from doublerbay.measure.length import Length
from doublerbay.measure.ratio import Ratio
from doublerbay.structure.footprint import Footprint

HALF = Decimal("0.5")


@dataclass(frozen=True, slots=True)
class Dent:
    """Metal pushed in, with the two judgements the inspector had to make."""

    depth: Length
    along: Length
    around: Length
    sharp_bottom: bool = False
    cracked: bool = False

    def __post_init__(self) -> None:
        if self.depth.mils <= 0:
            raise BadReading("a dent has a depth")
        if self.along.mils <= 0 or self.around.mils <= 0:
            raise BadReading("a dent has a diameter both ways")
        if self.depth >= self.narrow:
            raise BadReading("a dent deeper than it is wide is a puncture, not a dent")

    @property
    def wide(self) -> Length:
        return max(self.along, self.around)

    @property
    def narrow(self) -> Length:
        return min(self.along, self.around)

    @property
    def sharpness(self) -> Ratio:
        """Depth over the narrow diameter, which is what the tables are keyed on."""
        return Ratio.between(self.depth, self.narrow)

    @property
    def is_smooth(self) -> bool:
        """A dent with a clean bottom and no crack, which is the only kind that can be left."""
        return not self.sharp_bottom and not self.cracked

    @property
    def is_elongated(self) -> bool:
        """A dent twice as long as it is wide, which the manual treats as a trough."""
        return self.wide >= self.narrow * 2

    def footprint(self, centre: Offset) -> Footprint:
        """The skin it covers, for working out what it is close to."""
        return Footprint(centre, self.along, self.around, self.narrow * HALF)

    def deeper_than(self, other: Dent) -> bool:
        return self.depth > other.depth

    def __str__(self) -> str:
        head = f"{self.depth.inches} deep, {self.along.inches} by {self.around.inches}"
        if self.cracked:
            return f"{head}, cracked"
        return f"{head}, sharp" if self.sharp_bottom else head
