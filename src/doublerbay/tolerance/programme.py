"""The repeat inspections one aeroplane is carrying, and which of them are due.

Every category B repair puts a task on the aeroplane for the rest of its life,
and a tail that has been flying for fifteen years carries a lot of them. The
programme is that list, and the only two questions ever asked of it are what is
due now and what is due by the time it next comes into a hangar.

A task counts from the last time it was actually done and not from when the
repair went on. That distinction is the whole reason the programme is a
mutable record rather than a calculation: signing an inspection off resets its
clock, and the next one is due a repeat interval after that, not two repeat
intervals after the repair.
"""

from __future__ import annotations

from collections.abc import Iterator
from dataclasses import dataclass, replace
from datetime import date

from doublerbay.errors import BadReading, NotOnFile
from doublerbay.ids import PanelId, RepairId
from doublerbay.measure.interval import DueAt
from doublerbay.measure.usage import Usage
from doublerbay.tolerance.schedule import InspectionSchedule


@dataclass(frozen=True, slots=True)
class InspectionTask:
    """One repeat inspection on one repair."""

    against: RepairId
    part: PanelId
    schedule: InspectionSchedule
    embodied: date
    at_embodiment: Usage
    last_done: date | None = None
    at_last_done: Usage | None = None

    def __post_init__(self) -> None:
        if (self.last_done is None) != (self.at_last_done is None):
            raise BadReading(f"{self.against}: a completed inspection has a date and a reading")
        if self.last_done is not None and self.last_done < self.embodied:
            raise BadReading(f"{self.against}: inspected before the repair went on")

    @property
    def has_been_done(self) -> bool:
        return self.last_done is not None

    @property
    def is_a_one_off(self) -> bool:
        """A temporary repair's replacement limit, which does not come round again."""
        return not self.schedule.is_repeating

    def due(self) -> DueAt:
        """When this task next falls due, counting from the last time it was done."""
        if self.last_done is None or self.at_last_done is None:
            return self.schedule.first_due(self.at_embodiment, self.embodied)
        if self.is_a_one_off:
            raise BadReading(f"{self.against}: a replacement limit does not come round again")
        return self.schedule.next_due(self.at_last_done, self.last_done)

    def is_due(self, flown: Usage, today: date) -> bool:
        if self.has_been_done and self.is_a_one_off:
            return False
        return self.due().is_due(flown, today)

    def signed_off(self, on: date, flown: Usage) -> InspectionTask:
        """The task after somebody has done it, which resets its clock."""
        if self.has_been_done and self.is_a_one_off:
            raise BadReading(f"{self.against}: a replacement limit is only done once")
        return replace(self, last_done=on, at_last_done=flown)

    def __str__(self) -> str:
        when = self.last_done.isoformat() if self.last_done else "never"
        return f"{self.against} on {self.part}, {self.schedule.method}, last done {when}"


class InspectionProgramme:
    """Every repeat inspection one tail is carrying."""

    def __init__(self) -> None:
        self._tasks: dict[RepairId, InspectionTask] = {}

    def add(self, task: InspectionTask) -> None:
        if task.against in self._tasks:
            raise BadReading(f"{task.against} already has an inspection on the programme")
        self._tasks[task.against] = task

    def task_for(self, repair: RepairId) -> InspectionTask:
        try:
            return self._tasks[repair]
        except KeyError:
            raise NotOnFile(f"{repair} has no inspection on the programme") from None

    def sign_off(self, repair: RepairId, on: date, flown: Usage) -> InspectionTask:
        signed = self.task_for(repair).signed_off(on, flown)
        self._tasks[repair] = signed
        return signed

    def drop(self, repair: RepairId) -> None:
        """Take a task off, which happens when the repair it belongs to comes off."""
        if repair not in self._tasks:
            raise NotOnFile(f"{repair} has no inspection on the programme")
        del self._tasks[repair]

    def due_on(self, flown: Usage, today: date) -> tuple[InspectionTask, ...]:
        return tuple(task for task in self if task.is_due(flown, today))

    def never_done(self) -> tuple[InspectionTask, ...]:
        return tuple(task for task in self if not task.has_been_done)

    def __iter__(self) -> Iterator[InspectionTask]:
        return iter(sorted(self._tasks.values(), key=lambda task: str(task.against)))

    def __len__(self) -> int:
        return len(self._tasks)

    def __contains__(self, repair: object) -> bool:
        return repair in self._tasks

    def __str__(self) -> str:
        return f"{len(self._tasks)} repeat inspections"
