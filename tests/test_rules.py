from decimal import Decimal

import sample
from doublerbay.assess.placement import place
from doublerbay.assess.rules import (
    CHECKS,
    Case,
    allowed_check,
    area_check,
    blend_share_check,
    depth_check,
    edge_margin_check,
    exfoliation_check,
    length_check,
    residual_check,
    sharpness_check,
    smoothness_check,
)
from doublerbay.assess.verdict import Disposition
from doublerbay.damage.corrosion import Corrosion
from doublerbay.damage.crack import Crack
from doublerbay.damage.dent import Dent
from doublerbay.damage.kind import DamageKind, DamageOrigin
from doublerbay.damage.scoring import Scoring
from doublerbay.manual.cleanup import standard_rules
from doublerbay.manual.limits import LimitSet
from doublerbay.measure.angle import Angle
from doublerbay.measure.ratio import Ratio


def inch(text):
    return sample.inch(text)


def case(finding=None, limits=None, at=None, kind=DamageKind.DENT):
    finding = finding if finding is not None else sample.card(at=at or sample.cabin_point())
    return Case(
        finding=finding,
        placement=place(sample.tail_context(), finding),
        limits=limits if limits is not None else LimitSet(),
        cleanup=standard_rules().for_kind(finding.kind),
    )


def dent(depth="0.020", along="3.000", around="2.000", sharp=False, cracked=False):
    return sample.card(
        geometry=Dent(inch(depth), inch(along), inch(around), sharp, cracked),
        at=sample.cabin_point(stringer=10, down="4.000"),
    )


def scratch(depth="0.002", length="2.000"):
    return sample.card(
        kind=DamageKind.SCRATCH,
        geometry=Scoring(inch(depth), inch(length), inch("0.060"), Angle.of_degrees(0)),
        at=sample.cabin_point(stringer=10, down="4.000"),
    )


def rot(along="2.000", around="1.000", depth="0.001", exfoliating=False):
    return sample.card(
        kind=DamageKind.CORROSION,
        geometry=Corrosion(inch(along), inch(around), inch(depth), False, exfoliating),
        at=sample.cabin_point(stringer=10, down="4.000"),
        origin=DamageOrigin.ENVIRONMENT,
    )


def test_a_page_that_allows_nothing_fails_the_first_check():
    blocked = case(limits=LimitSet.nothing_allowable())
    assert not allowed_check(blocked).passed
    assert allowed_check(blocked).outcome is Disposition.REPAIRABLE


def test_a_page_that_allows_something_passes_the_first_check():
    assert allowed_check(case()).passed
    assert allowed_check(case()).outcome is Disposition.ALLOWABLE


def test_a_depth_inside_the_cap_passes():
    checked = depth_check(case(finding=dent("0.020"), limits=LimitSet(max_depth=inch("0.060"))))
    assert checked.passed
    assert "0.020 deep against a cap of 0.060" in checked.reason.detail


def test_a_scratch_over_the_cap_lands_on_rework_because_it_can_be_ground_out():
    checked = depth_check(case(finding=scratch("0.006"), limits=LimitSet(max_depth=inch("0.003"))))
    assert not checked.passed
    assert checked.outcome is Disposition.REWORKABLE


def test_a_dent_over_the_cap_lands_on_repair_because_there_is_nothing_to_grind():
    checked = depth_check(case(finding=dent("0.080"), limits=LimitSet(max_depth=inch("0.060"))))
    assert not checked.passed
    assert checked.outcome is Disposition.REPAIRABLE


def test_the_same_scratch_lands_on_repair_where_grinding_is_not_allowed():
    on_a_row = sample.card(
        kind=DamageKind.SCRATCH,
        geometry=Scoring(inch("0.006"), inch("2.000"), inch("0.060"), Angle.of_degrees(0)),
        at=sample.cabin_point(stringer=8, down="0.500"),
    )
    checked = depth_check(case(finding=on_a_row, limits=LimitSet(max_depth=inch("0.003"))))
    assert not checked.passed
    assert checked.outcome is Disposition.REPAIRABLE


def test_a_fractional_cap_is_read_against_the_thickness_at_the_damage():
    checked = depth_check(
        case(finding=scratch("0.003"), limits=LimitSet(max_depth_fraction=Ratio("0.10")))
    )
    assert checked.passed


def test_a_page_with_no_depth_cap_runs_no_depth_check():
    assert depth_check(case(finding=dent(), limits=LimitSet(max_length=inch("1.0")))) is None


