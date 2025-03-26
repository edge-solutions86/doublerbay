"""What a tail has flown, as the log book records it.

Nobody reads a counter continuously. The maintenance system posts a reading
every so often, usually at a check and sometimes at a month end, and everything
else is worked out between two of those readings. So utilisation is a list of
snapshots and not a number, and the answer to how much has this aeroplane flown
is always as of a date.

Counters never go backwards. A reading lower than the one before it is a
transposed digit or a reading posted against the wrong tail, and it gets
refused here rather than quietly turning a repair that was due into a repair
that is not.
"""

from __future__ import annotations

from bisect import bisect_right
from dataclasses import dataclass
from datetime import date
from decimal import Decimal

from doublerbay.errors import BadReading, NotOnFile
from doublerbay.ids import Serial
from doublerbay.measure.usage import Cycles, Hours, Usage


@dataclass(frozen=True, slots=True)
class Snapshot:
    """One posted reading of both counters."""

    taken: date
    usage: Usage

    def __str__(self) -> str:
        return f"{self.taken.isoformat()} {self.usage}"


@dataclass(frozen=True, slots=True)
class UtilisationLog:
    """Every posted reading for one tail, oldest first."""

    serial: Serial
    snapshots: tuple[Snapshot, ...]

    def __post_init__(self) -> None:
        if not self.snapshots:
            raise BadReading(f"{self.serial}: a log has at least one reading in it")
        taken = [snapshot.taken for snapshot in self.snapshots]
        if taken != sorted(taken):
            raise BadReading(f"{self.serial}: readings are posted oldest first")
        if len(set(taken)) != len(taken):
            raise BadReading(f"{self.serial}: two readings posted on the same day")
        for earlier, later in zip(self.snapshots, self.snapshots[1:], strict=False):
            if later.usage.cycles < earlier.usage.cycles:
                raise BadReading(f"{self.serial}: cycles fell between two readings")
            if later.usage.hours < earlier.usage.hours:
                raise BadReading(f"{self.serial}: hours fell between two readings")

    @classmethod
    def opened(cls, serial: Serial, on: date) -> UtilisationLog:
        """A brand new airframe, which has flown nothing."""
        return cls(serial, (Snapshot(on, Usage.zero()),))

    @property
    def _days(self) -> list[date]:
        return [snapshot.taken for snapshot in self.snapshots]

    @property
    def opened_on(self) -> date:
        return self.snapshots[0].taken

    @property
    def latest(self) -> Snapshot:
        return self.snapshots[-1]

    def posted(self, snapshot: Snapshot) -> UtilisationLog:
        """The log with one more reading on it."""
        return UtilisationLog(self.serial, (*self.snapshots, snapshot))

    def at(self, day: date) -> Usage:
        """What the counters read as of a day, taking the last posting on or before it."""
        if day < self.opened_on:
            raise NotOnFile(f"{self.serial}: nothing posted on or before {day.isoformat()}")
        index = bisect_right(self._days, day) - 1
        return self.snapshots[index].usage

    def flown_between(self, start: date, end: date) -> Usage:
        """How much was flown across a window, on the postings that bracket it."""
        if end < start:
            raise BadReading(f"{self.serial}: {end.isoformat()} is before {start.isoformat()}")
        return self.at(end) - self.at(start)

    def daily_rate(self, over: int = 365) -> tuple[Decimal, Decimal]:
        """Cycles a day and hours a day, taken over the last stretch of the log.

        Two readings are the minimum, and a log with only one is a tail that has
        not flown yet rather than one flying at zero.
        """
        if len(self.snapshots) < 2:
            raise NotOnFile(f"{self.serial}: one reading is not a rate")
        last = self.latest
        window_start = last.taken.toordinal() - over
        earliest = self.snapshots[0]
        for snapshot in self.snapshots:
            if snapshot.taken.toordinal() >= window_start:
                earliest = snapshot
                break
        days = (last.taken - earliest.taken).days
        if days <= 0:
            earliest = self.snapshots[0]
            days = (last.taken - earliest.taken).days
        if days <= 0:
            raise NotOnFile(f"{self.serial}: no time between readings to take a rate over")
        flown = last.usage - earliest.usage
        return (
            (Decimal(flown.cycles.count) / days).quantize(Decimal("0.001")),
            (Decimal(flown.hours.minutes) / 60 / days).quantize(Decimal("0.001")),
        )

    def __str__(self) -> str:
        return f"{self.serial}: {self.latest}"


def usage(cycles: int, hours: str | int) -> Usage:
    """A reading, written the way the maintenance system posts one."""
    return Usage(Cycles(cycles), Hours.of_hours(hours))
