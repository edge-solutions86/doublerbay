from datetime import date

import pytest

import sample
from doublerbay.assess.placement import place
from doublerbay.errors import BadReading, NotOnFile
from doublerbay.fleet.utilisation import usage
from doublerbay.grid.ata import Reference
from doublerbay.grid.surface import Offset
from doublerbay.ids import FindingId, PanelId, RepairId, Serial
from doublerbay.measure.interval import Interval
from doublerbay.measure.usage import Cycles
from doublerbay.repair.record import RepairRecord
from doublerbay.repair.scheme import select_scheme
from doublerbay.store.base import build_engine, build_schema
from doublerbay.store.decisions import row_to_task, task_to_row
from doublerbay.store.unit import UnitOfWork
from doublerbay.store.work import InspectionRow
from doublerbay.tolerance.method import InspectionMethod
from doublerbay.tolerance.programme import InspectionTask
from doublerbay.tolerance.schedule import InspectionSchedule

PAGE = Reference.parse("53-40-01")
FITTED = date(2025, 4, 2)
MSN = Serial("5127")
OTHER = Serial("5240")
PART = PanelId(53, 10, 143, "L")


def inch(text):
    return sample.inch(text)


def placed():
    return place(sample.tail_context(), sample.card(at=sample.cabin_point(stringer=10)))


def repair(sequence=1, centre=("468.000", "91.000")):
    return RepairRecord(
        reference=RepairId(53, sequence),
        part=PART,
        against=FindingId(2025, sequence),
        scheme=select_scheme(inch("2.000"), inch("2.000"), placed(), PAGE),
        centre=Offset(inch(centre[0]), inch(centre[1])),
        embodied=FITTED,
        at_usage=usage(13900, 24400),
    )


def schedule(threshold="8000 FC", repeat="4000 FC", method=InspectionMethod.HFEC):
    return InspectionSchedule(
        method=method,
        threshold=Interval.parse(threshold),
        repeat=Interval.parse(repeat) if repeat else None,
    )


def task(sequence=1, made=None, last=None, at_last=None):
    return InspectionTask(
        against=RepairId(53, sequence),
        part=PART,
        schedule=made or schedule(),
        embodied=FITTED,
        at_embodiment=usage(13900, 24400),
        last_done=last,
        at_last_done=at_last,
    )


@pytest.fixture
def engine():
    made = build_engine()
    build_schema(made)
    with UnitOfWork(made) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(sample.flying_log())
        unit.repairs.embody(repair(1), MSN)
    return made


def added(engine, one=None):
    with UnitOfWork(engine) as unit:
        unit.inspections.add(one or task())


def test_a_task_goes_in_and_comes_back(engine):
    added(engine)
    with UnitOfWork(engine) as unit:
        held = unit.inspections.get(RepairId(53, 1))
    assert held.part == PART
    assert held.schedule.method is InspectionMethod.HFEC


def test_the_threshold_and_the_repeat_stay_apart(engine):
    added(engine, task(made=schedule("8000 FC", "4000 FC")))
    with UnitOfWork(engine) as unit:
        held = unit.inspections.get(RepairId(53, 1)).schedule
    assert held.threshold.cycles == Cycles(8000)
    assert held.repeat.cycles == Cycles(4000)


def test_a_replacement_limit_has_no_repeat(engine):
    added(engine, task(made=schedule("18 MO", None)))
    with UnitOfWork(engine) as unit:
        held = unit.inspections.get(RepairId(53, 1))
    assert held.schedule.repeat is None
    assert held.is_a_one_off


def test_a_threshold_in_months_survives(engine):
    added(engine, task(made=schedule("18 MO", None)))
    with UnitOfWork(engine) as unit:
        assert unit.inspections.get(RepairId(53, 1)).schedule.threshold.months == 18


def test_a_threshold_in_hours_is_refused_rather_than_dropped():
    with pytest.raises(BadReading, match="threshold in hours"):
        task_to_row(task(made=schedule("500 FH", None)))


def test_a_repeat_in_days_is_refused_rather_than_dropped():
    with pytest.raises(BadReading, match="repeat in days"):
        task_to_row(task(made=schedule("8000 FC", "90 DY")))


def test_a_task_on_a_repair_that_is_not_on_file_is_refused(engine):
    with UnitOfWork(engine) as unit, pytest.raises(NotOnFile, match="not a repair on file"):
        unit.inspections.add(task(9))


def test_a_second_task_on_one_repair_is_refused(engine):
    added(engine)
    with UnitOfWork(engine) as unit, pytest.raises(BadReading, match="already carries"):
        unit.inspections.add(task())


