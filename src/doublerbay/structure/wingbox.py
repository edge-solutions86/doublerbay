"""The wing box, which is the same idea as the barrel with one thing that is not.

Ribs take the place of frames and spars take the place of stringers, and a card
is written the same way: a member and an offset from it. What breaks the
symmetry is that the wing tapers. The chord at the side of body is more than
twice the chord at the tip, so the front and rear spars are not a fixed distance
apart, and how far aft of the front spar a damage sits means a different
fraction of the box depending on how far outboard it is.

So the chord is carried per rib and interpolated between them, and the spar
positions come out of that rather than out of a constant. Getting that wrong
puts a damage in the wrong half of the box, and the two halves have different
limits because the rear spar carries the flap loads.
"""

from __future__ import annotations

from bisect import bisect_right
from dataclasses import dataclass, field
from decimal import Decimal

from doublerbay.errors import BadReading, NotOnFile
from doublerbay.grid.location import WingPoint
from doublerbay.grid.surface import Offset
from doublerbay.grid.wing import Rib, Spar, Surface, WingBay, WingStation
from doublerbay.grid.zone import Side
from doublerbay.ids import PanelId
from doublerbay.measure.length import Length
from doublerbay.structure.footprint import InstalledDoubler
from doublerbay.structure.panel import WingPanel
from doublerbay.structure.thickness import ThicknessMap

SparMember = Spar
RibOrSpar = Rib | Spar


@dataclass(frozen=True, slots=True)
class RibStation:
    """One rib, where it sits on the span and how long the chord is there."""

    rib: Rib
    at: WingStation
    chord: Length

    def __post_init__(self) -> None:
        if self.chord.mils <= 0:
            raise BadReading(f"{self.rib} has no chord")

    def __str__(self) -> str:
        return f"{self.rib} at {self.at}, chord {self.chord.inches}"


@dataclass(frozen=True, slots=True)
class WingMargin:
    """The nearest member bounding the panel, and how far off it is."""

    member: RibOrSpar
    distance: Length

    def __str__(self) -> str:
        return f"{self.distance.inches} off {self.member}"


@dataclass(frozen=True, slots=True)
class WingGrid:
    """One wing's ribs, with the spar positions expressed as a fraction of chord."""

    side: Side
    ribs: tuple[RibStation, ...]
    spar_fractions: dict[Spar, str]

    def __post_init__(self) -> None:
        if len(self.ribs) < 2:
            raise BadReading("a wing grid needs at least two ribs")
        numbers = [entry.rib.number for entry in self.ribs]
        spans = [entry.at.at.mils for entry in self.ribs]
        if numbers != sorted(numbers) or len(set(numbers)) != len(numbers):
            raise BadReading("ribs are listed inboard to outboard with no repeats")
        if spans != sorted(spans) or len(set(spans)) != len(spans):
            raise BadReading("rib stations climb outboard with no two together")
        if any(entry.rib.side is not self.side for entry in self.ribs):
            raise BadReading("every rib on a wing grid belongs to that wing")
        for spar in (Spar.FRONT, Spar.REAR):
            if spar not in self.spar_fractions:
                raise BadReading(f"a wing grid places the {spar}")

    @property
    def _spans(self) -> list[int]:
        return [entry.at.at.mils for entry in self.ribs]

    def station_of(self, rib: Rib) -> WingStation:
        return self._entry(rib).at

    def chord_of(self, rib: Rib) -> Length:
        return self._entry(rib).chord

    def _entry(self, rib: Rib) -> RibStation:
        for entry in self.ribs:
            if entry.rib == rib:
                return entry
        raise NotOnFile(f"{rib} is not on this wing grid")

    def chord_at(self, span: Length) -> Length:
        """The chord anywhere along the span, straight lined between ribs."""
        index = bisect_right(self._spans, span.mils) - 1
        if index < 0 or index >= len(self.ribs) - 1:
            if span.mils == self._spans[-1]:
                return self.ribs[-1].chord
            raise BadReading(f"{span.inches} is off the ribs this wing carries")
        inboard, outboard = self.ribs[index], self.ribs[index + 1]
        run = outboard.at.at - inboard.at.at
        into = span - inboard.at.at
        drop = inboard.chord - outboard.chord
        taken = Decimal(into.mils) / Decimal(run.mils) * Decimal(drop.mils)
        return Length(inboard.chord.mils - int(taken.to_integral_value()))

    def spar_at(self, spar: Spar, span: Length) -> Length:
        """How far aft of the leading edge a spar runs at this point on the span."""
        fraction = self.spar_fractions.get(spar)
        if fraction is None:
            raise NotOnFile(f"this wing has no {spar}")
        return self.chord_at(span) * Decimal(fraction)

    def absolute(self, point: WingPoint) -> Offset:
        """Span outboard of the centreline, and chord aft of the leading edge."""
        span = self.station_of(point.rib).at + point.outboard_of_rib
        aft = self.spar_at(point.spar, span) + point.aft_of_spar
        return Offset(aft, span)

    def _bracket(self, span: Length) -> tuple[RibStation, RibStation]:
        index = bisect_right(self._spans, span.mils) - 1
        if index < 0 or index >= len(self.ribs) - 1:
            raise BadReading(f"{span.inches} is off the ribs this wing carries")
        return self.ribs[index], self.ribs[index + 1]

    def bay_of(self, point: WingPoint) -> WingBay:
        here = self.absolute(point)
        inboard, outboard = self._bracket(here.around)
        return WingBay(inboard.rib, outboard.rib, Spar.FRONT, Spar.REAR, point.surface)

    def margin_of(self, point: WingPoint) -> WingMargin:
        here = self.absolute(point)
        inboard, outboard = self._bracket(here.around)
        front = self.spar_at(Spar.FRONT, here.around)
        rear = self.spar_at(Spar.REAR, here.around)
        candidates: list[tuple[int, RibOrSpar]] = [
            ((here.around - inboard.at.at).mils, inboard.rib),
            ((outboard.at.at - here.around).mils, outboard.rib),
            (abs(here.along - front).mils, Spar.FRONT),
            (abs(rear - here.along).mils, Spar.REAR),
        ]
        distance, member = min(candidates, key=lambda pair: pair[0])
        return WingMargin(member, Length(distance))


