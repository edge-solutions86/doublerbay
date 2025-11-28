"""Assembling the reports out of the repositories, in one place.

The counting lives in the other modules and takes plain tuples, which is what
keeps it testable without a database. This is the layer that goes and gets
those tuples. Both the http routes and the command line come through here, so
the two cannot drift into answering the same question differently, which is
the failure that makes a desk stop believing either of them.

Every function here takes the day as an argument. None of them reach for
today. A report run on Monday for Friday's meeting and the same report run on
Friday have to agree, and they only agree if the day is passed in.

A tail with one counter reading has no rate, and a rate is what turns cycles
into days. Rather than refuse the whole report, that tail gets the flat rate
the type is planned at. The report says which tails that happened to, because
a margin computed off a planning rate is a different quality of answer from
one computed off the aeroplane's own flying.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from decimal import Decimal

from doublerbay.errors import DoublerbayError
from doublerbay.fleet.forecast import Forecast
from doublerbay.ids import Serial
from doublerbay.report.damage import DamageCount, FleetDamage, count_fleet, count_open
from doublerbay.report.due import DueItem, due_list, overrun
from doublerbay.store.unit import UnitOfWork

# What the type is planned at, used only when a tail has no rate of its own.
PLANNED_CYCLES_A_DAY = Decimal("4.2")
PLANNED_HOURS_A_DAY = Decimal("8.1")


def rate_for(unit: UnitOfWork, serial: Serial) -> tuple[Forecast, bool]:
    """The tail's own rate, or the planning rate with a flag saying so."""
    log = unit.aircraft.log_for(serial)
    try:
        return Forecast.from_log(log), True
    except DoublerbayError:
        return Forecast(log, PLANNED_CYCLES_A_DAY, PLANNED_HOURS_A_DAY), False


@dataclass(frozen=True, slots=True)
class DueReport:
    """What falls due on one tail, and whether the rate behind it is real."""

    serial: Serial
    on: date
    items: tuple[DueItem, ...]
    measured_rate: bool

    @property
    def overrun(self) -> tuple[DueItem, ...]:
        return overrun(self.items)

    @property
    def is_grounded(self) -> bool:
        return bool(self.overrun)

    def __str__(self) -> str:
        note = "" if self.measured_rate else ", on the planning rate"
        return f"{self.serial}: {len(self.items)} due, {len(self.overrun)} over{note}"


def due_on_tail(
    unit: UnitOfWork, serial: Serial, on: date, within_days: int | None = None
) -> DueReport:
    """Everything with a limit on it against one tail, worst first."""
    forecast, measured = rate_for(unit, serial)
    flown = unit.aircraft.log_for(serial).at(on)
    deferrals = tuple(one for one in unit.deferrals.live() if _is_on(unit, one.against, serial))
    return DueReport(
        serial=serial,
        on=on,
        items=due_list(deferrals, serial, flown, forecast, on, within_days),
        measured_rate=measured,
    )


def _is_on(unit: UnitOfWork, reference, serial: Serial) -> bool:
    """Whether a card belongs to a tail, without loading the whole register."""
    try:
        return unit.findings.latest(reference).serial == serial
    except DoublerbayError:
        return False


def due_across_fleet(
    unit: UnitOfWork, on: date, within_days: int | None = None
) -> tuple[DueReport, ...]:
    """The due list for every tail on the register, worst tail first."""
    reports = [due_on_tail(unit, one.serial, on, within_days) for one in unit.aircraft.all()]
    reports.sort(key=lambda one: (-len(one.overrun), -len(one.items), one.serial.number))
    return tuple(reports)


def grounded(unit: UnitOfWork, on: date) -> tuple[DueReport, ...]:
    """Only the tails with something already past its limit."""
    return tuple(one for one in due_across_fleet(unit, on) if one.is_grounded)


def open_on_tail(unit: UnitOfWork, serial: Serial, on: date, recent_days: int = 30) -> DamageCount:
    """What is open on one tail as of a day."""
    return count_open(serial, unit.findings.on_tail(serial), on, recent_days)


def open_across_fleet(unit: UnitOfWork, on: date, recent_days: int = 30) -> FleetDamage:
    """What is open across the whole register as of a day."""
    holdings = {
        one.serial: unit.findings.on_tail(one.serial) for one in unit.aircraft.in_service_on(on)
    }
    return count_fleet(holdings, on, recent_days)
