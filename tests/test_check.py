from datetime import date

import pytest

from doublerbay.errors import BadReading
from doublerbay.fleet.check import CheckEvent, CheckKind, CheckProgramme, DiscoverySource
from doublerbay.fleet.utilisation import usage
from doublerbay.ids import Serial
from doublerbay.measure.interval import Interval

MSN = Serial("5127")


def programme(extra=None):
    intervals = {
        CheckKind.LINE: Interval.parse("750 FH / 60 DY"),
        CheckKind.HANGAR: Interval.parse("6000 FH / 24 MO"),
        CheckKind.HEAVY: Interval.parse("25000 FH / 72 MO"),
    }
    if extra:
        intervals.update(extra)
    return CheckProgramme(intervals)


def event(kind=CheckKind.HANGAR, started=date(2025, 1, 6), finished=date(2025, 1, 20)):
    return CheckEvent(MSN, kind, started, finished, usage(13800, 24200))


def test_the_checks_open_the_aeroplane_up_by_degrees():
    assert CheckKind.HEAVY.opens_more_than(CheckKind.HANGAR)
    assert CheckKind.HANGAR.opens_more_than(CheckKind.LINE)
    assert CheckKind.LINE.opens_more_than(CheckKind.WALKAROUND)
    assert not CheckKind.WALKAROUND.opens_more_than(CheckKind.TRANSIT)


def test_only_the_opened_up_checks_would_find_fine_damage():
    assert CheckKind.HANGAR.can_find_fine_damage
    assert CheckKind.NDT_SWEEP.can_find_fine_damage
    assert not CheckKind.LINE.can_find_fine_damage
    assert not CheckKind.WALKAROUND.can_find_fine_damage


def test_everything_but_the_walkaround_is_scheduled():
    assert CheckKind.LINE.is_scheduled
    assert not CheckKind.WALKAROUND.is_scheduled
    assert str(CheckKind.HANGAR) == "hangar check"


def test_the_sources_split_into_people_and_instruments():
    assert DiscoverySource.CREW_REPORT.is_reported_by_a_person
    assert DiscoverySource.WALKAROUND.is_reported_by_a_person
    assert not DiscoverySource.NDT.is_reported_by_a_person
    assert not DiscoverySource.SCHEDULED_CHECK.is_reported_by_a_person


def test_some_sources_come_with_something_that_happened():
    assert DiscoverySource.LIGHTNING_REPORT.carries_an_event
    assert DiscoverySource.GROUND_HANDLING.carries_an_event
    assert not DiscoverySource.SCHEDULED_CHECK.carries_an_event
    assert str(DiscoverySource.NDT) == "non destructive test"


def test_a_check_reads_back_the_tail_kind_and_day():
    assert str(event()) == "MSN 5127 hangar check 2025-01-06"


def test_a_check_counts_its_own_days_inclusively():
    assert event().days == 15
    assert event(started=date(2025, 1, 6), finished=date(2025, 1, 6)).days == 1


def test_a_check_that_finishes_before_it_starts_is_refused():
    with pytest.raises(BadReading, match="does not finish before it starts"):
        event(started=date(2025, 1, 20), finished=date(2025, 1, 6))


def test_a_day_inside_the_check_is_covered_by_it():
    assert event().covers(date(2025, 1, 6))
    assert event().covers(date(2025, 1, 14))
    assert event().covers(date(2025, 1, 20))
    assert not event().covers(date(2025, 1, 21))


def test_a_programme_has_to_set_the_three_scheduled_checks():
    with pytest.raises(BadReading, match="sets an interval for the heavy check"):
        CheckProgramme(
            {
                CheckKind.LINE: Interval.parse("750 FH"),
                CheckKind.HANGAR: Interval.parse("6000 FH"),
            }
        )


def test_an_interval_comes_back_for_a_kind_that_is_on_the_programme():
    assert programme().interval_for(CheckKind.HANGAR).months == 24
    with pytest.raises(BadReading, match="has no walkaround on it"):
        programme().interval_for(CheckKind.WALKAROUND)


def test_the_next_check_of_a_kind_runs_from_the_end_of_the_last_one():
    due = programme().next_after(event())
    assert due.hours.hours == 24200 + 6000
    assert due.on == date(2027, 1, 20)


def test_a_line_check_comes_round_far_sooner_than_a_hangar_check():
    line = programme().next_after(event(kind=CheckKind.LINE))
    hangar = programme().next_after(event(kind=CheckKind.HANGAR))
    assert line.on < hangar.on


def test_the_deepest_check_already_due_is_the_one_a_finding_rides_on():
    due = programme().next_after(event())
    assert programme().deepest_by(due, usage(13800, 24200), date(2025, 1, 20)) is None
    landed = programme().deepest_by(due, usage(13800, 31000), date(2025, 1, 20))
    assert landed is CheckKind.HEAVY


def test_a_programme_reads_back_shallowest_first():
    printed = str(programme())
    assert printed.index("line check") < printed.index("hangar check")
    assert printed.index("hangar check") < printed.index("heavy check")


def test_an_extra_kind_on_the_programme_is_carried():
    with_sweep = programme({CheckKind.NDT_SWEEP: Interval.parse("48 MO")})
    assert with_sweep.interval_for(CheckKind.NDT_SWEEP).months == 48
