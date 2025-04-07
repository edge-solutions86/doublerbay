"""The register, which is the one place that knows which tails there are.

Every other question the desk asks starts here: which aeroplanes are in service
today, which of them a manual page applies to, what a given tail has flown, and
when a limit on it is going to land. Holding that in one object rather than
passing four dictionaries around is the difference between a fleet report that
agrees with itself and one that does not.

Registrations are looked up too, because that is what people type, but they are
looked up against the tails currently wearing them. A registration that has
moved on answers for the aeroplane wearing it now, and the old repairs stay
filed under the serial where they belong.
"""

from __future__ import annotations

from collections.abc import Iterator
from datetime import date

from doublerbay.errors import BadReading, NotOnFile
from doublerbay.fleet.aircraft import Aircraft
from doublerbay.fleet.forecast import Forecast
from doublerbay.fleet.model import Effectivity
from doublerbay.fleet.utilisation import Snapshot, UtilisationLog
from doublerbay.ids import Registration, Serial
from doublerbay.measure.usage import Usage


class Fleet:
    """Every tail the operator holds, with its log."""

    def __init__(self) -> None:
        self._tails: dict[Serial, Aircraft] = {}
        self._logs: dict[Serial, UtilisationLog] = {}

    def enrol(self, aircraft: Aircraft, log: UtilisationLog | None = None) -> None:
        """Put a tail on the register, with a log if it already has one."""
        if aircraft.serial in self._tails:
            raise BadReading(f"{aircraft.serial} is already on the register")
        opened = (
            log if log is not None else UtilisationLog.opened(aircraft.serial, aircraft.delivered)
        )
        if opened.serial != aircraft.serial:
            raise BadReading(f"{opened.serial} is not the log for {aircraft.serial}")
        self._tails[aircraft.serial] = aircraft
        self._logs[aircraft.serial] = opened

    def amend(self, aircraft: Aircraft) -> None:
        """Replace a tail record after a re registration or a bulletin going on."""
        if aircraft.serial not in self._tails:
            raise NotOnFile(f"{aircraft.serial} is not on the register")
        self._tails[aircraft.serial] = aircraft

    def tail(self, serial: Serial) -> Aircraft:
        try:
            return self._tails[serial]
        except KeyError:
            raise NotOnFile(f"{serial} is not on the register") from None

    def log_for(self, serial: Serial) -> UtilisationLog:
        try:
            return self._logs[serial]
        except KeyError:
            raise NotOnFile(f"{serial} has no log on the register") from None

    def post(self, serial: Serial, snapshot: Snapshot) -> None:
        """Post a counter reading against a tail."""
        self._logs[serial] = self.log_for(serial).posted(snapshot)

    def by_registration(self, marks: Registration) -> Aircraft:
        """Whichever aeroplane is wearing these marks now."""
        for aircraft in self._tails.values():
            if aircraft.registration == marks:
                return aircraft
        raise NotOnFile(f"{marks} is not on the register")

    def in_service_on(self, day: date) -> tuple[Aircraft, ...]:
        return tuple(aircraft for aircraft in self._sorted() if aircraft.in_service_on(day))

    def covered_by(self, effectivity: Effectivity) -> tuple[Aircraft, ...]:
        """Which tails a manual page written for a range actually applies to."""
        return tuple(aircraft for aircraft in self._sorted() if aircraft.covered_by(effectivity))

    def forecast_for(self, serial: Serial, over: int = 365) -> Forecast:
        return Forecast.from_log(self.log_for(serial), over)

    def usage_on(self, day: date, serial: Serial) -> Usage:
        return self.log_for(serial).at(day)

    def fleet_usage_on(self, day: date) -> Usage:
        """Every tail that had flown by that day, added up."""
        total = Usage.zero()
        for serial, log in self._logs.items():
            if self._tails[serial].delivered <= day:
                total = total + log.at(day)
        return total

    def oldest_on(self, day: date) -> Aircraft:
        in_service = self.in_service_on(day)
        if not in_service:
            raise NotOnFile(f"nothing was in service on {day.isoformat()}")
        return min(in_service, key=lambda aircraft: aircraft.delivered)

    def _sorted(self) -> list[Aircraft]:
        return sorted(self._tails.values(), key=lambda aircraft: aircraft.serial.as_int)

    def __iter__(self) -> Iterator[Aircraft]:
        return iter(self._sorted())

    def __len__(self) -> int:
        return len(self._tails)

    def __contains__(self, serial: object) -> bool:
        return serial in self._tails

    def __str__(self) -> str:
        return f"{len(self._tails)} tails on the register"
