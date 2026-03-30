"""The structure audit routes, over the fleet and over one tail.

The fleet route is the one that gets opened once a quarter and then argued
about, so it comes back sorted worst first and carries the counts that decide
the argument rather than the whole picture. Somebody who wants the whole
picture asks for the tail.

Nothing here writes. An audit that could change a record would be an audit
somebody could make come out right.
"""

from __future__ import annotations

from datetime import date

from fastapi import APIRouter, Depends
from pydantic import BaseModel

from doublerbay.api.deps import deps
from doublerbay.ids import Serial
from doublerbay.report.audit import StructureAudit, audit
from doublerbay.store.unit import UnitOfWork

router = APIRouter(prefix="/audit", tags=["audit"])


class PanelLoadOut(BaseModel):
    part: str
    repairs: int
    temporary: int
    crowded: bool


class TemporaryOut(BaseModel):
    reference: str
    part: str
    embodied: date
    months_fitted: str
    overdue: bool


class AuditOut(BaseModel):
    serial: str
    on: date
    total_repairs: int
    clean: bool
    panels: list[PanelLoadOut]
    temporary: list[TemporaryOut]
    clashing: list[list[str]]


class FleetLineOut(BaseModel):
    serial: str
    total_repairs: int
    crowded_panels: int
    overdue_temporary: int
    clashes: int
    clean: bool


class FleetAuditOut(BaseModel):
    on: date
    tails: list[FleetLineOut]
    flagged: int


def audit_out(built: StructureAudit) -> AuditOut:
    return AuditOut(
        serial=built.serial.number,
        on=built.on,
        total_repairs=built.total_repairs,
        clean=built.is_clean,
        panels=[
            PanelLoadOut(
                part=str(one.part),
                repairs=one.repairs,
                temporary=one.temporary,
                crowded=one.is_crowded,
            )
            for one in built.panels
        ],
        temporary=[
            TemporaryOut(
                reference=str(one.reference),
                part=str(one.part),
                embodied=one.embodied,
                months_fitted=str(one.months_fitted),
                overdue=one.is_overdue,
            )
            for one in built.temporary
        ],
        clashing=[[str(one), str(other)] for one, other in built.clashing],
    )


def line_out(built: StructureAudit) -> FleetLineOut:
    return FleetLineOut(
        serial=built.serial.number,
        total_repairs=built.total_repairs,
        crowded_panels=len(built.crowded),
        overdue_temporary=len(built.overdue),
        clashes=len(built.clashing),
        clean=built.is_clean,
    )


def audit_tail(unit: UnitOfWork, serial: Serial, on: date) -> StructureAudit:
    """One tail's audit, off the repairs still fitted to it."""
    unit.aircraft.get(serial)
    return audit(serial, unit.repairs.on_tail(serial), on)


@router.get("/{serial}", response_model=AuditOut)
async def tail_audit(serial: str, on: date, deps=Depends(deps)) -> AuditOut:
    """What one aeroplane's structure is carrying."""
    with UnitOfWork(deps.engine) as unit:
        return audit_out(audit_tail(unit, Serial(serial), on))


@router.get("", response_model=FleetAuditOut)
async def fleet_audit(on: date, deps=Depends(deps)) -> FleetAuditOut:
    """The same picture across the register, worst first."""
    with UnitOfWork(deps.engine) as unit:
        built = [audit_tail(unit, one.serial, on) for one in unit.aircraft.in_service_on(on)]
    built.sort(
        key=lambda one: (
            -len(one.clashing),
            -len(one.overdue),
            -len(one.crowded),
            -one.total_repairs,
            one.serial.number,
        )
    )
    return FleetAuditOut(
        on=on,
        tails=[line_out(one) for one in built],
        flagged=sum(1 for one in built if not one.is_clean),
    )
