"""Asking the manufacturer, and the queue of questions waiting for an answer.

When the book runs out the operator cannot invent a repair. The question goes
to the type certificate holder as an engineering order, and what comes back is
a repair approval sheet: a scheme, a category, and usually a set of conditions
and a date it stops being valid. Until that arrives the aeroplane is either on
the ground or flying under a deferral, and both of those are expensive.

Which makes the turnaround the number the desk actually watches. An order sent
on Tuesday and acknowledged on Wednesday is being worked. An order sent three
weeks ago with no acknowledgement is a phone call somebody has to make, and the
queue exists to make that list rather than to store anything clever.
"""

from __future__ import annotations

import re
from collections.abc import Iterator
from dataclasses import dataclass, replace
from datetime import date, timedelta
from enum import Enum

from doublerbay.errors import BadReading, NotOnFile, OutOfSequence
from doublerbay.ids import FindingId
from doublerbay.repair.scheme import RepairCategory

_ORDER = re.compile(r"^EO-(\d{4})-(\d{4})$")
_SHEET = re.compile(r"^RAS-(\d{4})-(\d{4})$")

# How long the manufacturer has to acknowledge before somebody chases it.
ACKNOWLEDGE_WITHIN_DAYS = 5


@dataclass(frozen=True, slots=True)
class OrderId:
    """An engineering order number, as the year it was raised and a counter."""

    year: int
    sequence: int

    def __post_init__(self) -> None:
        if not 2000 <= self.year <= 2099:
            raise BadReading(f"{self.year} is not an order year")
        if not 1 <= self.sequence <= 9999:
            raise BadReading(f"{self.sequence} is not an order sequence")

    @classmethod
    def parse(cls, text: str) -> OrderId:
        match = _ORDER.match(text.strip().upper())
        if match is None:
            raise BadReading(f"{text!r} is not an engineering order number")
        return cls(int(match.group(1)), int(match.group(2)))

    def __str__(self) -> str:
        return f"EO-{self.year:04d}-{self.sequence:04d}"


class OrderState(Enum):
    """Where the question is."""

    RAISED = "raised"
    SENT = "sent"
    ACKNOWLEDGED = "acknowledged"
    APPROVED = "approved"
    REJECTED = "rejected"
    WITHDRAWN = "withdrawn"

    @property
    def is_open(self) -> bool:
        return self in (OrderState.RAISED, OrderState.SENT, OrderState.ACKNOWLEDGED)

    @property
    def is_answered(self) -> bool:
        return self in (OrderState.APPROVED, OrderState.REJECTED)

    def can_move_to(self, other: OrderState) -> bool:
        return other in _ORDER_MOVES[self]

    def __str__(self) -> str:
        return self.value


_ORDER_MOVES: dict[OrderState, frozenset[OrderState]] = {
    OrderState.RAISED: frozenset({OrderState.SENT, OrderState.WITHDRAWN}),
    OrderState.SENT: frozenset(
        {OrderState.ACKNOWLEDGED, OrderState.APPROVED, OrderState.REJECTED, OrderState.WITHDRAWN}
    ),
    OrderState.ACKNOWLEDGED: frozenset(
        {OrderState.APPROVED, OrderState.REJECTED, OrderState.WITHDRAWN}
    ),
    OrderState.APPROVED: frozenset(),
    OrderState.REJECTED: frozenset({OrderState.RAISED}),
    OrderState.WITHDRAWN: frozenset(),
}


@dataclass(frozen=True, slots=True)
class RepairApproval:
    """What comes back when the manufacturer says yes."""

    sheet: str
    category: RepairCategory
    conditions: tuple[str, ...] = ()
    valid_until: date | None = None

    def __post_init__(self) -> None:
        if not _SHEET.match(self.sheet.strip().upper()):
            raise BadReading(f"{self.sheet!r} is not a repair approval sheet number")

    def is_valid_on(self, day: date) -> bool:
        return self.valid_until is None or day <= self.valid_until

    def __str__(self) -> str:
        tail = f", expires {self.valid_until.isoformat()}" if self.valid_until else ""
        return f"{self.sheet} {self.category}{tail}"


