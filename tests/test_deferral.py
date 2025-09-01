from datetime import date

import pytest

from doublerbay.disposition.deferral import (
    STANDARD_DEFERRAL,
    Deferral,
    expired,
    grant,
    limit_for,
)
from doublerbay.errors import BadReading
from doublerbay.fleet.utilisation import usage
from doublerbay.ids import FindingId
from doublerbay.measure.interval import Interval
from doublerbay.tolerance.method import InspectionMethod

DR = FindingId(2025, 4182)
GRANTED = date(2025, 3, 14)
AT = usage(13800, 24200)


def deferral(limit=None, method=InspectionMethod.DVI, by="structures"):
    return Deferral(DR, GRANTED, AT, limit or STANDARD_DEFERRAL, method, by)


def test_a_deferral_reads_back_its_limit_and_what_happens_at_the_end():
    printed = str(deferral())
    assert printed.startswith("DR-2025-004182 deferred to")
    assert "detailed visual" in printed


def test_a_deferral_is_granted_by_somebody():
    with pytest.raises(BadReading, match="granted by somebody"):
        deferral(by="  ")


def test_the_standard_limit_runs_on_all_three_clocks():
    due = deferral().due
    assert due.cycles.count == 13800 + 500
    assert due.on == date(2025, 9, 10)


def test_a_deferral_expires_on_whichever_clock_gets_there_first():
    held = deferral()
    assert not held.is_expired(usage(14000, 24500), date(2025, 4, 1))
    assert held.is_expired(usage(14400, 24500), date(2025, 4, 1))
    assert held.is_expired(usage(14000, 24500), date(2025, 10, 1))


def test_the_days_left_run_down_and_stop_at_nothing():
    held = deferral()
    assert held.days_left(date(2025, 3, 14)) == 180
    assert held.days_left(date(2025, 9, 10)) == 0
    assert held.days_left(date(2026, 1, 1)) == 0


def test_an_engineer_can_set_a_tighter_limit_than_the_book():
    tight = limit_for(engineer_set=Interval.parse("50 FC / 30 DY"))
    assert tight.cycles.count == 50
    assert tight.days == 30


def test_with_no_engineer_limit_the_book_governs():
    assert limit_for() == STANDARD_DEFERRAL


def test_a_check_before_the_limit_pulls_the_deferral_in():
    limited = limit_for(next_check=date(2025, 5, 1), granted=GRANTED)
    assert limited.days == 48
    assert limited.cycles == STANDARD_DEFERRAL.cycles


def test_a_check_after_the_limit_leaves_it_alone():
    limited = limit_for(next_check=date(2026, 5, 1), granted=GRANTED)
    assert limited.days == STANDARD_DEFERRAL.days


def test_a_check_that_has_already_started_is_not_something_to_defer_to():
    with pytest.raises(BadReading, match="already started is not a deferral"):
        limit_for(next_check=GRANTED, granted=GRANTED)


def test_the_engineer_limit_and_the_check_both_bite_together():
    limited = limit_for(
        engineer_set=Interval.parse("50 FC / 90 DY"),
        next_check=date(2025, 4, 1),
        granted=GRANTED,
    )
    assert limited.cycles.count == 50
    assert limited.days == 18


def test_granting_produces_a_deferral_with_the_tightest_limit_on_it():
    held = grant(DR, GRANTED, AT, "structures", next_check=date(2025, 5, 1))
    assert held.limit.days == 48
    assert held.by == "structures"


def test_a_granted_deferral_carries_the_method_it_will_be_re_inspected_with():
    held = grant(DR, GRANTED, AT, "structures", method=InspectionMethod.HFEC)
    assert held.re_inspect_with is InspectionMethod.HFEC


def test_a_deferral_can_be_tightened_afterwards_and_the_first_one_stands():
    before = deferral()
    after = before.tightened_to(Interval.parse("30 DY"), "chief engineer", "fleet campaign")
    assert after.limit.days == 30
    assert after.reason == "fleet campaign"
    assert before.limit.days == 180


def test_tightening_keeps_the_date_and_the_reading_it_was_granted_at():
    after = deferral().tightened_to(Interval.parse("30 DY"), "chief engineer")
    assert after.granted == GRANTED
    assert after.at_usage == AT


def test_the_expiry_sweep_finds_the_ones_the_aeroplane_has_flown_past():
    fresh = deferral()
    stale = grant(FindingId(2025, 4183), date(2024, 1, 1), usage(12000, 21000), "structures")
    found = expired((fresh, stale), usage(14000, 24500), date(2025, 4, 1))
    assert [one.against.sequence for one in found] == [4183]


def test_the_expiry_sweep_over_nothing_finds_nothing():
    assert expired((), usage(99000, 99000), date(2099, 1, 1)) == ()


def test_a_reason_rides_along_with_the_deferral():
    held = grant(DR, GRANTED, AT, "structures", reason="doubler on order")
    assert held.reason == "doubler on order"
