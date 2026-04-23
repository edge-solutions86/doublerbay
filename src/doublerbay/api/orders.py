"""The routes for questions out to the manufacturer.

Every state move is its own route rather than one endpoint taking a target
state. A caller that can post any state can post the ones the machine would
refuse, and the refusal then reads as a validation error on a field instead of
as what it is, which is that the order has not been sent yet.

The chase list is the one anybody actually opens. An order sitting unanswered
for four months is a card the desk cannot close, and nothing else on this
service will surface it.
"""

from __future__ import annotations

from datetime import date

from fastapi import APIRouter, Depends
from pydantic import BaseModel

from doublerbay.api.deps import deps, order_from, reference_from
from doublerbay.api.paging import Page, paged, window
from doublerbay.api.schemas import read_enum
from doublerbay.api.security import Action, require
from doublerbay.disposition.engineering import EngineeringOrder, RepairApproval
from doublerbay.repair.scheme import RepairCategory
from doublerbay.service.orders import (
    acknowledge,
    approve,
    raise_order,
    reject,
    send,
    withdraw,
)
from doublerbay.store.unit import UnitOfWork

router = APIRouter(prefix="/orders", tags=["orders"])


class ApprovalOut(BaseModel):
    sheet: str
    category: str
    conditions: list[str]
    valid_until: date | None


class OrderOut(BaseModel):
    reference: str
    against: str
    question: str
    raised: date
    state: str
    sent: date | None
    answered: date | None
    open: bool
    approval: ApprovalOut | None


class ChasedOut(BaseModel):
    order: OrderOut
    days_waiting: int


class RaiseIn(BaseModel):
    against: str
    question: str
    raised: date
    by: str


class SentIn(BaseModel):
    on: date


class AnsweredIn(BaseModel):
    on: date
    by: str


class ApproveIn(BaseModel):
    on: date
    by: str
    sheet: str
    category: str
    conditions: list[str] = []
    valid_until: date | None = None

    def to_domain(self) -> RepairApproval:
        return RepairApproval(
            sheet=self.sheet,
            category=read_enum(RepairCategory, self.category, "repair category"),
            conditions=tuple(self.conditions),
            valid_until=self.valid_until,
        )


def order_out(order: EngineeringOrder) -> OrderOut:
    approval = order.approval
    return OrderOut(
        reference=str(order.reference),
        against=str(order.against),
        question=order.question,
        raised=order.raised,
        state=order.state.value,
        sent=order.sent,
        answered=order.answered,
        open=order.is_open,
        approval=None
        if approval is None
        else ApprovalOut(
            sheet=approval.sheet,
            category=approval.category.value,
            conditions=list(approval.conditions),
            valid_until=approval.valid_until,
        ),
    )


@router.post("", response_model=OrderOut, status_code=201)
async def raise_one(
    body: RaiseIn, deps=Depends(deps), key=Depends(require(Action.DISPOSITION))
) -> OrderOut:
    """Send a card out and hold it until the answer lands."""
    with UnitOfWork(deps.engine) as unit:
        raised = raise_order(
            unit, reference_from(body.against), body.question, body.raised, body.by
        )
        return order_out(raised.order)


@router.get("", response_model=Page[OrderOut])
async def open_orders(
    offset: int | None = None, limit: int | None = None, deps=Depends(deps)
) -> dict:
    """Everything still waiting on an answer, oldest first."""
    asked = window(offset, limit)
    with UnitOfWork(deps.engine) as unit:
        return paged(unit.orders.open_orders(), asked, order_out)


@router.get("/chase", response_model=list[ChasedOut])
async def chase(on: date, deps=Depends(deps)) -> list[ChasedOut]:
    """The ones that have been out too long, longest first."""
    with UnitOfWork(deps.engine) as unit:
        return [
            ChasedOut(order=order_out(one), days_waiting=one.days_waiting(on))
            for one in unit.orders.needing_chase(on)
        ]


@router.get("/{reference}", response_model=OrderOut)
async def read_order(reference: str, deps=Depends(deps)) -> OrderOut:
    """One order and whatever came back."""
    with UnitOfWork(deps.engine) as unit:
        return order_out(unit.orders.get(order_from(reference)))


@router.post("/{reference}/sent", response_model=OrderOut)
async def mark_sent(
    reference: str, body: SentIn, deps=Depends(deps), key=Depends(require(Action.DISPOSITION))
) -> OrderOut:
    """Record the day it actually went, which is not the day it was written."""
    with UnitOfWork(deps.engine) as unit:
        return order_out(send(unit, order_from(reference), body.on))


@router.post("/{reference}/acknowledged", response_model=OrderOut)
async def mark_acknowledged(
    reference: str, deps=Depends(deps), key=Depends(require(Action.DISPOSITION))
) -> OrderOut:
    """Record that the manufacturer has it, which stops the chase clock."""
    with UnitOfWork(deps.engine) as unit:
        return order_out(acknowledge(unit, order_from(reference)))


@router.post("/{reference}/approved", response_model=OrderOut)
async def mark_approved(
    reference: str, body: ApproveIn, deps=Depends(deps), key=Depends(require(Action.DISPOSITION))
) -> OrderOut:
    """Record an approval and put the card back where a repair can be raised."""
    with UnitOfWork(deps.engine) as unit:
        return order_out(approve(unit, order_from(reference), body.to_domain(), body.on, body.by))


@router.post("/{reference}/rejected", response_model=OrderOut)
async def mark_rejected(
    reference: str, body: AnsweredIn, deps=Depends(deps), key=Depends(require(Action.DISPOSITION))
) -> OrderOut:
    """Record a rejection, which still releases the card."""
    with UnitOfWork(deps.engine) as unit:
        return order_out(reject(unit, order_from(reference), body.on, body.by))


@router.post("/{reference}/withdrawn", response_model=OrderOut)
async def mark_withdrawn(
    reference: str, body: AnsweredIn, deps=Depends(deps), key=Depends(require(Action.DISPOSITION))
) -> OrderOut:
    """Take a question back, releasing the card the same way an answer does."""
    with UnitOfWork(deps.engine) as unit:
        return order_out(withdraw(unit, order_from(reference), body.on, body.by))
