"""Distance measured on the skin, not through it.

Two dents four inches apart on a drawing are further apart than that on the
aeroplane, because the drawing is flat and the barrel is not. Every separation
rule in the manual is written for a tape laid on the skin, so distances here
are on the developed surface: one component along the body and one component
around it, with the arc taken as the measurement rather than the chord.

The straight line between two points on that developed surface is the one the
separation rules mean. It is computed in whole mils with an integer square
root, which keeps it exact enough to sit on a limit boundary without a float
deciding which side of it the damage falls.
"""

from __future__ import annotations

import math
from dataclasses import dataclass
from decimal import Decimal

from doublerbay.measure.angle import Angle
from doublerbay.measure.length import Length


def hypotenuse(first: Length, second: Length) -> Length:
    """The third side, to the nearest mil, without going through a float."""
    squared = first.mils * first.mils + second.mils * second.mils
    root = math.isqrt(squared)
    # isqrt truncates. Take the nearer of the two candidates so a limit that
    # lands on the boundary is not quietly cleared by a rounding down.
    if (root + 1) * (root + 1) - squared < squared - root * root:
        root += 1
    return Length(root)


@dataclass(frozen=True, slots=True)
class Offset:
    """A separation on the developed skin, split into its two components."""

    along: Length
    around: Length

    @property
    def direct(self) -> Length:
        """The tape measure answer, which is what a separation limit is written for."""
        return hypotenuse(abs(self.along), abs(self.around))

    @property
    def is_zero(self) -> bool:
        return self.along.mils == 0 and self.around.mils == 0

    @property
    def bearing(self) -> Angle:
        """Which way the offset points, zero along the body and ninety around it."""
        if self.is_zero:
            raise ValueError("an offset of nothing has no bearing")
        radians = math.atan2(self.around.mils, self.along.mils)
        return Angle(round(math.degrees(radians) * 10))

    def within(self, radius: Length) -> bool:
        """Whether the direct distance is inside a radius, without taking a root.

        Comparing squares avoids the rounding in the root entirely, which
        matters when the radius is a limit and the answer decides a disposition.
        """
        reach = self.along.mils * self.along.mils + self.around.mils * self.around.mils
        return reach <= radius.mils * radius.mils

    def scaled(self, factor: Decimal | str) -> Offset:
        ratio = Decimal(factor)
        return Offset(self.along * ratio, self.around * ratio)

    def __add__(self, other: Offset) -> Offset:
        if not isinstance(other, Offset):
            return NotImplemented
        return Offset(self.along + other.along, self.around + other.around)

    def __neg__(self) -> Offset:
        return Offset(-self.along, -self.around)

    def __str__(self) -> str:
        return f"{self.along.inches} along, {self.around.inches} around"


ZERO_OFFSET = Offset(Length(0), Length(0))


def arc_length(radius: Length, subtended: Angle) -> Length:
    """How far round the barrel a given angle takes you at a given radius."""
    if radius.mils <= 0:
        raise ValueError("an arc needs a radius")
    turn = Decimal(subtended.tenths) / Decimal(3600)
    circumference = Decimal(radius.mils) * 2 * Decimal(str(math.pi))
    return Length(int((circumference * turn).to_integral_value()))


def subtended_angle(radius: Length, along_arc: Length) -> Angle:
    """The angle an arc of that length covers, which is the inverse of the above."""
    if radius.mils <= 0:
        raise ValueError("an arc needs a radius")
    circumference = Decimal(radius.mils) * 2 * Decimal(str(math.pi))
    turn = Decimal(along_arc.mils) / circumference
    return Angle(int((turn * 3600).to_integral_value()))
