import sample
from doublerbay.assess.assessor import assess, assess_all, worst_disposition
from doublerbay.assess.verdict import Disposition
from doublerbay.damage.corrosion import Corrosion
from doublerbay.damage.crack import Crack
from doublerbay.damage.dent import Dent
from doublerbay.damage.kind import DamageKind, DamageOrigin
from doublerbay.damage.scoring import Scoring
from doublerbay.manual.book import Book, v320_book
from doublerbay.measure.angle import Angle
from doublerbay.v320 import manual57

BOOK = v320_book()


def inch(text):
    return sample.inch(text)


def in_the_bay(down="4.000", stringer=10):
    return sample.cabin_point(stringer=stringer, down=down)


def dent_card(depth="0.020", along="3.000", around="2.000", sharp=False, at=None, sequence=1):
    return sample.card(
        sequence=sequence,
        geometry=Dent(inch(depth), inch(along), inch(around), sharp),
        at=at if at is not None else in_the_bay(),
    )


def on_the_land(stringer=10):
    """A point on the full thickness land, where a blend has room to work."""
    return sample.cabin_point(stringer=stringer, down="0.000")


def scratch_card(depth="0.002", at=None, sequence=1):
    return sample.card(
        sequence=sequence,
        kind=DamageKind.SCRATCH,
        geometry=Scoring(inch(depth), inch("2.000"), inch("0.060"), Angle.of_degrees(0)),
        at=at if at is not None else on_the_land(),
    )


def assessed(finding):
    return assess(BOOK, sample.tail_context(), finding)


def test_a_small_smooth_dent_in_open_skin_is_allowable():
    answer = assessed(dent_card(depth="0.010", along="6.000", around="4.000"))
    assert answer.disposition is Disposition.ALLOWABLE
    assert not answer.needs_work


def test_the_verdict_cites_the_page_and_the_revision_it_came_off():
    answer = assessed(dent_card(depth="0.010", along="6.000", around="4.000"))
    assert str(answer.verdict.reference) == "53-10-01"
    assert answer.verdict.revision == 12


def test_a_deep_dent_goes_to_a_repair_because_there_is_nothing_to_grind():
    answer = assessed(dent_card(depth="0.090", along="6.000", around="4.000"))
    assert answer.disposition is Disposition.REPAIRABLE
    assert "depth" in {reason.rule for reason in answer.verdict.failures()}


def test_a_sharp_dent_goes_to_a_repair_however_shallow_it_is():
    answer = assessed(dent_card(depth="0.010", along="0.150", around="0.120"))
    assert answer.disposition is Disposition.REPAIRABLE
    assert "sharpness" in {reason.rule for reason in answer.verdict.failures()}


def test_a_cracked_dent_goes_to_a_repair():
    cracked = sample.card(geometry=Dent(inch("0.010"), inch("6.000"), inch("4.000"), False, True))
    answer = assessed(cracked)
    assert answer.disposition is Disposition.REPAIRABLE


def test_every_rule_runs_and_not_just_the_ones_up_to_the_first_no():
    answer = assessed(dent_card(depth="0.090", along="0.200", around="0.180"))
    rules = {reason.rule for reason in answer.verdict.reasons}
    assert "depth" in rules
    assert "sharpness" in rules
    assert "smoothness" in rules


def test_the_worst_answer_stands_and_not_the_last_one_that_ran():
    answer = assessed(dent_card(depth="0.090", along="6.000", around="4.000"))
    assert answer.verdict.passes()
    assert answer.disposition is Disposition.REPAIRABLE


def test_a_shallow_scratch_in_open_skin_is_allowable():
    answer = assessed(scratch_card(depth="0.001"))
    assert answer.disposition is Disposition.ALLOWABLE


def test_a_scratch_over_the_cap_is_reworkable_because_it_can_be_ground_out():
    answer = assessed(scratch_card(depth="0.003"))
    assert answer.disposition is Disposition.REWORKABLE


