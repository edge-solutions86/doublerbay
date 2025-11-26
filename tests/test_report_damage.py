from dataclasses import replace
from datetime import date

import pytest

import sample
from doublerbay.damage.corrosion import Corrosion
from doublerbay.damage.crack import Crack
from doublerbay.damage.dent import Dent
from doublerbay.damage.kind import DamageKind, DamageOrigin
from doublerbay.errors import BadReading
from doublerbay.ids import FindingId, Serial
from doublerbay.measure.angle import Angle
from doublerbay.report.damage import (
    FUSELAGE_CHAPTER,
    WING_CHAPTER,
    chapter_of,
    count_fleet,
    count_open,
)

DAY = date(2025, 3, 14)
MSN = Serial("5127")
OTHER = Serial("5240")


GEOMETRY = {
    DamageKind.DENT: lambda: Dent(sample.inch("0.020"), sample.inch("3.000"), sample.inch("2.000")),
    DamageKind.CRACK: lambda: Crack(sample.inch("0.400"), Angle(0)),
    DamageKind.CORROSION: lambda: Corrosion(
        sample.inch("2.000"), sample.inch("1.000"), sample.inch("0.004")
    ),
}
ORIGIN = {
    DamageKind.DENT: DamageOrigin.IMPACT,
    DamageKind.CRACK: DamageOrigin.FATIGUE,
    DamageKind.CORROSION: DamageOrigin.ENVIRONMENT,
}


def card(sequence=1, kind=DamageKind.DENT, found=DAY, origin=None, at=None):
    return sample.card(
        sequence,
        kind,
        GEOMETRY[kind](),
        at=at,
        found=found,
        origin=origin or ORIGIN[kind],
    )


def wing_card(sequence=9):
    return card(sequence, DamageKind.CORROSION, at=sample.lower_wing_point())


def test_a_fuselage_card_files_under_fifty_three():
    assert chapter_of(card()) == FUSELAGE_CHAPTER


def test_a_wing_card_files_under_fifty_seven():
    assert chapter_of(wing_card()) == WING_CHAPTER


def test_a_tail_with_nothing_open_is_clean():
    counted = count_open(MSN, (), DAY)
    assert counted.is_clean
    assert counted.worst_kind is None
    assert str(counted) == "MSN 5127: nothing open"


def test_open_cards_are_counted():
    counted = count_open(MSN, (card(1), card(2)), DAY)
    assert counted.total == 2
    assert not counted.is_clean


def test_a_superseded_card_is_not_counted():
    carried = replace(card(1), superseded_by=FindingId(2025, 2))
    assert count_open(MSN, (carried, card(2)), DAY).total == 1


def test_a_card_found_after_the_day_asked_for_is_not_counted():
    later = card(2, found=date(2025, 6, 1))
    assert count_open(MSN, (card(1), later), DAY).total == 1


def test_the_kinds_are_counted_most_first():
    found = (card(1), card(2), card(3, DamageKind.CRACK))
    counted = count_open(MSN, found, DAY)
    assert counted.by_kind[0] == (DamageKind.DENT, 2)
    assert counted.worst_kind is DamageKind.DENT


def test_a_tie_between_kinds_breaks_by_name():
    found = (card(1, DamageKind.DENT), card(2, DamageKind.CRACK))
    counted = count_open(MSN, found, DAY)
    assert [kind for kind, _ in counted.by_kind] == [DamageKind.CRACK, DamageKind.DENT]


def test_a_kind_that_is_not_there_counts_zero():
    assert count_open(MSN, (card(1),), DAY).of_kind(DamageKind.CRACK) == 0


def test_the_chapters_are_counted_apart():
    counted = count_open(MSN, (card(1), wing_card(2)), DAY)
    assert counted.in_chapter(FUSELAGE_CHAPTER) == 1
    assert counted.in_chapter(WING_CHAPTER) == 1


def test_a_chapter_with_nothing_in_it_counts_zero():
    assert count_open(MSN, (card(1),), DAY).in_chapter(WING_CHAPTER) == 0


def test_the_origins_are_counted():
    found = (card(1), card(2, origin=DamageOrigin.HANDLING))
    assert dict(count_open(MSN, found, DAY).by_origin)[DamageOrigin.HANDLING] == 1


def test_the_recent_window_counts_only_what_landed_inside_it():
    old = card(1, found=date(2024, 12, 1))
    counted = count_open(MSN, (old, card(2)), DAY)
    assert counted.total == 2
    assert counted.raised_recently == 1


def test_the_recent_window_can_be_widened():
    old = card(1, found=date(2024, 12, 1))
    assert count_open(MSN, (old, card(2)), DAY, recent_days=365).raised_recently == 2


def test_a_card_landing_exactly_on_the_window_edge_is_inside_it():
    edge = card(1, found=date(2025, 2, 12))
    assert count_open(MSN, (edge,), DAY, recent_days=30).raised_recently == 1


def test_a_window_of_no_days_is_refused():
    with pytest.raises(BadReading, match="at least a day"):
        count_open(MSN, (), DAY, recent_days=0)


def test_a_tail_reads_back_with_its_worst_kind():
    counted = count_open(MSN, (card(1), card(2)), DAY)
    assert str(counted) == "MSN 5127: 2 open, worst dent"


def test_the_fleet_report_puts_the_worst_tail_first():
    report = count_fleet({MSN: (card(1),), OTHER: (card(2), card(3))}, DAY)
    assert report.tails[0].serial == OTHER
    assert report.total == 3


def test_two_tails_with_the_same_count_sort_by_serial():
    report = count_fleet({OTHER: (card(1),), MSN: (card(2),)}, DAY)
    assert [one.serial for one in report.tails] == [MSN, OTHER]


def test_a_clean_tail_is_named_as_clean():
    report = count_fleet({MSN: (card(1),), OTHER: ()}, DAY)
    assert report.clean_tails == (OTHER,)


def test_a_fleet_with_nothing_open_has_no_worst_tail():
    report = count_fleet({MSN: (), OTHER: ()}, DAY)
    assert report.worst_tail is None
    assert report.total == 0


def test_a_tail_can_be_picked_out_of_the_report():
    report = count_fleet({MSN: (card(1),), OTHER: ()}, DAY)
    assert report.for_tail(OTHER).is_clean


def test_a_tail_that_is_not_in_the_report_is_refused():
    report = count_fleet({MSN: (card(1),)}, DAY)
    with pytest.raises(BadReading, match="not in this report"):
        report.for_tail(OTHER)


def test_the_fleet_report_reads_back_as_one_line():
    report = count_fleet({MSN: (card(1),), OTHER: ()}, DAY)
    assert str(report) == "2 tails, 1 open on 2025-03-14"


def test_an_empty_register_reports_nothing():
    report = count_fleet({}, DAY)
    assert report.tails == ()
    assert report.total == 0
