from datetime import date

import pytest

import sample
from doublerbay.errors import BadReading, NotOnFile
from doublerbay.ids import FindingId, PanelId, Serial
from doublerbay.store.base import build_engine, build_schema
from doublerbay.store.decisions import row_to_corrosion, seen_here_before
from doublerbay.store.unit import UnitOfWork
from doublerbay.store.work import CorrosionRow
from doublerbay.tolerance.corrosion import CorrosionEntry, CorrosionLevel

DAY = date(2025, 3, 14)
EARLIER = date(2023, 6, 1)
MSN = Serial("5127")
OTHER = Serial("5240")
PART = PanelId(53, 10, 143, "L")
ELSEWHERE = PanelId(53, 10, 210, "R")


@pytest.fixture
def engine():
    made = build_engine()
    build_schema(made)
    with UnitOfWork(made) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(sample.flying_log())
        unit.aircraft.add(sample.aircraft("5240"))
        unit.aircraft.open_log(sample.flying_log("5240"))
    return made


def entry(part=PART, level=CorrosionLevel.ONE, found=DAY):
    return CorrosionEntry(part=part, level=level, found_on=found)


def record(engine, one=None, serial=MSN, sequence=1, reported=None):
    with UnitOfWork(engine) as unit:
        unit.corrosion.record(one or entry(), serial, FindingId(2025, sequence), reported)


def test_a_graded_finding_goes_in_and_comes_back(engine):
    record(engine)
    with UnitOfWork(engine) as unit:
        held = unit.corrosion.on_tail(MSN)
    assert len(held) == 1
    assert held[0].part == PART
    assert held[0].level is CorrosionLevel.ONE


def test_the_level_survives_as_its_number(engine):
    record(engine, entry(level=CorrosionLevel.THREE))
    with UnitOfWork(engine) as unit:
        assert unit.corrosion.on_tail(MSN)[0].level is CorrosionLevel.THREE


def test_entries_come_back_oldest_first(engine):
    record(engine, entry(found=DAY), sequence=1)
    record(engine, entry(found=EARLIER), sequence=2)
    with UnitOfWork(engine) as unit:
        assert [one.found_on for one in unit.corrosion.on_tail(MSN)] == [EARLIER, DAY]


def test_another_tail_s_corrosion_is_not_on_this_one(engine):
    record(engine, serial=OTHER)
    with UnitOfWork(engine) as unit:
        assert unit.corrosion.on_tail(MSN) == ()
        assert len(unit.corrosion.on_tail(OTHER)) == 1


def test_one_panel_can_be_asked_for(engine):
    record(engine, entry(part=PART), sequence=1)
    record(engine, entry(part=ELSEWHERE), sequence=2)
    with UnitOfWork(engine) as unit:
        assert len(unit.corrosion.on_panel(MSN, PART)) == 1


def test_a_panel_with_nothing_on_it_comes_back_empty(engine):
    with UnitOfWork(engine) as unit:
        assert unit.corrosion.on_panel(MSN, PART) == ()


def test_the_fleet_can_be_asked_for_one_level(engine):
    record(engine, entry(level=CorrosionLevel.TWO), sequence=1)
    record(engine, entry(level=CorrosionLevel.ONE), sequence=2)
    record(engine, entry(level=CorrosionLevel.TWO), serial=OTHER, sequence=3)
    with UnitOfWork(engine) as unit:
        assert len(unit.corrosion.at_level(CorrosionLevel.TWO)) == 2


def test_a_level_nobody_has_reached_comes_back_empty(engine):
    record(engine)
    with UnitOfWork(engine) as unit:
        assert unit.corrosion.at_level(CorrosionLevel.THREE) == ()


def test_a_level_three_that_has_not_gone_out_is_listed(engine):
    record(engine, entry(level=CorrosionLevel.THREE))
    with UnitOfWork(engine) as unit:
        assert len(unit.corrosion.unreported()) == 1


def test_a_level_three_already_reported_drops_off_the_list(engine):
    record(engine, entry(level=CorrosionLevel.THREE), reported=date(2025, 3, 15))
    with UnitOfWork(engine) as unit:
        assert unit.corrosion.unreported() == ()


def test_a_level_one_is_never_on_the_unreported_list(engine):
    record(engine, entry(level=CorrosionLevel.ONE))
    with UnitOfWork(engine) as unit:
        assert unit.corrosion.unreported() == ()


def test_marking_one_reported_takes_it_off_the_list(engine):
    record(engine, entry(level=CorrosionLevel.THREE))
    with UnitOfWork(engine) as unit:
        unit.corrosion.mark_reported(FindingId(2025, 1), date(2025, 3, 15))
    with UnitOfWork(engine) as unit:
        assert unit.corrosion.unreported() == ()


def test_reporting_one_that_is_not_on_the_register_is_refused(engine):
    with UnitOfWork(engine) as unit, pytest.raises(NotOnFile, match="not on the corrosion"):
        unit.corrosion.mark_reported(FindingId(2025, 9), DAY)


def test_a_finding_cannot_be_reported_before_it_was_found(engine):
    record(engine, entry(level=CorrosionLevel.THREE))
    with UnitOfWork(engine) as unit, pytest.raises(BadReading, match="before it was found"):
        unit.corrosion.mark_reported(FindingId(2025, 1), date(2024, 1, 1))


def test_a_panel_with_no_history_has_no_form(engine):
    with UnitOfWork(engine) as unit:
        assert not unit.corrosion.has_form_here(MSN, PART, DAY)


def test_a_panel_graded_before_has_form(engine):
    record(engine, entry(found=EARLIER))
    with UnitOfWork(engine) as unit:
        assert unit.corrosion.has_form_here(MSN, PART, DAY)


def test_an_entry_raised_later_does_not_count_as_history(engine):
    record(engine, entry(found=date(2026, 1, 1)))
    with UnitOfWork(engine) as unit:
        assert not unit.corrosion.has_form_here(MSN, PART, DAY)


def test_an_entry_on_the_same_day_does_not_count_as_history(engine):
    record(engine, entry(found=DAY))
    with UnitOfWork(engine) as unit:
        assert not unit.corrosion.has_form_here(MSN, PART, DAY)


def test_another_panel_s_history_is_not_this_panel_s(engine):
    record(engine, entry(part=ELSEWHERE, found=EARLIER))
    with UnitOfWork(engine) as unit:
        assert not unit.corrosion.has_form_here(MSN, PART, DAY)


def test_another_tail_s_history_is_not_this_tail_s(engine):
    record(engine, entry(found=EARLIER), serial=OTHER)
    with UnitOfWork(engine) as unit:
        assert not unit.corrosion.has_form_here(MSN, PART, DAY)


def test_history_is_read_off_rows_without_a_database():
    rows = [
        CorrosionRow(serial=MSN, against=FindingId(2025, 1), part=PART, level=1, found_on=EARLIER)
    ]
    assert seen_here_before(rows, PART, DAY)
    assert not seen_here_before(rows, ELSEWHERE, DAY)


def test_a_row_carrying_a_level_the_programme_does_not_grade_is_refused():
    row = CorrosionRow(serial=MSN, against=FindingId(2025, 1), part=PART, level=9, found_on=DAY)
    with pytest.raises(BadReading, match="not one the programme grades"):
        row_to_corrosion(row)
