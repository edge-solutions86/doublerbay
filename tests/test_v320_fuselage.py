import pytest

from doublerbay.errors import NotOnFile
from doublerbay.grid.coordinate import Station
from doublerbay.grid.lattice import Frame, Stringer
from doublerbay.grid.location import FuselagePoint
from doublerbay.grid.zone import Side, Zone
from doublerbay.measure.length import Length
from doublerbay.structure.joint import JointKind
from doublerbay.structure.panel import PanelKind
from doublerbay.v320.fuselage import (
    FRAME_COUNT,
    LAP_STRINGERS,
    STRINGER_ARCS,
    band_of,
    barrel,
    grid,
    kind_of,
    section_of,
    station_of_frame,
    zone_of,
)


def inch(text):
    return Length.of_inches(text)


def point(frame=15, aft="10.000", stringer=6, down="0.000"):
    return FuselagePoint(Frame(frame), inch(aft), Stringer(stringer, Side.LEFT), inch(down))


def test_the_nose_frame_sits_at_the_datum_station():
    assert station_of_frame(1) == Station.of_inches("178")
    assert station_of_frame(2) == Station.of_inches("198")


def test_the_wing_box_pitch_is_shorter_and_it_accumulates():
    assert station_of_frame(25).at - station_of_frame(24).at == inch("15.000")
    assert station_of_frame(24).at - station_of_frame(23).at == inch("20.000")
    assert station_of_frame(35).at - station_of_frame(34).at == inch("20.000")


def test_the_pressure_dome_frame_lands_where_the_shortening_leaves_it():
    assert station_of_frame(FRAME_COUNT) == Station.of_inches("1328")


def test_the_grid_carries_every_frame_and_stringer():
    built = grid()
    assert len(built.frames) == FRAME_COUNT
    assert len(built.stringers) == len(STRINGER_ARCS)
    assert built.around_of(Stringer(7, Side.LEFT)) == inch("63.700")


def test_the_sections_split_the_barrel_where_the_splices_are():
    assert section_of(1) == "forward"
    assert section_of(19) == "forward"
    assert section_of(20) == "centre"
    assert section_of(39) == "centre"
    assert section_of(40) == "aft"
    assert section_of(FRAME_COUNT) == "aft"


def test_the_bands_run_crown_to_keel():
    assert band_of(0) == "crown"
    assert band_of(4) == "upper"
    assert band_of(12) == "lower"
    assert band_of(20) == "keel"
    assert band_of(24) == "keel"


def test_the_tail_cone_is_not_pressurised_and_the_cabin_is():
    assert kind_of("upper", "aft") is PanelKind.UNPRESSURISED_SKIN
    assert kind_of("upper", "centre") is PanelKind.PRESSURISED_SKIN
    assert kind_of("crown", "forward") is PanelKind.CROWN_SKIN
    assert kind_of("keel", "forward") is PanelKind.BELLY_SKIN


def test_the_zones_carry_the_hand_in_the_last_digit():
    assert zone_of("upper", "forward", Side.LEFT) == Zone(211)
    assert zone_of("upper", "forward", Side.RIGHT) == Zone(212)
    assert zone_of("keel", "centre", Side.LEFT) == Zone(131)


def test_the_barrel_is_panelled_both_hands():
    built = barrel()
    left = [b for b in built.panels if b.panel.side is Side.LEFT]
    right = [b for b in built.panels if b.panel.side is Side.RIGHT]
    assert len(left) == len(right)
    assert len(built.panels) == 360


def test_every_panel_number_on_the_barrel_is_its_own():
    parts = [build.panel.part for build in barrel().panels]
    assert len(set(parts)) == len(parts)


def test_the_barrel_is_shared_rather_than_rebuilt():
    assert barrel() is barrel()


def test_a_cabin_point_resolves_onto_a_pressurised_panel():
    resolved = barrel().resolve(point())
    assert resolved.build.panel.kind is PanelKind.PRESSURISED_SKIN
    assert resolved.build.panel.nominal == inch("0.040")


def test_a_tail_cone_point_resolves_onto_an_unpressurised_panel():
    resolved = barrel().resolve(point(frame=50))
    assert resolved.build.panel.kind is PanelKind.UNPRESSURISED_SKIN
    assert resolved.build.thickness.is_flat


def test_a_centre_section_point_resolves_onto_heavier_skin():
    resolved = barrel().resolve(point(frame=30))
    assert resolved.build.panel.nominal == inch("0.050")


def test_the_lap_joints_are_where_the_manual_says_they_are():
    for number in LAP_STRINGERS:
        resolved = barrel().resolve(point(stringer=number, down="0.500"))
        joint = resolved.build.joint_on(Stringer(number, Side.LEFT))
        assert joint is not None
        assert joint.kind is JointKind.LAP


def test_a_stringer_with_no_lap_joint_carries_none():
    resolved = barrel().resolve(point(stringer=6, down="0.500"))
    assert resolved.build.joint_on(Stringer(6, Side.LEFT)) is None


def test_a_splice_frame_carries_a_butt_joint():
    resolved = barrel().resolve(point(frame=24, aft="1.000"))
    joint = resolved.build.joint_on(Frame(24))
    assert joint is not None
    assert joint.kind is JointKind.BUTT


def test_the_cabin_windows_are_on_the_barrel_and_named_by_frame():
    resolved = barrel().resolve(point(frame=20, aft="10.000", stringer=7))
    names = {cutout.name for cutout in resolved.build.cutouts}
    assert "W20L" in names


def test_a_point_off_the_end_of_the_barrel_finds_no_panel():
    with pytest.raises((NotOnFile, ValueError)):
        barrel().resolve(point(frame=FRAME_COUNT, aft="40.000"))
