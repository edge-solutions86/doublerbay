"""The routes that grant, tighten and lift a deferral.

Granting is the one write on this service that changes what an aeroplane is
allowed to do, so it is the one guarded by the role rather than by the key. A
mechanic can raise a card at three in the morning and post a counter reading,
and cannot decide that the fleet keeps flying with the crack they just found.

The limit is not sent as four numbers. It arrives as the text the manual
prints, which is what the engineer writes on the sheet anyway, and it is
parsed on this side of the wire. A client that had to know that ninety days
and three months are different clocks would get it wrong, and the parse
refuses what it cannot read rather than defaulting.
"""

from __future__ import annotations

from datetime import date

from fastapi import APIRouter, Depends, Response
from pydantic import BaseModel

from doublerbay.api.deps import deps, reference_from
from doublerbay.api.schemas import UsageOut, read_enum
from doublerbay.api.security import Action, require
from doublerbay.disposition.deferral import Deferral, grant
from doublerbay.disposition.state import DispositionState
from doublerbay.ids import FindingId
from doublerbay.measure.interval import Interval
from doublerbay.store.unit import UnitOfWork
from doublerbay.tolerance.method import InspectionMethod

router = APIRouter(prefix="/deferrals", tags=["deferrals"])


class DeferralIn(BaseModel):
    """A deferral as the engineer's sheet fills it in."""

    against: str
    granted: date
    by: str
    method: str = InspectionMethod.DVI.value
    limit: str | None = None
    next_check: date | None = None
    reason: str = ""


class TightenIn(BaseModel):
    limit: str
    by: str
    reason: str = ""


class LiftIn(BaseModel):
    on: date


class DueOut(BaseModel):
    cycles: int | None
    hours: str | None
    on: date | None
    written: str


class DeferralOut(BaseModel):
    against: str
    granted: date
    by: str
    at_usage: UsageOut
    limit: str
    due: DueOut
    re_inspect_with: str
    reason: str


def deferral_out(deferral: Deferral) -> DeferralOut:
    due = deferral.due
    return DeferralOut(
        against=str(deferral.against),
        granted=deferral.granted,
        by=deferral.by,
        at_usage=UsageOut(
            cycles=deferral.at_usage.cycles.count, hours=str(deferral.at_usage.hours.hours)
        ),
        limit=str(deferral.limit),
        due=DueOut(
            cycles=due.cycles.count if due.cycles else None,
            hours=str(due.hours.hours) if due.hours else None,
            on=due.on,
            written=str(due),
        ),
        re_inspect_with=deferral.re_inspect_with.value,
        reason=deferral.reason,
    )


@router.post("", response_model=DeferralOut, status_code=201)
async def grant_deferral(
    body: DeferralIn, deps=Depends(deps), key=Depends(require(Action.DEFER))
) -> DeferralOut:
    """Defer a card, writing the limit and the disposition move in one go."""
    against = reference_from(body.against)
    with UnitOfWork(deps.engine) as unit:
        finding = unit.findings.latest(against)
        deferral = grant(
            against=against,
            granted=body.granted,
            at_usage=unit.aircraft.log_for(finding.serial).at(body.granted),
            by=body.by,
            method=read_enum(InspectionMethod, body.method, "way of inspecting"),
            engineer_set=Interval.parse(body.limit) if body.limit else None,
            next_check=body.next_check,
            reason=body.reason,
        )
        unit.deferrals.grant(deferral)
        record = unit.dispositions.get(against)
        unit.dispositions.save(
            record.moved_to(DispositionState.DEFERRED, body.granted, body.by, body.reason)
        )
    return deferral_out(deferral)


@router.get("", response_model=list[DeferralOut])
async def live(deps=Depends(deps)) -> list[DeferralOut]:
    """Every deferral still being flown against."""
    with UnitOfWork(deps.engine) as unit:
        return [deferral_out(one) for one in unit.deferrals.live()]


@router.get("/{reference}", response_model=DeferralOut)
async def read_deferral(reference: str, deps=Depends(deps)) -> DeferralOut:
    """The live deferral against one card."""
    with UnitOfWork(deps.engine) as unit:
        return deferral_out(unit.deferrals.get(reference_from(reference)))


@router.post("/{reference}/tighten", response_model=DeferralOut)
async def tighten(
    reference: str,
    body: TightenIn,
    deps=Depends(deps),
    key=Depends(require(Action.DEFER)),
) -> DeferralOut:
    """Cut a live deferral back to a shorter limit."""
    held: FindingId = reference_from(reference)
    with UnitOfWork(deps.engine) as unit:
        current = unit.deferrals.get(held)
        unit.deferrals.tighten(
            current.tightened_to(Interval.parse(body.limit), body.by, body.reason)
        )
        unit.flush()
        return deferral_out(unit.deferrals.get(held))


@router.post("/{reference}/lift", status_code=204, response_class=Response)
async def lift(
    reference: str,
    body: LiftIn,
    deps=Depends(deps),
    key=Depends(require(Action.DEFER)),
) -> Response:
    """Close a deferral because the damage under it has been dealt with."""
    with UnitOfWork(deps.engine) as unit:
        unit.deferrals.lift(reference_from(reference), body.on)
    return Response(status_code=204)
