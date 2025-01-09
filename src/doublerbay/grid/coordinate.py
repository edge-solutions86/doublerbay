"""The three rulers the airframe is dimensioned against.

Station runs aft from a datum somewhere ahead of the nose, waterline runs up
from a datum somewhere below the keel, and buttock line runs outboard from the
centreline in both directions. All three are inches, all three are written on
the same drawing, and none of them means anything without its letter.

They are separate types on purpose. A station of 727.5 and a waterline of 727.5
are not the same place and comparing them is always a mistake, so the comparison
raises instead of quietly answering. That has caught more transcription errors
off a damage report than any validation rule in this package.
"""

from __future__ import annotations

import re
from decimal import Decimal, InvalidOperation
from functools import total_ordering
from typing import Self

from doublerbay.errors import BadReading, WrongMeasure
from doublerbay.measure.length import Length

_STATION = re.compile(r"^(?:STA\s*)?([+-]?[\d.]+)$", re.IGNORECASE)
_WATERLINE = re.compile(r"^(?:WL\s*)?([+-]?[\d.]+)$", re.IGNORECASE)
_BUTTOCK = re.compile(r"^(?:([LR])?BL\s*)?([+-]?[\d.]+)$", re.IGNORECASE)


def _inches(text: str, what: str) -> Length:
    """A decimal that is not a number is a bad reading, not an arithmetic fault."""
    try:
        return Length.of_inches(Decimal(text))
    except InvalidOperation:
        raise BadReading(f"{text!r} is not a {what}") from None


@total_ordering
class Axis:
    """One reading on one ruler. Subclasses differ only in what they are called."""

    mark = "AXIS"
    __slots__ = ("_at",)

    def __init__(self, at: Length) -> None:
        if not isinstance(at, Length):
            raise WrongMeasure(f"a coordinate is a length, got {at!r}")
        self._at = at

    @classmethod
    def of_inches(cls, value: Decimal | int | str) -> Self:
        return cls(Length.of_inches(value))

    @property
    def at(self) -> Length:
        return self._at

    @property
    def inches(self) -> Decimal:
        return self._at.inches

    def offset(self, distance: Length) -> Self:
        return type(self)(self._at + distance)

    def gap_to(self, other: Axis) -> Length:
        self._same_ruler(other)
        return abs(other._at - self._at)

    def _same_ruler(self, other: Axis) -> None:
        if type(self) is not type(other):
            raise WrongMeasure(f"{self.mark} and {other.mark} are different rulers")

    def __eq__(self, other: object) -> bool:
        if not isinstance(other, Axis):
            return NotImplemented
        if type(self) is not type(other):
            return False
        return self._at == other._at

    def __lt__(self, other: Axis) -> bool:
        if not isinstance(other, Axis):
            return NotImplemented
        self._same_ruler(other)
        return self._at < other._at

    def __hash__(self) -> int:
        return hash((self.mark, self._at))

    def __repr__(self) -> str:
        return f"{type(self).__name__}.of_inches('{self.inches}')"

    def __str__(self) -> str:
        return f"{self.mark} {self.inches}"


class Station(Axis):
    """Distance aft of the fuselage datum."""

    mark = "STA"
    __slots__ = ()

    @classmethod
    def parse(cls, text: str) -> Station:
        match = _STATION.match(text.strip())
        if match is None:
            raise BadReading(f"{text!r} is not a station")
        return cls(_inches(match.group(1), "station"))


class Waterline(Axis):
    """Height above the vertical datum."""

    mark = "WL"
    __slots__ = ()

    @classmethod
    def parse(cls, text: str) -> Waterline:
        match = _WATERLINE.match(text.strip())
        if match is None:
            raise BadReading(f"{text!r} is not a waterline")
        return cls(_inches(match.group(1), "waterline"))


class ButtockLine(Axis):
    """Distance outboard of the centreline, negative to the left."""

    mark = "BL"
    __slots__ = ()

    @classmethod
    def parse(cls, text: str) -> ButtockLine:
        match = _BUTTOCK.match(text.strip())
        if match is None:
            raise BadReading(f"{text!r} is not a buttock line")
        hand = match.group(1)
        reading = _inches(match.group(2), "buttock line")
        if hand and hand.upper() == "L":
            reading = -abs(reading)
        elif hand and hand.upper() == "R":
            reading = abs(reading)
        return cls(reading)

    @property
    def is_left(self) -> bool:
        return self._at.mils < 0

    @property
    def is_centreline(self) -> bool:
        return self._at.mils == 0

    def mirrored(self) -> ButtockLine:
        return ButtockLine(-self._at)

    def __str__(self) -> str:
        if self.is_centreline:
            return "BL 0.000"
        hand = "L" if self.is_left else "R"
        return f"{hand}BL {abs(self._at).inches}"
