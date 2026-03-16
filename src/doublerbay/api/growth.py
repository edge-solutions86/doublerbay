"""The growth routes, which answer how fast a crack actually moved.

One card at a time and a fleet wide list of the fast ones. The fleet list is
the one that matters: a crack growing faster than the analysis said it would
is the finding that changes an inspection programme, and nobody goes looking
for it card by card.

Only cracks come back. Everything else on this fleet is dispositioned on the
measurement it has now rather than on a trend, and offering a growth number
for a dent would invite somebody to plan against it.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from decimal import Decimal

from fastapi import APIRouter, Depends
from pydantic import BaseModel

from doublerbay.api.findings import deps, reference_from
from doublerbay.damage.kind import DamageKind
from doublerbay.errors import BadReading, DoublerbayError
from doublerbay.ids import Serial
from doublerbay.report.growth import Growth, growth_of
from doublerbay.store.unit import UnitOfWork

router = APIRouter(prefix="/growth", tags=["growth"])

# Above this, in inches per thousand cycles, somebody looks at the programme.
FAST = Decimal("0.05")


class StepOut(BaseModel):
    was: str
    now: str
    grew_by: str
    cycles: int
    measurable: bool
    rate: str | None


class GrowthOut(BaseModel):
    reference: str
    readings: int
    total: str
    has_grown: bool
    is_a_trend: bool
    worst_rate: str | None
    fast: bool
    steps: list[StepOut]


class FleetGrowthOut(BaseModel):
    on: date
    tracked: int
    fast: list[GrowthOut]


@dataclass(frozen=True, slots=True)
class _Rated:
    """A card and its growth, kept together while the list is sorted."""

    growth: Growth
    rate: Decimal


def step_out(step) -> StepOut:
    return StepOut(
        was=str(step.was.inches),
        now=str(step.now.inches),
        grew_by=str(step.grew_by.inches),
        cycles=step.cycles,
        measurable=step.is_measurable,
        rate=str(step.rate) if step.is_measurable else None,
    )


def growth_out(built: Growth) -> GrowthOut:
    worst = built.worst_rate
    return GrowthOut(
        reference=str(built.reference),
        readings=built.readings,
        total=str(built.total.inches),
        has_grown=built.has_grown,
        is_a_trend=built.is_a_trend,
        worst_rate=str(worst) if worst is not None else None,
        fast=worst is not None and worst > FAST,
        steps=[step_out(one) for one in built.steps],
    )


def growth_for(unit: UnitOfWork, reference) -> Growth:
    """One card's growth off its revisions.

    The kind is checked here rather than left to the first step. A card with
    one reading has no steps, so a dent would come back as a perfectly valid
    growth of nothing, and a client would take that as an answer.
    """
    revisions = unit.findings.history(reference)
    if not revisions:
        raise BadReading(f"{reference} has no revisions on file")
    kind = revisions[0].kind
    if kind is not DamageKind.CRACK:
        raise BadReading(f"{reference} is a {kind}, which is not tracked for growth")
    return growth_of(revisions)


@router.get("/{reference}", response_model=GrowthOut)
async def card_growth(reference: str, deps=Depends(deps)) -> GrowthOut:
    """How fast one crack has actually grown."""
    with UnitOfWork(deps.engine) as unit:
        return growth_out(growth_for(unit, reference_from(reference)))


@router.get("", response_model=FleetGrowthOut)
async def fleet_growth(on: date, deps=Depends(deps)) -> FleetGrowthOut:
    """Every crack on the fleet growing faster than the programme expects."""
    with UnitOfWork(deps.engine) as unit:
        tracked = unit.findings.of_kind(DamageKind.CRACK)
        rated = []
        for one in tracked:
            try:
                built = growth_for(unit, one.reference)
            except DoublerbayError:
                continue
            worst = built.worst_rate
            if worst is not None and worst > FAST:
                rated.append(_Rated(built, worst))
    rated.sort(key=lambda one: (-one.rate, str(one.growth.reference)))
    return FleetGrowthOut(
        on=on,
        tracked=len(tracked),
        fast=[growth_out(one.growth) for one in rated],
    )


@router.get("/aircraft/{serial}", response_model=list[GrowthOut])
async def tail_growth(serial: str, deps=Depends(deps)) -> list[GrowthOut]:
    """Every tracked crack on one aeroplane, fastest first."""
    with UnitOfWork(deps.engine) as unit:
        held = Serial(serial)
        unit.aircraft.get(held)
        built = [
            growth_for(unit, one.reference)
            for one in unit.findings.on_tail(held)
            if one.kind is DamageKind.CRACK
        ]
    built.sort(key=lambda one: (-(one.worst_rate or Decimal(-1)), str(one.reference)))
    return [growth_out(one) for one in built]
