"""Holes in the skin, and the band around them where nothing is allowed.

Every cutout is a stress raiser and every rectangular cutout is a stress raiser
concentrated into four corners. That is why no window on an aeroplane has a
square corner and why the corner radius is on the drawing rather than left to
the shop. It is also why damage near a cutout is not assessed with the same
numbers as damage in the open field of a bay.

The band is measured from the edge of the hole and not from its centre, so a
big door and a small drain hole with the same corner radius keep the same
distance clear. Inside that band the manual sends most things to a repair that
would have been allowable two inches further out.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from enum import Enum

from doublerbay.errors import BadReading
from doublerbay.grid.surface import Offset, hypotenuse
from doublerbay.measure.length import Length

HALF = Decimal("0.5")

# The keep out band is twice the corner radius, but never less than an inch,
# because a drain hole with a 0.125 radius still needs a working margin.
KEEP_OUT_FACTOR = 2
KEEP_OUT_FLOOR = Length.of_inches("1.000")


class CutoutKind(Enum):
    """What the hole is for, which decides whether it is a pressure boundary."""

    WINDOW = "window"
    DOOR = "door"
    ACCESS_PANEL = "access panel"
    ANTENNA = "antenna"
    DRAIN = "drain"
    VENT = "vent"

    @property
    def is_pressure_boundary(self) -> bool:
        """A hole that is plugged by something holding cabin pressure."""
        return self in (CutoutKind.WINDOW, CutoutKind.DOOR)

    @property
    def is_vented(self) -> bool:
        return self in (CutoutKind.DRAIN, CutoutKind.VENT)

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True, slots=True)
class Cutout:
    """A radiused rectangle in the skin, positioned in panel coordinates."""

    name: str
    kind: CutoutKind
    centre: Offset
    along: Length
    around: Length
    corner_radius: Length

    def __post_init__(self) -> None:
        if not self.name.strip():
            raise BadReading("a cutout has a name")
        if self.along.mils <= 0 or self.around.mils <= 0:
            raise BadReading(f"{self.name}: a cutout has a size")
        if self.corner_radius.mils <= 0:
            raise BadReading(f"{self.name}: a square corner is not a cutout")
        half = min(self.along, self.around) * HALF
        if self.corner_radius > half:
            raise BadReading(f"{self.name}: a radius of {self.corner_radius} is past the middle")

    @property
    def is_round(self) -> bool:
        """A hole whose radius has eaten both sides is a circle or a slot end."""
        return self.corner_radius * 2 == min(self.along, self.around)

    @property
    def keep_out(self) -> Length:
        band = self.corner_radius * KEEP_OUT_FACTOR
        return max(band, KEEP_OUT_FLOOR)

    def _to_corner_box(self, point: Offset) -> tuple[Length, Length]:
        """How far the point is outside the box the corner radii are struck from."""
        half_along = self.along * HALF - self.corner_radius
        half_around = self.around * HALF - self.corner_radius
        from_centre_along = abs(point.along - self.centre.along)
        from_centre_around = abs(point.around - self.centre.around)
        return (
            max(from_centre_along - half_along, Length(0)),
            max(from_centre_around - half_around, Length(0)),
        )

    def edge_distance(self, point: Offset) -> Length:
        """Distance from the point to the nearest edge of the hole, zero if inside."""
        out_along, out_around = self._to_corner_box(point)
        reach = hypotenuse(out_along, out_around)
        if reach <= self.corner_radius:
            return Length(0)
        return reach - self.corner_radius

    def contains(self, point: Offset) -> bool:
        return self.edge_distance(point) == Length(0)

    def in_keep_out(self, point: Offset) -> bool:
        """Whether a point is close enough to be assessed under the cutout rules."""
        if self.contains(point):
            return True
        return self.edge_distance(point) <= self.keep_out

    def near_a_corner(self, point: Offset) -> bool:
        """Whether the point is off the flat sides, where the concentration bites."""
        out_along, out_around = self._to_corner_box(point)
        return out_along.mils > 0 and out_around.mils > 0

    def __str__(self) -> str:
        return f"{self.name} ({self.kind})"
