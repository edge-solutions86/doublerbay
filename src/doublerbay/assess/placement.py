"""Turning a damage card into the six facts every limit is read against.

By the time a rule runs it needs to know what kind of skin this is, what part
of the panel the damage sits in, how thick the metal is exactly there, how far
it is from the nearest edge, what size fastener that edge is if it is a row,
and how big the panel is. Not one of those is on the card, and every one of
them comes out of a different place.

Doing it in one pass is the point. Reading the thickness off the panel drawing
and the region off the zone chart gets two answers about two different places,
and the mismatch is invisible because both look reasonable. Here they come from
one resolved point or the call fails.
"""

from __future__ import annotations

from dataclasses import dataclass

from doublerbay.damage.finding import Finding
from doublerbay.errors import BadReading
from doublerbay.fleet.catalogue import TailContext
from doublerbay.grid.ata import Chapter
from doublerbay.grid.location import FuselagePoint, WingPoint
from doublerbay.grid.surface import Offset
from doublerbay.ids import PanelId
from doublerbay.measure.fastener import FastenerSize
from doublerbay.measure.length import Length
from doublerbay.structure.build import Region
from doublerbay.structure.material import Material
from doublerbay.structure.panel import PanelKind


@dataclass(frozen=True, slots=True)
class Placement:
    """Everything about where a damage is, taken off one resolved point."""

    part: PanelId
    panel_kind: PanelKind
    chapter: Chapter
    material: Material
    region: Region
    thickness: Length
    edge_margin: Length
    panel_extent: Length
    centre: Offset
    nearest_fastener: FastenerSize | None = None

    def __post_init__(self) -> None:
        if self.thickness.mils <= 0:
            raise BadReading(f"{self.part}: the skin at the damage has no thickness")
        if self.panel_extent.mils <= 0:
            raise BadReading(f"{self.part}: the panel has no extent")

    @property
    def is_on_the_wing(self) -> bool:
        return self.chapter is Chapter.WINGS

    def __str__(self) -> str:
        return f"{self.part} {self.region}, {self.thickness.inches} thick"


def _fuselage_placement(tail: TailContext, point: FuselagePoint) -> Placement:
    barrel = tail.barrel
    resolved = barrel.resolve(point)
    build = resolved.build
    panel = build.panel

    joint = build.joint_on(resolved.margin.member)
    fastener = joint.row_nearest(resolved.margin.distance).size if joint and joint.rows else None

    along = barrel.grid.station_of(panel.aft).at - barrel.grid.station_of(panel.forward).at
    around = barrel.grid.around_of(panel.lower) - barrel.grid.around_of(panel.upper)

    return Placement(
        part=panel.part,
        panel_kind=panel.kind,
        chapter=panel.chapter,
        material=panel.material,
        region=resolved.spot.region,
        thickness=resolved.spot.thickness,
        edge_margin=resolved.margins.margin,
        panel_extent=max(along, around),
        centre=barrel.grid.absolute(point),
        nearest_fastener=fastener,
    )


def _wing_placement(tail: TailContext, point: WingPoint) -> Placement:
    box = tail.wing_box
    grid = box.grid_for(point.side)
    build = box.panel_at(point)
    panel = build.panel

    here = grid.absolute(point)
    margin = grid.margin_of(point)
    thickness = build.thickness.at(margin.distance)

    doubler = build.doubler_over(here)
    if doubler is not None:
        region = Region.UNDER_DOUBLER
        thickness = thickness + doubler.gauge
    elif build.thickness.on_the_step(margin.distance):
        region = Region.CHEM_MILL_STEP
    elif not build.thickness.is_flat and margin.distance <= build.thickness.land_width:
        region = Region.LAND
    else:
        region = Region.OPEN_FIELD

    span = grid.station_of(panel.outboard).at - grid.station_of(panel.inboard).at

    return Placement(
        part=panel.part,
        panel_kind=panel.kind,
        chapter=panel.chapter,
        material=panel.material,
        region=region,
        thickness=thickness,
        edge_margin=margin.distance,
        panel_extent=span,
        centre=here,
        nearest_fastener=None,
    )


def place(tail: TailContext, finding: Finding) -> Placement:
    """Resolve a card against the aeroplane it was raised on."""
    if finding.serial != tail.serial:
        raise BadReading(f"{finding.reference} is not a card on {tail.serial}")
    point = finding.point
    if isinstance(point, FuselagePoint):
        return _fuselage_placement(tail, point)
    return _wing_placement(tail, point)
