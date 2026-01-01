from datetime import date
from decimal import Decimal

import pytest

from doublerbay.errors import BadReading
from doublerbay.ids import PanelId, Serial
from doublerbay.report.corrosion import (
    REPEAT_THRESHOLD,
    RepeatLocation,
    programme_report,
    repeats_in,
)
from doublerbay.tolerance.corrosion import CorrosionEntry, CorrosionLevel

MSN = Serial("5127")
PART = PanelId(53, 10, 143, "L")
ELSEWHERE = PanelId(53, 10, 210, "R")
SINCE = date(2020, 1, 1)
UNTIL = date(2025, 12, 31)


def entry(part=PART, level=CorrosionLevel.ONE, found=date(2023, 6, 1)):
    return CorrosionEntry(part=part, level=level, found_on=found)


def report(*entries, since=SINCE, until=UNTIL):
    return programme_report(MSN, tuple(entries), since, until)


def test_a_tail_with_nothing_graded_is_coping():
    built = report()
    assert built.total == 0
    assert built.is_coping
    assert built.escalation_rate == Decimal("0.000")


def test_level_ones_alone_mean_the_programme_is_coping():
    built = report(entry(), entry(part=ELSEWHERE))
    assert built.total == 2
    assert built.is_coping


def test_one_level_two_means_it_is_not_coping():
    built = report(entry(), entry(level=CorrosionLevel.TWO, part=ELSEWHERE))
    assert not built.is_coping
    assert built.above_level_one == 1


def test_a_level_three_counts_as_above_level_one():
    built = report(entry(level=CorrosionLevel.THREE))
    assert built.above_level_one == 1


def test_the_levels_come_back_in_order():
    built = report(
        entry(level=CorrosionLevel.THREE),
        entry(level=CorrosionLevel.ONE, part=ELSEWHERE),
    )
    assert [level.value for level, _ in built.by_level] == [1, 3]


def test_a_level_can_be_counted_on_its_own():
    built = report(entry(), entry(part=ELSEWHERE))
    assert built.count_at(CorrosionLevel.ONE) == 2
    assert built.count_at(CorrosionLevel.TWO) == 0


def test_the_escalation_rate_is_the_share_above_level_one():
    built = report(
        entry(),
        entry(part=ELSEWHERE),
        entry(level=CorrosionLevel.TWO, part=PanelId(53, 10, 300, "L")),
    )
    assert built.escalation_rate == Decimal("0.333")


def test_a_fleet_with_everything_escalated_reads_as_one():
    assert report(entry(level=CorrosionLevel.TWO)).escalation_rate == Decimal("1.000")


def test_the_denominator_is_said_out_loud():
    assert "not panels inspected" in report().denominator


def test_a_finding_before_the_window_is_left_out():
    built = report(entry(found=date(2019, 1, 1)), entry(part=ELSEWHERE))
    assert built.total == 1


def test_a_finding_after_the_window_is_left_out():
    built = report(entry(found=date(2026, 6, 1)), entry(part=ELSEWHERE))
    assert built.total == 1


def test_a_finding_on_the_window_edge_is_inside_it():
    assert report(entry(found=SINCE)).total == 1
    assert report(entry(found=UNTIL)).total == 1


def test_a_window_that_ends_before_it_starts_is_refused():
    with pytest.raises(BadReading, match="ends after it starts"):
        report(since=UNTIL, until=SINCE)


def test_a_panel_graded_once_is_not_a_repeat():
    assert repeats_in((entry(),)) == ()


def test_a_panel_graded_twice_is_a_repeat():
    found = repeats_in((entry(found=date(2021, 1, 1)), entry(found=date(2023, 1, 1))))
    assert len(found) == 1
    assert found[0].times == 2


def test_a_repeat_carries_the_first_and_latest_grading():
    found = repeats_in((entry(found=date(2023, 1, 1)), entry(found=date(2021, 1, 1))))[0]
    assert found.first == date(2021, 1, 1)
    assert found.latest == date(2023, 1, 1)


def test_two_repeats_sort_by_how_often_first():
    entries = (
        entry(found=date(2021, 1, 1)),
        entry(found=date(2022, 1, 1)),
        entry(found=date(2023, 1, 1)),
        entry(part=ELSEWHERE, found=date(2021, 1, 1)),
        entry(part=ELSEWHERE, found=date(2022, 1, 1)),
    )
    assert [one.part for one in repeats_in(entries)] == [PART, ELSEWHERE]


def test_a_repeat_below_the_threshold_is_not_persistent():
    found = repeats_in((entry(found=date(2021, 1, 1)), entry(found=date(2023, 1, 1))))[0]
    assert not found.is_persistent


def test_a_repeat_at_the_threshold_is_persistent():
    entries = tuple(entry(found=date(2020 + step, 1, 1)) for step in range(REPEAT_THRESHOLD))
    assert repeats_in(entries)[0].is_persistent


def test_the_persistent_ones_are_picked_out_of_the_report():
    entries = (
        entry(found=date(2021, 1, 1)),
        entry(found=date(2022, 1, 1)),
        entry(found=date(2023, 1, 1)),
        entry(part=ELSEWHERE, found=date(2021, 1, 1)),
        entry(part=ELSEWHERE, found=date(2022, 1, 1)),
    )
    built = report(*entries)
    assert len(built.repeats) == 2
    assert [one.part for one in built.persistent] == [PART]


def test_a_repeat_reads_back_with_the_span_in_years():
    found = RepeatLocation(PART, 3, date(2021, 1, 1), date(2023, 1, 1))
    assert found.years_between == Decimal("2.000")
    assert str(found) == "5310-143L graded 3 times over 2.000 years"


def test_a_repeat_inside_one_year_reads_as_part_of_a_year():
    found = RepeatLocation(PART, 2, date(2023, 1, 1), date(2023, 7, 1))
    assert found.years_between < 1


def test_the_report_reads_back_as_one_line():
    assert str(report(entry())) == "MSN 5127: 1 graded, coping"
    assert "1 above level one" in str(report(entry(level=CorrosionLevel.TWO)))
