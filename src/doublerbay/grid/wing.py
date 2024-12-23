"""Ribs, spars and the two surfaces, which is the wing version of the same lattice.

A wing box is bounded fore and aft by spars and divided along its span by ribs,
and the panel between them is the wing equivalent of a bay. What has no
equivalent on the barrel is the surface. The upper skin spends the flight in
compression and the lower skin spends it in tension, and tension is what grows
cracks, so the same quarter inch scratch is a monitor on top and a repair
underneath.

Wing stations run outboard from the aircraft centreline, which means the side
of body is not zero and the numbers on the left and the right wing are the same
numbers. The hand is carried alongside and never inferred from the station.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from decimal import Decimal, InvalidOperation
from enum import Enum
from functools import total_ordering

from doublerbay.grid.coordinate import Axis
from doublerbay.grid.zone import Side
from doublerbay.measure.length import Length

_RIB = re.compile(r"^RIB\s*-?\s*(\d{1,2})\s*([LR])$", re.IGNORECASE)
_WING_STATION = re.compile(r"^(?:WS\s*)?([\d.]+)$", re.IGNORECASE)


class WingStation(Axis):
    """Distance outboard of the aircraft centreline, along the wing reference line."""

    mark = "WS"
    __slots__ = ()

    @classmethod
    def parse(cls, text: str) -> WingStation:
        match = _WING_STATION.match(text.strip())
        if match is None:
            raise ValueError(f"{text!r} is not a wing station")
        try:
            value = Decimal(match.group(1))
        except InvalidOperation:
            raise ValueError(f"{text!r} is not a wing station") from None
        return cls(Length.of_inches(value))


class Surface(Enum):
    """Which face of the wing box the skin is on."""

    UPPER = "upper"
    LOWER = "lower"

    @property
    def is_tension_critical(self) -> bool:
        """The lower skin is the one fatigue is written against."""
        return self is Surface.LOWER

    def __str__(self) -> str:
        return self.value


class Spar(Enum):
    """The three spanwise beams the box is built around."""

    FRONT = "front"
    MID = "mid"
    REAR = "rear"

    @property
    def order(self) -> int:
        return {"front": 0, "mid": 1, "rear": 2}[self.value]

    def is_forward_of(self, other: Spar) -> bool:
        return self.order < other.order

    def __str__(self) -> str:
        return f"{self.value} spar"


@total_ordering
@dataclass(frozen=True, slots=True)
class Rib:
    """A chordwise former, numbered outboard from the side of body."""

    number: int
    side: Side

    def __post_init__(self) -> None:
        if not 1 <= self.number <= 40:
            raise ValueError(f"rib {self.number} is not on this wing")
        if self.side is Side.CENTRE:
            raise ValueError("a rib belongs to one wing or the other")

    @classmethod
    def parse(cls, text: str) -> Rib:
        match = _RIB.match(text.strip())
        if match is None:
            raise ValueError(f"{text!r} is not a rib")
        hand = Side.LEFT if match.group(2).upper() == "L" else Side.RIGHT
        return cls(int(match.group(1)), hand)

    def is_inboard_of(self, other: Rib) -> bool:
        if self.side is not other.side:
            raise ValueError("ribs on opposite wings do not compare")
        return self.number < other.number

    def mirrored(self) -> Rib:
        return Rib(self.number, Side.RIGHT if self.side is Side.LEFT else Side.LEFT)

    def __lt__(self, other: Rib) -> bool:
        if not isinstance(other, Rib):
            return NotImplemented
        return (self.side.value, self.number) < (other.side.value, other.number)

    def __str__(self) -> str:
        return f"RIB{self.number}{self.side}"


@dataclass(frozen=True, slots=True)
class WingBay:
    """One panel of wing skin, bounded by two ribs and two spars, on one surface."""

    inboard: Rib
    outboard: Rib
    forward: Spar
    aft: Spar
    surface: Surface

    def __post_init__(self) -> None:
        if self.inboard.side is not self.outboard.side:
            raise ValueError("a wing bay does not cross the centreline")
        if not self.inboard.is_inboard_of(self.outboard):
            raise ValueError(f"{self.inboard} is not inboard of {self.outboard}")
        if not self.forward.is_forward_of(self.aft):
            raise ValueError(f"{self.forward} is not forward of {self.aft}")

    @property
    def side(self) -> Side:
        return self.inboard.side

    @property
    def rib_pitches(self) -> int:
        return self.outboard.number - self.inboard.number

    @property
    def is_tension_critical(self) -> bool:
        return self.surface.is_tension_critical

    def __str__(self) -> str:
        return f"{self.inboard}-{self.outboard} / {self.forward} to {self.aft} / {self.surface}"
