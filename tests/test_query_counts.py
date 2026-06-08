"""How many queries a report actually runs, asserted rather than hoped for.

The reports on this desk look fast on a test database with four aeroplanes and
one deferral each. The shape that hurts is the one that runs a query per row,
because it is invisible until the fleet is real and then it is the only thing
anybody talks about.

So the count is a test. Not a threshold on wall clock, which moves with the
machine, but the number of statements, which moves only when somebody writes a
loop with a query in it.
"""

from __future__ import annotations

from contextlib import contextmanager
from datetime import date

import pytest
from sqlalchemy import event

import sample
from conftest import new_engine
from doublerbay.disposition.deferral import grant
from doublerbay.disposition.state import DispositionRecord, DispositionState
from doublerbay.ids import FindingId, Serial
from doublerbay.measure.interval import Interval
from doublerbay.report.desk import due_on_tail, open_across_fleet
from doublerbay.store.unit import UnitOfWork

DAY = date(2025, 3, 14)
MSN = Serial("5127")


@contextmanager
def counted(engine):
    """Count every statement the engine runs inside the block."""
    seen: list[str] = []

    def record(conn, cursor, statement, parameters, context, executemany):
        seen.append(statement)

    event.listen(engine, "before_cursor_execute", record)
    try:
        yield seen
    finally:
        event.remove(engine, "before_cursor_execute", record)


@pytest.fixture
def engine():
    made = new_engine()
    with UnitOfWork(made) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(sample.flying_log())
    return made


def deferred(engine, how_many):
    """Put a number of deferred cards on the one tail."""
    for step in range(1, how_many + 1):
        reference = FindingId(2025, step)
        with UnitOfWork(engine) as unit:
            unit.findings.raise_finding(sample.card(step))
            unit.dispositions.save(
                DispositionRecord.raised(reference).moved_to(
                    DispositionState.ASSESSED, DAY, "a.okonkwo"
                )
            )
        with UnitOfWork(engine) as unit:
            unit.deferrals.grant(
                grant(
                    reference,
                    DAY,
                    sample.flown(13800, 24200),
                    "j.mercer",
                    engineer_set=Interval.parse("500 FC"),
                )
            )
            held = unit.dispositions.get(reference)
            unit.dispositions.save(held.moved_to(DispositionState.DEFERRED, DAY, "j.mercer"))


def due_queries(engine, how_many):
    deferred(engine, how_many)
    with UnitOfWork(engine) as unit, counted(engine) as seen:
        due_on_tail(unit, MSN, DAY)
    return len(seen)


def test_the_counter_sees_the_statements(engine):
    with UnitOfWork(engine) as unit, counted(engine) as seen:
        unit.aircraft.all()
    assert any(one.startswith("SELECT aircraft") for one in seen)


def test_the_counter_stops_counting_after_the_block(engine):
    with UnitOfWork(engine) as unit, counted(engine) as seen:
        unit.aircraft.all()
    before = len(seen)
    with UnitOfWork(engine) as unit:
        unit.aircraft.all()
    assert len(seen) == before


def test_one_deferral_takes_a_handful_of_queries(engine):
    assert due_queries(engine, 1) <= 8


def test_ten_deferrals_take_the_same_number_as_one(engine):
    one = due_queries(engine, 1)
    made = new_engine()
    with UnitOfWork(made) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(sample.flying_log())
    assert due_queries(made, 10) == one


def test_twenty_deferrals_take_the_same_number_as_ten(engine):
    ten = due_queries(engine, 10)
    made = new_engine()
    with UnitOfWork(made) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(sample.flying_log())
    assert due_queries(made, 20) == ten


def test_the_tail_lookup_is_one_query_however_many_cards(engine):
    deferred(engine, 10)
    references = tuple(FindingId(2025, step) for step in range(1, 11))
    with UnitOfWork(engine) as unit, counted(engine) as seen:
        found = unit.findings.serials_for(references)
    assert len(seen) == 1
    assert len(found) == 10


def test_an_empty_set_of_cards_runs_no_query(engine):
    with UnitOfWork(engine) as unit, counted(engine) as seen:
        assert unit.findings.serials_for(()) == {}
    assert seen == []


def test_a_card_that_is_not_on_file_is_simply_absent(engine):
    deferred(engine, 1)
    with UnitOfWork(engine) as unit:
        found = unit.findings.serials_for((FindingId(2025, 1), FindingId(2025, 99)))
    assert FindingId(2025, 1) in found
    assert FindingId(2025, 99) not in found


def test_the_open_count_does_not_grow_a_query_per_card(engine):
    deferred(engine, 2)
    with UnitOfWork(engine) as unit, counted(engine) as first:
        open_across_fleet(unit, DAY)
    made = new_engine()
    with UnitOfWork(made) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(sample.flying_log())
    deferred(made, 12)
    with UnitOfWork(made) as unit, counted(made) as second:
        open_across_fleet(unit, DAY)
    assert len(second) == len(first)


def test_the_due_list_still_finds_the_right_cards(engine):
    deferred(engine, 3)
    with UnitOfWork(engine) as unit:
        report = due_on_tail(unit, MSN, DAY)
    assert len(report.items) == 3


def test_another_tail_s_deferrals_are_still_left_out(engine):
    deferred(engine, 2)
    with UnitOfWork(engine) as unit:
        unit.aircraft.add(sample.aircraft("5240"))
        unit.aircraft.open_log(sample.flying_log("5240"))
    with UnitOfWork(engine) as unit:
        assert due_on_tail(unit, Serial("5240"), DAY).items == ()
