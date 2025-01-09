"""Fastener sizes, and the habit of measuring distance in diameters.

Nothing on a repair drawing is dimensioned in inches when it can be dimensioned
in D. Edge distance is two D, pitch is four D, row spacing is four D. The point
of that is that the rule survives a change of fastener size: move from a dash
five to a dash six and every spacing on the drawing grows with it and none of
the words change.

Sizes are dash numbers, which are thirty seconds of an inch. A dash five is
five thirty seconds, which is 0.156 to the nearest thousandth and 0.15625 in
truth, and the third place is where that rounding has to be pinned down once
rather than argued about per module.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from decimal import Decimal
from functools import total_ordering

from doublerbay.errors import BadReading
from doublerbay.measure.length import MILS_PER_INCH, Length

THIRTY_SECONDS = Decimal(32)

_DASH = re.compile(r"^-?(\d{1,2})$")

# What the shop actually puts through a skin. Anything outside this is a
# machined fitting and not a repair fastener.
STOCKED_DASHES: tuple[int, ...] = (3, 4, 5, 6, 8, 10, 12)

MIN_EDGE_DISTANCE_D = Decimal("2.0")
PREFERRED_EDGE_DISTANCE_D = Decimal("2.5")
MIN_PITCH_D = Decimal("4.0")
MIN_ROW_SPACING_D = Decimal("4.0")


@total_ordering
@dataclass(frozen=True, slots=True)
class FastenerSize:
    """A shank diameter, carried as the dash number it is called out by."""

    dash: int

    def __post_init__(self) -> None:
        if self.dash < 1:
            raise BadReading(f"a dash number counts thirty seconds, got {self.dash}")

    @classmethod
    def parse(cls, text: str) -> FastenerSize:
        match = _DASH.match(text.strip())
        if match is None:
            raise BadReading(f"{text!r} is not a dash number")
        return cls(int(match.group(1)))

    @property
    def diameter(self) -> Length:
        """The nominal shank, rounded to the thousandth the drawing carries."""
        exact = Decimal(self.dash) / THIRTY_SECONDS * MILS_PER_INCH
        return Length(int(exact.to_integral_value()))

    @property
    def exact_diameter(self) -> Decimal:
        """The shank before rounding, for anything that accumulates."""
        return Decimal(self.dash) / THIRTY_SECONDS

    def diameters(self, count: Decimal | int | str) -> Length:
        """A distance called out in D, turned into a length."""
        exact = self.exact_diameter * Decimal(count) * MILS_PER_INCH
        return Length(int(exact.to_integral_value()))

    def in_diameters(self, distance: Length) -> Decimal:
        """A measured distance, read back in D so it can meet a rule written in D."""
        inches = Decimal(distance.mils) / MILS_PER_INCH
        return (inches / self.exact_diameter).quantize(Decimal("0.01"))

    @property
    def min_edge_distance(self) -> Length:
        return self.diameters(MIN_EDGE_DISTANCE_D)

    @property
    def preferred_edge_distance(self) -> Length:
        return self.diameters(PREFERRED_EDGE_DISTANCE_D)

    @property
    def min_pitch(self) -> Length:
        return self.diameters(MIN_PITCH_D)

    @property
    def min_row_spacing(self) -> Length:
        return self.diameters(MIN_ROW_SPACING_D)

    def __lt__(self, other: FastenerSize) -> bool:
        if not isinstance(other, FastenerSize):
            return NotImplemented
        return self.dash < other.dash

    def __str__(self) -> str:
        return f"-{self.dash}"


def stocked() -> tuple[FastenerSize, ...]:
    return tuple(FastenerSize(dash) for dash in STOCKED_DASHES)


def is_stocked(size: FastenerSize) -> bool:
    return size.dash in STOCKED_DASHES


def next_size_up(size: FastenerSize) -> FastenerSize:
    """The oversize the shop goes to when a hole has been worked.

    A hole that has been reamed out or has an elongated bore is not repaired by
    putting the same fastener back in it.
    """
    for dash in STOCKED_DASHES:
        if dash > size.dash:
            return FastenerSize(dash)
    raise BadReading(f"{size} is the largest stocked fastener")
