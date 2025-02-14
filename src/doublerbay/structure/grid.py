"""Where the members actually are, which is what turns a written location into a place.

A damage card says FR41 plus twelve and a half aft. Nothing can be decided from
that until somebody knows that FR41 is at station 620 and that the next frame
back is at station 640, because the twelve and a half is either most of the way
across the bay or a third of it depending on the pitch, and the margin to the
nearest member is what every limit table is keyed on.

So this holds the two ladders, frames against stations and stringers against
arc from the crown, and answers three questions with them: where a point is in
absolute terms, which bay it falls in, and how far it is from the nearest
member bounding that bay. Everything downstream is built on those three.
"""

from __future__ import annotations

from bisect import bisect_right
from dataclasses import dataclass

from doublerbay.errors import BadReading, NotOnFile
from doublerbay.grid.coordinate import Station
from doublerbay.grid.lattice import Bay, Frame, Stringer
from doublerbay.grid.location import FuselagePoint
from doublerbay.grid.surface import Offset
from doublerbay.grid.zone import Side
from doublerbay.measure.length import Length
from doublerbay.structure.joint import JointMember


@dataclass(frozen=True, slots=True)
class FrameStation:
    """One frame and the station it sits at."""

    frame: Frame
    station: Station

    def __str__(self) -> str:
        return f"{self.frame} at {self.station}"


@dataclass(frozen=True, slots=True)
class StringerLine:
    """One stringer and how far round the barrel it runs, measured from the crown."""

    stringer: Stringer
    around: Length

    def __post_init__(self) -> None:
        if self.around.mils < 0:
            raise BadReading(f"{self.stringer} cannot be above the crown")

    def __str__(self) -> str:
        return f"{self.stringer} at {self.around.inches} round"


@dataclass(frozen=True, slots=True)
class Margin:
    """How far a point is off the nearest member, and which member that is."""

    member: JointMember
    distance: Length

    def __str__(self) -> str:
        return f"{self.distance.inches} off {self.member}"


@dataclass(frozen=True, slots=True)
class FuselageGrid:
    """The barrel's two ladders, in order."""

    frames: tuple[FrameStation, ...]
    stringers: tuple[StringerLine, ...]

    def __post_init__(self) -> None:
        if len(self.frames) < 2:
            raise BadReading("a grid needs at least two frames")
        if len(self.stringers) < 2:
            raise BadReading("a grid needs at least two stringers")
        numbers = [entry.frame.number for entry in self.frames]
        stations = [entry.station.at.mils for entry in self.frames]
        if numbers != sorted(numbers) or len(set(numbers)) != len(numbers):
            raise BadReading("frames are listed forward to aft with no repeats")
        if stations != sorted(stations) or len(set(stations)) != len(stations):
            raise BadReading("frame stations climb aft with no two on the same station")
        rungs = [entry.stringer.number for entry in self.stringers]
        arcs = [entry.around.mils for entry in self.stringers]
        if rungs != sorted(rungs) or len(set(rungs)) != len(rungs):
            raise BadReading("stringers are listed crown down with no repeats")
        if arcs != sorted(arcs) or len(set(arcs)) != len(arcs):
            raise BadReading("stringer arcs climb from the crown with no two together")

    @property
    def _stations(self) -> list[int]:
        return [entry.station.at.mils for entry in self.frames]

    @property
    def _arcs(self) -> list[int]:
        return [entry.around.mils for entry in self.stringers]

    def station_of(self, frame: Frame) -> Station:
        for entry in self.frames:
            if entry.frame == frame:
                return entry.station
        raise NotOnFile(f"{frame} is not on this grid")

    def around_of(self, stringer: Stringer) -> Length:
        for entry in self.stringers:
            if entry.stringer.number == stringer.number:
                return entry.around
        raise NotOnFile(f"{stringer} is not on this grid")

    def frame_pitch_aft_of(self, frame: Frame) -> Length:
        for near, far in zip(self.frames, self.frames[1:], strict=False):
            if near.frame == frame:
                return far.station.at - near.station.at
        raise NotOnFile(f"{frame} has no frame aft of it on this grid")

    def stringer_pitch_below(self, stringer: Stringer) -> Length:
        for upper, lower in zip(self.stringers, self.stringers[1:], strict=False):
            if upper.stringer.number == stringer.number:
                return lower.around - upper.around
        raise NotOnFile(f"{stringer} has no stringer below it on this grid")

    def absolute(self, point: FuselagePoint) -> Offset:
        """The point in barrel coordinates, aft of the datum and round from the crown."""
        along = self.station_of(point.frame).at + point.aft_of_frame
        around = self.around_of(point.stringer) + point.below_stringer
        return Offset(along, around)

    def _bracket_frames(self, along: Length) -> tuple[FrameStation, FrameStation]:
        index = bisect_right(self._stations, along.mils) - 1
        if index < 0 or index >= len(self.frames) - 1:
            raise BadReading(f"{along.inches} is off the frames this grid carries")
        return self.frames[index], self.frames[index + 1]

    def _bracket_stringers(self, around: Length) -> tuple[StringerLine, StringerLine]:
        index = bisect_right(self._arcs, around.mils) - 1
        if index < 0 or index >= len(self.stringers) - 1:
            raise BadReading(f"{around.inches} is off the stringers this grid carries")
        return self.stringers[index], self.stringers[index + 1]

    def bay_of(self, point: FuselagePoint) -> Bay:
        """Which single bay a written location actually falls in."""
        here = self.absolute(point)
        forward, aft = self._bracket_frames(here.along)
        upper, lower = self._bracket_stringers(here.around)
        side = point.side if point.side is not Side.CENTRE else Side.LEFT
        return Bay(
            forward.frame,
            aft.frame,
            self._hand(upper.stringer, side),
            self._hand(lower.stringer, side),
        )

    @staticmethod
    def _hand(stringer: Stringer, side: Side) -> Stringer:
        if stringer.is_crown:
            return stringer
        return Stringer(stringer.number, side)

    def margin_of(self, point: FuselagePoint) -> Margin:
        """The nearest of the four members bounding the bay, and how far off it is."""
        here = self.absolute(point)
        forward, aft = self._bracket_frames(here.along)
        upper, lower = self._bracket_stringers(here.around)
        side = point.side if point.side is not Side.CENTRE else Side.LEFT
        candidates: list[tuple[int, JointMember]] = [
            ((here.along - forward.station.at).mils, forward.frame),
            ((aft.station.at - here.along).mils, aft.frame),
            ((here.around - upper.around).mils, self._hand(upper.stringer, side)),
            ((lower.around - here.around).mils, self._hand(lower.stringer, side)),
        ]
        distance, member = min(candidates, key=lambda pair: pair[0])
        return Margin(member, Length(distance))

    def normalised(self, point: FuselagePoint) -> FuselagePoint:
        """The same place, rewritten off the frame and stringer that actually bound it.

        Two people measuring the same dent from different members write different
        cards. This is what makes those two cards compare equal.
        """
        bay = self.bay_of(point)
        here = self.absolute(point)
        return FuselagePoint(
            bay.forward,
            here.along - self.station_of(bay.forward).at,
            bay.upper,
            here.around - self.around_of(bay.upper),
        )
