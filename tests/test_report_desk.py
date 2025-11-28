from datetime import date

import pytest

import sample
from doublerbay.disposition.deferral import grant
from doublerbay.fleet.utilisation import Snapshot, UtilisationLog
from doublerbay.ids import FindingId, Serial
from doublerbay.measure.interval import Interval
from doublerbay.report.desk import (
    PLANNED_CYCLES_A_DAY,
    due_across_fleet,
    due_on_tail,
    grounded,
    open_across_fleet,
    open_on_tail,
    rate_for,
)
from doublerbay.store.base import build_engine, build_schema
from doublerbay.store.unit import UnitOfWork

DAY = date(2025, 3, 14)
MSN = Serial("5127")
OTHER = Serial("5240")


def log_with_two_readings(serial="5127"):
    opened = UtilisationLog.opened(Serial(serial), date(2016, 4, 12))
    opened = opened.posted(Snapshot(date(2024, 3, 14), sample.flown(12000, 21000)))
    return opened.posted(Snapshot(date(2025, 3, 1), sample.flown(13800, 24200)))


@pytest.fixture
def engine():
    made = build_engine()
    build_schema(made)
    with UnitOfWork(made) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(log_with_two_readings())
    return made


def enrol_other(engine, readings=True):
    with UnitOfWork(engine) as unit:
        unit.aircraft.add(sample.aircraft("5240"))
        unit.aircraft.open_log(
            log_with_two_readings("5240") if readings else UtilisationLog.opened(OTHER, DAY)
        )


def raise_card(engine, sequence=1, serial="5127", found=DAY):
    with UnitOfWork(engine) as unit:
        unit.findings.raise_finding(sample.card(sequence, serial=serial, found=found))
    return FindingId(2025, sequence)


def defer(engine, reference, limit="500 FC", on=DAY, at=(13800, 24200)):
    with UnitOfWork(engine) as unit:
        unit.deferrals.grant(
            grant(
                reference,
                on,
                sample.flown(*at),
                "j.mercer",
                engineer_set=Interval.parse(limit),
            )
        )


def test_a_tail_with_two_readings_gets_its_own_rate(engine):
    with UnitOfWork(engine) as unit:
        forecast, measured = rate_for(unit, MSN)
    assert measured
    assert forecast.cycles_a_day > 0


def test_a_tail_with_one_reading_falls_back_to_the_planning_rate(engine):
    enrol_other(engine, readings=False)
    with UnitOfWork(engine) as unit:
        forecast, measured = rate_for(unit, OTHER)
    assert not measured
    assert forecast.cycles_a_day == PLANNED_CYCLES_A_DAY


def test_a_tail_with_nothing_deferred_has_nothing_due(engine):
    with UnitOfWork(engine) as unit:
        report = due_on_tail(unit, MSN, DAY)
    assert report.items == ()
    assert not report.is_grounded


def test_a_deferral_shows_up_on_the_due_list(engine):
    defer(engine, raise_card(engine))
    with UnitOfWork(engine) as unit:
        report = due_on_tail(unit, MSN, DAY)
    assert len(report.items) == 1
    assert report.items[0].against == FindingId(2025, 1)


def test_the_report_says_whether_the_rate_behind_it_is_real(engine):
    defer(engine, raise_card(engine))
    with UnitOfWork(engine) as unit:
        assert due_on_tail(unit, MSN, DAY).measured_rate


def test_a_planning_rate_is_named_in_the_line(engine):
    enrol_other(engine, readings=False)
    with UnitOfWork(engine) as unit:
        assert "planning rate" in str(due_on_tail(unit, OTHER, DAY))


def test_another_tail_s_deferral_is_not_on_this_tail_s_list(engine):
    enrol_other(engine)
    defer(engine, raise_card(engine, 2, serial="5240"))
    with UnitOfWork(engine) as unit:
        assert due_on_tail(unit, MSN, DAY).items == ()
        assert len(due_on_tail(unit, OTHER, DAY).items) == 1


