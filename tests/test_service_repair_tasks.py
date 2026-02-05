from datetime import date

import pytest

import sample
from doublerbay.assess.placement import place
from doublerbay.disposition.state import DispositionRecord, DispositionState
from doublerbay.errors import NotOnFile
from doublerbay.fleet.utilisation import usage
from doublerbay.grid.ata import Reference
from doublerbay.grid.surface import Offset
from doublerbay.ids import FindingId, PanelId, RepairId, Serial
from doublerbay.repair.record import RepairRecord
from doublerbay.repair.scheme import RepairCategory, select_scheme
from doublerbay.service.repairs import DEFAULT_METHOD, embody, remove
from doublerbay.store.base import build_engine, build_schema
from doublerbay.store.unit import UnitOfWork
from doublerbay.tolerance.method import InspectionMethod

PAGE = Reference.parse("53-40-01")
DAY = date(2025, 3, 14)
FITTED = date(2025, 4, 2)
MSN = Serial("5127")
PART = PanelId(53, 10, 143, "L")


def inch(text):
    return sample.inch(text)


def placed():
    return place(sample.tail_context(), sample.card(at=sample.cabin_point(stringer=10)))


def record(sequence=1, temporary=False):
    return RepairRecord(
        reference=RepairId(53, sequence),
        part=PART,
        against=FindingId(2025, sequence),
        scheme=select_scheme(inch("2.000"), inch("2.000"), placed(), PAGE, None, temporary),
        centre=Offset(inch("468.000"), inch("91.000")),
        embodied=FITTED,
        at_usage=usage(13900, 24400),
    )


@pytest.fixture
def engine():
    made = build_engine()
    build_schema(made)
    with UnitOfWork(made) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(sample.flying_log())
    return made


def assessed(engine, sequence=1):
    reference = FindingId(2025, sequence)
    with UnitOfWork(engine) as unit:
        unit.findings.raise_finding(sample.card(sequence, at=sample.cabin_point(stringer=10)))
        unit.dispositions.save(
            DispositionRecord.raised(reference).moved_to(
                DispositionState.ASSESSED, DAY, "a.okonkwo"
            )
        )
    return reference


def fit(engine, one=None, method=DEFAULT_METHOD):
    with UnitOfWork(engine) as unit:
        return embody(unit, one or record(), MSN, "s.brandt", None, method)


def test_a_category_a_repair_carries_no_repeat_inspection(engine):
    assessed(engine)
    answer = fit(engine)
    if answer.reference and record().scheme.category is RepairCategory.A:
        assert not answer.carries_inspection


def test_a_temporary_repair_carries_a_replacement_limit(engine):
    assessed(engine)
    answer = fit(engine, record(temporary=True))
    assert answer.carries_inspection
    assert answer.inspection.repeat is None


def test_the_replacement_limit_is_on_the_calendar(engine):
    assessed(engine)
    fit(engine, record(temporary=True))
    with UnitOfWork(engine) as unit:
        held = unit.inspections.get(RepairId(53, 1))
    assert held.schedule.threshold.months is not None
    assert held.is_a_one_off


def test_a_repair_with_a_task_lands_on_the_tail_list(engine):
    assessed(engine)
    fit(engine, record(temporary=True))
    with UnitOfWork(engine) as unit:
        assert len(unit.inspections.on_tail(MSN)) == 1


def test_the_task_is_filed_against_the_repair_that_carries_it(engine):
    assessed(engine)
    fit(engine, record(temporary=True))
    with UnitOfWork(engine) as unit:
        assert unit.inspections.get(RepairId(53, 1)).against == RepairId(53, 1)


def test_the_task_starts_at_the_counters_the_repair_went_on_at(engine):
    assessed(engine)
    fit(engine, record(temporary=True))
    with UnitOfWork(engine) as unit:
        held = unit.inspections.get(RepairId(53, 1))
    assert held.at_embodiment == usage(13900, 24400)
    assert held.embodied == FITTED


def test_a_task_starts_never_done(engine):
    assessed(engine)
    fit(engine, record(temporary=True))
    with UnitOfWork(engine) as unit:
        assert len(unit.inspections.never_done(MSN)) == 1


def test_the_method_asked_for_reaches_the_task(engine):
    assessed(engine)
    fit(engine, record(temporary=True), method=InspectionMethod.UT)
    with UnitOfWork(engine) as unit:
        assert unit.inspections.get(RepairId(53, 1)).schedule.method is InspectionMethod.UT


def test_removing_a_repair_takes_its_task_with_it(engine):
    assessed(engine)
    fit(engine, record(temporary=True))
    with UnitOfWork(engine) as unit:
        remove(unit, RepairId(53, 1), date(2026, 1, 5))
    with UnitOfWork(engine) as unit:
        assert unit.inspections.on_tail(MSN) == ()
        with pytest.raises(NotOnFile):
            unit.inspections.get(RepairId(53, 1))


def test_removing_a_repair_with_no_task_is_not_an_error(engine):
    assessed(engine)
    answer = fit(engine)
    with UnitOfWork(engine) as unit:
        remove(unit, RepairId(53, 1), date(2026, 1, 5))
    with UnitOfWork(engine) as unit:
        assert unit.repairs.on_tail(MSN) == ()
    assert answer.reference == RepairId(53, 1)


def test_removing_a_repair_that_is_not_on_file_is_still_refused(engine):
    with UnitOfWork(engine) as unit, pytest.raises(NotOnFile):
        remove(unit, RepairId(53, 9), date(2026, 1, 5))


def test_two_repairs_each_get_their_own_task(engine):
    assessed(engine, 1)
    assessed(engine, 2)
    fit(engine, record(1, temporary=True))
    fit(engine, record(2, temporary=True))
    with UnitOfWork(engine) as unit:
        assert len(unit.inspections.on_tail(MSN)) == 2


def test_a_task_can_be_signed_off_after_the_repair_goes_on(engine):
    assessed(engine)
    fit(engine, record(temporary=True))
    with UnitOfWork(engine) as unit:
        signed = unit.inspections.sign_off(RepairId(53, 1), date(2026, 1, 5), usage(15000, 26000))
    assert signed.has_been_done
