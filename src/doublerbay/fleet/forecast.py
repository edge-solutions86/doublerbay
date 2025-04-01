"""When a limit is going to fall due, worked out from how the tail is flying.

A repeat inspection at three thousand cycles is not a date until somebody knows
what the aeroplane does in a week. A short haul tail gets there in eighteen
months and a long haul one on the same limit takes four years, and the planning
department needs the date rather than the counter because that is what a slot
in a hangar is booked against.

The projection is deliberately a straight line off the recent rate. Anything
cleverer is pretending to know about next summer's schedule, and the number is
re forecast every time a reading is posted anyway. What matters is that all
three clocks are projected and the earliest one wins, which is the same rule
the limit itself is written under.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, timedelta
from decimal import ROUND_CEILING, Decimal

from doublerbay.errors import BadReading
from doublerbay.fleet.utilisation import UtilisationLog
from doublerbay.measure.interval import DueAt
from doublerbay.measure.usage import Cycles, Hours, Usage

# Twenty years. Past this the answer is not a forecast, it is arithmetic on a
# rate that will have changed many times over.
DEFAULT_HORIZON_DAYS = 7300


@dataclass(frozen=True, slots=True)
class Forecast:
    """A tail's log plus the rate it is flying at."""

    log: UtilisationLog
    cycles_a_day: Decimal
    hours_a_day: Decimal
    horizon_days: int = DEFAULT_HORIZON_DAYS

    def __post_init__(self) -> None:
        if self.cycles_a_day < 0 or self.hours_a_day < 0:
            raise BadReading(f"{self.log.serial}: a rate does not run backwards")
        if self.horizon_days < 1:
            raise BadReading("a forecast horizon is at least a day")

    @classmethod
    def from_log(
        cls, log: UtilisationLog, over: int = 365, horizon_days: int = DEFAULT_HORIZON_DAYS
    ) -> Forecast:
        cycles_a_day, hours_a_day = log.daily_rate(over)
        return cls(log, cycles_a_day, hours_a_day, horizon_days)

    @property
    def as_of(self) -> date:
        return self.log.latest.taken

    @property
    def flown(self) -> Usage:
        return self.log.latest.usage

    def projected(self, day: date) -> Usage:
        """The counters on a future day, run forward at the current rate."""
        if day <= self.as_of:
            return self.log.at(day)
        ahead = (day - self.as_of).days
        cycles = self.flown.cycles + Cycles(int(self.cycles_a_day * ahead))
        minutes = int((self.hours_a_day * 60 * ahead).to_integral_value())
        return Usage(cycles, self.flown.hours + Hours(minutes))

    def _days_to_cycles(self, target: Cycles) -> int | None:
        if self.flown.cycles >= target:
            return 0
        if self.cycles_a_day <= 0:
            return None
        short = Decimal((target - self.flown.cycles).count)
        return int((short / self.cycles_a_day).to_integral_value(ROUND_CEILING))

    def _days_to_hours(self, target: Hours) -> int | None:
        if self.flown.hours >= target:
            return 0
        if self.hours_a_day <= 0:
            return None
        short = Decimal((target - self.flown.hours).minutes) / 60
        return int((short / self.hours_a_day).to_integral_value(ROUND_CEILING))

    def when_due(self, due: DueAt, today: date | None = None) -> date | None:
        """The earliest day any of the three clocks reaches the limit.

        None means no clock on this limit gets there inside the horizon, which
        for a rarely flown tail on a cycle limit is a real answer and not a
        failure to compute one.
        """
        start = today if today is not None else self.as_of
        if start < self.as_of:
            raise BadReading(f"{self.log.serial}: cannot forecast from before the last reading")
        offsets: list[int] = []
        if due.cycles is not None:
            reached = self._days_to_cycles(due.cycles)
            if reached is not None:
                offsets.append(reached)
        if due.hours is not None:
            reached = self._days_to_hours(due.hours)
            if reached is not None:
                offsets.append(reached)
        if due.on is not None:
            offsets.append(max((due.on - self.as_of).days, 0))
        if not offsets:
            return None
        soonest = min(offsets)
        if soonest > self.horizon_days:
            return None
        landing = self.as_of + timedelta(days=soonest)
        return max(landing, start)

    def days_until(self, due: DueAt, today: date | None = None) -> int | None:
        landing = self.when_due(due, today)
        if landing is None:
            return None
        start = today if today is not None else self.as_of
        return max((landing - start).days, 0)

    def __str__(self) -> str:
        return f"{self.log.serial}: {self.cycles_a_day} FC and {self.hours_a_day} FH a day"
