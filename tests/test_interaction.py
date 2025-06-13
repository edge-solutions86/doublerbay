from decimal import Decimal

import pytest

from doublerbay.damage.cluster import DEFAULT_SEPARATION, SeparationRule
from doublerbay.errors import BadReading
from doublerbay.manual.interaction import DEFAULT_RULES, CountLimits, InteractionRules
from doublerbay.measure.length import Length


def inch(text):
    return Length.of_inches(text)


def test_a_separation_rule_takes_the_larger_extent_and_multiplies_it():
    rule = SeparationRule(Decimal("2"), inch("1.000"))
    assert rule.between(inch("3.000"), inch("1.000")) == inch("6.000")
    assert rule.between(inch("1.000"), inch("3.000")) == inch("6.000")


def test_the_floor_holds_when_both_damages_are_small():
    rule = SeparationRule(Decimal("1"), inch("1.000"))
    assert rule.between(inch("0.250"), inch("0.250")) == inch("1.000")


def test_a_separation_rule_reads_back_how_it_is_stated():
    assert str(DEFAULT_SEPARATION) == "1 times the larger, floor 1.000"


def test_a_separation_multiple_of_nothing_is_refused():
    with pytest.raises(BadReading, match="is a multiple"):
        SeparationRule(Decimal("0"), inch("1.000"))


def test_a_separation_floor_that_runs_backwards_is_refused():
    with pytest.raises(BadReading, match="does not run backwards"):
        SeparationRule(Decimal("1"), inch("-0.001"))


def test_count_limits_read_back_what_they_count():
    assert str(CountLimits(per_bay=2, per_panel=6, per_cluster=3)) == (
        "3 in a group, 2 a bay, 6 a panel"
    )
    assert str(CountLimits()) == "not counted"


def test_a_page_that_counts_nothing_says_so():
    assert not CountLimits().counts_anything
    assert CountLimits(per_panel=6).counts_anything


def test_a_count_limit_of_nothing_is_refused():
    with pytest.raises(BadReading, match="per bay allows at least one"):
        CountLimits(per_bay=0)
    with pytest.raises(BadReading, match="per panel allows at least one"):
        CountLimits(per_panel=0)


def test_a_bay_cannot_hold_more_than_the_panel_it_is_in():
    with pytest.raises(BadReading, match="cannot hold more than the panel"):
        CountLimits(per_bay=8, per_panel=6)


def test_a_count_at_the_limit_is_not_over_it():
    counts = CountLimits(per_bay=2, per_panel=6, per_cluster=3)
    assert not counts.bay_exceeded(2)
    assert counts.bay_exceeded(3)
    assert not counts.panel_exceeded(6)
    assert counts.panel_exceeded(7)
    assert not counts.cluster_exceeded(3)
    assert counts.cluster_exceeded(4)


def test_a_limit_the_book_does_not_state_is_never_exceeded():
    assert not CountLimits().bay_exceeded(99)
    assert not CountLimits().panel_exceeded(99)
    assert not CountLimits().cluster_exceeded(99)


def test_the_room_left_on_a_panel_runs_down_and_stops_at_nothing():
    counts = CountLimits(per_panel=6)
    assert counts.room_left_on_panel(2) == 4
    assert counts.room_left_on_panel(6) == 0
    assert counts.room_left_on_panel(9) == 0


def test_a_panel_the_book_does_not_count_has_no_room_to_report():
    assert CountLimits(per_bay=2).room_left_on_panel(2) is None


def test_the_rules_come_off_a_page_the_way_it_states_them():
    rules = InteractionRules.from_book(multiple="1.5", floor="2.000", per_panel=4)
    assert rules.separation_between(inch("4.000"), inch("2.000")) == inch("6.000")
    assert rules.counts.per_panel == 4
    assert rules.counts.per_bay is None


def test_the_default_rules_carry_the_v320_numbers():
    assert DEFAULT_RULES.counts.per_bay == 2
    assert DEFAULT_RULES.counts.per_panel == 6
    assert DEFAULT_RULES.counts.per_cluster == 3
    assert DEFAULT_RULES.separation_between(inch("0.100"), inch("0.100")) == inch("1.000")


def test_the_rules_read_back_as_one_line():
    printed = str(DEFAULT_RULES)
    assert printed.startswith("separation 1 times the larger")
    assert "counts 3 in a group, 2 a bay, 6 a panel" in printed


def test_the_count_after_clustering_is_the_one_that_matters():
    counts = CountLimits(per_panel=6)
    assert counts.panel_exceeded(8)
    assert not counts.panel_exceeded(5)