def test_a_kind_with_no_depth_runs_no_depth_check():
    cracked = sample.card(
        kind=DamageKind.CRACK,
        geometry=Crack(inch("0.750"), Angle.of_degrees(0)),
        at=sample.cabin_point(stringer=10, down="4.000"),
        origin=DamageOrigin.FATIGUE,
    )
    assert depth_check(case(finding=cracked, limits=LimitSet(max_depth=inch("0.010")))) is None


def test_the_residual_floor_is_measured_on_what_is_left_under_the_damage():
    checked = residual_check(
        case(finding=scratch("0.002"), limits=LimitSet(min_residual_fraction=Ratio("0.90")))
    )
    assert checked.passed
    over = residual_check(
        case(finding=scratch("0.010"), limits=LimitSet(min_residual_fraction=Ratio("0.90")))
    )
    assert not over.passed
    assert over.outcome is Disposition.REPAIRABLE


def test_a_sharp_dent_fails_the_sharpness_check():
    limits = LimitSet(max_sharpness=Ratio("0.05"))
    assert sharpness_check(case(finding=dent("0.020", "3.000", "2.000"), limits=limits)).passed
    tight = sharpness_check(case(finding=dent("0.020", "0.300", "0.250"), limits=limits))
    assert not tight.passed


def test_sharpness_is_taken_on_the_narrow_diameter():
    limits = LimitSet(max_sharpness=Ratio("0.05"))
    checked = sharpness_check(case(finding=dent("0.020", "12.000", "0.300"), limits=limits))
    assert not checked.passed


def test_a_cracked_or_sharp_bottomed_dent_fails_the_smoothness_check():
    assert smoothness_check(case(finding=dent())).passed
    assert not smoothness_check(case(finding=dent(cracked=True))).passed
    assert not smoothness_check(case(finding=dent(sharp=True))).passed
    assert "cracked" in smoothness_check(case(finding=dent(cracked=True))).reason.detail


def test_only_a_dent_gets_the_dent_checks():
    assert sharpness_check(case(finding=scratch())) is None
    assert smoothness_check(case(finding=scratch())) is None


def test_a_score_longer_than_the_cap_fails():
    limits = LimitSet(max_length=inch("1.000"))
    assert not length_check(case(finding=scratch(length="2.000"), limits=limits)).passed
    assert length_check(case(finding=scratch(length="0.500"), limits=limits)).passed


def test_only_the_kinds_measured_as_a_patch_get_the_area_check():
    limits = LimitSet(max_area=Decimal("4.00"))
    assert area_check(case(finding=dent(), limits=limits)) is None
    assert area_check(case(finding=rot(), limits=limits)).passed


def test_corrosion_over_the_area_cap_fails():
    limits = LimitSet(max_area=Decimal("1.00"))
    assert not area_check(case(finding=rot(along="4.000", around="2.000"), limits=limits)).passed


def test_exfoliation_fails_whatever_the_depth_says():
    assert exfoliation_check(case(finding=rot())).passed
    lifted = exfoliation_check(case(finding=rot(depth="0.001", exfoliating=True)))
    assert not lifted.passed
    assert lifted.outcome is Disposition.REPAIRABLE


def test_only_corrosion_gets_the_exfoliation_check():
    assert exfoliation_check(case(finding=dent())) is None


def test_the_edge_margin_check_reads_the_fastener_where_there_is_one():
    on_a_row = sample.card(at=sample.cabin_point(stringer=8, down="0.500"))
    limits = LimitSet(min_edge_margin_diameters=Decimal("2.0"))
    checked = edge_margin_check(case(finding=on_a_row, limits=limits))
    assert checked is not None
    assert checked.outcome in (Disposition.ALLOWABLE, Disposition.REPAIRABLE)


def test_a_page_with_no_edge_margin_runs_no_edge_margin_check():
    assert edge_margin_check(case(finding=dent(), limits=LimitSet())) is None


def test_a_blend_that_would_take_too_much_of_the_panel_fails():
    wide = sample.card(
        kind=DamageKind.SCRATCH,
        geometry=Scoring(inch("0.006"), inch("30.000"), inch("0.060"), Angle.of_degrees(0)),
        at=sample.cabin_point(stringer=10, down="4.000"),
    )
    checked = blend_share_check(case(finding=wide))
    assert not checked.passed
    assert "of it" in checked.reason.detail


def test_a_small_blend_passes_the_share_check():
    assert blend_share_check(case(finding=scratch())).passed


def test_no_blend_share_is_checked_where_grinding_is_not_allowed():
    assert blend_share_check(case(finding=dent())) is None


def test_every_check_takes_a_case_and_answers_or_stands_aside():
    walked = case(finding=dent(), limits=LimitSet(max_depth=inch("0.060")))
    for check in CHECKS:
        answer = check(walked)
        assert answer is None or answer.reason.rule
