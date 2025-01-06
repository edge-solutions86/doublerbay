from decimal import Decimal

import pytest

from doublerbay.grid.surface import (
    ZERO_OFFSET,
    Offset,
    arc_length,
    hypotenuse,
    subtended_angle,
)
from doublerbay.measure.angle import Angle
from doublerbay.measure.length import Length


def inch(text):
    return Length.of_inches(text)


def offset(along="3.0", around="4.0"):
    return Offset(inch(along), inch(around))


def test_the_third_side_comes_out_exact_on_a_whole_triangle():
    assert hypotenuse(inch("3.0"), inch("4.0")) == inch("5.0")
    assert hypotenuse(inch("0"), inch("4.0")) == inch("4.0")


def test_the_third_side_rounds_to_the_nearer_mil():
    assert hypotenuse(inch("1.0"), inch("1.0")) == Length(1414)


def test_an_offset_reads_as_its_two_components():
    assert str(offset()) == "3.000 along, 4.000 around"
    assert offset().direct == inch("5.0")


def test_a_direction_does_not_change_the_distance():
    assert offset(along="-3.0", around="-4.0").direct == inch("5.0")


def test_an_offset_of_nothing_is_zero_and_has_no_bearing():
    assert ZERO_OFFSET.is_zero
    assert offset(along="0", around="0").is_zero
    with pytest.raises(ValueError, match="has no bearing"):
        ZERO_OFFSET.bearing


def test_an_offset_along_the_body_bears_zero_and_around_it_bears_ninety():
    assert offset(along="10.0", around="0").bearing == Angle.of_degrees(0)
    assert offset(along="0", around="10.0").bearing == Angle.of_degrees(90)
    assert offset(along="10.0", around="10.0").bearing == Angle.of_degrees(45)


def test_a_bearing_the_other_way_lands_on_the_far_side_of_the_turn():
    assert offset(along="-10.0", around="0").bearing == Angle.of_degrees(180)
    assert offset(along="0", around="-10.0").bearing == Angle.of_degrees(270)


def test_a_radius_check_agrees_with_the_distance_it_avoids_computing():
    assert offset().within(inch("5.0"))
    assert offset().within(inch("6.0"))
    assert not offset().within(inch("4.999"))


def test_the_radius_check_admits_its_own_boundary():
    assert offset(along="0", around="4.0").within(inch("4.0"))
    assert not offset(along="0", around="4.001").within(inch("4.0"))


def test_offsets_add_and_negate():
    assert offset() + offset() == Offset(inch("6.0"), inch("8.0"))
    assert -offset() == Offset(inch("-3.0"), inch("-4.0"))


def test_adding_something_that_is_not_an_offset_is_refused():
    with pytest.raises(TypeError):
        offset() + inch("3.0")  # type: ignore[operator]


def test_scaling_moves_both_components():
    assert offset().scaled("0.5") == Offset(inch("1.5"), inch("2.0"))
    assert offset().scaled(Decimal("2")) == Offset(inch("6.0"), inch("8.0"))


def test_an_arc_is_longer_than_the_angle_suggests_on_a_wide_barrel():
    quarter = arc_length(inch("74.0"), Angle.of_degrees(90))
    assert quarter == Length(116239)


def test_a_full_turn_is_the_circumference():
    assert arc_length(inch("100"), Angle.of_degrees(0)) == Length(0)
    assert arc_length(inch("100"), Angle.of_degrees(180)) == Length(314159)


def test_the_arc_and_the_angle_invert_each_other():
    radius = inch("74.0")
    back = subtended_angle(radius, arc_length(radius, Angle.of_degrees(90)))
    assert back == Angle.of_degrees(90)


def test_an_arc_without_a_radius_is_refused():
    with pytest.raises(ValueError, match="an arc needs a radius"):
        arc_length(inch("0"), Angle.of_degrees(90))
    with pytest.raises(ValueError, match="an arc needs a radius"):
        subtended_angle(inch("0"), inch("10"))
