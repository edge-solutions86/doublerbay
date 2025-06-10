from decimal import Decimal

import pytest

from doublerbay.errors import BadReading
from doublerbay.manual.limits import LimitSet
from doublerbay.measure.fastener import FastenerSize
from doublerbay.measure.length import Length
from doublerbay.measure.ratio import Ratio, Taper

SKIN = Length.of_inches("0.063")


def inch(text):
    return Length.of_inches(text)


def limits(**kwargs):
    return LimitSet(**kwargs)


def test_a_limit_set_reads_back_the_numbers_it_carries():
    printed = str(limits(max_depth=inch("0.005"), max_sharpness=Ratio("0.02")))
    assert "depth 0.005" in printed
    assert "sharpness 2.00%" in printed


def test_a_page_that_allows_nothing_says_so():
    assert str(LimitSet.nothing_allowable()) == "nothing allowable"
    assert not LimitSet.nothing_allowable().allowed


def test_a_page_that_allows_nothing_cannot_also_carry_a_limit():
    with pytest.raises(BadReading, match="does not carry limits"):
        LimitSet(allowed=False, max_depth=inch("0.005"))


def test_a_page_that_allows_something_and_caps_nothing_is_an_open_page():
    assert limits().is_open
    assert not limits(max_depth=inch("0.005")).is_open
    assert not LimitSet.nothing_allowable().is_open
    assert str(limits()) == "no limits stated"


def test_an_absolute_depth_cap_reads_straight_off():
    assert limits(max_depth=inch("0.005")).depth_cap(SKIN) == inch("0.005")


def test_a_fractional_depth_cap_is_read_against_the_thickness_at_the_damage():
    ten_percent = limits(max_depth_fraction=Ratio("0.10"))
    assert ten_percent.depth_cap(SKIN) == Length(6)
    assert ten_percent.depth_cap(inch("0.100")) == inch("0.010")


def test_the_tighter_of_the_two_depth_forms_governs():
    both = limits(max_depth=inch("0.005"), max_depth_fraction=Ratio("0.10"))
    assert both.depth_cap(SKIN) == Length(5)
    assert both.depth_cap(inch("0.032")) == Length(3)


def test_a_page_with_no_depth_form_caps_no_depth():
    assert limits(max_length=inch("2.000")).depth_cap(SKIN) is None


def test_an_absolute_residual_floor_reads_straight_off():
    assert limits(min_residual=inch("0.030")).residual_floor(SKIN) == inch("0.030")


def test_a_fractional_residual_floor_is_read_against_the_thickness():
    assert limits(min_residual_fraction=Ratio("0.80")).residual_floor(SKIN) == Length(50)


def test_the_higher_of_the_two_residual_forms_governs():
    both = limits(min_residual=inch("0.055"), min_residual_fraction=Ratio("0.80"))
    assert both.residual_floor(SKIN) == inch("0.055")
    assert both.residual_floor(inch("0.100")) == inch("0.080")


def test_a_page_with_no_residual_form_floors_nothing():
    assert limits(max_depth=inch("0.005")).residual_floor(SKIN) is None


def test_an_edge_margin_in_inches_reads_without_a_fastener():
    assert limits(min_edge_margin=inch("0.500")).edge_margin_floor() == inch("0.500")


def test_an_edge_margin_in_diameters_needs_the_fastener_it_is_written_against():
    in_d = limits(min_edge_margin_diameters=Decimal("2.0"))
    assert in_d.edge_margin_floor(FastenerSize(8)) == inch("0.500")
    assert in_d.edge_margin_floor(FastenerSize(4)) == inch("0.250")
    with pytest.raises(BadReading, match="needs a fastener size"):
        in_d.edge_margin_floor()


def test_the_larger_edge_margin_form_governs():
    both = limits(min_edge_margin=inch("0.400"), min_edge_margin_diameters=Decimal("2.0"))
    assert both.edge_margin_floor(FastenerSize(8)) == inch("0.500")
    assert both.edge_margin_floor(FastenerSize(4)) == inch("0.400")


def test_a_page_with_no_edge_margin_floors_nothing():
    assert limits(max_depth=inch("0.005")).edge_margin_floor(FastenerSize(5)) is None


def test_what_a_depth_cap_leaves_behind_can_be_compared_with_the_residual_floor():
    page = limits(max_depth=inch("0.005"), min_residual_fraction=Ratio("0.80"))
    assert page.implied_residual(SKIN) == inch("0.058")
    assert page.residual_floor(SKIN) == Length(50)
    assert page.implied_residual(SKIN) > page.residual_floor(SKIN)


def test_a_page_with_no_depth_cap_implies_no_residual():
    assert limits(max_length=inch("2.000")).implied_residual(SKIN) is None


def test_an_area_limit_has_to_be_an_area():
    with pytest.raises(BadReading, match="an area limit is an area"):
        limits(max_area=Decimal("0"))


def test_an_edge_margin_in_diameters_has_to_be_a_distance():
    with pytest.raises(BadReading, match="is a distance"):
        limits(min_edge_margin_diameters=Decimal("0"))


def test_a_blend_taper_rides_along_with_the_limits():
    assert limits(blend_taper=Taper(20)).blend_taper == Taper(20)
    assert limits().blend_taper is None


def test_a_limit_set_is_frozen_and_hashes_by_value():
    assert len({limits(max_depth=inch("0.005")), limits(max_depth=inch("0.005"))}) == 1
    with pytest.raises(AttributeError):
        limits().allowed = False
