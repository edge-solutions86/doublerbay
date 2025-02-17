"""Every edge a damage has to keep away from, and which of them is the governing one.

Edge margin is one number in the manual and four measurements on the aeroplane.
There is the free edge of the panel, there is the nearest row of holes, there is
whatever cutout is in the neighbourhood, and there is any doubler already
riveted on. A limit written as two diameters of edge margin means two diameters
from the nearest of all of them, and the one that governs is worth naming
because it is what the repair drawing has to move away from.

Positions here are in skin coordinates, aft of the datum and round from the
crown, which is what the grid hands out. Cutouts and doublers are placed in the
same coordinates, so nothing in this file converts between two frames of
reference and nothing in it can get that conversion backwards.

Rows run outward from the member their joint follows: aft of a frame, down from
a stringer. That is the drawing convention and it is stated once, here.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

from doublerbay.errors import BadReading
from doublerbay.grid.lattice import Frame
from doublerbay.grid.location import FuselagePoint
from doublerbay.grid.surface import Offset
from doublerbay.measure.length import Length
from doublerbay.structure.build import PanelBuild
from doublerbay.structure.grid import FuselageGrid


class EdgeKind(Enum):
    """What kind of edge a measurement was taken to."""

    PANEL_EDGE = "panel edge"
    FASTENER_ROW = "fastener row"
    CUTOUT = "cutout"
    INSTALLED_DOUBLER = "installed doubler"

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True, slots=True)
class EdgeDistance:
    """One measurement, to one named edge."""

    kind: EdgeKind
    to: str
    distance: Length

    def __str__(self) -> str:
        return f"{self.distance.inches} to {self.to}"


@dataclass(frozen=True, slots=True)
class MarginPicture:
    """Every edge in reach, with the nearest one called out."""

    distances: tuple[EdgeDistance, ...]

    def __post_init__(self) -> None:
        if not self.distances:
            raise BadReading("a margin picture has at least the panel edge in it")

    @property
    def governing(self) -> EdgeDistance:
        return min(self.distances, key=lambda edge: edge.distance.mils)

    @property
    def margin(self) -> Length:
        return self.governing.distance

    def to_kind(self, kind: EdgeKind) -> EdgeDistance | None:
        matching = [edge for edge in self.distances if edge.kind is kind]
        if not matching:
            return None
        return min(matching, key=lambda edge: edge.distance.mils)

    def clears(self, required: Length) -> bool:
        return self.margin >= required

    def __str__(self) -> str:
        return f"{self.governing} ({self.governing.kind})"


def _panel_edges(grid: FuselageGrid, build: PanelBuild, here: Offset) -> list[EdgeDistance]:
    panel = build.panel
    bounds = (
        (
            EdgeKind.PANEL_EDGE,
            str(panel.forward),
            abs(here.along - grid.station_of(panel.forward).at),
        ),
        (EdgeKind.PANEL_EDGE, str(panel.aft), abs(grid.station_of(panel.aft).at - here.along)),
        (EdgeKind.PANEL_EDGE, str(panel.upper), abs(here.around - grid.around_of(panel.upper))),
        (EdgeKind.PANEL_EDGE, str(panel.lower), abs(grid.around_of(panel.lower) - here.around)),
    )
    return [EdgeDistance(kind, to, distance) for kind, to, distance in bounds]


def _row_edges(grid: FuselageGrid, build: PanelBuild, here: Offset) -> list[EdgeDistance]:
    found: list[EdgeDistance] = []
    for joint in build.joints:
        if isinstance(joint.follows, Frame):
            base = grid.station_of(joint.follows).at
            reading = here.along
        else:
            base = grid.around_of(joint.follows)
            reading = here.around
        for row in joint.rows:
            gap = abs(reading - (base + row.offset))
            found.append(EdgeDistance(EdgeKind.FASTENER_ROW, f"{joint.follows} {row}", gap))
    return found


def measure_margins(grid: FuselageGrid, build: PanelBuild, point: FuselagePoint) -> MarginPicture:
    """Take every edge measurement the manual could mean by edge margin."""
    here = grid.absolute(point)
    found = _panel_edges(grid, build, here)
    found.extend(_row_edges(grid, build, here))
    found.extend(
        EdgeDistance(EdgeKind.CUTOUT, cutout.name, cutout.edge_distance(here))
        for cutout in build.cutouts
    )
    found.extend(
        EdgeDistance(EdgeKind.INSTALLED_DOUBLER, doubler.part, doubler.footprint.distance_to(here))
        for doubler in build.doublers
    )
    return MarginPicture(tuple(found))
