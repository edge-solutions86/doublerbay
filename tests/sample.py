"""A small stand in for the real V320 structure, for tests that need one.

Frames every twenty inches from station 600, stringers every eight inches down
from the crown, and three panels on the left hand side with a lap joint, a
window and somebody's old doubler between them. Small enough to work out the
right answer by hand and complete enough that every lookup has something to
find.
"""

from datetime import date

from doublerbay.grid.coordinate import Station
from doublerbay.grid.lattice import Frame, Stringer
from doublerbay.grid.location import FuselagePoint
from doublerbay.grid.surface import Offset
from doublerbay.grid.zone import Side, Zone
from doublerbay.ids import PanelId
from doublerbay.measure.fastener import FastenerSize
from doublerbay.measure.length import Length
from doublerbay.structure.airframe import Airframe
from doublerbay.structure.build import PanelBuild
from doublerbay.structure.cutout import Cutout, CutoutKind
from doublerbay.structure.footprint import DoublerForm, Footprint, InstalledDoubler
from doublerbay.structure.grid import FrameStation, FuselageGrid, StringerLine
from doublerbay.structure.joint import FastenerRow, Joint, JointKind
from doublerbay.structure.material import material
from doublerbay.structure.panel import FuselagePanel, PanelKind
from doublerbay.structure.thickness import ThicknessMap

FRAME_PITCH = 20
FIRST_STATION = 600
STRINGER_PITCH = 8
LAP_STRINGER = Stringer(4, Side.LEFT)


def inch(text: str) -> Length:
    return Length.of_inches(text)


def at(along: str, around: str) -> Offset:
    return Offset(inch(along), inch(around))


def grid() -> FuselageGrid:
    return FuselageGrid(
        frames=tuple(
            FrameStation(
                Frame(number),
                Station.of_inches(str(FIRST_STATION + (number - 40) * FRAME_PITCH)),
            )
            for number in range(40, 51)
        ),
        stringers=tuple(
            StringerLine(
                Stringer(number, Side.LEFT) if number else Stringer(0),
                inch(str(number * STRINGER_PITCH)),
            )
            for number in range(0, 9)
        ),
    )


def point(frame: int = 41, aft: str = "10.000", stringer: int = 2, down: str = "4.000"):
    return FuselagePoint(Frame(frame), inch(aft), Stringer(stringer, Side.LEFT), inch(down))


def _panel(sequence, forward, aft_frame, upper, lower, nominal="0.040", zone=231):
    return FuselagePanel(
        part=PanelId(53, 10, sequence, "L"),
        forward=Frame(forward),
        aft=Frame(aft_frame),
        upper=Stringer(upper, Side.LEFT) if upper else Stringer(0),
        lower=Stringer(lower, Side.LEFT),
        zone=Zone(zone),
        kind=PanelKind.PRESSURISED_SKIN,
        material=material("2024-T3 CLAD"),
        nominal=inch(nominal),
    )


def milled() -> ThicknessMap:
    return ThicknessMap(inch("0.063"), inch("0.040"), inch("1.000"))


def lap() -> Joint:
    return Joint(
        JointKind.LAP,
        LAP_STRINGER,
        (
            FastenerRow(FastenerSize(5), inch("0.750"), inch("0.500")),
            FastenerRow(FastenerSize(5), inch("0.750"), inch("1.375")),
        ),
    )


def window() -> Cutout:
    return Cutout(
        "W12L",
        CutoutKind.WINDOW,
        at("660.000", "20.000"),
        inch("13.000"),
        inch("9.000"),
        inch("2.000"),
    )


def old_patch() -> InstalledDoubler:
    return InstalledDoubler(
        part="RP-53-0007-1",
        form=DoublerForm.EXTERNAL,
        footprint=Footprint(at("630.000", "44.000"), inch("6.000"), inch("4.000"), inch("1.000")),
        material=material("2024-T3 CLAD"),
        gauge=inch("0.050"),
        fitted=date(2021, 4, 2),
    )


def forward_upper() -> PanelBuild:
    return PanelBuild(
        panel=_panel(142, 41, 43, 1, 4),
        thickness=milled(),
        joints=(lap(),),
        cutouts=(window(),),
    )


def aft_upper() -> PanelBuild:
    return PanelBuild(panel=_panel(143, 43, 45, 1, 4), thickness=milled(), joints=(lap(),))


def forward_lower() -> PanelBuild:
    return PanelBuild(
        panel=_panel(152, 41, 43, 4, 7, nominal="0.050", zone=131),
        thickness=ThicknessMap.flat(inch("0.050")),
        joints=(lap(),),
        doublers=(old_patch(),),
    )


def airframe() -> Airframe:
    return Airframe("V320-200", grid(), (forward_upper(), aft_upper(), forward_lower()))


def second_patch() -> InstalledDoubler:
    """A second doubler put on top of the first one, which is not allowed."""
    return InstalledDoubler(
        part="RP-53-0009-1",
        form=DoublerForm.EXTERNAL,
        footprint=Footprint(at("632.000", "44.000"), inch("6.000"), inch("4.000"), inch("1.000")),
        material=material("2024-T3 CLAD"),
        gauge=inch("0.050"),
        fitted=date(2022, 9, 30),
    )


def overlapping() -> PanelBuild:
    """A panel that claims bays the forward upper panel already claims."""
    return PanelBuild(panel=_panel(199, 41, 43, 2, 3), thickness=milled())
