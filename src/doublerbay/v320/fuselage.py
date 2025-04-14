"""The V320 barrel, as data.

Sixty one frames from the nose bulkhead at station 178 to the pressure dome at
1328, twenty inches apart everywhere except through the wing box where the
production splices force a shorter pitch. Twenty five stringers a side, from the
crown down to the keel, spaced round a seventy four inch radius.

The panels are laid out the way the type certificate holder cuts them: two
frame bays long and four stringer bays deep, gauge by section and by band,
heavier through the centre section where the wing goes in and lighter in the
tail cone where the barrel is not pressurised. Longitudinal lap joints run at
S-8 and S-16 on both hands and there is a circumferential butt joint at each
section splice.

None of these numbers are guessed. They are the ones the desk reads off the
structural repair manual, transcribed once here so that nothing downstream has
to carry a copy of them.
"""

from __future__ import annotations

from decimal import Decimal
from functools import lru_cache

from doublerbay.errors import BadReading
from doublerbay.grid.coordinate import Station
from doublerbay.grid.lattice import Frame, Stringer
from doublerbay.grid.surface import Offset
from doublerbay.grid.zone import Side, Zone
from doublerbay.ids import PanelId
from doublerbay.measure.fastener import FastenerSize
from doublerbay.measure.length import Length
from doublerbay.structure.airframe import Airframe
from doublerbay.structure.build import PanelBuild
from doublerbay.structure.cutout import Cutout, CutoutKind
from doublerbay.structure.grid import FrameStation, FuselageGrid, StringerLine
from doublerbay.structure.joint import FastenerRow, Joint, JointKind
from doublerbay.structure.material import Material, material
from doublerbay.structure.panel import FuselagePanel, PanelKind
from doublerbay.structure.thickness import ThicknessMap

MODEL = "V320-200"
BARREL_RADIUS = Length.of_inches("74.000")

NOSE_STATION = 178
FRAME_COUNT = 61

# Frame pitch is twenty inches except across the wing box, where the centre
# section splices sit at fifteen so the splice straps land on frames.
WING_BOX_FRAMES = range(24, 34)
STANDARD_PITCH = 20
WING_BOX_PITCH = 15

# Arc from the crown to each stringer, worked round the seventy four inch
# radius. The spacing opens up below the floor line because the keel beam takes
# the place of two stringers.
STRINGER_ARCS: tuple[str, ...] = (
    "0.000",
    "9.100",
    "18.200",
    "27.300",
    "36.400",
    "45.500",
    "54.600",
    "63.700",
    "72.800",
    "81.900",
    "91.000",
    "100.100",
    "109.200",
    "118.300",
    "127.400",
    "136.500",
    "145.600",
    "154.700",
    "163.800",
    "174.400",
    "185.000",
    "195.600",
    "206.200",
    "216.800",
    "227.400",
)

LAP_STRINGERS = (8, 16)
SPLICE_FRAMES = (10, 24, 34, 48)

CROWN_BAND = range(0, 4)
UPPER_BAND = range(4, 12)
LOWER_BAND = range(12, 20)
KEEL_BAND = range(20, 25)

PANEL_FRAME_SPAN = 2
PANEL_STRINGER_SPAN = 4

# Gauge by section and band. Heavier through the centre where the wing box
# loads come in, lighter aft of the rear pressure bulkhead.
GAUGES: dict[tuple[str, str], str] = {
    ("forward", "crown"): "0.050",
    ("forward", "upper"): "0.040",
    ("forward", "lower"): "0.050",
    ("forward", "keel"): "0.063",
    ("centre", "crown"): "0.063",
    ("centre", "upper"): "0.050",
    ("centre", "lower"): "0.063",
    ("centre", "keel"): "0.080",
    ("aft", "crown"): "0.040",
    ("aft", "upper"): "0.040",
    ("aft", "lower"): "0.050",
    ("aft", "keel"): "0.063",
}

# The section splits are fixed forward and float at the back, because the
# short series loses its frames out of the tail cone and nowhere else.
FORWARD_LAST = 20
CENTRE_LAST = 40
# How many frames the cabin windows run for before the tail cone starts.
WINDOW_FIRST_FRAME = 12
WINDOW_TAIL_MARGIN = 13


def station_of_frame(number: int) -> Station:
    """Walk the pitch table forward so the wing box shortening accumulates properly."""
    at = NOSE_STATION
    for frame in range(1, number):
        at += WING_BOX_PITCH if frame in WING_BOX_FRAMES else STANDARD_PITCH
    return Station.of_inches(str(at))


def section_bounds(frame_count: int = FRAME_COUNT) -> tuple[tuple[str, int, int], ...]:
    return (
        ("forward", 1, FORWARD_LAST),
        ("centre", FORWARD_LAST, CENTRE_LAST),
        ("aft", CENTRE_LAST, frame_count),
    )


def section_of(frame_number: int, frame_count: int = FRAME_COUNT) -> str:
    for name, first, last in section_bounds(frame_count):
        if first <= frame_number < last:
            return name
    return "aft"


def band_of(stringer_number: int) -> str:
    if stringer_number in CROWN_BAND:
        return "crown"
    if stringer_number in UPPER_BAND:
        return "upper"
    if stringer_number in LOWER_BAND:
        return "lower"
    return "keel"


def kind_of(band: str, section: str) -> PanelKind:
    if section == "aft":
        return PanelKind.UNPRESSURISED_SKIN
    if band == "crown":
        return PanelKind.CROWN_SKIN
    if band == "keel":
        return PanelKind.BELLY_SKIN
    return PanelKind.PRESSURISED_SKIN


