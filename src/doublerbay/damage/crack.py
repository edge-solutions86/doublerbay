"""A crack, which is the one kind that has no allowable size.

Everything else on the form has a table with a number in it. A crack has a
repair. What is measured is therefore not whether it is inside a limit but how
much of the structure it has already crossed and how fast it is going to cross
the rest, and that is length, direction, and whether it has broken through the
skin or is still buried in it.

A crack is reported by its two tips, because the tips are what get stop drilled
and what get re measured at the next inspection. Growth between two reports is
the number the engineer actually wants, and it only exists if the same two tips
were found and written down both times.
"""

from __future__ import annotations

from dataclasses import dataclass

from doublerbay.errors import BadReading
from doublerbay.grid.surface import Offset
from doublerbay.measure.angle import Angle, Orientation, orientation_of
from doublerbay.measure.length import Length
from doublerbay.structure.footprint import Footprint


@dataclass(frozen=True, slots=True)
class Crack:
    """A separation in the metal, measured tip to tip."""

    length: Length
    bearing: Angle
    through_thickness: bool = True
    stop_drilled: bool = False
    tips_found: int = 2

    def __post_init__(self) -> None:
        if self.length.mils <= 0:
            raise BadReading("a crack has a length")
        if self.tips_found not in (0, 1, 2):
            raise BadReading("a crack has nought, one or two tips found")
        if self.stop_drilled and self.tips_found == 0:
            raise BadReading("a crack cannot be stop drilled at a tip nobody found")

    @property
    def orientation(self) -> Orientation:
        return orientation_of(self.bearing)

    @property
    def is_hoop_loaded(self) -> bool:
        """Lying along the body, which puts the larger of the two stresses across it."""
        return self.orientation is Orientation.LONGITUDINAL

    @property
    def is_bounded(self) -> bool:
        """Both tips found, so the length is a length and not a lower bound."""
        return self.tips_found == 2

    @property
    def is_arrested(self) -> bool:
        """Stop drilled at both tips, which holds it until the repair goes on."""
        return self.stop_drilled and self.is_bounded

    def grown_from(self, earlier: Crack) -> Length:
        """How much longer it is than it was, which is what the interval is set on."""
        if not (self.is_bounded and earlier.is_bounded):
            raise BadReading("growth needs both tips found on both readings")
        if self.length < earlier.length:
            raise BadReading("a crack does not get shorter")
        return self.length - earlier.length

    def footprint(self, centre: Offset) -> Footprint:
        """A thin box along the crack, for working out what it is heading towards."""
        thickness = Length(10)
        if self.orientation is Orientation.CIRCUMFERENTIAL:
            return Footprint.square(centre, thickness, self.length)
        return Footprint.square(centre, self.length, thickness)

    def __str__(self) -> str:
        head = f"{self.length.inches} long, {self.bearing}"
        if not self.through_thickness:
            head = f"{head}, part depth"
        if not self.is_bounded:
            head = f"{head}, {self.tips_found} tips found"
        return f"{head}, stop drilled" if self.stop_drilled else head
