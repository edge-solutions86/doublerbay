from datetime import date

import pytest

from doublerbay.errors import BadReading
from doublerbay.grid.lattice import Bay, Frame, Stringer
from doublerbay.grid.surface import Offset
from doublerbay.grid.zone import Side, Zone
from doublerbay.ids import PanelId
from doublerbay.measure.fastener import FastenerSize
from doublerbay.measure.length import Length
from doublerbay.structure.build import PanelBuild, Region, SkinSpot
from doublerbay.structure.cutout import Cutout, CutoutKind
from doublerbay.structure.footprint import DoublerForm, Footprint, InstalledDoubler
from doublerbay.structure.joint import FastenerRow, Joint, JointKind
from doublerbay.structure.material import material
from doublerbay.structure.panel import FuselagePanel, PanelKind
from doublerbay.structure.thickness import ThicknessMap


def inch(text):
    return Length.of_inches(text)


def at(along, around):
    return Offset(inch(along), inch(around))


LAP_STRINGER = Stringer(12, Side.LEFT)


def skin(nominal="0.040"):
    return FuselagePanel(
        part=PanelId(53, 10, 142, "L"),
        forward=Frame(41),
        aft=Frame(44),
        upper=Stringer(12, Side.LEFT),
        lower=Stringer(15, Side.LEFT),
        zone=Zone(231),
        kind=PanelKind.PRESSURISED_SKIN,
        material=material("2024-T3 CLAD"),
        nominal=inch(nominal),
    )


def lap():
    return Joint(
        JointKind.LAP,
        LAP_STRINGER,
        (
            FastenerRow(FastenerSize(5), inch("0.750"), inch("0.500")),
            FastenerRow(FastenerSize(5), inch("0.750"), inch("1.375")),
        ),
    )


def window():
    return Cutout(
        "W12L",
        CutoutKind.WINDOW,
        at("20.000", "20.000"),
        inch("13.000"),
        inch("9.000"),
        inch("2.000"),
    )


def old_patch():
    return InstalledDoubler(
        part="RP-53-0007-1",
        form=DoublerForm.EXTERNAL,
        footprint=Footprint(at("50.000", "6.000"), inch("6.000"), inch("4.000"), inch("1.000")),
        material=material("2024-T3 CLAD"),
        gauge=inch("0.050"),
        fitted=date(2021, 4, 2),
    )


def build(**kwargs):
    settings = {
        "panel": skin(),
        "thickness": ThicknessMap(inch("0.063"), inch("0.040"), inch("1.000")),
        "joints": (lap(),),
        "cutouts": (window(),),
        "doublers": (old_patch(),),
    }
    settings.update(kwargs)
    return PanelBuild(**settings)


def test_the_regions_rank_tightest_first():
    assert Region.UNDER_DOUBLER.tighter_than(Region.OPEN_FIELD)
    assert Region.CUTOUT_CORNER.tighter_than(Region.CUTOUT_EDGE)
    assert Region.FASTENER_ROW.tighter_than(Region.JOINT_BAND)
    assert not Region.OPEN_FIELD.tighter_than(Region.LAND)
    assert Region.OPEN_FIELD.is_open_field


def test_a_land_is_thinner_than_the_panel_it_belongs_to_is_refused():
    with pytest.raises(BadReading, match="thinner than the panel nominal"):
        build(thickness=ThicknessMap(inch("0.032"), inch("0.032"), inch("1.000")))


def test_two_cutouts_with_the_same_name_are_refused():
    with pytest.raises(BadReading, match="same name"):
        build(cutouts=(window(), window()))


def test_open_field_is_what_is_left_when_nothing_else_reaches():
    spot = build().at(at("60.000", "30.000"), LAP_STRINGER, inch("3.000"))
    assert spot.region is Region.OPEN_FIELD
    assert spot.thickness == inch("0.040")
    assert "bay off S-12L" in spot.feature


def test_a_spot_on_the_land_reads_the_land_gauge():
    spot = build(joints=()).at(at("60.000", "30.000"), LAP_STRINGER, inch("0.500"))
    assert spot.region is Region.LAND
    assert spot.thickness == inch("0.063")


