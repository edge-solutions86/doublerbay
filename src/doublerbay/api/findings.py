"""The routes that raise, read and re measure damage cards.

Raising a card does two writes: the card and the disposition record that says
where it is in its life. They go in one transaction, because a card with no
disposition never appears on any report and nobody would notice for weeks.

The reference is allocated by the service and not sent by the caller. Two
mechanics filling in a form at the same time will both write the next number
they can see on the wall, and the desk would rather refuse a duplicate here
than find one in a fleet report six months later.
"""

from __future__ import annotations

from datetime import date

from fastapi import APIRouter, Depends
from pydantic import BaseModel

from doublerbay.api.deps import deps, reference_from
from doublerbay.api.schemas import (
    FindingIn,
    FindingOut,
    UsageIn,
    finding_out,
    measurements_in,
    read_enum,
)
from doublerbay.api.security import Action, require
from doublerbay.damage.kind import DamageKind
from doublerbay.disposition.state import DispositionState
from doublerbay.ids import Serial
from doublerbay.service.intake import next_reference, take_in
from doublerbay.service.measuring import re_measure
from doublerbay.store.unit import UnitOfWork

router = APIRouter(prefix="/findings", tags=["findings"])


class RevisionIn(BaseModel):
    """A card looked at again, with the new numbers on it."""

    found_on: date
    at_usage: UsageIn
    measurements: dict[str, object]


class SupersedeIn(BaseModel):
    by: str
    by_whom: str = "structures"


class RaisedOut(BaseModel):
    reference: str
    state: str
    corrosion_level: int | None = None
    must_be_reported: bool = False


@router.post("", response_model=RaisedOut, status_code=201)
async def raise_finding(
    body: FindingIn, deps=Depends(deps), key=Depends(require(Action.RAISE_CARD))
) -> RaisedOut:
    """Raise a damage card, with everything else raising one commits the desk to."""
    with UnitOfWork(deps.engine) as unit:
        reference = next_reference(unit, body.found_on)
        taken = take_in(unit, deps.book, deps.catalogue, body.to_domain(reference), body.urgent)
    return RaisedOut(
        reference=str(reference),
        state=DispositionState.RAISED.value,
        corrosion_level=taken.graded.level.value if taken.graded else None,
        must_be_reported=taken.must_be_reported,
    )


@router.get("/{reference}", response_model=FindingOut)
async def read_finding(reference: str, deps=Depends(deps)) -> FindingOut:
    """The latest revision of one card."""
    with UnitOfWork(deps.engine) as unit:
        return finding_out(unit.findings.latest(reference_from(reference)))


@router.get("/{reference}/history", response_model=list[FindingOut])
async def read_history(reference: str, deps=Depends(deps)) -> list[FindingOut]:
    """Every revision of one card, oldest first."""
    with UnitOfWork(deps.engine) as unit:
        return [finding_out(one) for one in unit.findings.history(reference_from(reference))]


class ReMeasuredOut(BaseModel):
    """A card looked at again, and how the answer moved."""

    finding: FindingOut
    was: str
    now: str
    got_worse: bool
    still_deferred: bool
    outgrew_its_deferral: bool


@router.post("/{reference}/revisions", response_model=ReMeasuredOut, status_code=201)
async def record_revision(
    reference: str,
    body: RevisionIn,
    deps=Depends(deps),
    key=Depends(require(Action.RAISE_CARD)),
) -> ReMeasuredOut:
    """Record a re measurement, and say what the new numbers changed.

    The response carries both dispositions rather than just the card. A client
    that got the card back would have to re assess it to find out whether
    anything moved, and half of them would not.
    """
    held = reference_from(reference)
    with UnitOfWork(deps.engine) as unit:
        current = unit.findings.latest(held)
        geometry = measurements_in(current.kind, dict(body.measurements))
        answer = re_measure(
            unit,
            deps.book,
            deps.catalogue,
            held,
            geometry,
            body.found_on,
            body.at_usage.to_domain(),
        )
        return ReMeasuredOut(
            finding=finding_out(answer.finding),
            was=answer.was.value,
            now=answer.now.value,
            got_worse=answer.got_worse,
            still_deferred=answer.still_deferred,
            outgrew_its_deferral=answer.outgrew_its_deferral,
        )


@router.post("/{reference}/supersede", response_model=FindingOut)
async def supersede(
    reference: str,
    body: SupersedeIn,
    deps=Depends(deps),
    key=Depends(require(Action.DISPOSITION)),
) -> FindingOut:
    """Close a card because another one now carries the damage."""
    with UnitOfWork(deps.engine) as unit:
        replacement = reference_from(body.by)
        unit.findings.latest(replacement)
        closed = unit.findings.supersede(reference_from(reference), replacement)
        record = unit.dispositions.get(reference_from(reference))
        unit.dispositions.save(
            record.moved_to(
                DispositionState.SUPERSEDED, deps.on(), body.by_whom, f"carried by {body.by}"
            )
        )
        return finding_out(closed)


tails = APIRouter(prefix="/aircraft", tags=["findings"])


@tails.get("/{serial}/findings", response_model=list[FindingOut])
async def on_tail(serial: str, open_only: bool = False, deps=Depends(deps)) -> list[FindingOut]:
    """Every card on one tail, or only the ones the desk still owns."""
    with UnitOfWork(deps.engine) as unit:
        held = Serial(serial)
        cards = unit.findings.open_on(held) if open_only else unit.findings.on_tail(held)
        return [finding_out(one) for one in cards]


kinds = APIRouter(prefix="/findings", tags=["findings"])


@kinds.get("-of-kind/{kind}", response_model=list[FindingOut])
async def of_kind(kind: str, deps=Depends(deps)) -> list[FindingOut]:
    """Every card of one kind across the fleet."""
    with UnitOfWork(deps.engine) as unit:
        held = read_enum(DamageKind, kind, "kind of damage")
        return [finding_out(one) for one in unit.findings.of_kind(held)]
