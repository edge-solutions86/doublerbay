from datetime import date

import pytest

import sample
from doublerbay.disposition.engineering import (
    EngineeringOrder,
    OrderId,
    OrderState,
    RepairApproval,
)
from doublerbay.errors import BadReading, NotOnFile
from doublerbay.ids import FindingId
from doublerbay.repair.scheme import RepairCategory
from doublerbay.store.base import build_engine, build_schema
from doublerbay.store.decisions import (
    conditions_from_text,
    conditions_to_text,
    row_to_order,
)
from doublerbay.store.unit import UnitOfWork
from doublerbay.store.work import OrderRow

DAY = date(2025, 3, 14)
LATER = date(2025, 4, 2)
AGAINST = FindingId(2025, 1)
QUESTION = "dent in the chem mill land at FR15, past the allowable, what may we do"


@pytest.fixture
def engine():
    made = build_engine()
    build_schema(made)
    with UnitOfWork(made) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(sample.flying_log())
        unit.findings.raise_finding(sample.card(1))
    return made


def order(sequence=1, against=AGAINST, raised=DAY):
    return EngineeringOrder(
        reference=OrderId(2025, sequence),
        against=against,
        question=QUESTION,
        raised=raised,
    )


def approval(conditions=("inspect at every C check",)):
    return RepairApproval(
        sheet="RAS-2025-4417",
        category=RepairCategory.B,
        conditions=conditions,
        valid_until=date(2027, 1, 1),
    )


def raised(engine, one=None):
    with UnitOfWork(engine) as unit:
        unit.orders.raise_order(one or order())


def test_an_order_goes_in_and_comes_back(engine):
    raised(engine)
    with UnitOfWork(engine) as unit:
        held = unit.orders.get(OrderId(2025, 1))
    assert held.against == AGAINST
    assert held.question == QUESTION
    assert held.state is OrderState.RAISED


def test_an_order_raised_twice_is_refused(engine):
    raised(engine)
    with UnitOfWork(engine) as unit, pytest.raises(BadReading, match="already been raised"):
        unit.orders.raise_order(order())


def test_an_order_that_is_not_on_file_is_refused(engine):
    with UnitOfWork(engine) as unit, pytest.raises(NotOnFile, match="not an order on file"):
        unit.orders.get(OrderId(2025, 9))


def test_a_moved_order_replaces_the_row_it_came_off(engine):
    raised(engine)
    with UnitOfWork(engine) as unit:
        unit.orders.save(unit.orders.get(OrderId(2025, 1)).sent_on(LATER))
    with UnitOfWork(engine) as unit:
        held = unit.orders.get(OrderId(2025, 1))
    assert held.state is OrderState.SENT
    assert held.sent == LATER


def test_saving_one_that_was_never_raised_is_refused(engine):
    with UnitOfWork(engine) as unit, pytest.raises(NotOnFile):
        unit.orders.save(order(9))


def test_an_approval_survives_the_round_trip(engine):
    raised(engine)
    with UnitOfWork(engine) as unit:
        sent = unit.orders.get(OrderId(2025, 1)).sent_on(LATER)
        unit.orders.save(sent.approved(approval(), date(2025, 6, 1)))
    with UnitOfWork(engine) as unit:
        held = unit.orders.get(OrderId(2025, 1))
    assert held.approval.sheet == "RAS-2025-4417"
    assert held.approval.category is RepairCategory.B
    assert held.approval.valid_until == date(2027, 1, 1)


def test_the_conditions_survive_the_round_trip(engine):
    raised(engine)
    every = ("inspect at every C check", "do not extend past the next heavy")
    with UnitOfWork(engine) as unit:
        sent = unit.orders.get(OrderId(2025, 1)).sent_on(LATER)
        unit.orders.save(sent.approved(approval(every), date(2025, 6, 1)))
    with UnitOfWork(engine) as unit:
        assert unit.orders.get(OrderId(2025, 1)).approval.conditions == every


def test_an_approval_with_no_conditions_comes_back_empty(engine):
    raised(engine)
    with UnitOfWork(engine) as unit:
        sent = unit.orders.get(OrderId(2025, 1)).sent_on(LATER)
        unit.orders.save(sent.approved(approval(()), date(2025, 6, 1)))
    with UnitOfWork(engine) as unit:
        assert unit.orders.get(OrderId(2025, 1)).approval.conditions == ()


