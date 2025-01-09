"""Lengths, held in whole thousandths of an inch.

Everything on the shop floor is called out in decimal inches to three places.
Skin thickness is 0.040, edge margin is 0.320, a body station is 727.5. Floats
lose the third place often enough to matter when a limit is exactly 0.040 and
the damage is exactly 0.040, so a length is an integer count of mils and the
decimal conversion happens at the edges.

Fastener and drill callouts come in as fractions instead, so the parser takes
both forms and a mixed number besides.
"""

from __future__ import annotations

import re
from decimal import Decimal
from functools import total_ordering

from doublerbay.errors import BadReading, WrongMeasure

MILS_PER_INCH = 1000
MM_PER_INCH = Decimal("25.4")

_DECIMAL = re.compile(r"^[+-]?(?:\d+\.?\d*|\.\d+)$")
_FRACTION = re.compile(r"^([+-]?)(?:(\d+)[ \-])?(\d+)/(\d+)$")


@total_ordering
class Length:
    """A distance along or through the skin, to the nearest thousandth of an inch."""

    __slots__ = ("_mils",)

    def __init__(self, mils: int) -> None:
        if not isinstance(mils, int) or isinstance(mils, bool):
            raise WrongMeasure(f"a length is a whole number of mils, got {mils!r}")
        self._mils = mils

    @classmethod
    def of_inches(cls, value: Decimal | int | str) -> Length:
        d = Decimal(value) if not isinstance(value, Decimal) else value
        scaled = d * MILS_PER_INCH
        rounded = int(scaled.to_integral_value())
        if scaled != rounded:
            raise BadReading(f"{d} in is finer than a thousandth")
        return cls(rounded)

    @classmethod
    def of_mm(cls, value: Decimal | int | str) -> Length:
        d = Decimal(value) if not isinstance(value, Decimal) else value
        return cls(int((d / MM_PER_INCH * MILS_PER_INCH).to_integral_value()))

    @property
    def mils(self) -> int:
        return self._mils

    @property
    def inches(self) -> Decimal:
        return (Decimal(self._mils) / MILS_PER_INCH).quantize(Decimal("0.001"))

    @property
    def mm(self) -> Decimal:
        return (self.inches * MM_PER_INCH).quantize(Decimal("0.01"))

    def __add__(self, other: Length) -> Length:
        if not isinstance(other, Length):
            return NotImplemented
        return Length(self._mils + other._mils)

    def __sub__(self, other: Length) -> Length:
        if not isinstance(other, Length):
            return NotImplemented
        return Length(self._mils - other._mils)

    def __mul__(self, factor: int | Decimal) -> Length:
        scaled = Decimal(self._mils) * Decimal(factor)
        return Length(int(scaled.to_integral_value()))

    __rmul__ = __mul__

    def __neg__(self) -> Length:
        return Length(-self._mils)

    def __abs__(self) -> Length:
        return Length(abs(self._mils))

    def __bool__(self) -> bool:
        return self._mils != 0

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, Length):
            return NotImplemented
        return self._mils == other._mils

    def __lt__(self, other: Length) -> bool:
        if not isinstance(other, Length):
            return NotImplemented
        return self._mils < other._mils

    def __hash__(self) -> int:
        return hash(("Length", self._mils))

    def __repr__(self) -> str:
        return f"Length.of_inches('{self.inches}')"

    def __str__(self) -> str:
        return f"{self.inches} in"


ZERO = Length(0)


def parse_length(text: str) -> Length:
    """Read a callout in either of the two forms the shop writes.

    ``0.040`` and ``727.5`` are decimal inches. ``3/32``, ``1-1/4`` and
    ``1 1/4`` are the fractional callouts that come off a drill chart.
    """
    cleaned = text.strip()
    if not cleaned:
        raise BadReading("empty length")
    if _DECIMAL.match(cleaned):
        return Length.of_inches(Decimal(cleaned))
    match = _FRACTION.match(cleaned)
    if match is None:
        raise BadReading(f"{text!r} is not a length callout")
    sign, whole, numerator, denominator = match.groups()
    if int(denominator) == 0:
        raise BadReading(f"{text!r} divides by zero")
    total = Decimal(whole or 0) + Decimal(numerator) / Decimal(denominator)
    if sign == "-":
        total = -total
    scaled = total * MILS_PER_INCH
    return Length(int(scaled.to_integral_value()))