def test_a_spot_on_the_taper_is_called_the_step():
    spot = build(joints=()).at(at("60.000", "30.000"), LAP_STRINGER, inch("1.200"))
    assert spot.region is Region.CHEM_MILL_STEP
    assert spot.feature == "chem mill step"


def test_a_flat_panel_has_no_land_and_no_step():
    flat = build(joints=(), thickness=ThicknessMap.flat(inch("0.063")))
    assert flat.at(at("60.000", "30.000"), LAP_STRINGER, inch("0")).region is Region.OPEN_FIELD


def test_a_spot_within_two_diameters_of_a_row_is_on_the_row():
    spot = build().at(at("60.000", "30.000"), LAP_STRINGER, inch("0.500"))
    assert spot.region is Region.FASTENER_ROW
    assert "lap joint" in spot.feature


def test_a_spot_in_the_joint_band_but_off_every_row_is_the_band():
    spot = build().at(at("60.000", "30.000"), LAP_STRINGER, inch("0.940"))
    assert spot.region is Region.JOINT_BAND
    assert spot.feature == "lap joint"


def test_a_spot_past_the_joint_band_is_not_the_joint_any_more():
    spot = build().at(at("60.000", "30.000"), LAP_STRINGER, inch("3.000"))
    assert spot.region is Region.OPEN_FIELD


def test_a_joint_on_another_member_does_not_reach():
    other = Stringer(14, Side.LEFT)
    spot = build().at(at("60.000", "30.000"), other, inch("0.500"))
    assert spot.region is Region.LAND
    assert build().joint_on(other) is None
    assert build().joint_on(LAP_STRINGER) is not None


def test_a_cutout_beats_a_fastener_row():
    spot = build().at(at("20.000", "20.000"), LAP_STRINGER, inch("0.500"))
    assert spot.region is Region.CUTOUT_EDGE
    assert spot.feature == "W12L"


def test_a_corner_of_a_cutout_beats_the_rest_of_its_band():
    spot = build().at(at("26.000", "24.000"), LAP_STRINGER, inch("3.000"))
    assert spot.region is Region.CUTOUT_CORNER


def test_a_spot_off_a_flat_side_of_a_cutout_is_the_edge_and_not_a_corner():
    spot = build().at(at("27.000", "20.000"), LAP_STRINGER, inch("3.000"))
    assert spot.region is Region.CUTOUT_EDGE


def test_the_nearest_cutout_is_the_one_that_answers():
    second = Cutout(
        "W13L",
        CutoutKind.WINDOW,
        at("40.000", "20.000"),
        inch("13.000"),
        inch("9.000"),
        inch("2.000"),
    )
    spot = build(cutouts=(window(), second)).at(at("34.000", "20.000"), LAP_STRINGER, inch("3.0"))
    assert spot.feature == "W13L"


def test_an_installed_doubler_beats_everything_and_adds_its_own_gauge():
    spot = build().at(at("50.000", "6.000"), LAP_STRINGER, inch("0.500"))
    assert spot.region is Region.UNDER_DOUBLER
    assert spot.feature == "RP-53-0007-1"
    assert spot.thickness == inch("0.113")


def test_the_spot_reads_back_what_it_found_and_how_thick_it_is():
    spot = SkinSpot(Region.OPEN_FIELD, inch("0.040"), "bay off S-12L")
    assert str(spot) == "in open field (bay off S-12L), 0.040"


def test_a_pad_on_the_bay_is_read_through_the_build():
    bay = Bay(Frame(41), Frame(42), Stringer(12, Side.LEFT), Stringer(13, Side.LEFT))
    assert build().thickness_at(inch("3.000"), bay) == inch("0.040")
    assert build().thickness_at(inch("0.500")) == inch("0.063")


def test_the_doubler_and_cutout_lookups_answer_nothing_when_nothing_is_there():
    assert build().doubler_over(at("0", "0")) is None
    assert build().cutout_over(at("0", "0")) is None
