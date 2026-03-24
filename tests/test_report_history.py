from datetime import date

import pytest

import sample
from doublerbay.damage.dent import Dent
from doublerbay.disposition.state import DispositionRecord, DispositionState
from doublerbay.errors import NotOnFile
from doublerbay.fleet.catalogue import TypeCatalogue
from doublerbay.ids import FindingId, Serial
from doublerbay.manual.book import v320_book
from doublerbay.report.history import Entry, story_of
from doublerbay.service.measuring import re_measure
from doublerbay.service.orders import raise_order, send
from doublerbay.store.base import build_engine, build_schema
from doublerbay.store.unit import UnitOfWork

DAY = date(2025, 3, 14)
AGAIN = date(2025, 9, 1)
AGAINST = FindingId(2025, 1)
MSN = Serial("5127")
QUESTION = "dent in the chem mill land at FR15, past the allowable, what may we do"


def dent(depth="0.020"):
    return Dent(sample.inch(depth), sample.inch("3.000"), sample.inch("2.000"))


@pytest.fixture
def engine():
    made = build_engine()
    build_schema(made)
    with UnitOfWork(made) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(sample.flying_log())
    return made


def raised(engine, sequence=1):
    reference = FindingId(2025, sequence)
    with UnitOfWork(engine) as unit:
        unit.findings.raise_finding(
            sample.card(sequence, geometry=dent(), at=sample.cabin_point(stringer=10))
        )
        unit.dispositions.save(DispositionRecord.raised(reference))
    return reference


def assess(engine, reference, on=DAY):
    with UnitOfWork(engine) as unit:
        held = unit.dispositions.get(reference)
        unit.dispositions.save(held.moved_to(DispositionState.ASSESSED, on, "a.okonkwo"))


def story(engine, reference=AGAINST):
    with UnitOfWork(engine) as unit:
        return story_of(unit, reference)


def test_a_card_that_is_not_on_file_has_no_story(engine):
    with UnitOfWork(engine) as unit, pytest.raises(NotOnFile):
        story_of(unit, FindingId(2025, 9))


def test_a_freshly_raised_card_has_one_line(engine):
    raised(engine)
    built = story(engine)
    assert len(built.entries) == 1
    assert built.entries[0].register == "card"
    assert "raised" in built.entries[0].what


def test_the_story_names_the_tail_it_is_on(engine):
    raised(engine)
    assert story(engine).serial == MSN


def test_a_move_lands_on_the_story(engine):
    reference = raised(engine)
    assess(engine, reference)
    built = story(engine)
    assert len(built.entries) == 2
    assert built.entries[1].register == "disposition"


def test_the_card_line_reads_before_the_move_on_the_same_day(engine):
    reference = raised(engine)
    assess(engine, reference)
    assert [one.register for one in story(engine).entries] == ["card", "disposition"]


def test_a_re_measurement_adds_a_card_line(engine):
    reference = raised(engine)
    assess(engine, reference)
    with UnitOfWork(engine) as unit:
        re_measure(unit, v320_book(), TypeCatalogue(), reference, dent("0.030"), AGAIN)
    built = story(engine)
    assert len(built.from_register("card")) == 2
    assert "revision 2" in built.entries[-1].what


def test_a_deferral_lands_on_the_story(engine):
    reference = raised(engine)
    assess(engine, reference)
    from doublerbay.disposition.deferral import grant
    from doublerbay.measure.interval import Interval

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
    built = story(engine)
    assert len(built.from_register("deferral")) == 1
    assert "granted by j.mercer" in built.from_register("deferral")[0].what


def test_an_order_lands_on_the_story_twice(engine):
    reference = raised(engine)
    assess(engine, reference)
    with UnitOfWork(engine) as unit:
        raise_order(unit, reference, QUESTION, DAY, "a.okonkwo")
    with UnitOfWork(engine) as unit:
        from doublerbay.disposition.engineering import OrderId

        send(unit, OrderId(2025, 1), date(2025, 3, 20))
    lines = story(engine).from_register("engineering")
    assert len(lines) == 2
    assert "raised" in lines[0].what
    assert "sent" in lines[1].what


