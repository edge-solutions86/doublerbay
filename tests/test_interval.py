from datetime import date

import pytest

from doublerbay.measure.interval import DueAt, Interval, add_months
from doublerbay.measure.usage import Cycles, Hours, Usage


def flown(cycles, hours):
    return Usage(Cycles(cycles), Hours.of_hours(hours))


def test_an_interval_needs_at_least_one_clock():
    with pytest.raises(ValueError, match="at least one clock"):
        Interval()


def test_a_clock_set_to_nothing_is_refused():
    with pytest.raises(ValueError, match="in months counts at least one"):
        Interval(months=0)
    with pytest.raises(ValueError, match="in days counts at least one"):
        Interval(days=0)
    with pytest.raises(ValueError, match="in cycles counts at least one"):
        Interval(cycles=Cycles(0))


def test_the_manual_form_reads_back():
    parsed = Interval.parse("3000 FC / 4500 FH / 24 MO")
    assert parsed.cycles == Cycles(3000)
    assert parsed.hours == Hours.of_hours(4500)
    assert parsed.months == 24
    assert parsed.days is None
    assert str(parsed) == "3000 FC / 4500.0 FH / 24 MO"


def test_a_single_term_is_an_interval():
    assert Interval.parse("10 DY").days == 10
    assert Interval.parse("50fc").cycles == Cycles(50)


@pytest.mark.parametrize("text", ["", "  ", "3000", "3000 XX", "FC 3000", "3000 FC / oops"])
def test_the_parser_refuses_anything_else(text):
    with pytest.raises(ValueError):
        Interval.parse(text)


def test_adding_months_lands_inside_a_short_month():
    assert add_months(date(2025, 1, 31), 1) == date(2025, 2, 28)
    assert add_months(date(2024, 1, 31), 1) == date(2024, 2, 29)
    assert add_months(date(2025, 3, 15), 24) == date(2027, 3, 15)
    assert add_months(date(2025, 12, 1), 1) == date(2026, 1, 1)


def test_an_interval_lands_on_a_reading_and_a_date():
    due = Interval.parse("3000 FC / 4500 FH / 24 MO").due_from(
        flown(12000, 20000), date(2025, 3, 15)
    )
    assert due.cycles == Cycles(15000)
    assert due.hours == Hours.of_hours(24500)
    assert due.on == date(2027, 3, 15)


def test_the_tighter_of_days_and_months_wins():
    due = Interval(months=1, days=10).due_from(Usage.zero(), date(2025, 3, 1))
    assert due.on == date(2025, 3, 11)


def test_a_clock_the_interval_is_silent_on_stays_silent():
    due = Interval.parse("500 FC").due_from(flown(1000, 2000), date(2025, 3, 15))
    assert due.cycles == Cycles(1500)
    assert due.hours is None
    assert due.on is None


def test_whichever_comes_first_falls_due_on_any_one_of_them():
    due = DueAt(cycles=Cycles(15000), hours=Hours.of_hours(24500), on=date(2027, 3, 15))
    assert due.is_due(flown(15000, 20000), date(2026, 1, 1))
    assert due.is_due(flown(12000, 24500), date(2026, 1, 1))
    assert due.is_due(flown(12000, 20000), date(2027, 3, 15))
    assert not due.is_due(flown(14999, 24499), date(2027, 3, 14))


def test_a_due_with_nothing_on_it_never_falls_due():
    assert not DueAt().is_due(flown(90000, 90000), date(2099, 1, 1))
    assert str(DueAt()) == "no limit"


def test_what_is_left_on_each_clock():
    due = DueAt(cycles=Cycles(15000), on=date(2026, 1, 11))
    assert due.remaining_cycles(flown(12000, 0)) == Cycles(3000)
    assert due.remaining_days(date(2026, 1, 1)) == 10
    assert due.remaining_cycles(flown(16000, 0)) == Cycles(0)
    assert due.remaining_days(date(2026, 2, 1)) == 0


def test_a_clock_that_was_never_set_has_nothing_left_on_it():
    due = DueAt(cycles=Cycles(15000))
    assert due.remaining_days(date(2026, 1, 1)) is None
    assert DueAt(on=date(2026, 1, 1)).remaining_cycles(flown(1, 1)) is None


def test_an_escalation_moves_every_clock_the_interval_names():
    escalated = Interval.parse("3000 FC / 4500 FH / 24 MO").scaled("1.5")
    assert escalated.cycles == Cycles(4500)
    assert escalated.hours == Hours.of_hours(6750)
    assert escalated.months == 36
    assert escalated.days is None


def test_scaling_to_nothing_is_refused():
    with pytest.raises(ValueError, match="scaled to nothing"):
        Interval(months=24).scaled("0")


def test_a_due_reads_as_the_three_ways_it_can_land():
    due = DueAt(cycles=Cycles(15000), on=date(2027, 3, 15))
    assert str(due) == "15000 FC or 2027-03-15"
