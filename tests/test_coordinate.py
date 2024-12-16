from decimal import Decimal

import pytest

from doublerbay.grid.coordinate import ButtockLine, Station, Waterline
from doublerbay.measure.length import Length


def inch(text):
    return Length.of_inches(text)


def test_a_station_carries_its_letter():
    assert str(Station.of_inches("727.5")) == "STA 727.500"
    assert Station.of_inches("727.5").inches == Decimal("727.500")


def test_a_waterline_and_a_station_print_apart():
    assert str(Waterline.of_inches("208")) == "WL 208.000"


@pytest.mark.parametrize(
    ("text", "inches"),
    [("727.5", "727.500"), ("STA 727.5", "727.500"), ("sta727.5", "727.500"), ("-14", "-14.000")],
)
def test_a_station_reads_with_or_without_its_letter(text, inches):
    assert Station.parse(text).inches == Decimal(inches)


@pytest.mark.parametrize("text", ["", "WL 208", "aft", "727.5 STA", "7.2.7.5"])
def test_a_station_refuses_what_is_not_one(text):
    with pytest.raises(ValueError, match="not a station"):
        Station.parse(text)


def test_a_waterline_reads_its_own_letter_only():
    assert Waterline.parse("WL 208").inches == Decimal("208.000")
    with pytest.raises(ValueError, match="not a waterline"):
        Waterline.parse("STA 208")


def test_a_buttock_line_takes_its_hand_from_the_prefix():
    assert ButtockLine.parse("LBL 42").is_left
    assert not ButtockLine.parse("RBL 42").is_left
    assert ButtockLine.parse("BL 0").is_centreline


def test_a_signed_buttock_line_reads_the_same_as_a_handed_one():
    assert ButtockLine.parse("-42") == ButtockLine.parse("LBL 42")
    assert ButtockLine.parse("42") == ButtockLine.parse("RBL 42")


def test_a_handed_prefix_wins_over_the_sign_that_was_typed():
    assert ButtockLine.parse("LBL -42") == ButtockLine.parse("LBL 42")
    assert ButtockLine.parse("RBL -42") == ButtockLine.parse("RBL 42")


def test_a_buttock_line_prints_its_hand_back():
    assert str(ButtockLine.parse("LBL 42")) == "LBL 42.000"
    assert str(ButtockLine.parse("RBL 42")) == "RBL 42.000"
    assert str(ButtockLine.parse("0")) == "BL 0.000"


def test_mirroring_crosses_the_centreline():
    assert ButtockLine.parse("LBL 42").mirrored() == ButtockLine.parse("RBL 42")
    assert ButtockLine.parse("BL 0").mirrored() == ButtockLine.parse("BL 0")


def test_two_rulers_never_compare():
    with pytest.raises(TypeError, match="different rulers"):
        Station.of_inches("208") < Waterline.of_inches("208")
    with pytest.raises(TypeError, match="different rulers"):
        Station.of_inches("208").gap_to(Waterline.of_inches("208"))


def test_two_rulers_are_never_equal_even_at_the_same_reading():
    assert Station.of_inches("208") != Waterline.of_inches("208")
    assert len({Station.of_inches("208"), Waterline.of_inches("208")}) == 2


def test_readings_on_one_ruler_order_and_measure():
    forward = Station.of_inches("360")
    aft = Station.of_inches("727.5")
    assert forward < aft
    assert forward.gap_to(aft) == inch("367.5")
    assert aft.gap_to(forward) == inch("367.5")


def test_an_offset_stays_on_the_same_ruler():
    moved = Station.of_inches("360").offset(inch("20"))
    assert isinstance(moved, Station)
    assert moved == Station.of_inches("380")


def test_a_coordinate_has_to_be_built_from_a_length():
    with pytest.raises(TypeError, match="a coordinate is a length"):
        Station(727.5)  # type: ignore[arg-type]


def test_readings_hash_and_repr_by_value():
    assert len({Station.of_inches("360"), Station.parse("STA 360")}) == 1
    assert repr(Station.of_inches("360")) == "Station.of_inches('360.000')"