def test_a_scratch_too_deep_to_grind_out_is_a_repair_instead():
    answer = assessed(scratch_card(depth="0.008"))
    assert answer.disposition is Disposition.REPAIRABLE
    assert "residual" in {reason.rule for reason in answer.verdict.failures()}


def test_the_same_scratch_on_a_fastener_row_is_a_repair():
    on_a_row = scratch_card(depth="0.003", at=sample.cabin_point(stringer=8, down="0.500"))
    answer = assessed(on_a_row)
    assert answer.disposition is Disposition.REPAIRABLE


def test_a_crack_is_a_repair_at_any_length():
    cracked = sample.card(
        kind=DamageKind.CRACK,
        geometry=Crack(inch("0.100"), Angle.of_degrees(0)),
        at=in_the_bay(),
        origin=DamageOrigin.FATIGUE,
    )
    answer = assessed(cracked)
    assert answer.disposition is Disposition.REPAIRABLE
    assert "allowable" in {reason.rule for reason in answer.verdict.failures()}


def test_exfoliating_corrosion_is_a_repair_at_any_depth():
    lifted = sample.card(
        kind=DamageKind.CORROSION,
        geometry=Corrosion(inch("1.000"), inch("1.000"), inch("0.001"), False, True),
        at=in_the_bay(),
        origin=DamageOrigin.ENVIRONMENT,
    )
    assert assessed(lifted).disposition is Disposition.REPAIRABLE


def test_a_wing_lower_dent_is_a_repair_where_the_same_dent_on_the_barrel_is_not():
    barrel = assessed(dent_card(depth="0.010", along="6.000", around="4.000"))
    wing = assessed(
        sample.card(
            geometry=Dent(inch("0.010"), inch("6.000"), inch("4.000")),
            at=sample.lower_wing_point(),
        )
    )
    assert barrel.disposition is Disposition.ALLOWABLE
    assert wing.disposition is Disposition.REPAIRABLE


def test_a_chapter_that_is_not_on_the_shelf_lands_outside_the_manual():
    wing_only = Book()
    wing_only.shelve(manual57.manual(), manual57.PAGES, manual57.allowable_damage)
    answer = assess(wing_only, sample.tail_context(), dent_card())
    assert answer.disposition is Disposition.OUT_OF_LIMITS
    assert answer.row is None
    assert answer.verdict.reference is None
    assert "not on the shelf" in answer.verdict.why()


def test_a_card_assessed_before_the_campaign_gets_the_looser_limit():
    early = sample.tail_context(day=sample.date(2024, 6, 1))
    late = sample.tail_context(day=sample.date(2025, 3, 14))
    card = dent_card(depth="0.070", along="6.000", around="4.000")
    assert assess(BOOK, early, card).disposition is Disposition.ALLOWABLE
    assert assess(BOOK, late, card).disposition is Disposition.REPAIRABLE


def test_an_assessment_reads_back_the_card_the_panel_and_the_answer():
    printed = str(assessed(dent_card(depth="0.010", along="6.000", around="4.000")))
    assert printed.startswith("DR-2025-000001 on 5310-")
    assert "allowable" in printed


def test_a_run_of_cards_comes_back_in_the_order_they_were_raised():
    cards = (dent_card(sequence=3), dent_card(sequence=1), dent_card(sequence=2))
    answers = assess_all(BOOK, sample.tail_context(), cards)
    assert [one.finding.reference.sequence for one in answers] == [1, 2, 3]


def test_the_worst_of_a_run_is_what_the_panel_reports():
    cards = (
        dent_card(sequence=1, depth="0.010", along="6.000", around="4.000"),
        dent_card(sequence=2, depth="0.090", along="6.000", around="4.000"),
    )
    answers = assess_all(BOOK, sample.tail_context(), cards)
    assert worst_disposition(answers) is Disposition.REPAIRABLE


def test_nothing_open_reports_as_allowable():
    assert worst_disposition(()) is Disposition.ALLOWABLE
