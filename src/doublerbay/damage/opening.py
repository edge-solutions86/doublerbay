"""Holes, buckles and disbonds, which are the three that end the conversation early.

A hole has no allowable size because there is nothing left to carry load
through. A buckle means the panel has already failed in compression and gone
somewhere it does not come back from, so the question is not the buckle but
what pushed it. A disbond means two things that were meant to act as one are
not, and the area of it is the whole finding because depth does not apply.

They are grouped here because none of them is graded on a limit table. What is
measured is what the repair has to cover, and for a buckle, whether anybody has
established that it is permanent rather than a panel oil canning under its own
weight on a warm day.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal

from doublerbay.damage.corrosion import square_inches
from doublerbay.errors import BadReading
from doublerbay.grid.surface import Offset
from doublerbay.measure.angle import Angle, Orientation, orientation_of
from doublerbay.measure.length import Length
from doublerbay.structure.footprint import Footprint


@dataclass(frozen=True, slots=True)
class Opening:
    """A hole or a puncture, which is the same thing measured two ways."""

    along: Length
    around: Length
    torn_edges: bool = False
    was_a_fastener_hole: bool = False

    def __post_init__(self) -> None:
        if self.along.mils <= 0 or self.around.mils <= 0:
            raise BadReading("an opening has a size both ways")

    @property
    def wide(self) -> Length:
        return max(self.along, self.around)

    @property
    def narrow(self) -> Length:
        return min(self.along, self.around)

    @property
    def is_round(self) -> bool:
        return self.along == self.around

    @property
    def needs_trimming_before_repair(self) -> bool:
        """A torn edge is a row of crack starters and gets cut back to clean metal."""
        return self.torn_edges

    def trimmed(self, back_by: Length) -> Opening:
        """The opening once the torn edges have been cut out, which is what gets covered."""
        if back_by.mils < 0:
            raise BadReading("trimming does not make an opening smaller")
        return Opening(
            self.along + back_by * 2,
            self.around + back_by * 2,
            torn_edges=False,
            was_a_fastener_hole=self.was_a_fastener_hole,
        )

    def footprint(self, centre: Offset) -> Footprint:
        return Footprint(centre, self.along, self.around, self.narrow * Decimal("0.5"))

    def __str__(self) -> str:
        head = f"{self.along.inches} by {self.around.inches}"
        if self.was_a_fastener_hole:
            head = f"{head}, worked fastener hole"
        return f"{head}, torn" if self.torn_edges else head


@dataclass(frozen=True, slots=True)
class Buckle:
    """A panel that has gone out of plane and stayed there."""

    length: Length
    height: Length
    bearing: Angle
    confirmed_permanent: bool = False

    def __post_init__(self) -> None:
        if self.length.mils <= 0:
            raise BadReading("a buckle has a length")
        if self.height.mils <= 0:
            raise BadReading("a buckle has a height out of plane")

    @property
    def orientation(self) -> Orientation:
        return orientation_of(self.bearing)

    @property
    def is_across_the_load(self) -> bool:
        """A buckle running around the barrel is across the compression that made it."""
        return self.orientation is Orientation.CIRCUMFERENTIAL

    @property
    def is_oil_canning(self) -> bool:
        """Not yet established as permanent, so it may pop back and may be nothing."""
        return not self.confirmed_permanent

    def footprint(self, centre: Offset) -> Footprint:
        across = max(self.height * 4, Length(500))
        if self.orientation is Orientation.CIRCUMFERENTIAL:
            return Footprint.square(centre, across, self.length)
        return Footprint.square(centre, self.length, across)

    def __str__(self) -> str:
        head = f"{self.length.inches} long, {self.height.inches} out of plane"
        return head if self.confirmed_permanent else f"{head}, not confirmed permanent"


@dataclass(frozen=True, slots=True)
class Disbond:
    """Two bonded parts that are no longer acting as one."""

    along: Length
    around: Length
    tap_tested: bool = False
    reaches_an_edge: bool = False

    def __post_init__(self) -> None:
        if self.along.mils <= 0 or self.around.mils <= 0:
            raise BadReading("a disbond covers an area")

    @property
    def area(self) -> Decimal:
        return square_inches(self.along, self.around)

    @property
    def is_bounded(self) -> bool:
        """A disbond that has been tapped out has a known edge to it."""
        return self.tap_tested

    @property
    def can_spread(self) -> bool:
        """One that runs to a free edge has nothing holding it from growing."""
        return self.reaches_an_edge

    def footprint(self, centre: Offset) -> Footprint:
        return Footprint.square(centre, self.along, self.around)

    def __str__(self) -> str:
        head = f"{self.area} sq in"
        if self.reaches_an_edge:
            head = f"{head}, runs to an edge"
        return head if self.tap_tested else f"{head}, extent not tapped out"
