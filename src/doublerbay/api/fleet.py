"""The routes for the register: tails, their counters, and what they are flying at.

A counter reading is the most frequent write this service takes and the one
most likely to be wrong, because it arrives from a maintenance system that has
its own idea of what a flight cycle is. The refusal it needs is the one already
in the domain: a reading that runs a counter backwards is refused with the
reason, rather than accepted and left for a repeat inspection to be computed
off years later.

The forecast is a read and it is deliberately not stored. It is a straight line
off the recent rate and it changes every time a reading is posted, so a stored
one is a stale one within a week.
"""

from __future__ import annotations

from datetime import date

from fastapi import APIRouter, Depends
from pydantic import BaseModel

from doublerbay.api.findings import deps
from doublerbay.api.schemas import AircraftIn, AircraftOut, UsageIn, UsageOut, aircraft_out
from doublerbay.api.security import Action, require
from doublerbay.fleet.forecast import Forecast
from doublerbay.fleet.utilisation import Snapshot, UtilisationLog
from doublerbay.ids import Registration, Serial
from doublerbay.store.unit import UnitOfWork

router = APIRouter(prefix="/aircraft", tags=["fleet"])


class ReadingIn(BaseModel):
    """One posted counter reading."""

    taken: date
    usage: UsageIn


class ReadingOut(BaseModel):
    taken: date
    usage: UsageOut


class UtilisationOut(BaseModel):
    serial: str
    opened_on: date
    latest: ReadingOut
    readings: list[ReadingOut]


class RateOut(BaseModel):
    serial: str
    as_of: date
    cycles_a_day: str
    hours_a_day: str
    average_leg: str


def reading_out(snapshot: Snapshot) -> ReadingOut:
    return ReadingOut(
        taken=snapshot.taken,
        usage=UsageOut(cycles=snapshot.usage.cycles.count, hours=str(snapshot.usage.hours.hours)),
    )


@router.post("", response_model=AircraftOut, status_code=201)
async def enrol(
    body: AircraftIn, deps=Depends(deps), key=Depends(require(Action.AMEND_FLEET))
) -> AircraftOut:
    """Put a tail on the register and open its log at delivery."""
    aircraft = body.to_domain()
    with UnitOfWork(deps.engine) as unit:
        unit.aircraft.add(aircraft)
        unit.aircraft.open_log(UtilisationLog.opened(aircraft.serial, aircraft.delivered))
    return aircraft_out(aircraft)


@router.get("", response_model=list[AircraftOut])
async def register(in_service_on: date | None = None, deps=Depends(deps)) -> list[AircraftOut]:
    """Every tail on the register, or only the ones in service on a day."""
    with UnitOfWork(deps.engine) as unit:
        held = (
            unit.aircraft.in_service_on(in_service_on)
            if in_service_on is not None
            else unit.aircraft.all()
        )
        return [aircraft_out(one) for one in held]


@router.get("/{serial}", response_model=AircraftOut)
async def read_tail(serial: str, deps=Depends(deps)) -> AircraftOut:
    """One tail, by the serial the airframe keeps for life."""
    with UnitOfWork(deps.engine) as unit:
        return aircraft_out(unit.aircraft.get(Serial(serial)))


@router.get("-by-marks/{marks}", response_model=AircraftOut)
async def read_by_marks(marks: str, deps=Depends(deps)) -> AircraftOut:
    """Whichever aeroplane is wearing these marks now."""
    with UnitOfWork(deps.engine) as unit:
        return aircraft_out(unit.aircraft.by_registration(Registration.parse(marks)))


@router.post("/{serial}/readings", response_model=UtilisationOut, status_code=201)
async def post_reading(
    serial: str,
    body: ReadingIn,
    deps=Depends(deps),
    key=Depends(require(Action.POST_READING)),
) -> UtilisationOut:
    """Post a counter reading, refusing one that runs a counter backwards."""
    held = Serial(serial)
    with UnitOfWork(deps.engine) as unit:
        unit.aircraft.post_reading(held, Snapshot(body.taken, body.usage.to_domain()))
        unit.flush()
        return utilisation_of(unit, held)


@router.get("/{serial}/utilisation", response_model=UtilisationOut)
async def read_utilisation(serial: str, deps=Depends(deps)) -> UtilisationOut:
    """Every reading posted against one tail."""
    with UnitOfWork(deps.engine) as unit:
        return utilisation_of(unit, Serial(serial))


@router.get("/{serial}/rate", response_model=RateOut)
async def read_rate(serial: str, over: int = 365, deps=Depends(deps)) -> RateOut:
    """What the tail is flying at, taken off the recent stretch of its log."""
    with UnitOfWork(deps.engine) as unit:
        log = unit.aircraft.log_for(Serial(serial))
    forecast = Forecast.from_log(log, over)
    return RateOut(
        serial=serial,
        as_of=forecast.as_of,
        cycles_a_day=str(forecast.cycles_a_day),
        hours_a_day=str(forecast.hours_a_day),
        average_leg=str(forecast.flown.average_leg),
    )


def utilisation_of(unit: UnitOfWork, serial: Serial) -> UtilisationOut:
    log = unit.aircraft.log_for(serial)
    return UtilisationOut(
        serial=serial.number,
        opened_on=log.opened_on,
        latest=reading_out(log.latest),
        readings=[reading_out(one) for one in log.snapshots],
    )
