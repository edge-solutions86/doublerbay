from dataclasses import replace

import sample
from doublerbay.assess.assessor import assess
from doublerbay.assess.placement import place
from doublerbay.assess.regions import (
    CORNER_CRACK,
    CORNER_REGIONS,
    STEP_REGIONS,
    STEP_SHARE,
    RegionCase,
    checks_for,
    corner_crack_check,
    step_residual_check,
    under_doubler_check,
)
from doublerbay.assess.rules import Case
from doublerbay.assess.verdict import Disposition
from doublerbay.damage.crack import Crack
from doublerbay.damage.dent import Dent
from doublerbay.damage.kind import DamageKind, DamageOrigin
from doublerbay.manual.book import v320_book
from doublerbay.measure.angle import Angle
from doublerbay.structure.build import Region


def inch(text):
    return sample.inch(text)


def crack(length="0.100"):
    return Crack(inch(length), Angle(0))


def dent(depth="0.020"):
    return Dent(inch(depth), inch("3.000"), inch("2.000"))


def card(geometry=None, kind=DamageKind.DENT, origin=DamageOrigin.IMPACT):
    return sample.card(1, kind, geometry or dent(), at=sample.cabin_point(), origin=origin)


def case(geometry=None, region=Region.OPEN_FIELD, kind=DamageKind.DENT, origin=DamageOrigin.IMPACT):
    finding = card(geometry, kind, origin)
    placement = replace(place(sample.tail_context(), finding), region=region)
    book = v320_book()
    row = book.limits_for(
        placement.chapter,
        sample.DAY,
        sample.aircraft(),
        finding.kind,
        placement.panel_kind,
        placement.region,
    )
    return Case(finding, placement, row.limits, book.cleanup.for_kind(finding.kind))


def held(**rest):
    return RegionCase(case(**rest))


def test_a_region_case_reads_back_with_its_region():
    assert "in open field" in str(held())


def test_the_thin_side_is_a_share_of_the_nominal():
    made = held()
    assert made.thin_side.mils == int(made.case.thickness.mils * STEP_SHARE)


def test_the_step_rule_says_nothing_in_open_field():
    assert step_residual_check(held()) is None


def test_the_step_rule_applies_on_a_chem_mill_step():
    answer = step_residual_check(held(region=Region.CHEM_MILL_STEP))
    assert answer is not None


def test_the_step_rule_applies_on_a_land():
    assert step_residual_check(held(region=Region.LAND)) is not None


def test_the_step_regions_are_the_ones_where_thickness_changes():
    assert {Region.CHEM_MILL_STEP, Region.LAND} == STEP_REGIONS


def test_a_shallow_blend_leaves_material_on_the_thin_side():
    answer = step_residual_check(held(geometry=dent("0.005"), region=Region.CHEM_MILL_STEP))
    assert answer.passed
    assert "left of" in str(answer)


def test_a_deep_blend_goes_through_the_thin_side():
    answer = step_residual_check(held(geometry=dent("0.400"), region=Region.CHEM_MILL_STEP))
    assert not answer.passed
    assert "goes through" in str(answer)
    assert answer.outcome is Disposition.REPAIRABLE


def test_the_corner_rule_says_nothing_in_open_field():
    assert corner_crack_check(held(geometry=crack(), kind=DamageKind.CRACK)) is None


def test_the_corner_rule_says_nothing_about_a_dent():
    assert corner_crack_check(held(region=Region.CUTOUT_CORNER)) is None


def test_the_corner_regions_are_the_cutout_ones():
    assert {Region.CUTOUT_CORNER, Region.CUTOUT_EDGE} == CORNER_REGIONS


def test_a_short_crack_at_a_corner_passes():
    answer = corner_crack_check(
        held(
            geometry=crack("0.100"),
            region=Region.CUTOUT_CORNER,
            kind=DamageKind.CRACK,
            origin=DamageOrigin.FATIGUE,
        )
    )
    assert answer.passed
    assert "at the corner" in str(answer)


def test_a_long_crack_at_a_corner_goes_out_of_limits():
    answer = corner_crack_check(
        held(
            geometry=crack("0.500"),
            region=Region.CUTOUT_CORNER,
            kind=DamageKind.CRACK,
            origin=DamageOrigin.FATIGUE,
        )
    )
    assert not answer.passed
    assert answer.outcome is Disposition.OUT_OF_LIMITS


def test_a_crack_exactly_at_the_threshold_is_out_of_limits():
    answer = corner_crack_check(
        held(
            geometry=Crack(CORNER_CRACK, Angle(0)),
            region=Region.CUTOUT_CORNER,
            kind=DamageKind.CRACK,
            origin=DamageOrigin.FATIGUE,
        )
    )
    assert not answer.passed


def test_a_crack_in_the_keep_out_is_named_differently():
    answer = corner_crack_check(
        held(
            geometry=crack("0.100"),
            region=Region.CUTOUT_EDGE,
            kind=DamageKind.CRACK,
            origin=DamageOrigin.FATIGUE,
        )
    )
    assert "in the keep out" in str(answer)


def test_the_doubler_rule_says_nothing_in_open_field():
    assert under_doubler_check(held()) is None


def test_a_damage_under_a_doubler_always_fails():
    answer = under_doubler_check(held(region=Region.UNDER_DOUBLER))
    assert not answer.passed
    assert "comes off before" in str(answer)


def test_open_field_collects_no_region_rules():
    assert checks_for(case()) == ()


def test_a_chem_mill_step_collects_the_step_rule():
    assert len(checks_for(case(region=Region.CHEM_MILL_STEP))) == 1


def test_under_a_doubler_collects_the_doubler_rule():
    assert len(checks_for(case(region=Region.UNDER_DOUBLER))) == 1


def test_a_crack_at_a_corner_collects_the_corner_rule():
    found = checks_for(
        case(
            geometry=crack("0.500"),
            region=Region.CUTOUT_CORNER,
            kind=DamageKind.CRACK,
            origin=DamageOrigin.FATIGUE,
        )
    )
    assert len(found) == 1


def test_the_region_rules_reach_a_real_verdict():
    finding = card()
    answer = assess(v320_book(), sample.tail_context(), finding)
    named = {one.rule for one in answer.verdict.reasons}
    assert "allowable" in named
