"""The read only routes that answer what is due and what is open.

None of these write and none of them are guarded beyond the read role. A
report that a mechanic cannot pull up on a phone in a hangar is a report that
gets replaced by a spreadsheet somebody keeps on a laptop, and then the desk
has two answers.

The day is a required parameter rather than defaulting to now. These numbers
end up in a slide for a meeting, and a slide that says what was due at the
moment somebody clicked is not reproducible. Making the day explicit means
the same request run twice gives the same answer.
"""

from __future__ import annotations

from datetime import date

from fastapi import APIRouter, Depends, Query
from pydantic import BaseModel

from doublerbay.api.deps import deps
from doublerbay.errors import BadReading
from doublerbay.ids import Serial
from doublerbay.report.desk import (
    DueReport,
    due_across_fleet,
    due_on_tail,
    grounded,
    open_across_fleet,
    open_on_tail,
)
from doublerbay.report.due import DueItem
from doublerbay.store.unit import UnitOfWork

router = APIRouter(prefix="/reports", tags=["reports"])

MAX_HORIZON_DAYS = 3650


class DueItemOut(BaseModel):
    serial: str
    against: str
    what: str
    due: str
    days: int
    clock: str
    overrun: bool


class DueReportOut(BaseModel):
    serial: str
    on: date
    measured_rate: bool
    grounded: bool
    items: list[DueItemOut]


class FleetDueOut(BaseModel):
    on: date
    tails: list[DueReportOut]
    overrun: int


class CountedOut(BaseModel):
    name: str
    count: int


class OpenTailOut(BaseModel):
    serial: str
    on: date
    total: int
    raised_recently: int
    by_kind: list[CountedOut]
    by_chapter: list[CountedOut]
    by_origin: list[CountedOut]


class OpenFleetOut(BaseModel):
    on: date
    total: int
    clean_tails: list[str]
    tails: list[OpenTailOut]


def item_out(item: DueItem) -> DueItemOut:
    return DueItemOut(
        serial=item.serial.number,
        against=str(item.against),
        what=item.what,
        due=str(item.due),
        days=item.margin.days,
        clock=item.margin.clock.value,
        overrun=item.is_overrun,
    )


def report_out(report: DueReport) -> DueReportOut:
    return DueReportOut(
        serial=report.serial.number,
        on=report.on,
        measured_rate=report.measured_rate,
        grounded=report.is_grounded,
        items=[item_out(one) for one in report.items],
    )


def counted_out(pairs) -> list[CountedOut]:
    return [CountedOut(name=str(name), count=count) for name, count in pairs]


def open_out(counted) -> OpenTailOut:
    return OpenTailOut(
        serial=counted.serial.number,
        on=counted.on,
        total=counted.total,
        raised_recently=counted.raised_recently,
        by_kind=counted_out(counted.by_kind),
        by_chapter=counted_out(counted.by_chapter),
        by_origin=counted_out(counted.by_origin),
    )


def checked_horizon(within: int | None) -> int | None:
    """Refuse a horizon this desk would not plan against."""
    if within is None:
        return None
    if within < 0:
        raise BadReading("a horizon is a count of days ahead")
    if within > MAX_HORIZON_DAYS:
        raise BadReading(f"this desk does not plan past {MAX_HORIZON_DAYS} days")
    return within


@router.get("/due", response_model=FleetDueOut)
async def fleet_due(
    on: date, within: int | None = Query(default=None), deps=Depends(deps)
) -> FleetDueOut:
    """What falls due across the register, worst tail first."""
    horizon = checked_horizon(within)
    with UnitOfWork(deps.engine) as unit:
        reports = due_across_fleet(unit, on, horizon)
    return FleetDueOut(
        on=on,
        tails=[report_out(one) for one in reports],
        overrun=sum(len(one.overrun) for one in reports),
    )


@router.get("/due/{serial}", response_model=DueReportOut)
async def tail_due(
    serial: str, on: date, within: int | None = Query(default=None), deps=Depends(deps)
) -> DueReportOut:
    """What falls due on one tail."""
    horizon = checked_horizon(within)
    with UnitOfWork(deps.engine) as unit:
        return report_out(due_on_tail(unit, Serial(serial), on, horizon))


@router.get("/grounded", response_model=FleetDueOut)
async def fleet_grounded(on: date, deps=Depends(deps)) -> FleetDueOut:
    """The tails with something already past its limit."""
    with UnitOfWork(deps.engine) as unit:
        reports = grounded(unit, on)
    return FleetDueOut(
        on=on,
        tails=[report_out(one) for one in reports],
        overrun=sum(len(one.overrun) for one in reports),
    )


@router.get("/open", response_model=OpenFleetOut)
async def fleet_open(on: date, recent: int = 30, deps=Depends(deps)) -> OpenFleetOut:
    """How much damage is open across the register."""
    with UnitOfWork(deps.engine) as unit:
        counted = open_across_fleet(unit, on, recent)
    return OpenFleetOut(
        on=on,
        total=counted.total,
        clean_tails=[one.number for one in counted.clean_tails],
        tails=[open_out(one) for one in counted.tails],
    )


@router.get("/open/{serial}", response_model=OpenTailOut)
async def tail_open(serial: str, on: date, recent: int = 30, deps=Depends(deps)) -> OpenTailOut:
    """How much damage is open on one tail, by kind and by chapter."""
    with UnitOfWork(deps.engine) as unit:
        return open_out(open_on_tail(unit, Serial(serial), on, recent))
