"""The joint rules seen through the assessor rather than on their own.

The unit tests for the rules build a joint by hand. These ones let the real
V320 structure decide where the joints are, which is the only way to catch a
rule that works perfectly and never fires because nothing on the aeroplane
ever reaches it.
"""

import sample
from doublerbay.assess.assessor import assess
from doublerbay.assess.joints import JOINT_CHECKS
from doublerbay.assess.placement import place
from doublerbay.assess.verdict import Disposition
from doublerbay.damage.crack import Crack
from doublerbay.damage.dent import Dent
from doublerbay.damage.kind import DamageKind, DamageOrigin
from doublerbay.manual.book import v320_book
from doublerbay.measure.angle import Angle
from doublerbay.structure.build import Region


def inch(text):
    return sample.inch(text)


def crack(bearing=0, length="0.300"):
    return Crack(inch(length), Angle(bearing))


def card(geometry=None, at=None, kind=DamageKind.CRACK, origin=DamageOrigin.FATIGUE):
    return sample.card(1, kind, geometry or crack(), at=at or sample.cabin_point(), origin=origin)


def placed(finding):
    return place(sample.tail_context(), finding)


def assessed(finding):
    return assess(v320_book(), sample.tail_context(), finding)


def on_a_row():
    """A point the real structure puts in a fastener row.

    Found by walking the stringers rather than asserted, because a hard coded
    offset is a test that passes until somebody moves a stringer by an inch.
    """
    for stringer in range(1, 30):
        for below in ("0.000", "0.200", "0.500"):
            point = sample.cabin_point(stringer=stringer, down=below)
            spot = placed(card(at=point))
            if spot.region is Region.FASTENER_ROW:
                return point
    raise AssertionError("no point on this aeroplane lands in a fastener row")


def away_from_a_row():
    """A point the real structure puts nowhere near a joint."""
    for stringer in range(1, 30):
        for below in ("6.000", "4.000", "3.000"):
            point = sample.cabin_point(stringer=stringer, down=below)
            if placed(card(at=point)).joint is None:
                return point
    raise AssertionError("no point on this aeroplane lands clear of a joint")


def test_the_structure_actually_has_a_fastener_row_on_it():
    assert on_a_row() is not None


def test_a_damage_in_a_row_carries_the_joint_on_its_placement():
    assert placed(card(at=on_a_row())).joint is not None


def test_a_damage_away_from_a_row_carries_no_joint():
    assert placed(card(at=away_from_a_row())).joint is None


def test_a_damage_in_a_row_knows_which_fastener_is_nearest():
    assert placed(card(at=on_a_row())).nearest_fastener is not None


def test_the_joint_rules_reach_the_verdict_in_a_row():
    named = {one.rule for one in assessed(card(at=on_a_row())).verdict.reasons}
    assert "fatigue critical" in named


def test_the_joint_rules_stay_out_of_a_verdict_away_from_a_row():
    named = {one.rule for one in assessed(card(at=away_from_a_row())).verdict.reasons}
    assert "fatigue critical" not in named
    assert "along the row" not in named


def test_a_crack_along_the_row_is_named_in_the_verdict():
    reasons = assessed(card(crack(0), at=on_a_row())).verdict.reasons
    assert any(one.rule == "along the row" for one in reasons)


def test_a_dent_in_a_row_gets_no_direction_rule():
    dent = Dent(inch("0.010"), inch("1.000"), inch("1.000"))
    made = card(dent, at=on_a_row(), kind=DamageKind.DENT, origin=DamageOrigin.IMPACT)
    named = {one.rule for one in assessed(made).verdict.reasons}
    assert "along the row" not in named
    assert "fatigue critical" in named


def test_a_damage_in_a_lap_joint_is_never_left_as_it_stands():
    answer = assessed(card(at=on_a_row()))
    assert answer.disposition is not Disposition.ALLOWABLE


def test_the_same_damage_away_from_a_row_can_be_allowable():
    dent = Dent(inch("0.005"), inch("1.000"), inch("1.000"))
    made = card(dent, at=away_from_a_row(), kind=DamageKind.DENT, origin=DamageOrigin.IMPACT)
    assert assessed(made).disposition is Disposition.ALLOWABLE


def test_every_joint_rule_has_a_chance_to_fire():
    reasons = assessed(card(crack(0), at=on_a_row())).verdict.reasons
    named = {one.rule for one in reasons}
    assert len(named & {"joint edge margin", "along the row", "fatigue critical"}) == len(
        JOINT_CHECKS
    )


def test_the_ordinary_rules_still_run_in_a_row():
    named = {one.rule for one in assessed(card(at=on_a_row())).verdict.reasons}
    assert "allowable" in named


def test_the_verdict_still_names_the_page_it_came_off():
    assert assessed(card(at=on_a_row())).verdict.reference is not None
