import pytest

import sample
from doublerbay.assess.panel import assess_by_panel, assess_panel
from doublerbay.assess.verdict import Disposition
from doublerbay.damage.dent import Dent
from doublerbay.manual.book import v320_book

BOOK = v320_book()


def inch(text):
    return sample.inch(text)


def small_dent(sequence, aft="10.000", stringer=10, down="4.000", along="1.000"):
    return sample.card(
        sequence=sequence,
        geometry=Dent(inch("0.002"), inch(along), inch(along)),
        at=sample.cabin_point(frame=15, aft=aft, stringer=stringer, down=down),
    )


def panel(*cards):
    return assess_panel(BOOK, sample.tail_context(), cards)


def test_one_card_on_a_panel_is_one_group():
    found = panel(small_dent(1))
    assert found.card_count == 1
    assert found.damage_count == 1
    assert found.disposition is Disposition.ALLOWABLE


def test_the_panel_reads_back_its_cards_groups_and_answer():
    printed = str(panel(small_dent(1)))
    assert "1 cards in 1 groups" in printed
    assert "allowable" in printed


def test_a_panel_with_no_cards_is_refused():
    with pytest.raises(ValueError, match="at least one card"):
        panel()


def test_cards_on_two_different_panels_cannot_be_assessed_as_one():
    far_apart = sample.card(sequence=2, at=sample.cabin_point(frame=40))
    with pytest.raises(ValueError, match="is not on"):
        panel(small_dent(1), far_apart)


def test_two_far_apart_cards_stay_two_groups():
    found = panel(small_dent(1, aft="2.000"), small_dent(2, aft="16.000"))
    assert found.damage_count == 2


def test_two_close_cards_come_back_as_one_group():
    found = panel(small_dent(1, aft="8.000"), small_dent(2, aft="8.500"))
    assert found.damage_count == 1
    assert found.card_count == 2


def test_a_card_taken_together_with_a_neighbour_says_so_on_its_own_face():
    found = panel(small_dent(1, aft="8.000"), small_dent(2, aft="8.500"))
    one = found.for_card(small_dent(1).reference)
    assert "group" in {reason.rule for reason in one.verdict.reasons}
    group_reason = next(r for r in one.verdict.reasons if r.rule == "group")
    assert "inside the clear separation" in group_reason.detail


def test_a_group_inside_the_count_does_not_raise_the_answer():
    found = panel(small_dent(1, aft="8.000"), small_dent(2, aft="8.500"))
    assert found.disposition is Disposition.ALLOWABLE


def test_a_group_over_the_count_raises_every_card_in_it():
    crowded = panel(
        small_dent(1, aft="8.000"),
        small_dent(2, aft="8.400"),
        small_dent(3, aft="8.800"),
        small_dent(4, aft="9.200"),
    )
    assert crowded.damage_count == 1
    assert crowded.disposition is Disposition.REPAIRABLE
    for one in crowded.assessments:
        assert one.disposition is Disposition.REPAIRABLE


def test_the_bay_count_bites_on_separate_damages_in_one_bay():
    spread = panel(
        small_dent(1, aft="2.000", down="2.000"),
        small_dent(2, aft="9.000", down="6.000"),
        small_dent(3, aft="16.000", down="2.000"),
    )
    assert spread.damage_count == 3
    assert spread.disposition is Disposition.REPAIRABLE
    reasons = {reason.rule for reason in spread.assessments[0].verdict.reasons}
    assert "bay count" in reasons


def test_two_damages_in_a_bay_are_inside_the_bay_count():
    pair = panel(
        small_dent(1, aft="2.000", down="2.000"),
        small_dent(2, aft="16.000", down="6.000"),
    )
    assert pair.disposition is Disposition.ALLOWABLE


def test_every_card_carries_the_panel_count_in_its_trail():
    found = panel(small_dent(1))
    rules = {reason.rule for reason in found.assessments[0].verdict.reasons}
    assert "panel count" in rules


def test_the_panel_count_is_taken_on_groups_and_not_on_cards():
    crowded = panel(
        small_dent(1, aft="8.000"),
        small_dent(2, aft="8.400"),
        small_dent(3, aft="8.800"),
    )
    assert crowded.card_count == 3
    assert crowded.damage_count == 1
    panel_reason = next(
        reason for reason in crowded.assessments[0].verdict.reasons if reason.rule == "panel count"
    )
    assert panel_reason.detail.startswith("1 on")


def test_a_card_that_is_not_on_the_panel_cannot_be_looked_up():
    found = panel(small_dent(1))
    with pytest.raises(KeyError):
        found.for_card(small_dent(9).reference)


def test_cards_split_across_panels_come_back_as_one_assessment_each():
    forward = small_dent(1)
    aft = sample.card(sequence=2, at=sample.cabin_point(frame=40, stringer=10))
    panels = assess_by_panel(BOOK, sample.tail_context(), (forward, aft))
    assert len(panels) == 2
    assert {one.card_count for one in panels} == {1}


def test_the_split_keeps_cards_on_one_panel_together():
    panels = assess_by_panel(
        BOOK,
        sample.tail_context(),
        (small_dent(1, aft="2.000"), small_dent(2, aft="16.000")),
    )
    assert len(panels) == 1
    assert panels[0].card_count == 2
