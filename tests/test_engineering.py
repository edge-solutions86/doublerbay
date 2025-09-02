from datetime import date

import pytest

from doublerbay.disposition.engineering import (
    EngineeringOrder,
    EngineeringQueue,
    OrderId,
    OrderState,
    RepairApproval,
)
from doublerbay.errors import BadReading, NotOnFile, OutOfSequence
from doublerbay.ids import FindingId
from doublerbay.repair.scheme import RepairCategory

DR = FindingId(2025, 4182)
RAISED = date(2025, 3, 14)
SENT = date(2025, 3, 17)


def order(sequence=1, question="crack past the book at FR41"):
    return EngineeringOrder(OrderId(2025, sequence), DR, question, RAISED)


def approval(sheet="RAS-2025-0007", until=None):
    return RepairApproval(sheet, RepairCategory.B, ("inspect at 3000 FC",), until)


def test_an_order_number_reads_and_prints_padded():
    assert str(OrderId(2025, 7)) == "EO-2025-0007"
    assert OrderId.parse("EO-2025-0007") == OrderId(2025, 7)


@pytest.mark.parametrize("text", ["", "EO-2025-7", "2025-0007", "RAS-2025-0007"])
def test_an_order_number_refuses_anything_else(text):
    with pytest.raises(BadReading, match="not an engineering order number"):
        OrderId.parse(text)


def test_an_order_year_and_sequence_are_bounded():
    with pytest.raises(BadReading, match="not an order year"):
        OrderId(1999, 1)
    with pytest.raises(BadReading, match="not an order sequence"):
        OrderId(2025, 0)


def test_an_order_asks_something():
    with pytest.raises(BadReading, match="an order asks something"):
        EngineeringOrder(OrderId(2025, 1), DR, "  ", RAISED)


def test_an_order_reads_back_what_it_is_against_and_where_it_is():
    assert str(order()) == "EO-2025-0001 against DR-2025-004182, raised"


def test_the_open_states_are_the_ones_still_waiting():
    assert OrderState.RAISED.is_open
    assert OrderState.SENT.is_open
    assert OrderState.ACKNOWLEDGED.is_open
    assert not OrderState.APPROVED.is_open
    assert not OrderState.REJECTED.is_open


def test_approved_and_rejected_are_the_two_answers():
    assert OrderState.APPROVED.is_answered
    assert OrderState.REJECTED.is_answered
    assert not OrderState.WITHDRAWN.is_answered


def test_an_order_walks_from_raised_to_approved():
    walked = order().sent_on(SENT).acknowledged().approved(approval(), date(2025, 4, 10))
    assert walked.state is OrderState.APPROVED
    assert walked.approval.sheet == "RAS-2025-0007"
    assert not walked.is_open


def test_an_order_can_be_approved_without_being_acknowledged_first():
    walked = order().sent_on(SENT).approved(approval(), date(2025, 4, 10))
    assert walked.state is OrderState.APPROVED


def test_an_order_cannot_be_approved_before_it_is_sent():
    with pytest.raises(OutOfSequence, match="cannot go from raised to approved"):
        order().approved(approval(), date(2025, 4, 10))


def test_an_approved_order_goes_nowhere_else():
    done = order().sent_on(SENT).approved(approval(), date(2025, 4, 10))
    with pytest.raises(OutOfSequence):
        done.withdrawn(date(2025, 5, 1))


def test_a_rejected_order_can_be_raised_again():
    turned_down = order().sent_on(SENT).rejected(date(2025, 4, 10))
    assert turned_down.state is OrderState.REJECTED
    again = turned_down.raised_again()
    assert again.state is OrderState.RAISED
    assert again.sent is None
    assert again.answered is None


def test_an_approved_order_has_to_carry_the_sheet():
    with pytest.raises(BadReading, match="carries an approval sheet"):
        EngineeringOrder(
            OrderId(2025, 1), DR, "question", RAISED, OrderState.APPROVED, SENT, RAISED, None
        )


def test_a_sheet_number_has_to_look_like_one():
    with pytest.raises(BadReading, match="not a repair approval sheet number"):
        RepairApproval("EO-2025-0007", RepairCategory.B)


def test_an_approval_reads_back_its_sheet_and_category():
    assert str(approval()) == "RAS-2025-0007 category B"
    dated = approval(until=date(2027, 1, 1))
    assert str(dated).endswith("expires 2027-01-01")


def test_an_approval_with_no_expiry_never_runs_out():
    assert approval().is_valid_on(date(2099, 1, 1))


def test_an_approval_with_an_expiry_stops_being_valid_after_it():
    dated = approval(until=date(2027, 1, 1))
    assert dated.is_valid_on(date(2027, 1, 1))
    assert not dated.is_valid_on(date(2027, 1, 2))


def test_the_wait_counts_from_when_it_was_raised_until_it_is_sent():
    assert order().days_waiting(date(2025, 3, 20)) == 6


def test_the_wait_counts_from_when_it_was_sent_once_it_has_been():
    assert order().sent_on(SENT).days_waiting(date(2025, 3, 20)) == 3


def test_the_wait_stops_when_the_answer_arrives():
    answered = order().sent_on(SENT).approved(approval(), date(2025, 4, 10))
    assert answered.days_waiting(date(2099, 1, 1)) == 24


def test_an_order_sent_and_not_acknowledged_needs_chasing_after_the_turnaround():
    sent = order().sent_on(SENT)
    assert not sent.needs_chasing(date(2025, 3, 22))
    assert sent.needs_chasing(date(2025, 3, 23))


def test_an_acknowledged_order_does_not_need_chasing():
    assert not order().sent_on(SENT).acknowledged().needs_chasing(date(2025, 6, 1))


def test_an_unsent_order_does_not_need_chasing():
    assert not order().needs_chasing(date(2099, 1, 1))


def queue(*orders):
    held = EngineeringQueue()
    for one in orders or (order(),):
        held.raise_order(one)
    return held


def test_the_queue_counts_what_is_on_it_and_what_is_open():
    held = queue(order(1), order(2))
    assert str(held) == "2 orders, 2 open"
    assert len(held) == 2


def test_an_order_raised_twice_is_refused():
    held = queue(order(1))
    with pytest.raises(BadReading, match="has already been raised"):
        held.raise_order(order(1))


def test_an_order_not_on_the_queue_is_a_lookup_miss():
    with pytest.raises(NotOnFile, match="is not on the queue"):
        queue().order(OrderId(2025, 9))
    with pytest.raises(NotOnFile, match="is not on the queue"):
        queue().replace_order(order(9))


def test_replacing_an_order_keeps_the_new_state():
    held = queue(order(1))
    held.replace_order(held.order(OrderId(2025, 1)).sent_on(SENT))
    assert held.order(OrderId(2025, 1)).state is OrderState.SENT


def test_the_chase_list_is_the_ones_nobody_has_answered():
    held = queue(order(1), order(2))
    held.replace_order(held.order(OrderId(2025, 1)).sent_on(SENT))
    assert [one.reference.sequence for one in held.needing_chase(date(2025, 4, 1))] == [1]


def test_orders_can_be_pulled_out_by_the_card_they_are_against():
    held = queue(order(1), order(2))
    assert len(held.against(DR)) == 2
    assert held.against(FindingId(2025, 9999)) == ()


def test_the_next_number_carries_on_from_the_highest_used_that_year():
    held = queue(order(1), order(7))
    assert held.next_reference(2025) == OrderId(2025, 8)
    assert held.next_reference(2026) == OrderId(2026, 1)