@dataclass(frozen=True, slots=True)
class WingBuild:
    """A wing panel with what has been done to it."""

    panel: WingPanel
    thickness: ThicknessMap
    doublers: tuple[InstalledDoubler, ...] = field(default_factory=tuple)

    def __post_init__(self) -> None:
        if self.thickness.land < self.panel.nominal:
            raise BadReading(f"{self.panel.part}: the land is thinner than the panel nominal gauge")

    def doubler_over(self, local: Offset) -> InstalledDoubler | None:
        for doubler in self.doublers:
            if doubler.footprint.contains(local):
                return doubler
        return None


@dataclass(frozen=True, slots=True)
class WingBox:
    """Both wings, as grids plus panels."""

    model: str
    grids: dict[Side, WingGrid]
    panels: tuple[WingBuild, ...]

    def __post_init__(self) -> None:
        if Side.LEFT not in self.grids or Side.RIGHT not in self.grids:
            raise BadReading(f"{self.model}: a wing box has both wings on it")
        parts = [build.panel.part for build in self.panels]
        if len(set(parts)) != len(parts):
            raise BadReading(f"{self.model}: two wing panels with the same part number")

    def grid_for(self, side: Side) -> WingGrid:
        try:
            return self.grids[side]
        except KeyError:
            raise NotOnFile(f"{self.model} has no {side} wing") from None

    def build_for(self, part: PanelId) -> WingBuild:
        for build in self.panels:
            if build.panel.part == part:
                return build
        raise NotOnFile(f"{part} is not a wing panel on the {self.model}")

    def panel_at(self, point: WingPoint) -> WingBuild:
        grid = self.grid_for(point.side)
        bay = grid.bay_of(point)
        for build in self.panels:
            if (
                build.panel.surface is point.surface
                and build.panel.spans_rib(bay.inboard)
                and build.panel.spans_rib(bay.outboard)
            ):
                return build
        raise NotOnFile(f"no {point.surface} panel on the {self.model} covers {bay}")

    def surfaces(self) -> frozenset[Surface]:
        return frozenset(build.panel.surface for build in self.panels)

    def __str__(self) -> str:
        return f"{self.model} wing box, {len(self.panels)} panels"
