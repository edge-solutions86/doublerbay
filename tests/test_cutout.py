import pytest

from doublerbay.errors import BadReading
from doublerbay.grid.surface import Offset
from doublerbay.measure.length import Length
from doublerbay.structure.cutout import Cutout, CutoutKind


def inch(text):
    return Length.of_inches(text)


def at(along, around):
    return Offset(inch(along), inch(around))


def hatch(along="8.000", around="6.000", radius="1.000", kind=CutoutKind.ACCESS_PANEL):
    return Cutout("191AL", kind, at("10.000", "10.000"), inch(along), inch(around), inch(radius))


def test_a_cutout_names_itself_and_what_it_is_for():
    assert str(hatch()) == "191AL (access panel)"


def test_the_plugged_holes_are_the_pressure_boundary():
    assert CutoutKind.WINDOW.is_pressure_boundary
    assert CutoutKind.DOOR.is_pressure_boundary
    assert not CutoutKind.DRAIN.is_pressure_boundary
    assert CutoutKind.DRAIN.is_vented
    assert not CutoutKind.WINDOW.is_vented


def test_a_cutout_needs_a_name_and_a_size():
    with pytest.raises(BadReading, match="a cutout has a name"):
        Cutout(" ", CutoutKind.DRAIN, at("0", "0"), inch("1"), inch("1"), inch("0.5"))
    with pytest.raises(BadReading, match="has a size"):
        hatch(along="0")


def test_a_square_corner_is_not_a_cutout():
    with pytest.raises(BadReading, match="a square corner is not a cutout"):
        hatch(radius="0")


def test_a_radius_past_the_middle_is_refused():
    with pytest.raises(BadReading, match="past the middle"):
        hatch(radius="3.001")


def test_a_radius_that_meets_in_the_middle_is_a_round_hole():
    assert hatch(along="1.000", around="1.000", radius="0.500").is_round
    assert not hatch().is_round


def test_the_centre_of_a_hole_is_inside_it():
    assert hatch().contains(at("10.000", "10.000"))
    assert hatch().edge_distance(at("10.000", "10.000")) == Length(0)


def test_the_edge_of_a_hole_counts_as_inside_it():
    assert hatch().contains(at("14.000", "10.000"))
    assert hatch().contains(at("10.000", "13.000"))


def test_just_outside_the_edge_measures_from_the_edge():
    assert hatch().edge_distance(at("15.000", "10.000")) == inch("1.000")
    assert hatch().edge_distance(at("10.000", "14.000")) == inch("1.000")


def test_a_point_off_a_corner_measures_round_the_radius():
    assert hatch().edge_distance(at("14.000", "13.000")) == Length(414)


def test_the_keep_out_band_is_twice_the_radius():
    assert hatch().keep_out == inch("2.000")
    assert hatch(radius="2.000", around="6.000").keep_out == inch("4.000")


def test_the_keep_out_band_never_falls_below_its_floor():
    drain = hatch(along="1.000", around="1.000", radius="0.125", kind=CutoutKind.DRAIN)
    assert drain.keep_out == inch("1.000")


def test_a_point_inside_the_band_is_assessed_under_the_cutout_rules():
    assert hatch().in_keep_out(at("15.000", "10.000"))
    assert hatch().in_keep_out(at("16.000", "10.000"))
    assert not hatch().in_keep_out(at("17.000", "10.000"))


def test_a_point_inside_the_hole_is_inside_the_band_as_well():
    assert hatch().in_keep_out(at("10.000", "10.000"))


def test_a_point_off_a_flat_side_is_not_a_corner_point():
    assert not hatch().near_a_corner(at("15.000", "10.000"))
    assert not hatch().near_a_corner(at("10.000", "14.000"))


def test_a_point_off_the_diagonal_is_a_corner_point():
    assert hatch().near_a_corner(at("14.000", "13.000"))
    assert hatch().near_a_corner(at("16.000", "15.000"))


def test_the_hole_is_positioned_where_it_was_put_and_not_at_the_origin():
    moved = Cutout(
        "191AL",
        CutoutKind.ACCESS_PANEL,
        at("40.000", "10.000"),
        inch("8.000"),
        inch("6.000"),
        inch("1.000"),
    )
    assert moved.contains(at("40.000", "10.000"))
    assert not moved.contains(at("10.000", "10.000"))


def test_a_cutout_is_frozen_and_hashes_by_value():
    assert len({hatch(), hatch()}) == 1
    with pytest.raises(AttributeError):
        hatch().corner_radius = inch("2.000")
