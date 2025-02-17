from datetime import date

import pytest

from doublerbay.errors import BadReading
from doublerbay.grid.coordinate import Station
from doublerbay.grid.lattice import Frame, Stringer
from doublerbay.grid.location import FuselagePoint
from doublerbay.grid.surface import Offset
from doublerbay.grid.zone import Side, Zone
from doublerbay.ids import PanelId
from doublerbay.measure.fastener import FastenerSize
from doublerbay.measure.length import Length
from doublerbay.structure.build import PanelBuild
from doublerbay.structure.cutout import Cutout, CutoutKind
from doublerbay.structure.footprint import DoublerForm, Footprint, InstalledDoubler
from doublerbay.structure.grid import FrameStation, FuselageGrid, StringerLine
from doublerbay.structure.joint import FastenerRow, Joint, JointKind
from doublerbay.structure.margin import EdgeDistance, EdgeKind, MarginPicture, measure_margins
from doublerbay.structure.material import material
from doublerbay.structure.panel import FuselagePanel, PanelKind
from doublerbay.structure.thickness import ThicknessMap


def inch(text):
    return Length.of_inches(text)


def at(along, around):
    return Offset(inch(along), inch(around))


def grid():
    return FuselageGrid(
        frames=tuple(
            FrameStation(Frame(number), Station.of_inches(str(600 + (number - 40) * 20)))
            for number in range(40, 46)
        ),
        stringers=tuple(
            StringerLine(
                Stringer(number, Side.LEFT) if number else Stringer(0), inch(str(number * 8))
            )
            for number in range(0, 6)
        ),
    )


def panel():
    return FuselagePanel(
        part=PanelId(53, 10, 142, "L"),
        forward=Frame(41),
        aft=Frame(43),
        upper=Stringer(1, Side.LEFT),
        lower=Stringer(4, Side.LEFT),
        zone=Zone(231),
        kind=PanelKind.PRESSURISED_SKIN,
        material=material("2024-T3 CLAD"),
        nominal=inch("0.040"),
    )


def lap():
    return Joint(
        JointKind.LAP,
        Stringer(1, Side.LEFT),
        (FastenerRow(FastenerSize(5), inch("0.750"), inch("0.500")),),
    )


def window():
    return Cutout(
        "W12L",
        CutoutKind.WINDOW,
        at("660.000", "20.000"),
        inch("13.000"),
        inch("9.000"),
        inch("2.000"),
    )


def old_patch():
    return InstalledDoubler(
        part="RP-53-0007-1",
        form=DoublerForm.EXTERNAL,
        footprint=Footprint(at("630.000", "30.000"), inch("6.000"), inch("4.000"), inch("1.000")),
        material=material("2024-T3 CLAD"),
        gauge=inch("0.050"),
        fitted=date(2021, 4, 2),
    )


def build(joints=(), cutouts=(), doublers=()):
    return PanelBuild(
        panel=panel(),
        thickness=ThicknessMap(inch("0.063"), inch("0.040"), inch("1.000")),
        joints=joints,
        cutouts=cutouts,
        doublers=doublers,
    )


def point(frame=41, aft="10.000", stringer=2, down="4.000"):
    return FuselagePoint(Frame(frame), inch(aft), Stringer(stringer, Side.LEFT), inch(down))


def test_the_panel_edges_are_always_measured():
    picture = measure_margins(grid(), build(), point())
    assert len(picture.distances) == 4
    assert all(edge.kind is EdgeKind.PANEL_EDGE for edge in picture.distances)


def test_the_governing_edge_is_the_nearest_one():
    picture = measure_margins(grid(), build(), point())
    assert picture.margin == inch("10.000")
    assert picture.governing.to == "FR41"


def test_a_point_near_the_aft_boundary_governs_off_that_frame():
    picture = measure_margins(grid(), build(), point(frame=42, aft="18.000"))
    assert picture.governing.to == "FR43"
    assert picture.margin == inch("2.000")


def test_a_fastener_row_is_measured_out_from_the_member_it_follows():
    picture = measure_margins(grid(), build(joints=(lap(),)), point(stringer=1, down="0.750"))
    row = picture.to_kind(EdgeKind.FASTENER_ROW)
    assert row is not None
    assert row.distance == inch("0.250")
    assert picture.governing.kind is EdgeKind.FASTENER_ROW


def test_a_cutout_is_measured_to_its_edge_and_not_its_centre():
    picture = measure_margins(grid(), build(cutouts=(window(),)), point(frame=42, aft="11.000"))
    cutout = picture.to_kind(EdgeKind.CUTOUT)
    assert cutout is not None
    assert cutout.to == "W12L"
    assert cutout.distance == inch("2.500")


def test_an_installed_doubler_is_an_edge_the_next_repair_has_to_clear():
    picture = measure_margins(grid(), build(doublers=(old_patch(),)), point(stringer=3, down="3.0"))
    doubler = picture.to_kind(EdgeKind.INSTALLED_DOUBLER)
    assert doubler is not None
    assert doubler.to == "RP-53-0007-1"
    assert doubler.distance == inch("1.000")


def test_a_kind_that_was_never_measured_answers_nothing():
    picture = measure_margins(grid(), build(), point())
    assert picture.to_kind(EdgeKind.CUTOUT) is None
    assert picture.to_kind(EdgeKind.FASTENER_ROW) is None


def test_the_nearest_of_several_edges_of_one_kind_is_the_one_reported():
    two_rows = Joint(
        JointKind.LAP,
        Stringer(1, Side.LEFT),
        (
            FastenerRow(FastenerSize(5), inch("0.750"), inch("0.500")),
            FastenerRow(FastenerSize(5), inch("0.750"), inch("1.500")),
        ),
    )
    picture = measure_margins(grid(), build(joints=(two_rows,)), point(stringer=1, down="1.400"))
    row = picture.to_kind(EdgeKind.FASTENER_ROW)
    assert row is not None
    assert row.distance == inch("0.100")


def test_everything_in_reach_is_measured_at_once():
    picture = measure_margins(
        grid(),
        build(joints=(lap(),), cutouts=(window(),), doublers=(old_patch(),)),
        point(),
    )
    kinds = {edge.kind for edge in picture.distances}
    assert kinds == set(EdgeKind)


def test_a_required_margin_is_met_or_it_is_not():
    picture = measure_margins(grid(), build(), point())
    assert picture.clears(inch("10.000"))
    assert not picture.clears(inch("10.001"))


def test_a_picture_with_nothing_in_it_is_refused():
    with pytest.raises(BadReading, match="at least the panel edge"):
        MarginPicture(())


def test_the_picture_reads_back_the_edge_that_governs():
    picture = MarginPicture((EdgeDistance(EdgeKind.CUTOUT, "W12L", inch("1.500")),))
    assert str(picture) == "1.500 to W12L (cutout)"
    assert str(picture.governing) == "1.500 to W12L"
