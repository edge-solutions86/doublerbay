from datetime import date
from decimal import Decimal

import pytest

from doublerbay.errors import BadReading, NotOnFile
from doublerbay.fleet.utilisation import Snapshot, UtilisationLog, usage
from doublerbay.ids import Serial
from doublerbay.measure.usage import Cycles, Hours, Usage

MSN = Serial("5127")


def log(*readings):
    if not readings:
        readings = (
            (date(2024, 1, 1), 12000, 21000),
            (date(2024, 7, 1), 12900, 22600),
            (date(2025, 1, 1), 13800, 24200),
        )
    return UtilisationLog(
        MSN, tuple(Snapshot(day, usage(cycles, hours)) for day, cycles, hours in readings)
    )


def test_a_reading_reads_back_as_a_date_and_both_counters():
    assert (
        str(Snapshot(date(2025, 1, 1), usage(13800, 24200))) == "2025-01-01 13800 FC / 24200.0 FH"
    )


def test_a_log_reads_back_its_latest_reading():
    assert str(log()) == "MSN 5127: 2025-01-01 13800 FC / 24200.0 FH"
    assert log().latest.usage == usage(13800, 24200)
    assert log().opened_on == date(2024, 1, 1)


def test_a_new_airframe_opens_at_nothing():
    fresh = UtilisationLog.opened(MSN, date(2025, 6, 1))
    assert fresh.latest.usage == Usage.zero()
    assert fresh.opened_on == date(2025, 6, 1)


def test_an_empty_log_is_refused():
    with pytest.raises(BadReading, match="at least one reading"):
        UtilisationLog(MSN, ())


def test_readings_have_to_be_posted_oldest_first():
    with pytest.raises(BadReading, match="posted oldest first"):
        UtilisationLog(MSN, tuple(reversed(log().snapshots)))


def test_two_readings_on_one_day_are_refused():
    with pytest.raises(BadReading, match="same day"):
        log(
            (date(2024, 1, 1), 12000, 21000),
            (date(2024, 1, 1), 12100, 21200),
        )


def test_a_counter_that_falls_is_refused():
    with pytest.raises(BadReading, match="cycles fell"):
        log(
            (date(2024, 1, 1), 12000, 21000),
            (date(2024, 7, 1), 11900, 22600),
        )
    with pytest.raises(BadReading, match="hours fell"):
        log(
            (date(2024, 1, 1), 12000, 21000),
            (date(2024, 7, 1), 12900, 20000),
        )


def test_a_reading_is_taken_at_the_last_posting_on_or_before_the_day():
    assert log().at(date(2024, 7, 1)) == usage(12900, 22600)
    assert log().at(date(2024, 8, 15)) == usage(12900, 22600)
    assert log().at(date(2025, 6, 1)) == usage(13800, 24200)


def test_a_day_before_the_log_opens_has_nothing_posted():
    with pytest.raises(NotOnFile, match="nothing posted on or before"):
        log().at(date(2023, 12, 31))


def test_what_was_flown_across_a_window_is_the_difference_of_two_postings():
    flown = log().flown_between(date(2024, 1, 1), date(2025, 1, 1))
    assert flown.cycles == Cycles(1800)
    assert flown.hours == Hours.of_hours(3200)


def test_a_window_that_runs_backwards_is_refused():
    with pytest.raises(BadReading, match="is before"):
        log().flown_between(date(2025, 1, 1), date(2024, 1, 1))


def test_a_window_inside_one_posting_flew_nothing():
    assert log().flown_between(date(2024, 7, 2), date(2024, 8, 15)) == Usage.zero()


def test_posting_a_reading_leaves_the_old_log_alone():
    before = log()
    after = before.posted(Snapshot(date(2025, 7, 1), usage(14700, 25800)))
    assert after.latest.usage == usage(14700, 25800)
    assert before.latest.usage == usage(13800, 24200)


def test_posting_a_reading_that_falls_is_refused_at_the_post():
    with pytest.raises(BadReading, match="cycles fell"):
        log().posted(Snapshot(date(2025, 7, 1), usage(13000, 25800)))


def test_the_daily_rate_comes_off_the_recent_stretch_of_the_log():
    cycles_a_day, hours_a_day = log().daily_rate()
    assert cycles_a_day == Decimal("4.891")
    assert hours_a_day == Decimal("8.696")


def test_a_window_with_no_reading_inside_it_falls_back_to_the_whole_log():
    cycles_a_day, _ = log().daily_rate(over=100)
    assert cycles_a_day == Decimal("4.918")


def test_one_reading_is_not_a_rate():
    fresh = UtilisationLog.opened(MSN, date(2025, 6, 1))
    with pytest.raises(NotOnFile, match="one reading is not a rate"):
        fresh.daily_rate()


def test_a_window_that_catches_only_the_last_reading_falls_back_to_the_whole_log():
    cycles_a_day, _ = log().daily_rate(over=0)
    assert cycles_a_day == Decimal("4.918")
