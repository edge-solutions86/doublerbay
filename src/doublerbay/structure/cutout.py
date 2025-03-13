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

The shape itself is a rounded rectangle and lives in one place now. What is left
here is the part that is about holes rather than about geometry: what the hole
is for, and how far clear of it damage has to stay.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

from doublerbay.errors import BadReading
from doublerbay.grid.surface import Offset
from doublerbay.measure.length import Length
from doublerbay.structure.region import RoundedRect

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
    """A hole in the skin, with the band around it that damage has to keep out of."""

    name: str
    kind: CutoutKind
    centre: Offset
    along: Length
    around: Length
    corner_radius: Length

    def __post_init__(self) -> None:
        if not self.name.strip():
            raise BadReading("a cutout has a name")
        if self.corner_radius.mils <= 0:
            raise BadReading(f"{self.name}: a square corner is not a cutout")
        try:
            shape = self.shape
        except BadReading as refusal:
            raise BadReading(f"{self.name}: {refusal}") from None
        if shape.is_round and self.kind.is_pressure_boundary:
            raise BadReading(f"{self.name}: a pressure boundary hole is not a circle")

    @property
    def shape(self) -> RoundedRect:
        return RoundedRect(self.centre, self.along, self.around, self.corner_radius)

    @property
    def is_round(self) -> bool:
        return self.shape.is_round

    @property
    def keep_out(self) -> Length:
        band = self.corner_radius * KEEP_OUT_FACTOR
        return max(band, KEEP_OUT_FLOOR)

    def edge_distance(self, point: Offset) -> Length:
        return self.shape.edge_distance(point)

    def contains(self, point: Offset) -> bool:
        return self.shape.contains(point)

    def near_a_corner(self, point: Offset) -> bool:
        return self.shape.off_a_corner(point)

    def in_keep_out(self, point: Offset) -> bool:
        """Whether a point is close enough to be assessed under the cutout rules."""
        return self.edge_distance(point) <= self.keep_out

    def __str__(self) -> str:
        return f"{self.name} ({self.kind})"
