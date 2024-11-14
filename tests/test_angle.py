from decimal import Decimal

import pytest

from doublerbay.measure.angle import (
    AXIS_BAND,
    COUNTERSINK_100,
    Angle,
    Orientation,
    orientation_of,
)


def deg(value):
    return Angle.of_degrees(value)


def test_an_angle_keeps_its_tenth():
    assert deg("12.5").tenths == 125
    assert deg("12.5").degrees == Decimal("12.5")
    assert str(deg("12.5")) == "12.5 deg"


def test_finer_than_a_tenth_is_refused():
    with pytest.raises(ValueError, match="finer than a tenth"):
        deg("12.55")


def test_tenths_must_be_whole():
    with pytest.raises(TypeError):
        Angle(12.5)  # type: ignore[arg-type]


def test_an_angle_folds_into_a_full_turn():
    assert deg(370) == deg(10)
    assert deg(-10) == deg(350)


def test_a_crack_axis_folds_onto_a_half_turn():
    assert deg(275).folded() == deg(95)
    assert deg(95).folded() == deg(95)
    assert deg(180).folded() == deg(0)


def test_separation_takes_the_short_way_round():
    assert deg(10).separation(deg(350)) == deg(20)
    assert deg(350).separation(deg(10)) == deg(20)
    assert deg(0).separation(deg(180)) == deg(180)


def test_axis_separation_ignores_which_end_you_measured_from():
    assert deg(275).axis_separation(deg(95)) == deg(0)
    assert deg(10).axis_separation(deg(170)) == deg(20)


def test_angles_add_and_subtract_and_stay_on_the_turn():
    assert deg(350) + deg(20) == deg(10)
    assert deg(10) - deg(20) == deg(350)


def test_angles_order_and_hash_by_value():
    assert deg(10) < deg(20)
    assert sorted([deg(90), deg(0), deg(45)]) == [deg(0), deg(45), deg(90)]
    assert len({deg(90), deg(450)}) == 1


def test_mixing_an_angle_with_a_number_is_refused():
    with pytest.raises(TypeError):
        deg(90) + 90  # type: ignore[operator]


@pytest.mark.parametrize(
    ("bearing", "expected"),
    [
        (0, Orientation.LONGITUDINAL),
        (14, Orientation.LONGITUDINAL),
        (166, Orientation.LONGITUDINAL),
        (180, Orientation.LONGITUDINAL),
        (90, Orientation.CIRCUMFERENTIAL),
        (76, Orientation.CIRCUMFERENTIAL),
        (104, Orientation.CIRCUMFERENTIAL),
        (270, Orientation.CIRCUMFERENTIAL),
        (45, Orientation.OBLIQUE),
        (30, Orientation.OBLIQUE),
        (135, Orientation.OBLIQUE),
    ],
)
def test_a_bearing_lands_on_an_axis_or_between_them(bearing, expected):
    assert orientation_of(deg(bearing)) == expected


def test_the_band_edge_belongs_to_the_axis():
    assert orientation_of(AXIS_BAND) == Orientation.LONGITUDINAL
    assert orientation_of(deg(15) + deg(1)) == Orientation.OBLIQUE


def test_the_countersink_is_the_angle_it_says_it_is():
    assert COUNTERSINK_100.degrees == Decimal("100.0")


def test_repr_round_trips():
    assert repr(deg("12.5")) == "Angle.of_degrees('12.5')"
