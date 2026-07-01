"""The shared test helpers, tested, because everything else leans on them.

A fixture that quietly does the wrong thing makes a hundred tests pass while
proving nothing, and it is the one file in a suite nobody checks. So the
helpers get the same treatment as the code they exercise.
"""

from datetime import date

import pytest

from conftest import (
    KEYS,
    TODAY,
    assess_card,
    assessed_card,
    client_for,
    deps_for,
    enrolled,
    new_engine,
    raise_card,
)
from doublerbay.disposition.state import DispositionState
from doublerbay.errors import NotOnFile
from doublerbay.ids import FindingId, Serial
from doublerbay.store.base import table_names
from doublerbay.store.unit import UnitOfWork


def test_a_new_engine_has_the_schema_on_it():
    engine = new_engine()
    with UnitOfWork(engine) as unit:
        assert unit.aircraft.all() == ()
    assert len(table_names()) > 5


def test_a_new_engine_holds_no_fleet():
    with UnitOfWork(new_engine()) as unit:
        assert unit.aircraft.all() == ()


def test_enrolling_puts_one_tail_on():
    engine = new_engine()
    enrolled(engine)
    with UnitOfWork(engine) as unit:
        assert len(unit.aircraft.all()) == 1


def test_enrolling_opens_a_log():
    engine = new_engine()
    enrolled(engine)
    with UnitOfWork(engine) as unit:
        assert unit.aircraft.log_for(Serial("5127")).snapshots


def test_another_serial_can_be_enrolled():
    engine = new_engine()
    enrolled(engine, "5240")
    with UnitOfWork(engine) as unit:
        assert unit.aircraft.get(Serial("5240")).serial == Serial("5240")


def test_the_dependencies_pin_the_day_the_suite_answers_as_of():
    assert deps_for(new_engine()).on() == TODAY


def test_the_day_can_be_left_unpinned():
    assert deps_for(new_engine(), None).on() == date.today()


def test_a_client_carries_the_role_it_was_asked_for():
    assert client_for(new_engine(), "reader").headers["x-api-key"] == KEYS["reader"]["x-api-key"]


def test_every_role_has_a_key():
    assert set(KEYS) == {"reader", "mechanic", "engineer", "chief"}


def test_a_raised_card_is_on_the_register():
    engine = new_engine()
    enrolled(engine)
    reference = raise_card(engine)
    with UnitOfWork(engine) as unit:
        assert unit.findings.latest(reference).reference == reference


def test_a_raised_card_starts_raised():
    engine = new_engine()
    enrolled(engine)
    reference = raise_card(engine)
    with UnitOfWork(engine) as unit:
        assert unit.dispositions.get(reference).state is DispositionState.RAISED


def test_a_raised_card_takes_its_sequence():
    engine = new_engine()
    enrolled(engine)
    assert raise_card(engine, 7) == FindingId(2025, 7)


def test_a_raised_card_can_be_put_on_another_tail():
    engine = new_engine()
    enrolled(engine)
    enrolled(engine, "5240")
    reference = raise_card(engine, 1, "5240")
    with UnitOfWork(engine) as unit:
        assert unit.findings.latest(reference).serial == Serial("5240")


def test_assessing_moves_the_card():
    engine = new_engine()
    enrolled(engine)
    reference = assess_card(engine, raise_card(engine))
    with UnitOfWork(engine) as unit:
        assert unit.dispositions.get(reference).state is DispositionState.ASSESSED


def test_an_assessed_card_does_both_steps():
    engine = new_engine()
    enrolled(engine)
    reference = assessed_card(engine)
    with UnitOfWork(engine) as unit:
        assert unit.dispositions.get(reference).state is DispositionState.ASSESSED
        assert unit.findings.latest(reference)


def test_an_assessed_card_records_one_move():
    engine = new_engine()
    enrolled(engine)
    reference = assessed_card(engine)
    with UnitOfWork(engine) as unit:
        assert len(unit.dispositions.get(reference).history) == 1


def test_assessing_a_card_that_is_not_there_is_refused():
    engine = new_engine()
    enrolled(engine)
    with pytest.raises(NotOnFile):
        assess_card(engine, FindingId(2025, 99))
