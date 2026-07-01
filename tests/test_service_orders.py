from datetime import date

import pytest

import sample
from conftest import assessed_card
from doublerbay.disposition.engineering import OrderId, OrderState, RepairApproval
from doublerbay.disposition.state import DispositionRecord, DispositionState
from doublerbay.errors import BadReading, NotOnFile, OutOfSequence
from doublerbay.ids import FindingId
from doublerbay.repair.scheme import RepairCategory
from doublerbay.service.orders import (
    acknowledge,
    approve,
    approved_category,
    raise_order,
    reject,
    send,
    withdraw,
)
from doublerbay.store.base import build_engine, build_schema
from doublerbay.store.unit import UnitOfWork

DAY = date(2025, 3, 14)
SENT = date(2025, 3, 20)
ANSWERED = date(2025, 6, 1)
AGAINST = FindingId(2025, 1)
QUESTION = "dent in the chem mill land at FR15, past the allowable, what may we do"


@pytest.fixture
def engine():
    made = build_engine()
    build_schema(made)
    with UnitOfWork(made) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(sample.flying_log())
    return made


def assessed(engine, sequence=1):
    """One card, raised and looked up, off the shared helper."""
    return assessed_card(engine, sequence)


def approval(sheet="RAS-2025-4417", until=date(2027, 1, 1)):
    return RepairApproval(
        sheet=sheet,
        category=RepairCategory.B,
        conditions=("inspect at every C check",),
        valid_until=until,
    )


def out(engine, against=AGAINST, question=QUESTION, on=DAY):
    with UnitOfWork(engine) as unit:
        return raise_order(unit, against, question, on, "a.okonkwo")


def test_raising_an_order_holds_the_card(engine):
    assessed(engine)
    answer = out(engine)
    assert answer.card_state is DispositionState.HELD
    with UnitOfWork(engine) as unit:
        assert unit.dispositions.get(AGAINST).state is DispositionState.HELD


def test_the_order_goes_on_file(engine):
    assessed(engine)
    out(engine)
    with UnitOfWork(engine) as unit:
        assert unit.orders.get(OrderId(2025, 1)).question == QUESTION


def test_the_order_number_is_allocated_here(engine):
    assessed(engine, 1)
    assessed(engine, 2)
    out(engine)
    second = out(engine, FindingId(2025, 2))
    assert second.order.reference == OrderId(2025, 2)


def test_a_raised_order_reads_back_as_one_line(engine):
    assessed(engine)
    assert str(out(engine)) == "EO-2025-0001 raised, card held for engineering"


def test_a_question_that_says_nothing_is_refused(engine):
    assessed(engine)
    with pytest.raises(BadReading, match="says what is being asked"):
        out(engine, question="help")


def test_a_card_that_is_not_on_file_cannot_go_out(engine):
    with pytest.raises(NotOnFile):
        out(engine, FindingId(2025, 9))


def test_a_card_already_out_cannot_go_out_again(engine):
    assessed(engine)
    out(engine)
    with pytest.raises(OutOfSequence, match="already out to the manufacturer"):
        out(engine)


def test_a_raised_card_cannot_be_held_before_it_is_assessed(engine):
    with UnitOfWork(engine) as unit:
        unit.findings.raise_finding(sample.card(1))
        unit.dispositions.save(DispositionRecord.raised(AGAINST))
    with pytest.raises(OutOfSequence, match="cannot go from raised to held"):
        out(engine)


def test_sending_records_the_day_it_actually_went(engine):
    assessed(engine)
    out(engine)
    with UnitOfWork(engine) as unit:
        answer = send(unit, OrderId(2025, 1), SENT)
    assert answer.state is OrderState.SENT
    assert answer.sent == SENT


def test_acknowledging_moves_the_order_on(engine):
    assessed(engine)
    out(engine)
    with UnitOfWork(engine) as unit:
        send(unit, OrderId(2025, 1), SENT)
        assert acknowledge(unit, OrderId(2025, 1)).state is OrderState.ACKNOWLEDGED


def test_an_approval_releases_the_card(engine):
    assessed(engine)
    out(engine)
    with UnitOfWork(engine) as unit:
        send(unit, OrderId(2025, 1), SENT)
        approve(unit, OrderId(2025, 1), approval(), ANSWERED, "a.okonkwo")
    with UnitOfWork(engine) as unit:
        assert unit.dispositions.get(AGAINST).state is DispositionState.ASSESSED


