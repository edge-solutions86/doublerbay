from decimal import Decimal

import pytest

import sample
from doublerbay.assess.placement import place
from doublerbay.damage.dent import Dent
from doublerbay.damage.kind import DamageKind
from doublerbay.damage.scoring import Scoring
from doublerbay.errors import Refused
from doublerbay.manual.cleanup import standard_rules
from doublerbay.manual.limits import LimitSet
from doublerbay.measure.angle import Angle
from doublerbay.measure.ratio import Ratio
from doublerbay.repair.blend import blend_would_clear, plan_blend

RULES = standard_rules()


def inch(text):
    return sample.inch(text)


def scratch(depth="0.003", length="2.000", at=None):
    return sample.card(
        kind=DamageKind.SCRATCH,
        geometry=Scoring(inch(depth), inch(length), inch("0.060"), Angle.of_degrees(0)),
        at=at if at is not None else sample.cabin_point(stringer=10, down="0.000"),
    )


def planned(finding=None, limits=None):
    finding = finding if finding is not None else scratch()
    placement = place(sample.tail_context(), finding)
    return plan_blend(finding, placement, RULES.for_kind(finding.kind), limits or LimitSet())


def test_the_cut_goes_past_the_bottom_of_the_damage():
    plan = planned(scratch(depth="0.003"))
    assert plan.cut_depth == inch("0.005")


def test_what_is_left_is_the_thickness_less_the_cut():
    plan = planned(scratch(depth="0.003"))
    assert plan.residual == inch("0.035")


def test_the_blend_runs_out_past_both_ends_of_the_damage():
    plan = planned(scratch(depth="0.003", length="2.000"))
    assert plan.along == inch("2.200")
    assert plan.around > inch("0.060")


def test_the_plan_reads_back_as_a_work_card_line():
    printed = str(planned(scratch(depth="0.003")))
    assert printed.startswith("cut 0.005 at 20:1")
    assert "0.035 left" in printed


def test_even_the_shallowest_blend_goes_through_the_cladding_on_thin_skin():
    shallow = sample.card(
        kind=DamageKind.SCRATCH,
        geometry=Scoring(inch("0.001"), inch("1.000"), inch("0.060"), Angle.of_degrees(0)),
        at=sample.cabin_point(stringer=10, down="0.000"),
    )
    plan = planned(shallow)
    assert plan.cut_depth == inch("0.003")
    assert plan.breaches_clad
    assert plan.finish_required


def test_a_cut_through_the_cladding_asks_for_the_finish_back():
    plan = planned(scratch(depth="0.005"))
    assert plan.breaches_clad
    assert plan.finish_required
    assert str(plan).endswith("refinish")


def test_a_kind_with_no_depth_has_nothing_to_blend():
    dented = sample.card(geometry=Dent(inch("0.010"), inch("3.000"), inch("2.000")))
    with pytest.raises(Refused, match="no blend here"):
        planned(dented)


def test_a_blend_is_refused_where_the_book_will_not_have_one():
    on_a_row = scratch(at=sample.cabin_point(stringer=8, down="0.500"))
    with pytest.raises(Refused, match="no blend here"):
        planned(on_a_row)


def test_a_cut_that_would_go_through_the_skin_is_refused():
    deep = scratch(depth="0.060")
    with pytest.raises(Refused, match="nothing left"):
        planned(deep)


def test_the_plan_area_is_the_finished_blend_and_not_the_damage():
    plan = planned(scratch(depth="0.003", length="2.000"))
    assert plan.area > Decimal("0.10")
    assert plan.longer_side == plan.along


def test_a_plan_that_leaves_enough_clears_the_residual_floor():
    plan = planned(scratch(depth="0.003"))
    assert plan.leaves_enough(LimitSet(min_residual_fraction=Ratio("0.85")), inch("0.040"))
    assert not plan.leaves_enough(LimitSet(min_residual_fraction=Ratio("0.95")), inch("0.040"))


def test_a_plan_with_no_residual_floor_always_leaves_enough():
    assert planned().leaves_enough(LimitSet(), inch("0.040"))


def test_a_plan_inside_the_area_cap_clears_it():
    plan = planned(scratch(depth="0.003", length="1.000"))
    assert plan.within_area(LimitSet(max_area=Decimal("4.00")))
    assert not plan.within_area(LimitSet(max_area=Decimal("0.01")))
    assert plan.within_area(LimitSet())


def test_a_blend_that_gets_the_damage_inside_the_book_says_so():
    finding = scratch(depth="0.003")
    placement = place(sample.tail_context(), finding)
    limits = LimitSet(min_residual_fraction=Ratio("0.85"))
    assert blend_would_clear(finding, placement, RULES.for_kind(finding.kind), limits)


def test_a_blend_that_would_not_leave_enough_does_not_clear_it():
    finding = scratch(depth="0.010")
    placement = place(sample.tail_context(), finding)
    limits = LimitSet(min_residual_fraction=Ratio("0.85"))
    assert not blend_would_clear(finding, placement, RULES.for_kind(finding.kind), limits)


def test_a_blend_that_cannot_happen_at_all_does_not_clear_it():
    finding = scratch(at=sample.cabin_point(stringer=8, down="0.500"))
    placement = place(sample.tail_context(), finding)
    assert not blend_would_clear(finding, placement, RULES.for_kind(finding.kind), LimitSet())


def test_a_blend_taking_too_much_of_the_panel_does_not_clear_it():
    finding = scratch(depth="0.003", length="30.000")
    placement = place(sample.tail_context(), finding)
    assert not blend_would_clear(finding, placement, RULES.for_kind(finding.kind), LimitSet())
