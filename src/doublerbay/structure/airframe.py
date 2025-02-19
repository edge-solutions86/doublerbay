"""The barrel put together, and the one call that turns a card into everything about it.

Up to here the pieces answer questions if you already know where to point them.
The grid knows where the members are but not what panel is on them. The build
knows what is on a panel but not where the panel is. This joins the two, so a
damage card goes in and the bay, the panel, the spot and the margins come out
together, measured off the same resolved point.

Together matters. Reading the thickness from one call and the margin from
another is how the two end up describing places an inch apart, and an inch is
the difference between the pocket floor and the land.
"""

from __future__ import annotations

from dataclasses import dataclass

from doublerbay.errors import BadReading, NotOnFile
from doublerbay.grid.lattice import Bay
from doublerbay.grid.location import FuselagePoint
from doublerbay.grid.zone import Zone
from doublerbay.ids import PanelId
from doublerbay.structure.build import PanelBuild, SkinSpot
from doublerbay.structure.grid import FuselageGrid, Margin
from doublerbay.structure.margin import MarginPicture, measure_margins


@dataclass(frozen=True, slots=True)
class Resolved:
    """Everything the desk needs about one point, taken in one pass."""

    point: FuselagePoint
    bay: Bay
    build: PanelBuild
    spot: SkinSpot
    margin: Margin
    margins: MarginPicture

    @property
    def part(self) -> PanelId:
        return self.build.panel.part

    def __str__(self) -> str:
        return f"{self.part} {self.bay}, {self.spot}"


@dataclass(frozen=True, slots=True)
class Airframe:
    """One aircraft type's barrel, as grid plus panels."""

    model: str
    grid: FuselageGrid
    panels: tuple[PanelBuild, ...]

    def __post_init__(self) -> None:
        if not self.model.strip():
            raise BadReading("an airframe has a model")
        if not self.panels:
            raise BadReading(f"{self.model}: an airframe has panels on it")
        parts = [build.panel.part for build in self.panels]
        if len(set(parts)) != len(parts):
            raise BadReading(f"{self.model}: two panels with the same part number")

    def build_for(self, part: PanelId) -> PanelBuild:
        for build in self.panels:
            if build.panel.part == part:
                return build
        raise NotOnFile(f"{part} is not a panel on the {self.model}")

    def bay_at(self, point: FuselagePoint) -> Bay:
        return self.grid.bay_of(point)

    def panel_at(self, point: FuselagePoint) -> PanelBuild:
        bay = self.bay_at(point)
        for build in self.panels:
            if build.panel.covers(bay):
                return build
        raise NotOnFile(f"no panel on the {self.model} covers {bay}")

    def resolve(self, point: FuselagePoint) -> Resolved:
        """Everything about one written location, in one pass over one resolved point."""
        bay = self.bay_at(point)
        build = self.panel_at(point)
        margin = self.grid.margin_of(point)
        local = self.grid.absolute(point)
        spot = build.at(local, margin.member, margin.distance, bay)
        margins = measure_margins(self.grid, build, point)
        return Resolved(point, bay, build, spot, margin, margins)

    @property
    def zones(self) -> frozenset[Zone]:
        return frozenset(build.panel.zone for build in self.panels)

    def in_zone(self, zone: Zone) -> tuple[PanelBuild, ...]:
        return tuple(build for build in self.panels if build.panel.zone == zone)

    @property
    def bay_count(self) -> int:
        return sum(build.panel.bay_count for build in self.panels)

    def __str__(self) -> str:
        return f"{self.model}, {len(self.panels)} panels"
