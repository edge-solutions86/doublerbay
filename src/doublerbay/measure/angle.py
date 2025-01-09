"""Angles, and the one thing the desk actually wants out of them.

Damage that has a direction has to be reported with one, because on a
pressurised barrel the direction is most of the answer. Hoop stress runs
circumferentially and it is roughly twice the axial stress, so a crack lying
along the body axis is being pulled open by the larger of the two and a crack
lying around the barrel is not. The same half inch of crack is a hold in one
orientation and a monitor in the other.

A crack has an axis and not a heading. Ninety five degrees and two hundred and
seventy five degrees are the same crack, so orientation folds onto a half turn
before anything is decided from it.
"""

from __future__ import annotations

from decimal import Decimal
from enum import Enum
from functools import total_ordering

from doublerbay.errors import BadReading, WrongMeasure

TENTHS_PER_DEGREE = 10
FULL_TURN = 360 * TENTHS_PER_DEGREE
HALF_TURN = 180 * TENTHS_PER_DEGREE


@total_ordering
class Angle:
    """A bearing on the skin surface, to a tenth of a degree, folded into a full turn.

    Zero runs aft along the body axis and ninety runs around the barrel, which
    is how the damage report form is laid out.
    """

    __slots__ = ("_tenths",)

    def __init__(self, tenths: int) -> None:
        if not isinstance(tenths, int) or isinstance(tenths, bool):
            raise WrongMeasure(f"an angle is a whole number of tenths, got {tenths!r}")
        self._tenths = tenths % FULL_TURN

    @classmethod
    def of_degrees(cls, value: Decimal | int | str) -> Angle:
        scaled = Decimal(value) * TENTHS_PER_DEGREE
        rounded = int(scaled.to_integral_value())
        if scaled != rounded:
            raise BadReading(f"{value} deg is finer than a tenth")
        return cls(rounded)

    @property
    def tenths(self) -> int:
        return self._tenths

    @property
    def degrees(self) -> Decimal:
        return (Decimal(self._tenths) / TENTHS_PER_DEGREE).quantize(Decimal("0.1"))

    def folded(self) -> Angle:
        """The same line, reported on the half turn a crack axis lives on."""
        return Angle(self._tenths % HALF_TURN)

    def separation(self, other: Angle) -> Angle:
        """The smaller of the two ways round between two bearings."""
        gap = abs(self._tenths - other._tenths)
        return Angle(min(gap, FULL_TURN - gap))

    def axis_separation(self, other: Angle) -> Angle:
        """The same, for two lines that have no direction."""
        gap = abs(self.folded()._tenths - other.folded()._tenths)
        return Angle(min(gap, HALF_TURN - gap))

    def __add__(self, other: Angle) -> Angle:
        if not isinstance(other, Angle):
            return NotImplemented
        return Angle(self._tenths + other._tenths)

    def __sub__(self, other: Angle) -> Angle:
        if not isinstance(other, Angle):
            return NotImplemented
        return Angle(self._tenths - other._tenths)

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, Angle):
            return NotImplemented
        return self._tenths == other._tenths

    def __lt__(self, other: Angle) -> bool:
        if not isinstance(other, Angle):
            return NotImplemented
        return self._tenths < other._tenths

    def __hash__(self) -> int:
        return hash(("Angle", self._tenths))

    def __repr__(self) -> str:
        return f"Angle.of_degrees('{self.degrees}')"

    def __str__(self) -> str:
        return f"{self.degrees} deg"


class Orientation(Enum):
    """What a directional damage is lying along, once its axis is folded."""

    LONGITUDINAL = "longitudinal"
    CIRCUMFERENTIAL = "circumferential"
    OBLIQUE = "oblique"


# Anything within this of an axis is called out as lying on it. Wider than the
# report form's own resolution on purpose, because a tape measure held against
# a curved skin does not do better.
AXIS_BAND = Angle.of_degrees(15)

LONGITUDINAL = Angle.of_degrees(0)
CIRCUMFERENTIAL = Angle.of_degrees(90)


def orientation_of(bearing: Angle) -> Orientation:
    """Which axis a bearing is lying along, or neither."""
    if bearing.axis_separation(LONGITUDINAL) <= AXIS_BAND:
        return Orientation.LONGITUDINAL
    if bearing.axis_separation(CIRCUMFERENTIAL) <= AXIS_BAND:
        return Orientation.CIRCUMFERENTIAL
    return Orientation.OBLIQUE


COUNTERSINK_100 = Angle.of_degrees(100)
COUNTERSINK_82 = Angle.of_degrees(82)