def test_conditions_fold_onto_one_column_and_back():
    text = conditions_to_text(("one", "two"))
    assert conditions_from_text(text) == ("one", "two")


def test_no_conditions_store_as_nothing():
    assert conditions_to_text(()) is None
    assert conditions_to_text(("  ",)) is None
    assert conditions_from_text(None) == ()


def test_a_blank_line_in_the_stored_column_is_dropped():
    assert conditions_from_text("one\n\ntwo") == ("one", "two")


def test_a_condition_that_is_not_one_line_is_refused():
    with pytest.raises(BadReading, match="one line"):
        conditions_to_text(("one\ntwo",))


def test_a_row_with_half_an_approval_is_refused():
    row = OrderRow(
        reference="EO-2025-0001",
        against=AGAINST,
        question=QUESTION,
        raised=DAY,
        state="approved",
        sheet="RAS-2025-4417",
        approved_category=None,
    )
    with pytest.raises(BadReading, match="half an approval"):
        row_to_order(row)


def test_the_open_orders_come_back_oldest_first(engine):
    raised(engine, order(1, raised=LATER))
    raised(engine, order(2, raised=DAY))
    with UnitOfWork(engine) as unit:
        assert [one.reference.sequence for one in unit.orders.open_orders()] == [2, 1]


def test_an_answered_order_drops_off_the_open_list(engine):
    raised(engine)
    with UnitOfWork(engine) as unit:
        sent = unit.orders.get(OrderId(2025, 1)).sent_on(LATER)
        unit.orders.save(sent.rejected(date(2025, 6, 1)))
    with UnitOfWork(engine) as unit:
        assert unit.orders.open_orders() == ()


def test_the_orders_against_one_card_come_back_together(engine):
    raised(engine, order(1))
    raised(engine, order(2, against=FindingId(2025, 2), raised=LATER))
    with UnitOfWork(engine) as unit:
        assert len(unit.orders.against(AGAINST)) == 1


def test_a_card_with_no_orders_comes_back_empty(engine):
    with UnitOfWork(engine) as unit:
        assert unit.orders.against(AGAINST) == ()


def test_an_order_out_a_long_time_needs_chasing(engine):
    raised(engine)
    with UnitOfWork(engine) as unit:
        unit.orders.save(unit.orders.get(OrderId(2025, 1)).sent_on(DAY))
    with UnitOfWork(engine) as unit:
        assert len(unit.orders.needing_chase(date(2026, 1, 1))) == 1


def test_an_order_just_sent_does_not_need_chasing(engine):
    raised(engine)
    with UnitOfWork(engine) as unit:
        unit.orders.save(unit.orders.get(OrderId(2025, 1)).sent_on(DAY))
    with UnitOfWork(engine) as unit:
        assert unit.orders.needing_chase(DAY) == ()


def test_the_chase_list_puts_the_longest_wait_first(engine):
    raised(engine, order(1, raised=date(2024, 1, 1)))
    raised(engine, order(2, against=FindingId(2025, 2), raised=DAY))
    with UnitOfWork(engine) as unit:
        unit.orders.save(unit.orders.get(OrderId(2025, 1)).sent_on(date(2024, 1, 1)))
        unit.orders.save(unit.orders.get(OrderId(2025, 2)).sent_on(DAY))
    with UnitOfWork(engine) as unit:
        chasing = unit.orders.needing_chase(date(2026, 1, 1))
    assert [one.reference.sequence for one in chasing] == [1, 2]


def test_the_next_reference_starts_at_one(engine):
    with UnitOfWork(engine) as unit:
        assert unit.orders.next_reference(2025) == OrderId(2025, 1)


def test_the_next_reference_follows_what_is_already_there(engine):
    raised(engine)
    with UnitOfWork(engine) as unit:
        assert unit.orders.next_reference(2025) == OrderId(2025, 2)


def test_the_next_reference_starts_again_in_a_new_year(engine):
    raised(engine)
    with UnitOfWork(engine) as unit:
        assert unit.orders.next_reference(2026) == OrderId(2026, 1)
