from datetime import date
from decimal import Decimal

import pytest

import sample
from doublerbay.disposition.deferral import grant
from doublerbay.errors import BadReading
from doublerbay.fleet.forecast import Forecast
from doublerbay.ids import FindingId, Serial
from doublerbay.measure.interval import DueAt, Interval
from doublerbay.measure.usage import Cycles, Hours
from doublerbay.report.due import (
    HORIZON_DAYS,
    Clock,
    Margin,
    days_to,
    deferral_due,
    due_list,
    overrun,
)
from doublerbay.tolerance.method import InspectionMethod

DAY = date(2025, 3, 14)
MSN = Serial("5127")
FLOWN = sample.flown(13800, 24200)


def rate(cycles="4", hours="7"):
    return Forecast(sample.flying_log(), Decimal(cycles), Decimal(hours))


def cycles_due(count):
    return DueAt(cycles=Cycles(count))


def deferral(sequence=41, limit="500 FC", on=DAY, method=InspectionMethod.DVI):
    return grant(
        FindingId(2025, sequence),
        on,
        FLOWN,
        "j.mercer",
        method=method,
        engineer_set=Interval.parse(limit),
    )


def test_a_cycles_limit_becomes_days_at_the_rate():
    margin = days_to(cycles_due(13840), FLOWN, rate("4"), DAY)
    assert margin.clock is Clock.CYCLES
    assert margin.days == 10


def test_an_hours_limit_becomes_days_at_the_rate():
    due = DueAt(hours=Hours.of_hours("24270"))
    assert days_to(due, FLOWN, rate(hours="7"), DAY).days == 10


def test_a_calendar_limit_is_already_in_days():
    margin = days_to(DueAt(on=date(2025, 3, 24)), FLOWN, rate(), DAY)
    assert margin == Margin(10, Clock.CALENDAR)


def test_the_tightest_of_the_three_is_the_one_reported():
    due = DueAt(cycles=Cycles(13840), hours=Hours.of_hours("24500"), on=date(2026, 1, 1))
    assert days_to(due, FLOWN, rate(), DAY).clock is Clock.CYCLES


def test_a_limit_already_passed_comes_back_negative():
    margin = days_to(cycles_due(13780), FLOWN, rate("4"), DAY)
    assert margin.days == -5
    assert margin.is_overrun


def test_an_overrun_rounds_away_from_the_aeroplane():
    # Ten cycles over at three a day is four days over, not three.
    margin = days_to(cycles_due(13790), FLOWN, rate("3"), DAY)
    assert margin.days == -4


def test_a_margin_left_rounds_towards_the_aeroplane():
    assert days_to(cycles_due(13810), FLOWN, rate("3"), DAY).days == 3


def test_a_limit_landing_exactly_on_today_is_due_now():
    margin = days_to(cycles_due(13800), FLOWN, rate("4"), DAY)
    assert margin.days == 0
    assert margin.is_due_now
    assert not margin.is_overrun


def test_a_tail_that_is_not_flying_never_reaches_a_cycles_limit():
    assert days_to(cycles_due(14000), FLOWN, rate("0"), DAY).days == HORIZON_DAYS


def test_a_tail_that_is_not_flying_is_still_over_a_limit_it_passed():
    assert days_to(cycles_due(13000), FLOWN, rate("0"), DAY).days == 0


def test_a_margin_beyond_the_horizon_is_cut_at_the_horizon():
    assert days_to(cycles_due(999999), FLOWN, rate("1"), DAY).days == HORIZON_DAYS


def test_a_due_point_with_no_clocks_cannot_be_counted_down():
    with pytest.raises(BadReading, match="no clocks on it"):
        days_to(DueAt(), FLOWN, rate(), DAY)


def test_a_margin_reads_back_as_days_and_a_clock():
    assert str(Margin(10, Clock.CYCLES)) == "10 days on flight cycles"
    assert str(Margin(-3, Clock.CALENDAR)) == "3 days over on calendar"


def test_a_deferral_becomes_a_line_naming_the_method():
    item = deferral_due(deferral(method=InspectionMethod.HFEC), MSN, FLOWN, rate(), DAY)
    assert "high frequency eddy current" in item.what
    assert item.against == FindingId(2025, 41)


def test_a_due_line_reads_back_with_the_tail_and_the_margin():
    item = deferral_due(deferral(limit="500 FC"), MSN, FLOWN, rate("4"), DAY)
    assert str(item).startswith("MSN 5127 DR-2025-000041 deferral")
    assert "125 days" in str(item)


def test_the_list_puts_the_worst_at_the_top():
    items = due_list(
        (deferral(41, "500 FC"), deferral(42, "50 FC"), deferral(43, "200 FC")),
        MSN,
        FLOWN,
        rate("4"),
        DAY,
    )
    assert [one.against.sequence for one in items] == [42, 43, 41]


def test_two_lines_with_the_same_margin_sort_by_reference():
    items = due_list((deferral(43, "50 FC"), deferral(42, "50 FC")), MSN, FLOWN, rate("4"), DAY)
    assert [one.against.sequence for one in items] == [42, 43]


def test_a_horizon_cuts_the_list_off():
    items = due_list(
        (deferral(41, "500 FC"), deferral(42, "50 FC")), MSN, FLOWN, rate("4"), DAY, within_days=30
    )
    assert [one.against.sequence for one in items] == [42]


def test_a_horizon_still_carries_the_ones_already_over():
    over = grant(
        FindingId(2025, 44),
        date(2024, 1, 1),
        sample.flown(100, 200),
        "j.mercer",
        engineer_set=Interval.parse("50 FC"),
    )
    items = due_list((over,), MSN, FLOWN, rate("4"), DAY, within_days=1)
    assert len(items) == 1


def test_a_horizon_that_runs_backwards_is_refused():
    with pytest.raises(BadReading, match="count of days ahead"):
        due_list((deferral(),), MSN, FLOWN, rate(), DAY, within_days=-1)


def test_an_empty_list_is_an_empty_list():
    assert due_list((), MSN, FLOWN, rate(), DAY) == ()


def test_the_grounding_list_is_only_what_is_already_over():
    over = grant(
        FindingId(2025, 44),
        date(2024, 1, 1),
        sample.flown(100, 200),
        "j.mercer",
        engineer_set=Interval.parse("50 FC"),
    )
    items = due_list((deferral(41, "500 FC"), over), MSN, FLOWN, rate("4"), DAY)
    assert [one.against.sequence for one in overrun(items)] == [44]


def test_nothing_over_is_an_empty_grounding_list():
    items = due_list((deferral(41, "500 FC"),), MSN, FLOWN, rate("4"), DAY)
    assert overrun(items) == ()
