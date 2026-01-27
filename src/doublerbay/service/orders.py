"""Raising a question to the manufacturer, and doing what the answer says.

A card the book has no allowable for is not a card the desk can decide. It
goes out to the type certificate holder as an engineering order, and the card
sits held until the answer comes back. Holding it is the point: a card that
stayed assessed would keep appearing on the list of things somebody could
disposition, and somebody eventually would.

When the answer comes back approved it carries a repair category, and that
category is what the shop is allowed to fit. So approving an order puts the
card back to assessed, which is where a repair can be raised against it. A
rejection does the same, because a rejection is still an answer and the desk
now has to find another way.

The question text is the operator's, not this module's. Generating it from a
template would produce four hundred identical letters and the manufacturer
would start answering them all the same way.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from doublerbay.disposition.engineering import (
    EngineeringOrder,
    OrderId,
    OrderState,
    RepairApproval,
)
from doublerbay.disposition.state import DispositionState
from doublerbay.errors import BadReading, OutOfSequence
from doublerbay.ids import FindingId
from doublerbay.store.unit import UnitOfWork

# The shortest question worth sending. Anything shorter is a subject line.
SHORTEST_QUESTION = 20


@dataclass(frozen=True, slots=True)
class Raised:
    """An order out, and where it left the card."""

    order: EngineeringOrder
    card_state: DispositionState

    def __str__(self) -> str:
        return f"{self.order.reference} raised, card {self.card_state}"


def raise_order(unit: UnitOfWork, against: FindingId, question: str, on: date, by: str) -> Raised:
    """Send a card out to the manufacturer and hold it until the answer lands."""
    asked = question.strip()
    if len(asked) < SHORTEST_QUESTION:
        raise BadReading("a question to the manufacturer says what is being asked")
    unit.findings.latest(against)
    held = unit.dispositions.get(against)
    if held.state is DispositionState.HELD:
        raise OutOfSequence(f"{against} is already out to the manufacturer")

    order = EngineeringOrder(
        reference=unit.orders.next_reference(on.year),
        against=against,
        question=asked,
        raised=on,
    )
    unit.orders.raise_order(order)
    unit.dispositions.save(
        held.moved_to(DispositionState.HELD, on, by, f"out as {order.reference}")
    )
    unit.flush()
    return Raised(order, DispositionState.HELD)


def send(unit: UnitOfWork, reference: OrderId, on: date) -> EngineeringOrder:
    """Record that the order actually went out, which is not when it was written."""
    sent = unit.orders.get(reference).sent_on(on)
    unit.orders.save(sent)
    unit.flush()
    return sent


def acknowledge(unit: UnitOfWork, reference: OrderId) -> EngineeringOrder:
    """Record that the manufacturer has it, which stops the chase clock."""
    seen = unit.orders.get(reference).acknowledged()
    unit.orders.save(seen)
    unit.flush()
    return seen


def approve(
    unit: UnitOfWork, reference: OrderId, approval: RepairApproval, on: date, by: str
) -> EngineeringOrder:
    """Record an approval and put the card back where a repair can be raised."""
    answered = unit.orders.get(reference).approved(approval, on)
    unit.orders.save(answered)
    _release(unit, answered, on, by, f"approved on {approval.sheet}")
    unit.flush()
    return answered


def reject(unit: UnitOfWork, reference: OrderId, on: date, by: str) -> EngineeringOrder:
    """Record a rejection, which still releases the card because it is an answer."""
    answered = unit.orders.get(reference).rejected(on)
    unit.orders.save(answered)
    _release(unit, answered, on, by, "rejected, another way needed")
    unit.flush()
    return answered


def withdraw(unit: UnitOfWork, reference: OrderId, on: date, by: str) -> EngineeringOrder:
    """Take a question back, which releases the card the same way an answer does."""
    pulled = unit.orders.get(reference).withdrawn(on)
    unit.orders.save(pulled)
    _release(unit, pulled, on, by, "question withdrawn")
    unit.flush()
    return pulled


def _release(unit: UnitOfWork, order: EngineeringOrder, on: date, by: str, note: str) -> None:
    """Put a held card back to assessed, and leave one that moved on alone.

    A card can be superseded while its order is out, and superseding is
    terminal. Forcing it back to assessed because a letter arrived would
    resurrect a card the desk deliberately closed.
    """
    held = unit.dispositions.get(order.against)
    if held.state is not DispositionState.HELD:
        return
    unit.dispositions.save(held.moved_to(DispositionState.ASSESSED, on, by, note))


def approved_category(unit: UnitOfWork, against: FindingId, on: date):
    """The category the manufacturer allowed for this card, if any still stands."""
    for order in unit.orders.against(against):
        approval = order.approval
        if order.state is OrderState.APPROVED and approval is not None and approval.is_valid_on(on):
            return approval.category
    return None
