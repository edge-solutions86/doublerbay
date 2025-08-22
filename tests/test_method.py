import pytest

from doublerbay.errors import BadReading, NotOnFile
from doublerbay.measure.length import Length
from doublerbay.tolerance.method import (
    InspectionMethod,
    by_cost,
    cheapest_that_finds,
    is_credible,
)


def inch(text):
    return Length.of_inches(text)


def test_a_method_names_itself_the_way_the_card_does():
    assert str(InspectionMethod.HFEC) == "high frequency eddy current"
    assert str(InspectionMethod.GVI) == "general visual"


def test_the_visual_methods_are_not_non_destructive_testing():
    assert not InspectionMethod.GVI.is_ndt
    assert not InspectionMethod.DVI.is_ndt
    assert InspectionMethod.HFEC.is_ndt
    assert InspectionMethod.UT.is_ndt


def test_only_eddy_current_needs_the_paint_off():
    assert InspectionMethod.HFEC.needs_paint_off
    assert InspectionMethod.LFEC.needs_paint_off
    assert not InspectionMethod.UT.needs_paint_off
    assert not InspectionMethod.DVI.needs_paint_off


def test_the_methods_that_see_the_far_side_of_the_skin():
    assert InspectionMethod.LFEC.reaches_the_far_side
    assert InspectionMethod.UT.reaches_the_far_side
    assert InspectionMethod.XRAY.reaches_the_far_side
    assert not InspectionMethod.HFEC.reaches_the_far_side
    assert not InspectionMethod.GVI.reaches_the_far_side


def test_a_walk_round_finds_a_big_crack_and_not_a_small_one():
    assert InspectionMethod.GVI.can_find(inch("2.000"))
    assert not InspectionMethod.GVI.can_find(inch("1.999"))


def test_eddy_current_finds_what_the_eye_never_would():
    assert InspectionMethod.HFEC.can_find(inch("0.050"))
    assert not InspectionMethod.DVI.can_find(inch("0.050"))


def test_nothing_has_no_size_to_find():
    with pytest.raises(BadReading, match="no size to find"):
        InspectionMethod.GVI.can_find(inch("0"))


def test_the_methods_list_in_order_of_cost_and_not_of_sensitivity():
    ordered = by_cost()
    assert ordered[0] is InspectionMethod.GVI
    assert ordered[-1] is InspectionMethod.HFEC
    assert ordered[0].finds_down_to > ordered[-1].finds_down_to


def test_the_cheapest_method_that_can_do_the_job_is_the_one_chosen():
    assert cheapest_that_finds(inch("3.000")) is InspectionMethod.GVI
    assert cheapest_that_finds(inch("1.000")) is InspectionMethod.DVI
    assert cheapest_that_finds(inch("0.300")) is InspectionMethod.XRAY
    assert cheapest_that_finds(inch("0.060")) is InspectionMethod.HFEC


def test_the_boundary_belongs_to_the_cheaper_method():
    assert cheapest_that_finds(inch("2.000")) is InspectionMethod.GVI
    assert cheapest_that_finds(inch("1.999")) is InspectionMethod.DVI


def test_looking_for_something_on_the_far_side_rules_out_half_the_methods():
    assert cheapest_that_finds(inch("3.000"), far_side=True) is InspectionMethod.XRAY
    assert cheapest_that_finds(inch("0.150"), far_side=True) in (
        InspectionMethod.UT,
        InspectionMethod.LFEC,
    )


def test_nothing_finds_a_crack_smaller_than_the_best_method_can():
    with pytest.raises(NotOnFile, match="nothing on file finds"):
        cheapest_that_finds(inch("0.010"))


def test_nothing_on_the_far_side_is_found_below_what_ultrasonic_can_do():
    with pytest.raises(NotOnFile, match="nothing on file finds"):
        cheapest_that_finds(inch("0.060"), far_side=True)


def test_a_method_that_would_not_find_it_is_not_credible():
    assert is_credible(InspectionMethod.GVI, inch("3.000"))
    assert not is_credible(InspectionMethod.GVI, inch("0.100"))


def test_a_near_side_method_is_not_credible_for_far_side_damage():
    assert not is_credible(InspectionMethod.HFEC, inch("3.000"), far_side=True)
    assert is_credible(InspectionMethod.UT, inch("3.000"), far_side=True)


def test_every_method_carries_a_size_it_finds_down_to():
    for method in InspectionMethod:
        assert method.finds_down_to.mils > 0
