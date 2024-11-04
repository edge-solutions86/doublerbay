from decimal import Decimal

import pytest

from doublerbay.measure import Length, parse_length
from doublerbay.measure.length import ZERO


def test_a_thousandth_survives_the_round_trip():
    assert Length.of_inches("0.040").mils == 40
    assert Length.of_inches("0.040").inches == Decimal("0.040")


def test_a_body_station_keeps_its_half_inch():
    assert Length.of_inches("727.5").mils == 727500
    assert str(Length.of_inches("727.5")) == "727.500 in"


def test_finer_than_a_thousandth_is_refused():
    with pytest.raises(ValueError, match="finer than a thousandth"):
        Length.of_inches("0.0405")


def test_mils_must_be_whole():
    with pytest.raises(TypeError):
        Length(40.5)  # type: ignore[arg-type]


def test_a_bool_is_not_a_count_of_mils():
    with pytest.raises(TypeError):
        Length(True)


def test_millimetres_come_back_as_inches():
    assert Length.of_mm("25.4") == Length.of_inches("1.000")
    assert Length.of_inches("1.000").mm == Decimal("25.40")


def test_arithmetic_stays_in_mils():
    a = Length.of_inches("0.040")
    b = Length.of_inches("0.016")
    assert (a + b).inches == Decimal("0.056")
    assert (a - b).inches == Decimal("0.024")
    assert (a * 3).inches == Decimal("0.120")
    assert (3 * a) == a * 3
    assert abs(-a) == a
    assert (-a).mils == -40


def test_scaling_by_a_ratio_rounds_to_the_thousandth():
    assert (Length.of_inches("0.063") * Decimal("0.5")).mils == 32


def test_ordering_and_equality():
    thin = Length.of_inches("0.032")
    thick = Length.of_inches("0.063")
    assert thin < thick
    assert thick >= thin
    assert thin != thick
    assert thin == Length.of_inches("0.032")
    assert sorted([thick, thin]) == [thin, thick]


def test_zero_is_falsey_and_everything_else_is_not():
    assert not ZERO
    assert Length.of_inches("0.001")


def test_a_length_hashes_by_value():
    assert len({Length.of_inches("0.040"), Length(40)}) == 1


def test_mixing_a_length_with_a_bare_number_is_refused():
    with pytest.raises(TypeError):
        Length.of_inches("0.040") + 40  # type: ignore[operator]


def test_comparing_a_length_with_a_bare_number_is_refused():
    with pytest.raises(TypeError):
        Length.of_inches("0.040") < 40  # type: ignore[operator]


@pytest.mark.parametrize(
    ("text", "mils"),
    [
        ("0.040", 40),
        (" 0.063 ", 63),
        (".5", 500),
        ("727.5", 727500),
        ("3/32", 94),
        ("1/4", 250),
        ("1-1/4", 1250),
        ("1 1/4", 1250),
        ("-1/8", -125),
    ],
)
def test_the_parser_takes_both_callout_forms(text, mils):
    assert parse_length(text).mils == mils


@pytest.mark.parametrize("text", ["", "   ", "3/0", "wide", "1//4", "0.040 in"])
def test_the_parser_refuses_what_is_not_a_callout(text):
    with pytest.raises(ValueError):
        parse_length(text)


def test_a_fraction_rounds_to_the_nearest_mil():
    assert parse_length("3/32").inches == Decimal("0.094")


def test_repr_round_trips():
    length = Length.of_inches("0.040")
    assert repr(length) == "Length.of_inches('0.040')"