@dataclass(frozen=True, slots=True)
class EngineeringOrder:
    """One question to the manufacturer and whatever came back."""

    reference: OrderId
    against: FindingId
    question: str
    raised: date
    state: OrderState = OrderState.RAISED
    sent: date | None = None
    answered: date | None = None
    approval: RepairApproval | None = None

    def __post_init__(self) -> None:
        if not self.question.strip():
            raise BadReading(f"{self.reference}: an order asks something")
        if self.state is OrderState.APPROVED and self.approval is None:
            raise BadReading(f"{self.reference}: an approved order carries an approval sheet")

    @property
    def is_open(self) -> bool:
        return self.state.is_open

    def days_waiting(self, today: date) -> int:
        """How long the question has been out, counting from when it was sent."""
        if self.sent is None:
            return (today - self.raised).days
        end = self.answered if self.answered is not None else today
        return (end - self.sent).days

    def needs_chasing(self, today: date) -> bool:
        """Sent, not acknowledged, and past the turnaround the manufacturer works to."""
        if self.state is not OrderState.SENT or self.sent is None:
            return False
        return today > self.sent + timedelta(days=ACKNOWLEDGE_WITHIN_DAYS)

    def _moved(self, state: OrderState, **changes: object) -> EngineeringOrder:
        if not self.state.can_move_to(state):
            raise OutOfSequence(f"{self.reference} cannot go from {self.state} to {state}")
        return replace(self, state=state, **changes)  # type: ignore[arg-type]

    def sent_on(self, day: date) -> EngineeringOrder:
        return self._moved(OrderState.SENT, sent=day)

    def acknowledged(self) -> EngineeringOrder:
        return self._moved(OrderState.ACKNOWLEDGED)

    def approved(self, approval: RepairApproval, on: date) -> EngineeringOrder:
        return self._moved(OrderState.APPROVED, approval=approval, answered=on)

    def rejected(self, on: date) -> EngineeringOrder:
        return self._moved(OrderState.REJECTED, answered=on)

    def withdrawn(self, on: date) -> EngineeringOrder:
        return self._moved(OrderState.WITHDRAWN, answered=on)

    def raised_again(self) -> EngineeringOrder:
        """A rejected question reworked and put back in, which happens often."""
        return self._moved(OrderState.RAISED, sent=None, answered=None, approval=None)

    def __str__(self) -> str:
        return f"{self.reference} against {self.against}, {self.state}"


class EngineeringQueue:
    """Every question out to the manufacturer, and which of them need chasing."""

    def __init__(self) -> None:
        self._orders: dict[OrderId, EngineeringOrder] = {}

    def raise_order(self, order: EngineeringOrder) -> None:
        if order.reference in self._orders:
            raise BadReading(f"{order.reference} has already been raised")
        self._orders[order.reference] = order

    def order(self, reference: OrderId) -> EngineeringOrder:
        try:
            return self._orders[reference]
        except KeyError:
            raise NotOnFile(f"{reference} is not on the queue") from None

    def replace_order(self, order: EngineeringOrder) -> None:
        if order.reference not in self._orders:
            raise NotOnFile(f"{order.reference} is not on the queue")
        self._orders[order.reference] = order

    def open_orders(self) -> tuple[EngineeringOrder, ...]:
        return tuple(one for one in self if one.is_open)

    def needing_chase(self, today: date) -> tuple[EngineeringOrder, ...]:
        return tuple(one for one in self if one.needs_chasing(today))

    def against(self, finding: FindingId) -> tuple[EngineeringOrder, ...]:
        return tuple(one for one in self if one.against == finding)

    def next_reference(self, year: int) -> OrderId:
        used = [one.sequence for one in self._orders if one.year == year]
        return OrderId(year, max(used, default=0) + 1)

    def __iter__(self) -> Iterator[EngineeringOrder]:
        return iter(sorted(self._orders.values(), key=lambda one: str(one.reference)))

    def __len__(self) -> int:
        return len(self._orders)

    def __str__(self) -> str:
        return f"{len(self._orders)} orders, {len(self.open_orders())} open"
