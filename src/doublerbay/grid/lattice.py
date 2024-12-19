"""Frames and stringers, which are what a bay is bounded by.

The barrel is a lattice. Frames run around it at fixed stations and stringers
run along it at fixed positions around the circumference, and the square of
skin between two of each is a bay. Almost every limit in the manual is written
per bay, because a bay is what carries the load between its edges and what a
crack has to cross before it reaches something that will stop it.

Stringers count outwards from the crown, S-1 immediately off the top
centreline and climbing as they come down the side, so S-4 is above S-22 on the
aircraft and below it in the number. Getting that inverted puts damage on the
wrong half of the barrel, so the ordering here is explicit and tested rather
than left to the integer.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from functools import total_ordering

from doublerbay.grid.zone import Side

_STRINGER = re.compile(r"^S-?(\d{1,2})\s*([LR])?$", re.IGNORECASE)
_FRAME = re.compile(r"^(?:FR|FS)\s*-?\s*(\d{1,3})$", re.IGNORECASE)

CROWN = 0
MAX_STRINGER = 48


@total_ordering
@dataclass(frozen=True, slots=True)
class Stringer:
    """A longitudinal stiffener, numbered down from the crown on one hand."""

    number: int
    side: Side = Side.CENTRE

    def __post_init__(self) -> None:
        if not CROWN <= self.number <= MAX_STRINGER:
            raise ValueError(f"S-{self.number} is not on this barrel")
        if self.number == CROWN and self.side is not Side.CENTRE:
            raise ValueError("the crown stringer has no hand")
        if self.number != CROWN and self.side is Side.CENTRE:
            raise ValueError(f"S-{self.number} has to say which hand it is on")

    @classmethod
    def parse(cls, text: str) -> Stringer:
        match = _STRINGER.match(text.strip())
        if match is None:
            raise ValueError(f"{text!r} is not a stringer")
        number = int(match.group(1))
        hand = match.group(2)
        if number == CROWN:
            return cls(CROWN)
        if hand is None:
            raise ValueError(f"{text!r} does not say which hand")
        return cls(number, Side.LEFT if hand.upper() == "L" else Side.RIGHT)

    @property
    def is_crown(self) -> bool:
        return self.number == CROWN

    def mirrored(self) -> Stringer:
        if self.is_crown:
            return self
        return Stringer(self.number, Side.RIGHT if self.side is Side.LEFT else Side.LEFT)

    def is_above(self, other: Stringer) -> bool:
        """Higher on the barrel, which is the lower number."""
        return self.number < other.number

    def spans_to(self, other: Stringer) -> int:
        """How many stringer pitches apart, on the same hand."""
        if self.side is not other.side and not (self.is_crown or other.is_crown):
            raise ValueError("stringers on opposite hands do not span")
        return abs(self.number - other.number)

    def __lt__(self, other: Stringer) -> bool:
        if not isinstance(other, Stringer):
            return NotImplemented
        return (self.number, self.side.value) < (other.number, other.side.value)

    def __str__(self) -> str:
        return f"S-{self.number}" if self.is_crown else f"S-{self.number}{self.side}"


@total_ordering
@dataclass(frozen=True, slots=True)
class Frame:
    """A ring former, identified by its number and located by the type definition."""

    number: int

    def __post_init__(self) -> None:
        if self.number < 1:
            raise ValueError(f"frame numbers start at one, got {self.number}")

    @classmethod
    def parse(cls, text: str) -> Frame:
        match = _FRAME.match(text.strip())
        if match is None:
            raise ValueError(f"{text!r} is not a frame")
        return cls(int(match.group(1)))

    def next_aft(self) -> Frame:
        return Frame(self.number + 1)

    def spans_to(self, other: Frame) -> int:
        return abs(self.number - other.number)

    def __lt__(self, other: Frame) -> bool:
        if not isinstance(other, Frame):
            return NotImplemented
        return self.number < other.number

    def __str__(self) -> str:
        return f"FR{self.number}"


@dataclass(frozen=True, slots=True)
class Bay:
    """One square of skin, bounded forward and aft by frames and above and below by stringers."""

    forward: Frame
    aft: Frame
    upper: Stringer
    lower: Stringer

    def __post_init__(self) -> None:
        if self.forward >= self.aft:
            raise ValueError(f"{self.forward} is not forward of {self.aft}")
        if not self.upper.is_above(self.lower):
            raise ValueError(f"{self.upper} is not above {self.lower}")
        if self.upper.side is not self.lower.side and not self.upper.is_crown:
            raise ValueError("a bay does not straddle the crown")

    @property
    def side(self) -> Side:
        return self.lower.side

    @property
    def frame_pitches(self) -> int:
        return self.forward.spans_to(self.aft)

    @property
    def stringer_pitches(self) -> int:
        return self.upper.spans_to(self.lower)

    @property
    def is_single(self) -> bool:
        """A bay of one pitch each way, which is what a limit is written against."""
        return self.frame_pitches == 1 and self.stringer_pitches == 1

    def __str__(self) -> str:
        return f"{self.forward}-{self.aft} / {self.upper}-{self.lower}"
