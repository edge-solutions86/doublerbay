"""A rectangle with radiused corners, which is the shape everything on the skin is.

A window is one, a doubler is one, an access hatch is one, and a repair patch is
one. They were being drawn twice with the same arithmetic in two files, and the
two copies had already started to drift: one of them let a zero corner radius
through and the other refused it. That is exactly the sort of difference nobody
finds until a limit lands on the boundary.

The arithmetic is the standard one. Shrink the rectangle by the corner radius,
measure how far outside that smaller box the point is on each axis, take the
hypotenuse, and subtract the radius. Inside the box both components come out as
zero and the answer is zero, which is what inside is supposed to mean.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from typing import Self

from doublerbay.errors import BadReading
from doublerbay.grid.surface import Offset, hypotenuse
from doublerbay.measure.length import Length

HALF = Decimal("0.5")


@dataclass(frozen=True, slots=True)
class RoundedRect:
    """A rectangle with radiused corners, positioned in skin coordinates."""

    centre: Offset
    along: Length
    around: Length
    corner_radius: Length

    def __post_init__(self) -> None:
        if self.along.mils <= 0 or self.around.mils <= 0:
            raise BadReading("a rounded rectangle has a size")
        if self.corner_radius.mils < 0:
            raise BadReading("a corner radius does not run backwards")
        if self.corner_radius > min(self.along, self.around) * HALF:
            raise BadReading("a corner radius past the middle is not a rectangle")

    @property
    def is_round(self) -> bool:
        """A radius that has eaten the shorter side leaves a circle or a slot end."""
        return self.corner_radius * 2 == min(self.along, self.around)

    @property
    def longer_side(self) -> Length:
        return max(self.along, self.around)

    def outside_box(self, point: Offset) -> tuple[Length, Length]:
        """How far the point sits outside the box the corner radii are struck from."""
        half_along = self.along * HALF - self.corner_radius
        half_around = self.around * HALF - self.corner_radius
        return (
            max(abs(point.along - self.centre.along) - half_along, Length(0)),
            max(abs(point.around - self.centre.around) - half_around, Length(0)),
        )

    def edge_distance(self, point: Offset) -> Length:
        """Distance out to the nearest edge, zero anywhere inside."""
        out_along, out_around = self.outside_box(point)
        reach = hypotenuse(out_along, out_around)
        if reach <= self.corner_radius:
            return Length(0)
        return reach - self.corner_radius

    def contains(self, point: Offset) -> bool:
        return self.edge_distance(point).mils == 0

    def off_a_corner(self, point: Offset) -> bool:
        """Whether the point is off a corner rather than off a flat side."""
        out_along, out_around = self.outside_box(point)
        return out_along.mils > 0 and out_around.mils > 0

    def grown(self, by: Length) -> Self:
        """The same shape with a margin all round it, which is how clearance is tested."""
        if by.mils < 0:
            raise BadReading("a rounded rectangle does not grow inwards")
        return type(self)(
            self.centre,
            self.along + by * 2,
            self.around + by * 2,
            self.corner_radius + by,
        )

    def gap_along(self, other: RoundedRect) -> Length:
        overlap = (self.along + other.along) * HALF
        apart = abs(other.centre.along - self.centre.along)
        return max(apart - overlap, Length(0))

    def gap_around(self, other: RoundedRect) -> Length:
        overlap = (self.around + other.around) * HALF
        apart = abs(other.centre.around - self.centre.around)
        return max(apart - overlap, Length(0))

    def overlaps(self, other: RoundedRect) -> bool:
        """Two shapes sharing skin, taken on the enclosing rectangles."""
        return self.gap_along(other).mils == 0 and self.gap_around(other).mils == 0

    def clearance_to(self, other: RoundedRect) -> Length:
        """The shortest gap between the two enclosing rectangles, zero if they touch."""
        along, around = self.gap_along(other), self.gap_around(other)
        if along.mils == 0:
            return around
        if around.mils == 0:
            return along
        return hypotenuse(along, around)

    def clears(self, other: RoundedRect, by: Length) -> bool:
        return self.clearance_to(other) >= by

    def __str__(self) -> str:
        return f"{self.along.inches} by {self.around.inches} at {self.centre}"
