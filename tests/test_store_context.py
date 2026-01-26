from datetime import date

import pytest

import sample
from doublerbay.errors import NotOnFile
from doublerbay.fleet.catalogue import TypeCatalogue
from doublerbay.fleet.utilisation import Snapshot, UtilisationLog
from doublerbay.ids import Serial
from doublerbay.store.base import build_engine, build_schema
from doublerbay.store.context import context_for
from doublerbay.store.unit import UnitOfWork

DAY = date(2025, 3, 14)
MSN = Serial("5127")


def log_with_two_readings():
    opened = UtilisationLog.opened(MSN, date(2016, 4, 12))
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


def built(engine, on=DAY, serial=MSN):
    with UnitOfWork(engine) as unit:
        return context_for(unit, TypeCatalogue(), serial, on)


def test_the_context_carries_the_aeroplane(engine):
    assert built(engine).serial == MSN


def test_the_context_carries_the_day_it_was_asked_for(engine):
    assert built(engine).on == DAY


def test_the_counters_come_off_the_log_at_that_day(engine):
    assert built(engine).flown.cycles.count == 13800


def test_an_earlier_day_gets_the_earlier_counters(engine):
    assert built(engine, date(2024, 6, 1)).flown.cycles.count == 12000


def test_the_structure_definition_comes_off_the_series(engine):
    assert built(engine).barrel is not None
    assert built(engine).wing_box is not None


def test_a_tail_that_is_not_on_the_register_is_refused(engine):
    with pytest.raises(NotOnFile):
        built(engine, serial=Serial("9999"))


def test_a_day_before_delivery_is_refused(engine):
    with pytest.raises(NotOnFile, match="not in service on 2010-01-01"):
        built(engine, date(2010, 1, 1))


def test_the_day_of_delivery_itself_is_in_service(engine):
    assert built(engine, date(2016, 4, 12)).on == date(2016, 4, 12)


def test_the_age_comes_off_the_delivery_date(engine):
    assert built(engine).age_years > 8