def test_an_approval_keeps_its_sheet_and_conditions(engine):
    assessed(engine)
    out(engine)
    with UnitOfWork(engine) as unit:
        send(unit, OrderId(2025, 1), SENT)
        approve(unit, OrderId(2025, 1), approval(), ANSWERED, "a.okonkwo")
    with UnitOfWork(engine) as unit:
        held = unit.orders.get(OrderId(2025, 1)).approval
    assert held.sheet == "RAS-2025-4417"
    assert held.conditions == ("inspect at every C check",)


def test_a_rejection_also_releases_the_card(engine):
    assessed(engine)
    out(engine)
    with UnitOfWork(engine) as unit:
        send(unit, OrderId(2025, 1), SENT)
        answer = reject(unit, OrderId(2025, 1), ANSWERED, "a.okonkwo")
    assert answer.state is OrderState.REJECTED
    with UnitOfWork(engine) as unit:
        assert unit.dispositions.get(AGAINST).state is DispositionState.ASSESSED


def test_withdrawing_releases_the_card(engine):
    assessed(engine)
    out(engine)
    with UnitOfWork(engine) as unit:
        answer = withdraw(unit, OrderId(2025, 1), ANSWERED, "a.okonkwo")
    assert answer.state is OrderState.WITHDRAWN
    with UnitOfWork(engine) as unit:
        assert unit.dispositions.get(AGAINST).state is DispositionState.ASSESSED


def test_a_card_superseded_while_the_order_was_out_is_left_alone(engine):
    assessed(engine, 1)
    assessed(engine, 2)
    out(engine)
    with UnitOfWork(engine) as unit:
        held = unit.dispositions.get(AGAINST)
        unit.dispositions.save(
            held.moved_to(DispositionState.SUPERSEDED, DAY, "a.okonkwo", "carried elsewhere")
        )
    with UnitOfWork(engine) as unit:
        withdraw(unit, OrderId(2025, 1), ANSWERED, "a.okonkwo")
    with UnitOfWork(engine) as unit:
        assert unit.dispositions.get(AGAINST).state is DispositionState.SUPERSEDED


def test_an_answered_order_drops_off_the_open_list(engine):
    assessed(engine)
    out(engine)
    with UnitOfWork(engine) as unit:
        send(unit, OrderId(2025, 1), SENT)
        reject(unit, OrderId(2025, 1), ANSWERED, "a.okonkwo")
    with UnitOfWork(engine) as unit:
        assert unit.orders.open_orders() == ()


def test_an_approved_category_comes_back_for_the_card(engine):
    assessed(engine)
    out(engine)
    with UnitOfWork(engine) as unit:
        send(unit, OrderId(2025, 1), SENT)
        approve(unit, OrderId(2025, 1), approval(), ANSWERED, "a.okonkwo")
    with UnitOfWork(engine) as unit:
        assert approved_category(unit, AGAINST, ANSWERED) is RepairCategory.B


def test_an_approval_past_its_validity_no_longer_stands(engine):
    assessed(engine)
    out(engine)
    with UnitOfWork(engine) as unit:
        send(unit, OrderId(2025, 1), SENT)
        approve(unit, OrderId(2025, 1), approval(until=ANSWERED), ANSWERED, "a.okonkwo")
    with UnitOfWork(engine) as unit:
        assert approved_category(unit, AGAINST, date(2028, 1, 1)) is None


def test_a_rejected_order_gives_no_category(engine):
    assessed(engine)
    out(engine)
    with UnitOfWork(engine) as unit:
        send(unit, OrderId(2025, 1), SENT)
        reject(unit, OrderId(2025, 1), ANSWERED, "a.okonkwo")
    with UnitOfWork(engine) as unit:
        assert approved_category(unit, AGAINST, ANSWERED) is None


def test_a_card_with_no_orders_gives_no_category(engine):
    assessed(engine)
    with UnitOfWork(engine) as unit:
        assert approved_category(unit, AGAINST, ANSWERED) is None


def test_an_order_that_is_not_on_file_cannot_be_sent(engine):
    with UnitOfWork(engine) as unit, pytest.raises(NotOnFile):
        send(unit, OrderId(2025, 9), SENT)
