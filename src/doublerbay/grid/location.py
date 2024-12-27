"""Where the damage is, written the way the mechanic wrote it.

Nobody measures a body station on an aeroplane. They find the frame with the
tape, read the number off the stencil, and measure aft of it. So a location is
a reference member and an offset from it, and turning that into an absolute
station is a later step that needs the type definition loaded.

Keeping the reference member means the record survives a rebuild of the grid.
It also means two people who measured from different frames end up with
different records of the same point, which is the reason the grid gets asked to
normalise a location before anything is compared.
"""

from __future__ import annotations

from dataclasses import dataclass

from doublerbay.grid.ata import Chapter
from doublerbay.grid.lattice import Frame, Stringer
from doublerbay.grid.wing import Rib, Spar, Surface
from doublerbay.grid.zone import Side
from doublerbay.measure.length import Length

# No frame bay on the V320 is anywhere near this wide, so an offset past it
# means the wrong reference member was written on the card. The real check
# happens against the loaded grid, where the actual pitch is known.
SANITY_OFFSET = Length.of_inches("60.000")


def _sane(offset: Length, what: str) -> None:
    if abs(offset) > SANITY_OFFSET:
        raise ValueError(f"{offset} {what} is further than any bay on this aircraft")


@dataclass(frozen=True, slots=True)
class FuselagePoint:
    """A point on the barrel, as an offset from a frame and a stringer."""

    frame: Frame
    aft_of_frame: Length
    stringer: Stringer
    below_stringer: Length

    def __post_init__(self) -> None:
        _sane(self.aft_of_frame, "aft of frame")
        _sane(self.below_stringer, "below stringer")

    @property
    def chapter(self) -> Chapter:
        return Chapter.FUSELAGE

    @property
    def side(self) -> Side:
        return self.stringer.side

    @property
    def is_on_a_frame(self) -> bool:
        return self.aft_of_frame.mils == 0

    @property
    def is_on_a_stringer(self) -> bool:
        return self.below_stringer.mils == 0

    def mirrored(self) -> FuselagePoint:
        return FuselagePoint(
            self.frame, self.aft_of_frame, self.stringer.mirrored(), self.below_stringer
        )

    def __str__(self) -> str:
        aft = self._written(self.aft_of_frame, "aft", "fwd")
        down = self._written(self.below_stringer, "down", "up")
        return f"{self.frame} {aft}, {self.stringer} {down}"

    @staticmethod
    def _written(offset: Length, positive: str, negative: str) -> str:
        if offset.mils == 0:
            return "on"
        word = positive if offset.mils > 0 else negative
        return f"{abs(offset).inches} {word}"


@dataclass(frozen=True, slots=True)
class WingPoint:
    """A point on a wing surface, as an offset from a rib and a spar."""

    rib: Rib
    outboard_of_rib: Length
    spar: Spar
    aft_of_spar: Length
    surface: Surface

    def __post_init__(self) -> None:
        _sane(self.outboard_of_rib, "outboard of rib")
        _sane(self.aft_of_spar, "aft of spar")
        if self.aft_of_spar.mils < 0:
            raise ValueError("a point aft of a spar is not forward of it")

    @property
    def chapter(self) -> Chapter:
        return Chapter.WINGS

    @property
    def side(self) -> Side:
        return self.rib.side

    @property
    def is_tension_critical(self) -> bool:
        return self.surface.is_tension_critical

    def mirrored(self) -> WingPoint:
        return WingPoint(
            self.rib.mirrored(),
            self.outboard_of_rib,
            self.spar,
            self.aft_of_spar,
            self.surface,
        )

    def __str__(self) -> str:
        return (
            f"{self.rib} {self.outboard_of_rib.inches} outbd, "
            f"{self.spar} {self.aft_of_spar.inches} aft, {self.surface}"
        )


Point = FuselagePoint | WingPoint


def chapter_of(point: Point) -> Chapter:
    return point.chapter


def same_side(first: Point, second: Point) -> bool:
    return first.side is second.side
