from decimal import Decimal

import pytest

from doublerbay.grid.coordinate import Station
from doublerbay.grid.wing import Rib, Spar, Surface, WingBay, WingStation
from doublerbay.grid.zone import Side


def test_a_wing_station_reads_with_or_without_its_letter():
    assert WingStation.parse("WS 240.5").inches == Decimal("240.500")
    assert WingStation.parse("240.5") == WingStation.of_inches("240.5")
    assert str(WingStation.of_inches("240.5")) == "WS 240.500"


@pytest.mark.parametrize("text", ["", "STA 240", "outboard", "2.4.0"])
def test_a_wing_station_refuses_what_is_not_one(text):
    with pytest.raises(ValueError, match="not a wing station"):
        WingStation.parse(text)


def test_a_wing_station_is_not_a_body_station():
    assert WingStation.of_inches("240") != Station.of_inches("240")
    with pytest.raises(TypeError, match="different rulers"):
        WingStation.of_inches("240") < Station.of_inches("240")


def test_the_lower_surface_is_the_one_fatigue_is_written_against():
    assert Surface.LOWER.is_tension_critical
    assert not Surface.UPPER.is_tension_critical
    assert str(Surface.LOWER) == "lower"


def test_the_spars_run_front_to_rear():
    assert Spar.FRONT.is_forward_of(Spar.MID)
    assert Spar.MID.is_forward_of(Spar.REAR)
    assert not Spar.REAR.is_forward_of(Spar.FRONT)
    assert str(Spar.FRONT) == "front spar"


def test_a_rib_carries_a_number_and_a_wing():
    assert str(Rib(12, Side.LEFT)) == "RIB12L"
    assert Rib.parse("RIB12L") == Rib(12, Side.LEFT)
    assert Rib.parse("rib-12r") == Rib(12, Side.RIGHT)


@pytest.mark.parametrize("text", ["", "RIB12", "12L", "RIB100L", "RIBL"])
def test_the_rib_parser_refuses_anything_else(text):
    with pytest.raises(ValueError, match="not a rib"):
        Rib.parse(text)


def test_a_rib_belongs_to_one_wing():
    with pytest.raises(ValueError, match="one wing or the other"):
        Rib(12, Side.CENTRE)


def test_a_rib_number_off_the_wing_is_refused():
    with pytest.raises(ValueError, match="not on this wing"):
        Rib(0, Side.LEFT)
    with pytest.raises(ValueError, match="not on this wing"):
        Rib(41, Side.LEFT)


def test_ribs_run_outboard_by_number_within_one_wing():
    assert Rib(4, Side.LEFT).is_inboard_of(Rib(12, Side.LEFT))
    with pytest.raises(ValueError, match="opposite wings do not compare"):
        Rib(4, Side.LEFT).is_inboard_of(Rib(12, Side.RIGHT))


def test_mirroring_a_rib_swaps_the_wing():
    assert Rib(12, Side.LEFT).mirrored() == Rib(12, Side.RIGHT)


def test_ribs_sort_by_wing_then_number():
    ordered = sorted([Rib(12, Side.RIGHT), Rib(4, Side.LEFT), Rib(12, Side.LEFT)])
    assert [str(rib) for rib in ordered] == ["RIB4L", "RIB12L", "RIB12R"]


def wing_bay(inboard=4, outboard=5, forward=Spar.FRONT, aft=Spar.REAR, surface=Surface.LOWER):
    return WingBay(Rib(inboard, Side.LEFT), Rib(outboard, Side.LEFT), forward, aft, surface)


def test_a_wing_bay_is_two_ribs_two_spars_and_a_surface():
    assert str(wing_bay()) == "RIB4L-RIB5L / front spar to rear spar / lower"
    assert wing_bay().side is Side.LEFT
    assert wing_bay().rib_pitches == 1


def test_a_lower_wing_bay_is_tension_critical_and_an_upper_one_is_not():
    assert wing_bay(surface=Surface.LOWER).is_tension_critical
    assert not wing_bay(surface=Surface.UPPER).is_tension_critical


def test_a_wing_bay_needs_its_bounds_the_right_way_round():
    with pytest.raises(ValueError, match="is not inboard of"):
        wing_bay(inboard=5, outboard=4)
    with pytest.raises(ValueError, match="is not forward of"):
        wing_bay(forward=Spar.REAR, aft=Spar.FRONT)


def test_a_wing_bay_does_not_cross_the_centreline():
    with pytest.raises(ValueError, match="does not cross the centreline"):
        WingBay(Rib(4, Side.LEFT), Rib(5, Side.RIGHT), Spar.FRONT, Spar.REAR, Surface.LOWER)
