"""The story route, which is the one anybody asks for in a meeting.

Somebody says "what happened with the crack on 5127", and the answer is not
one record. It is the card, the moves, the deferral, the letter that went out
and the doubler that finally went on, in the order they happened. This route
is that answer.

It is read only and it is deliberately unfiltered. A story with the boring
lines removed is a story somebody will accuse the desk of curating, which is
the exact accusation this record exists to survive.
"""

from __future__ import annotations

from datetime import date

from fastapi import APIRouter, Depends
from pydantic import BaseModel

from doublerbay.api.findings import deps, reference_from
from doublerbay.report.history import ORDER, Story, story_of
from doublerbay.store.unit import UnitOfWork

router = APIRouter(prefix="/history", tags=["history"])


class EntryOut(BaseModel):
    """One line of the story.

    The field is came_from rather than register because pydantic's own model
    already has a register attribute, and shadowing it works until the day
    somebody subclasses this.
    """

    on: date
    came_from: str
    what: str


class StoryOut(BaseModel):
    reference: str
    serial: str
    opened: date
    latest: date
    days_open: int
    entries: list[EntryOut]


class RegistersOut(BaseModel):
    """Which registers a story can carry lines from, in reading order."""

    registers: list[str]


def story_out(built: Story) -> StoryOut:
    return StoryOut(
        reference=str(built.reference),
        serial=built.serial.number,
        opened=built.opened,
        latest=built.latest,
        days_open=built.days_open,
        entries=[
            EntryOut(on=one.on, came_from=one.register, what=one.what) for one in built.entries
        ],
    )


@router.get("/registers", response_model=RegistersOut)
async def registers() -> RegistersOut:
    """The registers a story reads from, in the order they read on one day."""
    return RegistersOut(registers=list(ORDER))


@router.get("/{reference}", response_model=StoryOut)
async def card_history(reference: str, deps=Depends(deps)) -> StoryOut:
    """Everything that happened to one card, oldest first."""
    with UnitOfWork(deps.engine) as unit:
        return story_out(story_of(unit, reference_from(reference)))