def test_a_repair_carrying_no_task_is_refused(engine):
    with UnitOfWork(engine) as unit, pytest.raises(NotOnFile, match="carries no repeat"):
        unit.inspections.get(RepairId(53, 1))


def test_a_new_task_has_never_been_done(engine):
    added(engine)
    with UnitOfWork(engine) as unit:
        assert not unit.inspections.get(RepairId(53, 1)).has_been_done
        assert len(unit.inspections.never_done(MSN)) == 1


def test_signing_off_records_the_day_and_the_counters(engine):
    added(engine)
    with UnitOfWork(engine) as unit:
        unit.inspections.sign_off(RepairId(53, 1), date(2026, 1, 5), usage(15000, 26000))
    with UnitOfWork(engine) as unit:
        held = unit.inspections.get(RepairId(53, 1))
    assert held.last_done == date(2026, 1, 5)
    assert held.at_last_done.cycles == Cycles(15000)


def test_a_signed_off_task_is_no_longer_never_done(engine):
    added(engine)
    with UnitOfWork(engine) as unit:
        unit.inspections.sign_off(RepairId(53, 1), date(2026, 1, 5), usage(15000, 26000))
    with UnitOfWork(engine) as unit:
        assert unit.inspections.never_done(MSN) == ()


def test_signing_off_before_the_repair_went_on_is_refused(engine):
    added(engine)
    with UnitOfWork(engine) as unit, pytest.raises(BadReading, match="inspected before"):
        unit.inspections.sign_off(RepairId(53, 1), date(2024, 1, 1), usage(15000, 26000))


def test_signing_off_a_task_that_is_not_there_is_refused(engine):
    with UnitOfWork(engine) as unit, pytest.raises(NotOnFile):
        unit.inspections.sign_off(RepairId(53, 9), FITTED, usage(15000, 26000))


def test_a_replacement_limit_is_only_signed_off_once(engine):
    added(engine, task(made=schedule("18 MO", None)))
    with UnitOfWork(engine) as unit:
        unit.inspections.sign_off(RepairId(53, 1), date(2026, 1, 5), usage(15000, 26000))
    with UnitOfWork(engine) as unit, pytest.raises(BadReading, match="only done once"):
        unit.inspections.sign_off(RepairId(53, 1), date(2027, 1, 5), usage(16000, 27000))


def test_a_task_can_be_dropped(engine):
    added(engine)
    with UnitOfWork(engine) as unit:
        unit.inspections.drop(RepairId(53, 1))
    with UnitOfWork(engine) as unit:
        assert unit.inspections.on_tail(MSN) == ()


def test_dropping_one_that_is_not_there_is_refused(engine):
    with UnitOfWork(engine) as unit, pytest.raises(NotOnFile):
        unit.inspections.drop(RepairId(53, 9))


def test_a_task_on_a_removed_repair_drops_off_the_tail(engine):
    added(engine)
    with UnitOfWork(engine) as unit:
        unit.repairs.remove(RepairId(53, 1), date(2026, 1, 5))
    with UnitOfWork(engine) as unit:
        assert unit.inspections.on_tail(MSN) == ()


def test_another_tail_s_tasks_are_not_on_this_one(engine):
    added(engine)
    with UnitOfWork(engine) as unit:
        unit.aircraft.add(sample.aircraft("5240"))
        unit.aircraft.open_log(sample.flying_log("5240"))
    with UnitOfWork(engine) as unit:
        assert unit.inspections.on_tail(OTHER) == ()


def test_nothing_is_due_before_the_threshold(engine):
    added(engine)
    with UnitOfWork(engine) as unit:
        assert unit.inspections.due_on(MSN, usage(14000, 24500), FITTED) == ()


def test_a_task_past_its_threshold_is_due(engine):
    added(engine)
    with UnitOfWork(engine) as unit:
        due = unit.inspections.due_on(MSN, usage(30000, 50000), date(2030, 1, 1))
    assert len(due) == 1


def test_a_row_with_half_a_sign_off_is_refused():
    row = task_to_row(task())
    row.last_done = date(2026, 1, 5)
    with pytest.raises(BadReading, match="half a sign off"):
        row_to_task(row)


def test_a_row_with_no_threshold_left_on_it_is_refused():
    row = InspectionRow(
        against=RepairId(53, 1),
        part=PART,
        method="high frequency eddy current",
        threshold_cycles=None,
        threshold_months=None,
        repeat_cycles=None,
        embodied=FITTED,
        at_cycles=usage(13900, 24400).cycles,
        at_hours=usage(13900, 24400).hours,
    )
    with pytest.raises(BadReading, match="no threshold on it"):
        row_to_task(row)
