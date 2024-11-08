from decimal import Decimal

import pytest

from doublerbay.measure.length import Length
from doublerbay.measure.ratio import Ratio, Taper, sharpness


def inch(text):
    return Length.of_inches(text)


def test_a_ratio_is_one_length_over_another():
    assert Ratio.between(inch("0.040"), inch("2.000")).value == Decimal("0.0200")


def test_a_ratio_over_nothing_is_refused():
    with pytest.raises(ZeroDivisionError):
        Ratio.between(inch("0.040"), inch("0"))


def test_a_ratio_reads_as_a_percentage():
    assert str(Ratio("0.025")) == "2.50%"
    assert Ratio("0.025").percent == Decimal("2.50")


def test_a_ratio_scales_a_length():
    assert Ratio("0.5").of(inch("0.040")) == inch("0.020")


def test_ratios_order_and_compare():
    assert Ratio("0.01") < Ratio("0.02")
    assert Ratio("0.02") >= Ratio("0.02")
    assert Ratio("0.0200") == Ratio("0.02")
    assert len({Ratio("0.02"), Ratio("0.0200")}) == 1


def test_the_shallow_dent_and_the_crease_grade_apart():
    shallow = sharpness(inch("0.250"), inch("6.000"))
    crease = sharpness(inch("0.250"), inch("0.500"))
    assert shallow < crease
    assert crease.percent == Decimal("50.00")


def test_a_taper_reads_and_writes_the_way_the_shop_does():
    assert str(Taper.parse("20:1")) == "20:1"
    assert Taper.parse(" 30 : 1 ").run == 30


@pytest.mark.parametrize("text", ["20", "1:20", "20:2", "", "twenty:1"])
def test_the_taper_parser_refuses_anything_else(text):
    with pytest.raises(ValueError):
        Taper.parse(text)


def test_a_taper_flatter_than_one_to_one_is_not_a_taper():
    with pytest.raises(ValueError, match="at least one to one"):
        Taper(0)


def test_the_runout_is_the_depth_times_the_run():
    assert Taper(20).runout(inch("0.005")) == inch("0.100")


def test_the_bigger_number_is_the_gentler_blend():
    assert Taper(30).is_gentler_than(Taper(20))
    assert not Taper(20).is_gentler_than(Taper(30))


def test_a_gentler_blend_satisfies_a_sharper_requirement():
    assert Taper(30).satisfies(Taper(20))
    assert Taper(20).satisfies(Taper(20))
    assert not Taper(15).satisfies(Taper(20))


def test_tapers_order_by_their_run():
    assert sorted([Taper(30), Taper(10), Taper(20)]) == [Taper(10), Taper(20), Taper(30)]
    assert len({Taper(20), Taper(20)}) == 1


def test_a_taper_is_not_a_ratio():
    assert Taper(20) != Ratio("20")
    assert Ratio("20") != Taper(20)
