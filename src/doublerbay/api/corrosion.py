"""The corrosion programme's own routes, kept apart from the damage routes.

A corrosion finding lives twice: as a damage card the desk closes when the
metal is blended out, and as a programme entry that never closes. These routes
are the second one. Nothing here can raise a finding, because a programme
entry with no card behind it is a number somebody typed.

The reporting clock is the sharp end. A level three finding has three days to
reach the authority, and the only useful thing a service can do about that is
make the list of ones that have not gone out impossible to miss.
"""

from __future__ import annotations

from datetime import date

from fastapi import APIRouter, Depends
from pydantic import BaseModel

from doublerbay.api.deps import deps, reference_from
from doublerbay.api.schemas import read_enum
from doublerbay.api.security import Action, require
from doublerbay.errors import BadReading
from doublerbay.ids import Serial
from doublerbay.report.corrosion import ProgrammeReport, programme_report
from doublerbay.store.unit import UnitOfWork
from doublerbay.tolerance.corrosion import CorrosionEntry, CorrosionLevel

router = APIRouter(prefix="/corrosion", tags=["corrosion"])


class EntryOut(BaseModel):
    part: str
    level: int
    found_on: date
    must_be_reported: bool
    report_by: date | None


class RepeatOut(BaseModel):
    part: str
    times: int
    first: date
    latest: date
    years_between: str
    persistent: bool


class LevelCountOut(BaseModel):
    level: int
    count: int


class ProgrammeOut(BaseModel):
    serial: str
    since: date
    until: date
    total: int
    above_level_one: int
    coping: bool
    escalation_rate: str
    denominator: str
    by_level: list[LevelCountOut]
    repeats: list[RepeatOut]


class ReportedIn(BaseModel):
    on: date


def entry_out(entry: CorrosionEntry) -> EntryOut:
    return EntryOut(
        part=str(entry.part),
        level=entry.level.value,
        found_on=entry.found_on,
        must_be_reported=entry.must_be_reported,
        report_by=entry.report_by,
    )


def programme_out(report: ProgrammeReport) -> ProgrammeOut:
    return ProgrammeOut(
        serial=report.serial.number,
        since=report.since,
        until=report.until,
        total=report.total,
        above_level_one=report.above_level_one,
        coping=report.is_coping,
        escalation_rate=str(report.escalation_rate),
        denominator=report.denominator,
        by_level=[
            LevelCountOut(level=level.value, count=count) for level, count in report.by_level
        ],
        repeats=[
            RepeatOut(
                part=str(one.part),
                times=one.times,
                first=one.first,
                latest=one.latest,
                years_between=str(one.years_between),
                persistent=one.is_persistent,
            )
            for one in report.repeats
        ],
    )


def level_from(value: int) -> CorrosionLevel:
    return read_enum(CorrosionLevel, value, "corrosion level")  # type: ignore[arg-type]


@router.get("/unreported", response_model=list[EntryOut])
async def unreported(deps=Depends(deps)) -> list[EntryOut]:
    """Level three findings the authority has not been told about yet."""
    with UnitOfWork(deps.engine) as unit:
        return [entry_out(one) for one in unit.corrosion.unreported()]


@router.get("/level/{level}", response_model=list[EntryOut])
async def at_level(level: int, deps=Depends(deps)) -> list[EntryOut]:
    """Every finding across the fleet graded at one level."""
    with UnitOfWork(deps.engine) as unit:
        return [entry_out(one) for one in unit.corrosion.at_level(level_from(level))]


@router.get("/aircraft/{serial}", response_model=list[EntryOut])
async def on_tail(serial: str, deps=Depends(deps)) -> list[EntryOut]:
    """Every graded finding on one aeroplane, oldest first."""
    with UnitOfWork(deps.engine) as unit:
        held = Serial(serial)
        unit.aircraft.get(held)
        return [entry_out(one) for one in unit.corrosion.on_tail(held)]


@router.get("/aircraft/{serial}/programme", response_model=ProgrammeOut)
async def programme(serial: str, since: date, until: date, deps=Depends(deps)) -> ProgrammeOut:
    """One tail's programme picture over a window."""
    if until < since:
        raise BadReading("a reporting window ends after it starts")
    with UnitOfWork(deps.engine) as unit:
        held = Serial(serial)
        unit.aircraft.get(held)
        entries = unit.corrosion.on_tail(held)
    return programme_out(programme_report(held, entries, since, until))


@router.post("/{reference}/reported", response_model=list[EntryOut])
async def mark_reported(
    reference: str,
    body: ReportedIn,
    deps=Depends(deps),
    key=Depends(require(Action.DISPOSITION)),
) -> list[EntryOut]:
    """Record that a level three finding went out to the authority."""
    with UnitOfWork(deps.engine) as unit:
        unit.corrosion.mark_reported(reference_from(reference), body.on)
        unit.flush()
        return [entry_out(one) for one in unit.corrosion.unreported()]