def test_an_answered_order_adds_a_third_line(engine):
    reference = raised(engine)
    assess(engine, reference)
    with UnitOfWork(engine) as unit:
        raise_order(unit, reference, QUESTION, DAY, "a.okonkwo")
    with UnitOfWork(engine) as unit:
        from doublerbay.disposition.engineering import OrderId
        from doublerbay.service.orders import reject

        send(unit, OrderId(2025, 1), date(2025, 3, 20))
        reject(unit, OrderId(2025, 1), date(2025, 6, 1), "a.okonkwo")
    assert len(story(engine).from_register("engineering")) == 3


def test_a_card_with_no_orders_has_no_engineering_lines(engine):
    raised(engine)
    assert story(engine).from_register("engineering") == ()


def test_a_repair_lands_on_the_story(engine):
    reference = raised(engine)
    assess(engine, reference)
    _fit(engine, reference)
    lines = story(engine).from_register("repair")
    assert len(lines) == 1
    assert "fitted" in lines[0].what


def test_a_removed_repair_adds_a_second_line(engine):
    reference = raised(engine)
    assess(engine, reference)
    _fit(engine, reference)
    with UnitOfWork(engine) as unit:
        unit.repairs.remove(_repair_id(), date(2026, 1, 5))
    lines = story(engine).from_register("repair")
    assert len(lines) == 2
    assert "removed" in lines[1].what


def test_the_story_spans_from_the_first_day_to_the_last(engine):
    reference = raised(engine)
    assess(engine, reference)
    with UnitOfWork(engine) as unit:
        re_measure(unit, v320_book(), TypeCatalogue(), reference, dent("0.030"), AGAIN)
    built = story(engine)
    assert built.opened == DAY
    assert built.latest == AGAIN
    assert built.days_open == 171


def test_a_one_day_story_spans_no_days(engine):
    raised(engine)
    assert story(engine).days_open == 0


def test_the_story_reads_back_with_a_count_and_a_span(engine):
    raised(engine)
    assert str(story(engine)) == "DR-2025-000001 on MSN 5127: 1 entries over 0 days"


def test_an_entry_reads_back_with_its_register(engine):
    made = Entry(DAY, "card", "raised")
    assert str(made) == "2025-03-14 card: raised"


def test_an_unknown_register_sorts_last():
    assert Entry(DAY, "somewhere else", "x").rank == 5


def test_the_registers_sort_in_the_order_they_happen():
    assert Entry(DAY, "card", "x").rank < Entry(DAY, "repair", "x").rank


def _fit(engine, reference):
    from doublerbay.assess.placement import place
    from doublerbay.fleet.utilisation import usage
    from doublerbay.grid.ata import Reference
    from doublerbay.grid.surface import Offset
    from doublerbay.ids import PanelId
    from doublerbay.repair.record import RepairRecord
    from doublerbay.repair.scheme import select_scheme
    from doublerbay.service.repairs import embody

    placed = place(sample.tail_context(), sample.card(at=sample.cabin_point(stringer=10)))
    record = RepairRecord(
        reference=_repair_id(),
        part=PanelId(53, 10, 143, "L"),
        against=reference,
        scheme=select_scheme(
            sample.inch("2.000"), sample.inch("2.000"), placed, Reference.parse("53-40-01")
        ),
        centre=Offset(sample.inch("468.000"), sample.inch("91.000")),
        embodied=date(2025, 4, 2),
        at_usage=usage(13900, 24400),
    )
    with UnitOfWork(engine) as unit:
        embody(unit, record, MSN, "s.brandt")


def _repair_id():
    from doublerbay.ids import RepairId

    return RepairId(53, 1)
