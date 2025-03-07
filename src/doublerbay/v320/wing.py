"""The V320 wing, as data.

Twenty six ribs a side, from the side of body at wing station 70 out to the tip
rib at 620, twenty two inches apart. The chord runs from two hundred and forty
at the root to seventy eight at the tip, which is where the taper in the wing
box comes from.

The two surfaces are not built the same and it is not a detail. The upper skin
is 7075 because it spends the flight in compression and 7075 is the stronger
alloy. The lower skin is 2024 because it spends the flight in tension and 2024
is the tougher one, and toughness is what stops a crack in the surface that
grows them. The gauges taper outboard on both, from a quarter inch at the root
down to a tenth at the tip, because the bending moment does.
"""

from __future__ import annotations

from decimal import Decimal
from functools import lru_cache

from doublerbay.grid.wing import Rib, Spar, Surface, WingStation
from doublerbay.grid.zone import Side, Zone
from doublerbay.ids import PanelId
from doublerbay.measure.length import Length
from doublerbay.structure.material import Material, material
from doublerbay.structure.panel import PanelKind, WingPanel
from doublerbay.structure.thickness import ThicknessMap
from doublerbay.structure.wingbox import RibStation, WingBox, WingBuild, WingGrid

MODEL = "V320-200"

SIDE_OF_BODY = 70
RIB_PITCH = 22
RIB_COUNT = 26

ROOT_CHORD = Decimal("240.000")
TIP_CHORD = Decimal("78.000")

SPAR_FRACTIONS: dict[Spar, str] = {Spar.FRONT: "0.15", Spar.REAR: "0.65"}

PANEL_RIB_SPAN = 4

# Skin gauge falls outboard because the bending moment does. Called out at the
# inboard rib of each panel and held constant across it, which is how the plate
# is ordered.
GAUGES: tuple[str, ...] = ("0.250", "0.220", "0.190", "0.160", "0.125", "0.100")

# The machined pocket floor, as a fraction of the plate the panel is cut from.
POCKET_FRACTION = Decimal("0.6")
LAND_WIDTH = "1.500"


def station_of_rib(number: int) -> WingStation:
    return WingStation.of_inches(str(SIDE_OF_BODY + (number - 1) * RIB_PITCH))


def chord_at_rib(number: int) -> Length:
    """Straight line taper from the root chord to the tip chord."""
    span = Decimal(number - 1) / Decimal(RIB_COUNT - 1)
    return Length.of_inches(
        (ROOT_CHORD - (ROOT_CHORD - TIP_CHORD) * span).quantize(Decimal("0.001"))
    )


def material_of(surface: Surface) -> Material:
    return material("2024-T351") if surface is Surface.LOWER else material("7075-T6")


def kind_of(surface: Surface) -> PanelKind:
    return PanelKind.WING_LOWER if surface is Surface.LOWER else PanelKind.WING_UPPER


def zone_of(surface: Surface, side: Side, group: int) -> Zone:
    hundred = 500 if side is Side.LEFT else 600
    ten = min(group + 1, 8)
    unit = 1 if surface is Surface.LOWER else 3
    if side is Side.RIGHT:
        unit += 1
    return Zone(hundred + ten * 10 + unit)


def grid(side: Side) -> WingGrid:
    return WingGrid(
        side,
        tuple(
            RibStation(Rib(number, side), station_of_rib(number), chord_at_rib(number))
            for number in range(1, RIB_COUNT + 1)
        ),
        dict(SPAR_FRACTIONS),
    )


def _thickness(gauge: Length) -> ThicknessMap:
    pocket = Length(int((Decimal(gauge.mils) * POCKET_FRACTION).to_integral_value()))
    return ThicknessMap(gauge, pocket, Length.of_inches(LAND_WIDTH))


def _panels(side: Side) -> list[WingBuild]:
    built: list[WingBuild] = []
    hand = "L" if side is Side.LEFT else "R"
    sequence = 20 if side is Side.LEFT else 60
    for surface in (Surface.LOWER, Surface.UPPER):
        for group, first in enumerate(range(1, RIB_COUNT, PANEL_RIB_SPAN)):
            last = min(first + PANEL_RIB_SPAN, RIB_COUNT)
            if last <= first:
                continue
            gauge = Length.of_inches(GAUGES[min(group, len(GAUGES) - 1)])
            built.append(
                WingBuild(
                    panel=WingPanel(
                        part=PanelId(57, 10, sequence, hand),
                        inboard=Rib(first, side),
                        outboard=Rib(last, side),
                        forward=Spar.FRONT,
                        aft=Spar.REAR,
                        surface=surface,
                        zone=zone_of(surface, side, group),
                        kind=kind_of(surface),
                        material=material_of(surface),
                        nominal=gauge,
                    ),
                    thickness=_thickness(gauge),
                )
            )
            sequence += 1
    return built


@lru_cache(maxsize=1)
def wing_box() -> WingBox:
    """Both wings, built once and shared."""
    return WingBox(
        MODEL,
        {Side.LEFT: grid(Side.LEFT), Side.RIGHT: grid(Side.RIGHT)},
        tuple(_panels(Side.LEFT) + _panels(Side.RIGHT)),
    )