def zone_of(band: str, section: str, side: Side) -> Zone:
    hundred = 200 if band in ("crown", "upper") else 100
    ten = {"forward": 1, "centre": 3, "aft": 5}[section]
    unit = 1 if side is Side.LEFT else 2
    return Zone(hundred + ten * 10 + unit)


def material_of(band: str) -> Material:
    return material("7075-T6 CLAD") if band == "keel" else material("2024-T3 CLAD")


def grid(frame_count: int = FRAME_COUNT) -> FuselageGrid:
    return FuselageGrid(
        frames=tuple(
            FrameStation(Frame(number), station_of_frame(number))
            for number in range(1, frame_count + 1)
        ),
        stringers=tuple(
            StringerLine(
                Stringer(number, Side.LEFT) if number else Stringer(0),
                Length.of_inches(arc),
            )
            for number, arc in enumerate(STRINGER_ARCS)
        ),
    )


def _lap(stringer: Stringer) -> Joint:
    return Joint(
        JointKind.LAP,
        stringer,
        (
            FastenerRow(FastenerSize(5), Length.of_inches("0.750"), Length.of_inches("0.500")),
            FastenerRow(FastenerSize(5), Length.of_inches("0.750"), Length.of_inches("1.375")),
            FastenerRow(FastenerSize(5), Length.of_inches("0.750"), Length.of_inches("2.250")),
        ),
    )


def _butt(frame: Frame) -> Joint:
    return Joint(
        JointKind.BUTT,
        frame,
        (
            FastenerRow(FastenerSize(6), Length.of_inches("0.940"), Length.of_inches("0.625")),
            FastenerRow(FastenerSize(6), Length.of_inches("0.940"), Length.of_inches("1.500")),
        ),
    )


def _thickness(gauge: Length, section: str) -> ThicknessMap:
    if section == "aft":
        return ThicknessMap.flat(gauge)
    land = gauge
    pocket = Length(int((Decimal(gauge.mils) * Decimal("0.7")).to_integral_value()))
    return ThicknessMap(land, pocket, Length.of_inches("1.000"))


def _windows(forward: Frame, aft: Frame, upper: Stringer, frame_count: int) -> tuple[Cutout, ...]:
    """A window in each frame bay of the passenger cabin, between S-6 and S-9."""
    last_window = frame_count - WINDOW_TAIL_MARGIN
    if upper.number != 4 or not WINDOW_FIRST_FRAME <= forward.number < last_window:
        return ()
    found = []
    for number in range(forward.number, aft.number):
        centre = station_of_frame(number).at + Length.of_inches("10.000")
        found.append(
            Cutout(
                f"W{number}L",
                CutoutKind.WINDOW,
                Offset(centre, Length.of_inches("63.700")),
                Length.of_inches("13.000"),
                Length.of_inches("9.000"),
                Length.of_inches("2.000"),
            )
        )
    return tuple(found)


def _panels(side: Side, frame_count: int) -> list[PanelBuild]:
    built: list[PanelBuild] = []
    sequence = 100 if side is Side.LEFT else 500
    for first_frame in range(1, frame_count, PANEL_FRAME_SPAN):
        last_frame = min(first_frame + PANEL_FRAME_SPAN, frame_count)
        if last_frame <= first_frame:
            continue
        for first_stringer in range(0, len(STRINGER_ARCS) - 1, PANEL_STRINGER_SPAN):
            last_stringer = min(first_stringer + PANEL_STRINGER_SPAN, len(STRINGER_ARCS) - 1)
            section = section_of(first_frame, frame_count)
            band = band_of(first_stringer)
            gauge = Length.of_inches(GAUGES[(section, band)])
            upper = Stringer(first_stringer, side) if first_stringer else Stringer(0)
            panel = FuselagePanel(
                part=PanelId(53, 10, sequence, "L" if side is Side.LEFT else "R"),
                forward=Frame(first_frame),
                aft=Frame(last_frame),
                upper=upper,
                lower=Stringer(last_stringer, side),
                zone=zone_of(band, section, side),
                kind=kind_of(band, section),
                material=material_of(band),
                nominal=gauge,
            )
            joints = tuple(
                _lap(Stringer(number, side))
                for number in LAP_STRINGERS
                if first_stringer <= number < last_stringer
            )
            joints += tuple(
                _butt(Frame(number))
                for number in SPLICE_FRAMES
                if first_frame <= number < last_frame
            )
            built.append(
                PanelBuild(
                    panel=panel,
                    thickness=_thickness(gauge, section),
                    joints=joints,
                    cutouts=_windows(Frame(first_frame), Frame(last_frame), upper, frame_count)
                    if side is Side.LEFT
                    else (),
                )
            )
            sequence += 1
    return built


@lru_cache(maxsize=4)
def barrel_for(frame_count: int = FRAME_COUNT) -> Airframe:
    """A barrel of a given length, built once per length and shared.

    The three series share a cross section and differ in how many frames go
    between the wing box and the tail cone, so one definition and a frame count
    covers all of them.
    """
    if frame_count < CENTRE_LAST + 2:
        raise BadReading(f"a barrel of {frame_count} frames has no tail cone")
    panels = _panels(Side.LEFT, frame_count) + _panels(Side.RIGHT, frame_count)
    return Airframe(f"{MODEL} ({frame_count} frames)", grid(frame_count), tuple(panels))


def barrel() -> Airframe:
    """The standard series barrel, which is what most of the fleet is."""
    return barrel_for(FRAME_COUNT)
