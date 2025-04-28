"""Scratches, gouges and nicks, which are the same measurement with different words.

All three are metal taken off along a line. What separates them on the report
form is how much: a scratch is a mark you can catch a fingernail in, a gouge
has displaced metal at its edges, and a nick is short and local, usually where
something was dropped on an edge. The manual files all three in one section and
gives them one set of limits keyed on depth, so the geometry is one type with
the kind carried alongside.

The direction matters and it is not intuition. Skin is worked hardest across
the hoop direction, so a score lying along the body axis is being pulled open
by the larger stress. The same 0.010 deep score is a blend job in one
orientation and a hold in the other, and the bearing is what says which.
"""

from __future__ import annotations

from dataclasses import dataclass

from doublerbay.errors import BadReading
from doublerbay.grid.surface import Offset
from doublerbay.measure.angle import Angle, Orientation, orientation_of
from doublerbay.measure.length import Length
from doublerbay.measure.ratio import Taper
from doublerbay.structure.footprint import Footprint


@dataclass(frozen=True, slots=True)
class Scoring:
    """Metal removed along a line, with the direction it was removed along."""

    depth: Length
    length: Length
    width: Length
    bearing: Angle
    sharp_bottom: bool = False

    def __post_init__(self) -> None:
        if self.depth.mils <= 0:
            raise BadReading("a score has a depth")
        if self.length.mils <= 0:
            raise BadReading("a score has a length")
        if self.width.mils <= 0:
            raise BadReading("a score has a width")
        if self.width > self.length:
            raise BadReading("a score wider than it is long is not a score")

    @property
    def orientation(self) -> Orientation:
        return orientation_of(self.bearing)

    @property
    def is_hoop_loaded(self) -> bool:
        """Lying along the body, so hoop stress is pulling it open."""
        return self.orientation is Orientation.LONGITUDINAL

    @property
    def is_across_the_barrel(self) -> bool:
        return self.orientation is Orientation.CIRCUMFERENTIAL

    def blend_length(self, taper: Taper) -> Length:
        """How long the cleanup runs once both ends are feathered out."""
        return self.length + taper.runout(self.depth) * 2

    def blend_width(self, taper: Taper) -> Length:
        return self.width + taper.runout(self.depth) * 2

    def footprint(self, centre: Offset) -> Footprint:
        """The skin the score covers, laid out along and across the body."""
        if self.is_across_the_barrel:
            return Footprint.square(centre, self.width, self.length)
        return Footprint.square(centre, self.length, self.width)

    def __str__(self) -> str:
        head = f"{self.depth.inches} deep, {self.length.inches} long, {self.bearing}"
        return f"{head}, sharp" if self.sharp_bottom else head
