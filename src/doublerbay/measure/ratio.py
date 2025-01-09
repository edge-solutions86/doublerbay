"""Dimensionless measures, and the two the manual keeps arguing with.

A dent is judged on how sharp it is, not only on how deep it is, and sharpness
is depth over the diameter it spreads across. A quarter inch dip across six
inches of skin is nothing. The same quarter inch across half an inch is a
crease and the panel comes off.

A blend out is judged on its runout, written the way a machinist writes it, as
a run per unit of depth. Twenty to one means half a thousandth of depth costs
you ten thousandths of run in every direction. The trap that catches people is
that the bigger number is the gentler blend, so a taper of 30:1 satisfies a
requirement for 20:1 and never the other way round.
"""

from __future__ import annotations

import re
from decimal import Decimal
from functools import total_ordering

from doublerbay.errors import BadReading
from doublerbay.measure.length import Length

PLACES = Decimal("0.0001")

_TAPER = re.compile(r"^(\d+)\s*:\s*1$")


@total_ordering
class Ratio:
    """One length over another, carried to four places."""

    __slots__ = ("_value",)

    def __init__(self, value: Decimal | int | str) -> None:
        self._value = Decimal(value).quantize(PLACES)

    @classmethod
    def between(cls, numerator: Length, denominator: Length) -> Ratio:
        if denominator.mils == 0:
            raise ZeroDivisionError("a ratio over a zero length says nothing")
        return cls(Decimal(numerator.mils) / Decimal(denominator.mils))

    @property
    def value(self) -> Decimal:
        return self._value

    @property
    def percent(self) -> Decimal:
        return (self._value * 100).quantize(Decimal("0.01"))

    def of(self, length: Length) -> Length:
        return length * self._value

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, Ratio):
            return NotImplemented
        return self._value == other._value

    def __lt__(self, other: Ratio) -> bool:
        if not isinstance(other, Ratio):
            return NotImplemented
        return self._value < other._value

    def __hash__(self) -> int:
        return hash(("Ratio", self._value))

    def __repr__(self) -> str:
        return f"Ratio('{self._value}')"

    def __str__(self) -> str:
        return f"{self.percent}%"


@total_ordering
class Taper:
    """A runout slope, written as a run per one of depth."""

    __slots__ = ("_run",)

    def __init__(self, run: int) -> None:
        if run < 1:
            raise BadReading(f"a taper runs at least one to one, got {run}")
        self._run = run

    @classmethod
    def parse(cls, text: str) -> Taper:
        match = _TAPER.match(text.strip())
        if match is None:
            raise BadReading(f"{text!r} is not a taper, write it as 20:1")
        return cls(int(match.group(1)))

    @property
    def run(self) -> int:
        return self._run

    def runout(self, depth: Length) -> Length:
        """How far out from the deepest point the blend has to feather."""
        return depth * self._run

    def is_gentler_than(self, other: Taper) -> bool:
        return self._run > other._run

    def satisfies(self, required: Taper) -> bool:
        """A gentler blend meets a requirement for a sharper one."""
        return self._run >= required._run

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, Taper):
            return NotImplemented
        return self._run == other._run

    def __lt__(self, other: Taper) -> bool:
        if not isinstance(other, Taper):
            return NotImplemented
        return self._run < other._run

    def __hash__(self) -> int:
        return hash(("Taper", self._run))

    def __repr__(self) -> str:
        return f"Taper({self._run})"

    def __str__(self) -> str:
        return f"{self._run}:1"


def sharpness(depth: Length, across: Length) -> Ratio:
    """Depth over the width it spreads across, which is how a dent is graded."""
    return Ratio.between(depth, across)