def test_a_deferral_against_a_card_that_is_not_on_file_is_left_out(engine):
    defer(engine, FindingId(2025, 99))
    with UnitOfWork(engine) as unit:
        assert due_on_tail(unit, MSN, DAY).items == ()


def test_a_lifted_deferral_drops_off_the_due_list(engine):
    reference = raise_card(engine)
    defer(engine, reference)
    with UnitOfWork(engine) as unit:
        unit.deferrals.lift(reference, DAY)
    with UnitOfWork(engine) as unit:
        assert due_on_tail(unit, MSN, DAY).items == ()


def test_a_horizon_cuts_the_tail_report_down(engine):
    defer(engine, raise_card(engine, 1), "500 FC")
    defer(engine, raise_card(engine, 2), "20 FC")
    with UnitOfWork(engine) as unit:
        assert len(due_on_tail(unit, MSN, DAY, within_days=30).items) == 1


def test_a_tail_past_a_limit_is_grounded(engine):
    defer(engine, raise_card(engine), "1 FC", on=date(2024, 1, 1), at=(100, 200))
    with UnitOfWork(engine) as unit:
        report = due_on_tail(unit, MSN, DAY)
    assert report.is_grounded
    assert len(report.overrun) == 1


def test_the_fleet_report_covers_every_tail(engine):
    enrol_other(engine)
    with UnitOfWork(engine) as unit:
        assert len(due_across_fleet(unit, DAY)) == 2


def test_the_fleet_report_puts_the_grounded_tail_first(engine):
    enrol_other(engine)
    defer(engine, raise_card(engine, 2, serial="5240"), "1 FC", on=date(2024, 1, 1), at=(100, 200))
    with UnitOfWork(engine) as unit:
        assert due_across_fleet(unit, DAY)[0].serial == OTHER


def test_two_tails_with_nothing_due_sort_by_serial(engine):
    enrol_other(engine)
    with UnitOfWork(engine) as unit:
        assert [one.serial for one in due_across_fleet(unit, DAY)] == [MSN, OTHER]


def test_the_grounding_list_is_only_the_tails_that_are_over(engine):
    enrol_other(engine)
    defer(engine, raise_card(engine, 2, serial="5240"), "1 FC", on=date(2024, 1, 1), at=(100, 200))
    defer(engine, raise_card(engine, 1), "500 FC")
    with UnitOfWork(engine) as unit:
        assert [one.serial for one in grounded(unit, DAY)] == [OTHER]


def test_a_fleet_with_nothing_over_grounds_nothing(engine):
    defer(engine, raise_card(engine), "500 FC")
    with UnitOfWork(engine) as unit:
        assert grounded(unit, DAY) == ()


def test_the_open_count_on_one_tail_comes_off_the_cards(engine):
    raise_card(engine, 1)
    raise_card(engine, 2)
    with UnitOfWork(engine) as unit:
        assert open_on_tail(unit, MSN, DAY).total == 2


def test_a_tail_with_no_cards_counts_clean(engine):
    with UnitOfWork(engine) as unit:
        assert open_on_tail(unit, MSN, DAY).is_clean


def test_the_recent_window_reaches_the_tail_count(engine):
    raise_card(engine, 1, found=date(2024, 12, 1))
    with UnitOfWork(engine) as unit:
        assert open_on_tail(unit, MSN, DAY).raised_recently == 0
        assert open_on_tail(unit, MSN, DAY, recent_days=365).raised_recently == 1


def test_the_fleet_open_count_covers_the_register(engine):
    enrol_other(engine)
    raise_card(engine, 1)
    raise_card(engine, 2, serial="5240")
    with UnitOfWork(engine) as unit:
        report = open_across_fleet(unit, DAY)
    assert report.total == 2
    assert len(report.tails) == 2


def test_a_tail_not_yet_delivered_is_left_out_of_the_fleet_count(engine):
    with UnitOfWork(engine) as unit:
        report = open_across_fleet(unit, date(2000, 1, 1))
    assert report.tails == ()
