"""The routes that draw a repair, fit it, and take it off again.

A repair is not sent in as a drawing. The caller says how big the hole is and
where, and the service works out the doubler, because the sizing rules are the
whole point of having a service. A client that could post its own gauge and
fastener pitch would be a client that could post a repair the manual does not
allow, and the desk would find out at the audit.

So the request is small: which card, how big, and whether it is temporary. The
response is the whole drawing, because that is what goes on the sheet the shop
works from.
"""

from __future__ import annotations

from datetime import date

from fastapi import APIRouter, Depends, Response
from pydantic import BaseModel

from doublerbay.api.findings import deps, reference_from
from doublerbay.api.schemas import inches
from doublerbay.api.security import Action, require
from doublerbay.assess.placement import place
from doublerbay.grid.ata import Reference
from doublerbay.grid.surface import Offset
from doublerbay.ids import RepairId, Serial
from doublerbay.measure.fastener import FastenerSize
from doublerbay.repair.record import RepairRecord
from doublerbay.repair.scheme import select_scheme
from doublerbay.service.repairs import embody, remove
from doublerbay.store.context import context_for
from doublerbay.store.unit import UnitOfWork

router = APIRouter(prefix="/repairs", tags=["repairs"])


class DoublerOut(BaseModel):
    """The drawing, in the units the shop measures in."""

    material: str
    gauge: str
    along: str
    around: str
    fastener_dash: int
    fastener_count: int
    rows_each_side: int
    per_row: int
    pitch: str
    row_spacing: str
    edge_distance: str


class RepairOut(BaseModel):
    reference: str
    part: str
    against: str
    form: str
    category: str
    sealed: bool
    page: str
    filler: str | None
    centre_along: str
    centre_around: str
    embodied: date
    removed: date | None
    needs_inspection: bool
    doubler: DoublerOut


class EmbodyIn(BaseModel):
    """A repair as the shop asks for it, which is a hole and a place."""

    against: str
    sequence: int
    along: str
    around: str
    centre_along: str
    centre_around: str
    embodied: date
    by: str
    page: str = "53-40-01"
    fastener_dash: int | None = None
    temporary: bool = False


class EmbodiedOut(BaseModel):
    repair: RepairOut
    card_state: str
    deferral_lifted: bool
    clashes: list[list[str]]


class RemoveIn(BaseModel):
    on: date


def doubler_out(record: RepairRecord) -> DoublerOut:
    plan = record.scheme.doubler
    return DoublerOut(
        material=str(plan.material),
        gauge=str(plan.gauge.inches),
        along=str(plan.along.inches),
        around=str(plan.around.inches),
        fastener_dash=plan.size.dash,
        fastener_count=plan.fastener_count,
        rows_each_side=plan.rows_each_side,
        per_row=plan.per_row,
        pitch=str(plan.pitch.inches),
        row_spacing=str(plan.row_spacing.inches),
        edge_distance=str(plan.edge_distance.inches),
    )


def repair_out(record: RepairRecord) -> RepairOut:
    scheme = record.scheme
    return RepairOut(
        reference=str(record.reference),
        part=str(record.part),
        against=str(record.against),
        form=scheme.form.value,
        category=scheme.category.value,
        sealed=scheme.sealed,
        page=str(scheme.reference),
        filler=str(scheme.filler.inches) if scheme.filler else None,
        centre_along=str(record.centre.along.inches),
        centre_around=str(record.centre.around.inches),
        embodied=record.embodied,
        removed=record.removed,
        needs_inspection=record.needs_inspection,
        doubler=doubler_out(record),
    )


@router.post("", response_model=EmbodiedOut, status_code=201)
async def draw_and_fit(
    body: EmbodyIn, deps=Depends(deps), key=Depends(require(Action.DISPOSITION))
) -> EmbodiedOut:
    """Work out the repair for a hole, fit it, and move the card behind it."""
    against = reference_from(body.against)
    with UnitOfWork(deps.engine) as unit:
        finding = unit.findings.latest(against)
        tail = context_for(unit, deps.catalogue, finding.serial, body.embodied)
        placement = place(tail, finding)
        scheme = select_scheme(
            inches(body.along, "repair length"),
            inches(body.around, "repair width"),
            placement,
            Reference.parse(body.page),
            FastenerSize(body.fastener_dash) if body.fastener_dash else None,
            body.temporary,
        )
        record = RepairRecord(
            reference=RepairId(placement.chapter.value, body.sequence),
            part=placement.part,
            against=against,
            scheme=scheme,
            centre=Offset(
                inches(body.centre_along, "offset"), inches(body.centre_around, "offset")
            ),
            embodied=body.embodied,
            at_usage=tail.flown,
        )
        answer = embody(unit, record, finding.serial, body.by, body.embodied)
        return EmbodiedOut(
            repair=repair_out(record),
            card_state=answer.state.value,
            deferral_lifted=answer.deferral_lifted,
            clashes=[[str(one), str(other)] for one, other in answer.clashes],
        )


@router.get("/aircraft/{serial}", response_model=list[RepairOut])
async def on_tail(serial: str, fitted_only: bool = True, deps=Depends(deps)) -> list[RepairOut]:
    """Every repair on one aeroplane, still fitted or ever fitted."""
    with UnitOfWork(deps.engine) as unit:
        held = Serial(serial)
        unit.aircraft.get(held)
        return [repair_out(one) for one in unit.repairs.on_tail(held, fitted_only)]


@router.get("/{chapter}/{sequence}", response_model=RepairOut)
async def read_repair(chapter: int, sequence: int, deps=Depends(deps)) -> RepairOut:
    """One repair, with its whole drawing."""
    with UnitOfWork(deps.engine) as unit:
        return repair_out(unit.repairs.get(RepairId(chapter, sequence)))


@router.post("/{chapter}/{sequence}/remove", status_code=204, response_class=Response)
async def take_off(
    chapter: int,
    sequence: int,
    body: RemoveIn,
    deps=Depends(deps),
    key=Depends(require(Action.DISPOSITION)),
) -> Response:
    """Take a repair off, which does not reopen the card it answered."""
    with UnitOfWork(deps.engine) as unit:
        remove(unit, RepairId(chapter, sequence), body.on)
    return Response(status_code=204)
