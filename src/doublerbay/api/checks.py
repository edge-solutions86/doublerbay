"""The work pack route, which is the one planning actually integrates with.

Everything else on this service answers a question about one record. This
answers a question about one aeroplane and one week, and it is the only route
that reaches across three registers to do it. That is deliberate: the join
belongs on this side of the wire, because a planning system that had to make
it would make it slightly differently every release.

The pack comes back in three lists rather than one, for the same reason the
command line prints it in three blocks. The consumer is going to route the
inspections to the shop and the deferrals to the engineer, and flattening
them means the consumer has to unflatten them.
"""

from __future__ import annotations

from datetime import date

from fastapi import APIRouter, Depends
from pydantic import BaseModel

from doublerbay.api.deps import deps
from doublerbay.errors import BadReading
from doublerbay.ids import Serial
from doublerbay.report.workpack import PackLine, WorkPack, build, references_on
from doublerbay.store.unit import UnitOfWork

router = APIRouter(prefix="/checks", tags=["checks"])

# The furthest ahead a check will reach for work. A year of pulled forward
# tasks is not a check, it is a heavy visit somebody has not planned yet.
MAX_PULL_FORWARD_DAYS = 365


class LineOut(BaseModel):
    what: str
    reference: str
    detail: str
    overrun: bool


class PackOut(BaseModel):
    serial: str
    opens: date
    closes: date
    days: int
    total: int
    overrun: int
    deferrals: list[LineOut]
    inspections: list[LineOut]
    waiting: list[LineOut]
    references: list[str]


def line_out(line: PackLine) -> LineOut:
    return LineOut(
        what=line.what,
        reference=line.reference,
        detail=line.detail,
        overrun=line.overrun,
    )


def pack_out(pack: WorkPack) -> PackOut:
    return PackOut(
        serial=pack.serial.number,
        opens=pack.opens,
        closes=pack.closes,
        days=pack.days,
        total=pack.total,
        overrun=len(pack.overrun),
        deferrals=[line_out(one) for one in pack.deferrals],
        inspections=[line_out(one) for one in pack.inspections],
        waiting=[line_out(one) for one in pack.waiting],
        references=list(references_on(pack)),
    )


def checked_pull(within: int | None) -> int | None:
    """Refuse a reach this desk would not call a check."""
    if within is None:
        return None
    if within < 0:
        raise BadReading("a check reaches forward, not back")
    if within > MAX_PULL_FORWARD_DAYS:
        raise BadReading(f"a check does not pull work in from past {MAX_PULL_FORWARD_DAYS} days")
    return within


@router.get("/{serial}/pack", response_model=PackOut)
async def work_pack(
    serial: str, opens: date, closes: date, within: int | None = None, deps=Depends(deps)
) -> PackOut:
    """Everything one tail owes the desk between two days."""
    reach = checked_pull(within)
    with UnitOfWork(deps.engine) as unit:
        held = Serial(serial)
        unit.aircraft.get(held)
        return pack_out(build(unit, held, opens, closes, reach))
