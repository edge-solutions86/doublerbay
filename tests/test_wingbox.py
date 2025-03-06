import pytest

from doublerbay.errors import BadReading, NotOnFile
from doublerbay.grid.location import WingPoint
from doublerbay.grid.wing import Rib, Spar, Surface, WingStation
from doublerbay.grid.zone import Side, Zone
from doublerbay.ids import PanelId
from doublerbay.measure.length import Length
from doublerbay.structure.material import material
from doublerbay.structure.panel import PanelKind, WingPanel
from doublerbay.structure.thickness import ThicknessMap
from doublerbay.structure.wingbox import RibStation, WingBox, WingBuild, WingGrid, WingMargin

FRACTIONS = {Spar.FRONT: "0.15", Spar.REAR: "0.65"}


def inch(text):
    return Length.of_inches(text)


def ribs(side=Side.LEFT):
    return tuple(
        RibStation(
            Rib(number, side),
            WingStation.of_inches(str(60 + (number - 1) * 40)),
            inch(str(200 - (number - 1) * 10)),
        )
        for number in range(1, 14)
    )


def grid(side=Side.LEFT):
    return WingGrid(side, ribs(side), dict(FRACTIONS))


def point(rib=1, outboard="20.000", spar=Spar.FRONT, aft="10.000", surface=Surface.LOWER):
    return WingPoint(Rib(rib, Side.LEFT), inch(outboard), spar, inch(aft), surface)


def panel(sequence=22, inboard=1, outboard=5, surface=Surface.LOWER):
    return WingPanel(
        part=PanelId(57, 10, sequence, "L"),
        inboard=Rib(inboard, Side.LEFT),
        outboard=Rib(outboard, Side.LEFT),
        forward=Spar.FRONT,
        aft=Spar.REAR,
        surface=surface,
        zone=Zone(531),
        kind=PanelKind.WING_LOWER if surface is Surface.LOWER else PanelKind.WING_UPPER,
        material=material("2024-T351"),
        nominal=inch("0.160"),
    )


def build(**kwargs):
    return WingBuild(panel(**kwargs), ThicknessMap.flat(inch("0.160")))


def box():
    return WingBox(
        "V320-200",
        {Side.LEFT: grid(Side.LEFT), Side.RIGHT: grid(Side.RIGHT)},
        (
            build(),
            build(sequence=23, inboard=5, outboard=9),
            build(sequence=32, surface=Surface.UPPER),
        ),
    )


def test_a_rib_station_carries_where_it_is_and_how_long_the_chord_is():
    entry = ribs()[0]
    assert str(entry) == "RIB1L at WS 60.000, chord 200.000"


def test_a_rib_with_no_chord_is_refused():
    with pytest.raises(BadReading, match="has no chord"):
        RibStation(Rib(1, Side.LEFT), WingStation.of_inches("60"), inch("0"))


def test_a_grid_needs_two_ribs_and_both_spars():
    with pytest.raises(BadReading, match="at least two ribs"):
        WingGrid(Side.LEFT, ribs()[:1], dict(FRACTIONS))
    with pytest.raises(BadReading, match="places the rear spar"):
        WingGrid(Side.LEFT, ribs(), {Spar.FRONT: "0.15"})


def test_ribs_are_listed_inboard_to_outboard():
    with pytest.raises(BadReading, match="inboard to outboard"):
        WingGrid(Side.LEFT, tuple(reversed(ribs())), dict(FRACTIONS))


def test_every_rib_on_a_grid_belongs_to_that_wing():
    with pytest.raises(BadReading, match="belongs to that wing"):
        WingGrid(Side.RIGHT, ribs(Side.LEFT), dict(FRACTIONS))


def test_a_rib_off_the_grid_is_a_lookup_miss():
    with pytest.raises(NotOnFile, match="is not on this wing grid"):
        grid().station_of(Rib(30, Side.LEFT))


def test_the_chord_at_a_rib_is_the_chord_of_that_rib():
    assert grid().chord_at(inch("60.000")) == inch("200.000")
    assert grid().chord_of(Rib(2, Side.LEFT)) == inch("190.000")


