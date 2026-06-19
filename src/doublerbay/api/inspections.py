"""The routes for the repeat inspections the fitted repairs carry.

There is no route that creates one. A repeat inspection exists because a
repair of a certain category went on a certain panel, and letting a caller
post one directly would let somebody put a task on the fleet that no repair
justifies, or worse, quietly not put one on a repair that needs it.

Signing off is the write this actually needs, and it takes the counters as
well as the date. A sign off with a date and no reading resets the calendar
clock and leaves the cycles clock where it was, so the next one falls due at
the wrong time and nobody notices for two years.
"""

from __future__ import annotations

from datetime import date

from fastapi import APIRouter, Depends
from pydantic import BaseModel

from doublerbay.api.deps import deps, tail_on
from doublerbay.api.schemas import UsageIn, UsageOut
from doublerbay.api.security import Action, require
from doublerbay.ids import RepairId
from doublerbay.store.unit import UnitOfWork
from doublerbay.tolerance.programme import InspectionTask

router = APIRouter(prefix="/inspections", tags=["inspections"])


class DueOut(BaseModel):
    cycles: int | None
    hours: str | None
    on: date | None
    written: str


class TaskOut(BaseModel):
    against: str
    part: str
    method: str
    threshold: str
    repeat: str | None
    embodied: date
    at_embodiment: UsageOut
    last_done: date | None
    at_last_done: UsageOut | None
    one_off: bool
    due: DueOut | None


class SignOffIn(BaseModel):
    on: date
    flown: UsageIn


def usage_out(flown) -> UsageOut:
    return UsageOut(cycles=flown.cycles.count, hours=str(flown.hours.hours))


def task_out(task: InspectionTask) -> TaskOut:
    return TaskOut(
        against=str(task.against),
        part=str(task.part),
        method=task.schedule.method.value,
        threshold=str(task.schedule.threshold),
        repeat=str(task.schedule.repeat) if task.schedule.repeat else None,
        embodied=task.embodied,
        at_embodiment=usage_out(task.at_embodiment),
        last_done=task.last_done,
        at_last_done=usage_out(task.at_last_done) if task.at_last_done else None,
        one_off=task.is_a_one_off,
        due=_due_out(task),
    )


def _due_out(task: InspectionTask) -> DueOut | None:
    """When it next falls due, or nothing for a one off already done."""
    if task.has_been_done and task.is_a_one_off:
        return None
    due = task.due()
    return DueOut(
        cycles=due.cycles.count if due.cycles else None,
        hours=str(due.hours.hours) if due.hours else None,
        on=due.on,
        written=str(due),
    )


@router.get("/aircraft/{serial}", response_model=list[TaskOut])
async def on_tail(serial: str, deps=Depends(deps)) -> list[TaskOut]:
    """Every repeat inspection on one aeroplane, oldest repair first."""
    with UnitOfWork(deps.engine) as unit:
        return [task_out(one) for one in unit.inspections.on_tail(tail_on(unit, serial))]


@router.get("/aircraft/{serial}/never-done", response_model=list[TaskOut])
async def never_done(serial: str, deps=Depends(deps)) -> list[TaskOut]:
    """The ones nobody has signed off yet, where the threshold still bites."""
    with UnitOfWork(deps.engine) as unit:
        return [task_out(one) for one in unit.inspections.never_done(tail_on(unit, serial))]


@router.get("/aircraft/{serial}/due", response_model=list[TaskOut])
async def due_on(serial: str, on: date, deps=Depends(deps)) -> list[TaskOut]:
    """Everything on one tail that has come due as of a day."""
    with UnitOfWork(deps.engine) as unit:
        held = tail_on(unit, serial)
        flown = unit.aircraft.log_for(held).at(on)
        return [task_out(one) for one in unit.inspections.due_on(held, flown, on)]


@router.get("/{chapter}/{sequence}", response_model=TaskOut)
async def read_task(chapter: int, sequence: int, deps=Depends(deps)) -> TaskOut:
    """The repeat inspection one repair carries."""
    with UnitOfWork(deps.engine) as unit:
        return task_out(unit.inspections.get(RepairId(chapter, sequence)))


@router.post("/{chapter}/{sequence}/done", response_model=TaskOut)
async def sign_off(
    chapter: int,
    sequence: int,
    body: SignOffIn,
    deps=Depends(deps),
    key=Depends(require(Action.DISPOSITION)),
) -> TaskOut:
    """Record that the inspection was done, at the counters it was done at."""
    with UnitOfWork(deps.engine) as unit:
        signed = unit.inspections.sign_off(
            RepairId(chapter, sequence), body.on, body.flown.to_domain()
        )
        unit.flush()
        return task_out(signed)
