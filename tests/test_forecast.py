from datetime import date
from decimal import Decimal

import pytest

from doublerbay.errors import BadReading
from doublerbay.fleet.forecast import Forecast
from doublerbay.fleet.utilisation import Snapshot, UtilisationLog, usage
from doublerbay.ids import Serial
from doublerbay.measure.interval import DueAt, Interval
from doublerbay.measure.usage import Cycles, Hours

MSN = Serial("5127")
AS_OF = date(2025, 1, 1)


def log():
    return UtilisationLog(
        MSN,
        (
            Snapshot(date(2024, 1, 1), usage(12000, 21000)),
            Snapshot(AS_OF, usage(13800, 24200)),
        ),
    )


def forecast(cycles_a_day="5", hours_a_day="9", horizon_days=7300):
    return Forecast(log(), Decimal(cycles_a_day), Decimal(hours_a_day), horizon_days)


def test_a_forecast_reads_back_the_rate_it_is_running_at():
    assert str(forecast()) == "MSN 5127: 5 FC and 9 FH a day"
    assert forecast().as_of == AS_OF
    assert forecast().flown == usage(13800, 24200)


def test_a_rate_taken_off_the_log_matches_the_log():
    taken = Forecast.from_log(log())
    assert taken.cycles_a_day == Decimal("4.918")
    assert taken.hours_a_day == Decimal("8.743")


def test_a_negative_rate_is_refused():
    with pytest.raises(BadReading, match="does not run backwards"):
        forecast(cycles_a_day="-1")


def test_a_horizon_of_nothing_is_refused():
    with pytest.raises(BadReading, match="at least a day"):
        forecast(horizon_days=0)


def test_the_projection_runs_the_counters_forward_at_the_rate():
    ahead = forecast().projected(date(2025, 1, 31))
    assert ahead.cycles == Cycles(13800 + 5 * 30)
    assert ahead.hours == Hours.of_hours(24200 + 9 * 30)


def test_a_projection_backwards_reads_the_log_instead_of_forecasting():
    assert forecast().projected(date(2024, 6, 1)) == usage(12000, 21000)
    assert forecast().projected(AS_OF) == usage(13800, 24200)


def test_a_cycle_limit_lands_when_the_cycles_get_there():
    due = DueAt(cycles=Cycles(14100))
    assert forecast().when_due(due) == date(2025, 3, 2)


def test_an_hour_limit_lands_when_the_hours_get_there():
    due = DueAt(hours=Hours.of_hours(24470))
    assert forecast().when_due(due) == date(2025, 1, 31)


def test_a_calendar_limit_lands_on_its_own_date():
    due = DueAt(on=date(2025, 3, 1))
    assert forecast().when_due(due) == date(2025, 3, 1)


def test_the_earliest_of_the_three_clocks_wins():
    due = Interval.parse("3000 FC / 300 FH / 24 MO").due_from(usage(13800, 24200), AS_OF)
    assert forecast().when_due(due) == date(2025, 2, 4)


def test_a_limit_already_reached_lands_today():
    due = DueAt(cycles=Cycles(13000))
    assert forecast().when_due(due) == AS_OF


def test_a_tail_that_is_not_flying_never_reaches_a_cycle_limit():
    grounded = forecast(cycles_a_day="0", hours_a_day="0")
    assert grounded.when_due(DueAt(cycles=Cycles(14100))) is None


def test_a_grounded_tail_still_reaches_a_calendar_limit():
    grounded = forecast(cycles_a_day="0", hours_a_day="0")
    assert grounded.when_due(DueAt(on=date(2026, 1, 1))) == date(2026, 1, 1)


def test_a_limit_past_the_horizon_is_not_forecast():
    near_sighted = forecast(horizon_days=30)
    assert near_sighted.when_due(DueAt(cycles=Cycles(14100))) is None
    assert near_sighted.when_due(DueAt(cycles=Cycles(13900))) == date(2025, 1, 21)


def test_a_due_with_no_clock_on_it_never_lands():
    assert forecast().when_due(DueAt()) is None
    assert forecast().days_until(DueAt()) is None


def test_the_days_left_counts_from_today_and_not_from_the_reading():
    due = DueAt(cycles=Cycles(14100))
    assert forecast().days_until(due) == 60
    assert forecast().days_until(due, today=date(2025, 2, 1)) == 29


def test_a_limit_already_past_has_no_days_left():
    due = DueAt(cycles=Cycles(13000))
    assert forecast().days_until(due, today=date(2025, 6, 1)) == 0


def test_forecasting_from_before_the_last_reading_is_refused():
    with pytest.raises(BadReading, match="before the last reading"):
        forecast().when_due(DueAt(cycles=Cycles(14100)), today=date(2024, 6, 1))
