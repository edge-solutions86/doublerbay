"""The routes that answer what happens to a damage, as of a date.

An assessment is a read and it is never stored. It is a function of the card,
the aeroplane, the structure and the revision of the book in force on the day,
and every one of those moves. Storing the answer means serving a stale one
after the next revision lands, which is the exact failure the whole revision
model exists to prevent.

The date is a parameter for the same reason. Asking what this was assessed as
in March and asking what it would be assessed as today are different questions
and the desk asks both, so the route takes the day rather than assuming now.
"""

from __future__ import annotations

from datetime import date

from fastapi import APIRouter, Depends
from pydantic import BaseModel

from doublerbay.api.deps import deps, reference_from
from doublerbay.api.schemas import AssessmentOut, assessment_out
from doublerbay.assess.assessor import assess, worst_disposition
from doublerbay.assess.panel import assess_by_panel
from doublerbay.ids import Serial
from doublerbay.store.context import context_for
from doublerbay.store.unit import UnitOfWork

router = APIRouter(tags=["assessment"])


class PanelAssessmentOut(BaseModel):
    """Every card on one panel, assessed together."""

    part: str
    card_count: int
    damage_count: int
    disposition: str
    cards: list[AssessmentOut]


class TailAssessmentOut(BaseModel):
    serial: str
    on: date
    disposition: str
    panels: list[PanelAssessmentOut]


@router.get("/findings/{reference}/assessment", response_model=AssessmentOut)
async def assess_one(reference: str, on: date | None = None, deps=Depends(deps)) -> AssessmentOut:
    """Walk one card through the book as it stood on a day."""
    day = on if on is not None else deps.on()
    with UnitOfWork(deps.engine) as unit:
        finding = unit.findings.latest(reference_from(reference))
        tail = context_for(unit, deps.catalogue, finding.serial, day)
        return assessment_out(assess(deps.book, tail, finding))


@router.get("/aircraft/{serial}/assessment", response_model=TailAssessmentOut)
async def assess_tail(serial: str, on: date | None = None, deps=Depends(deps)) -> TailAssessmentOut:
    """Assess every open card on one tail, panel by panel."""
    day = on if on is not None else deps.on()
    held = Serial(serial)
    with UnitOfWork(deps.engine) as unit:
        tail = context_for(unit, deps.catalogue, held, day)
        cards = unit.findings.open_on(held)

    if not cards:
        return TailAssessmentOut(
            serial=held.number, on=day, disposition="allowable as it stands", panels=[]
        )

    panels = assess_by_panel(deps.book, tail, cards)
    every = tuple(one for panel in panels for one in panel.assessments)
    return TailAssessmentOut(
        serial=held.number,
        on=day,
        disposition=worst_disposition(every).value,
        panels=[
            PanelAssessmentOut(
                part=str(panel.part),
                card_count=panel.card_count,
                damage_count=panel.damage_count,
                disposition=panel.disposition.value,
                cards=[assessment_out(one) for one in panel.assessments],
            )
            for panel in panels
        ],
    )
