from dataclasses import replace

import sample
from doublerbay.assess.joints import (
    ALONG_WITHIN_DEGREES,
    IN_THE_ROW,
    JointCase,
    along_the_row_check,
    bearing_of,
    checks_for,
    fatigue_critical_check,
    inside_margin_check,
    runs_along,
)
from doublerbay.assess.placement import place
from doublerbay.assess.rules import Case
from doublerbay.assess.verdict import Disposition
from doublerbay.damage.crack import Crack
from doublerbay.damage.kind import DamageKind, DamageOrigin
from doublerbay.grid.lattice import Frame, Stringer
from doublerbay.grid.zone import Side
from doublerbay.manual.book import v320_book
from doublerbay.measure.angle import Angle
from doublerbay.measure.fastener import FastenerSize
from doublerbay.structure.joint import FastenerRow, Joint, JointKind


def inch(text):
    return sample.inch(text)


def row(offset="0.500", dash=5):
    return FastenerRow(size=FastenerSize(dash), pitch=inch("1.000"), offset=inch(offset))


def joint(kind=JointKind.LAP, offsets=("0.500",), longitudinal=True):
    return Joint(
        kind=kind,
        follows=_member(longitudinal),
        rows=tuple(row(one) for one in offsets),
    )


def _member(longitudinal):
    """A joint follows a stringer along the body or a frame around it."""
    return Stringer(6, Side.LEFT) if longitudinal else Frame(15)


def crack(bearing=0, length="0.400"):
    return Crack(inch(length), Angle(bearing))


def card(geometry=None, kind=DamageKind.CRACK, origin=DamageOrigin.FATIGUE):
    return sample.card(
        1, kind, geometry or crack(), at=sample.cabin_point(stringer=10), origin=origin
    )


def case(geometry=None, margin=None, dash=5, kind=DamageKind.CRACK, origin=DamageOrigin.FATIGUE):
    finding = card(geometry, kind, origin)
    placement = place(sample.tail_context(), finding)
    if margin is not None:
        placement = replace(placement, edge_margin=inch(margin))
    placement = replace(placement, nearest_fastener=FastenerSize(dash) if dash else None)
    book = v320_book()
    row_found = book.limits_for(
        placement.chapter,
        sample.DAY,
        sample.aircraft(),
        finding.kind,
        placement.panel_kind,
        placement.region,
    )
    return Case(finding, placement, row_found.limits, book.cleanup.for_kind(finding.kind))


def held(margin="0.100", **rest):
    return JointCase(case(margin=margin, **rest), joint())


def test_a_crack_has_a_bearing():
    assert bearing_of(card()) is not None


def test_a_dent_has_no_bearing():
    from doublerbay.damage.dent import Dent

    made = sample.card(1, DamageKind.DENT, Dent(inch("0.020"), inch("3.000"), inch("2.000")))
    assert bearing_of(made) is None


def test_a_crack_along_the_body_runs_with_a_longitudinal_row():
    assert runs_along(card(crack(0)), joint()) is True


def test_a_crack_across_the_body_runs_against_a_longitudinal_row():
    assert runs_along(card(crack(900)), joint()) is False


def test_a_crack_just_off_the_line_still_runs_along_it():
    assert runs_along(card(crack(ALONG_WITHIN_DEGREES * 10)), joint()) is True


def test_a_crack_past_the_tolerance_does_not_run_along_it():
    assert runs_along(card(crack(ALONG_WITHIN_DEGREES * 10 + 10)), joint()) is False


def test_a_crack_at_the_far_end_of_the_line_still_runs_along_it():
    assert runs_along(card(crack(1800)), joint()) is True


def test_a_crack_across_a_circumferential_row_runs_along_it():
    across = joint(kind=JointKind.BUTT, longitudinal=False)
    assert runs_along(card(crack(900)), across) is True


def test_a_damage_with_no_direction_says_nothing():
    from doublerbay.damage.dent import Dent

    made = sample.card(1, DamageKind.DENT, Dent(inch("0.020"), inch("3.000"), inch("2.000")))
    assert runs_along(made, joint()) is None


def test_a_damage_close_to_the_row_is_in_it():
    assert held(margin="0.400").is_in_the_row


def test_a_damage_far_from_the_row_is_not_in_it():
    assert not held(margin="8.000").is_in_the_row


def test_a_damage_with_no_known_fastener_is_never_in_the_row():
    assert not held(margin="0.400", dash=0).is_in_the_row


def test_the_threshold_is_in_fastener_diameters():
    assert IN_THE_ROW == 2


def test_a_joint_case_reads_back_with_its_joint():
    assert str(held()).startswith("DR-2025-000001 on ")


def test_nothing_applies_when_the_damage_is_clear_of_the_row():
    assert inside_margin_check(held(margin="8.000")) is None
    assert along_the_row_check(held(margin="8.000")) is None
    assert fatigue_critical_check(held(margin="8.000")) is None


def test_a_damage_inside_the_margin_fails_the_margin_rule():
    answer = inside_margin_check(held(margin="0.400"))
    assert not answer.passed
    assert answer.outcome is Disposition.REPAIRABLE


def test_a_damage_outside_the_margin_passes_it():
    made = JointCase(case(margin="0.100"), joint(offsets=("0.200",)))
    answer = inside_margin_check(made)
    assert answer is not None
    assert answer.passed


def test_a_crack_along_the_row_fails_that_rule():
    answer = along_the_row_check(held(margin="0.400", geometry=crack(0)))
    assert not answer.passed
    assert "runs with the rivet line" in str(answer)


def test_a_crack_across_the_row_passes_it():
    answer = along_the_row_check(held(margin="0.400", geometry=crack(900)))
    assert answer.passed


def test_a_damage_with_no_direction_gets_no_along_rule():
    from doublerbay.damage.dent import Dent

    made = JointCase(
        case(
            geometry=Dent(inch("0.020"), inch("3.000"), inch("2.000")),
            margin="0.400",
            kind=DamageKind.DENT,
            origin=DamageOrigin.IMPACT,
        ),
        joint(),
    )
    assert along_the_row_check(made) is None


def test_a_lap_joint_is_fatigue_critical():
    answer = fatigue_critical_check(held(margin="0.400"))
    assert not answer.passed
    assert answer.outcome is Disposition.REWORKABLE


def test_a_butt_joint_is_not_fatigue_critical():
    made = JointCase(case(margin="0.400"), joint(kind=JointKind.BUTT, longitudinal=False))
    assert fatigue_critical_check(made).passed


def test_no_joint_means_no_joint_rules():
    assert checks_for(case(margin="0.400"), None) == ()


def test_a_damage_in_a_row_collects_every_rule():
    assert len(checks_for(case(margin="0.400"), joint())) == 3


def test_a_damage_clear_of_the_row_collects_none():
    assert checks_for(case(margin="8.000"), joint()) == ()