def test_the_chord_between_ribs_is_straight_lined():
    assert grid().chord_at(inch("80.000")) == inch("195.000")
    assert grid().chord_at(inch("90.000")) == inch("192.500")


def test_the_chord_at_the_last_rib_is_still_a_chord():
    assert grid().chord_at(inch("540.000")) == inch("80.000")


def test_a_span_off_the_wing_has_no_chord():
    with pytest.raises(BadReading, match="is off the ribs"):
        grid().chord_at(inch("541.000"))


def test_the_spars_move_aft_as_the_chord_grows():
    assert grid().spar_at(Spar.FRONT, inch("60.000")) == inch("30.000")
    assert grid().spar_at(Spar.FRONT, inch("540.000")) == inch("12.000")
    assert grid().spar_at(Spar.REAR, inch("60.000")) == inch("130.000")


def test_a_spar_the_wing_does_not_carry_is_a_lookup_miss():
    with pytest.raises(NotOnFile, match="has no mid spar"):
        grid().spar_at(Spar.MID, inch("60.000"))


def test_a_written_wing_point_resolves_to_span_and_chord():
    here = grid().absolute(point())
    assert here.around == inch("80.000")
    assert here.along == inch("39.250")


def test_a_point_falls_in_the_rib_bay_that_brackets_it():
    bay = grid().bay_of(point())
    assert bay.inboard == Rib(1, Side.LEFT)
    assert bay.outboard == Rib(2, Side.LEFT)
    assert bay.is_tension_critical


def test_the_margin_is_the_nearest_of_the_two_ribs_and_two_spars():
    margin = grid().margin_of(point())
    assert margin.member is Spar.FRONT
    assert margin.distance == inch("10.000")


def test_the_margin_can_be_to_a_rib_instead():
    margin = grid().margin_of(point(outboard="4.000", aft="30.000"))
    assert margin.member == Rib(1, Side.LEFT)
    assert margin.distance == inch("4.000")


def test_the_margin_can_be_to_the_rear_spar():
    margin = grid().margin_of(point(spar=Spar.REAR, aft="2.000"))
    assert margin.member is Spar.REAR
    assert margin.distance == inch("2.000")


def test_a_wing_box_carries_both_wings():
    assert str(box()) == "V320-200 wing box, 3 panels"
    assert box().grid_for(Side.LEFT).side is Side.LEFT
    with pytest.raises(BadReading, match="has both wings on it"):
        WingBox("V320-200", {Side.LEFT: grid()}, (build(),))


def test_two_wing_panels_with_the_same_part_number_are_refused():
    with pytest.raises(BadReading, match="same part number"):
        WingBox("V320-200", {Side.LEFT: grid(), Side.RIGHT: grid(Side.RIGHT)}, (build(), build()))


def test_a_wing_panel_is_found_by_its_part_number():
    assert box().build_for(PanelId(57, 10, 23, "L")).panel.inboard == Rib(5, Side.LEFT)
    with pytest.raises(NotOnFile, match="is not a wing panel"):
        box().build_for(PanelId(57, 10, 99, "L"))


def test_a_point_lands_on_the_panel_that_covers_its_bay_and_surface():
    assert box().panel_at(point()).panel.part == PanelId(57, 10, 22, "L")
    upper = point(surface=Surface.UPPER)
    assert box().panel_at(upper).panel.part == PanelId(57, 10, 32, "L")


def test_a_point_further_outboard_lands_on_the_next_panel():
    outboard = point(rib=6)
    assert box().panel_at(outboard).panel.part == PanelId(57, 10, 23, "L")


def test_a_point_on_no_wing_panel_is_a_lookup_miss():
    with pytest.raises(NotOnFile, match="covers"):
        box().panel_at(point(rib=10))


def test_the_box_knows_which_surfaces_it_carries():
    assert box().surfaces() == frozenset({Surface.LOWER, Surface.UPPER})


def test_a_wing_margin_reads_back_what_it_is_off():
    assert str(WingMargin(Spar.FRONT, inch("2.000"))) == "2.000 off front spar"


def test_a_wing_build_will_not_take_a_land_thinner_than_its_panel():
    with pytest.raises(BadReading, match="thinner than the panel nominal"):
        WingBuild(panel(), ThicknessMap.flat(inch("0.100")))
