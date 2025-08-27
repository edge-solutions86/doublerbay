from datetime import date

import pytest

from doublerbay.damage.corrosion import Corrosion
from doublerbay.errors import BadReading
from doublerbay.ids import PanelId
from doublerbay.measure.length import Length
from doublerbay.tolerance.corrosion import (
    CorrosionContext,
    CorrosionEntry,
    CorrosionLevel,
    grade,
    level_for,
)

PART = PanelId(53, 10, 143, "L")
FOUND = date(2025, 5, 6)


def inch(text):
    return Length.of_inches(text)


def rot(depth="0.004", exfoliating=False):
    return Corrosion(inch("2.000"), inch("1.000"), inch(depth), False, exfoliating)


def context(within_limits=True, before=False, urgent=False):
    return CorrosionContext(PART, within_limits, before, urgent)


def test_the_levels_order_and_read_back():
    assert CorrosionLevel.THREE.worse_than(CorrosionLevel.TWO)
    assert CorrosionLevel.TWO.worse_than(CorrosionLevel.ONE)
    assert str(CorrosionLevel.TWO) == "level 2"


def test_only_level_three_goes_to_the_authority():
    assert CorrosionLevel.THREE.is_reportable
    assert not CorrosionLevel.TWO.is_reportable
    assert not CorrosionLevel.ONE.is_reportable


def test_anything_above_level_one_says_the_programme_is_not_coping():
    assert not CorrosionLevel.ONE.needs_programme_review
    assert CorrosionLevel.TWO.needs_programme_review
    assert CorrosionLevel.THREE.is_worse_than_expected


def test_corrosion_inside_the_limits_is_level_one():
    assert level_for(rot(), context(within_limits=True)) is CorrosionLevel.ONE


def test_the_same_finding_is_level_one_the_first_time_and_two_the_second():
    outside = context(within_limits=False, before=False)
    again = context(within_limits=False, before=True)
    assert level_for(rot(), outside) is CorrosionLevel.ONE
    assert level_for(rot(), again) is CorrosionLevel.TWO


def test_corrosion_inside_the_limits_stays_level_one_however_often_it_comes_back():
    assert level_for(rot(), context(within_limits=True, before=True)) is CorrosionLevel.ONE


def test_exfoliation_is_never_inside_the_limits_whatever_the_context_says():
    lifted = rot(depth="0.001", exfoliating=True)
    assert level_for(lifted, context(within_limits=True)) is CorrosionLevel.ONE
    assert level_for(lifted, context(within_limits=True, before=True)) is CorrosionLevel.TWO


def test_an_urgent_airworthiness_concern_is_level_three_whatever_else_is_true():
    assert level_for(rot(), context(urgent=True)) is CorrosionLevel.THREE
    assert level_for(rot(), context(within_limits=True, urgent=True)) is CorrosionLevel.THREE


def test_the_context_reads_back_what_the_programme_knows():
    assert str(context()) == "5310-143L, first time here"
    assert str(context(before=True)).endswith("seen here before")


def test_grading_produces_an_entry_with_the_level_on_it():
    entry = grade(rot(), context(), FOUND)
    assert entry.level is CorrosionLevel.ONE
    assert entry.part == PART
    assert str(entry) == "5310-143L level 1 on 2025-05-06"


def test_a_level_three_entry_carries_a_clock():
    entry = grade(rot(), context(urgent=True), FOUND)
    assert entry.must_be_reported
    assert entry.report_by == date(2025, 5, 9)


def test_a_lower_level_entry_carries_no_clock():
    entry = grade(rot(), context(), FOUND)
    assert not entry.must_be_reported
    assert entry.report_by is None
    assert not entry.is_overdue(date(2099, 1, 1))


def test_a_level_three_report_goes_overdue_after_its_clock_runs_out():
    entry = grade(rot(), context(urgent=True), FOUND)
    assert not entry.is_overdue(date(2025, 5, 9))
    assert entry.is_overdue(date(2025, 5, 10))


def test_a_surface_stain_cannot_be_outside_the_allowable_limits():
    stain = Corrosion(inch("2.000"), inch("1.000"), inch("0"))
    with pytest.raises(BadReading, match="cannot be outside the allowable limits"):
        grade(stain, context(within_limits=False), FOUND)


def test_a_surface_stain_inside_the_limits_grades_normally():
    stain = Corrosion(inch("2.000"), inch("1.000"), inch("0"))
    assert grade(stain, context(), FOUND).level is CorrosionLevel.ONE


def test_a_surface_stain_can_still_be_an_urgent_concern():
    stain = Corrosion(inch("2.000"), inch("1.000"), inch("0"))
    entry = grade(stain, context(within_limits=False, urgent=True), FOUND)
    assert entry.level is CorrosionLevel.THREE


def test_an_entry_is_frozen_and_hashes_by_value():
    assert (
        len(
            {CorrosionEntry(PART, CorrosionLevel.ONE, FOUND)}
            | {CorrosionEntry(PART, CorrosionLevel.ONE, FOUND)}
        )
        == 1
    )
