"""A small fleet with a plausible history on it, for a demonstration.

Every service like this ends up with a seed script, and most of them seed one
aeroplane with one dent, which demonstrates nothing. The interesting reports
are the ones that need a shape: a tail that is grounded, a tail that is clean,
a panel with three repairs on it, a corrosion location that has come back.

So this builds four aeroplanes with different problems on purpose. It is not
random and it takes no seed value, because a demonstration that shows
something different every time is one nobody can write a script against.

It refuses to run against a database that already holds a fleet. Seeding on
top of real records is how a demonstration ends up in a fleet report.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date, timedelta

from doublerbay.damage.corrosion import Corrosion
from doublerbay.damage.dent import Dent
from doublerbay.damage.finding import Finding
from doublerbay.damage.kind import DamageKind, DamageOrigin
from doublerbay.disposition.deferral import grant
from doublerbay.disposition.state import DispositionState
from doublerbay.errors import BadReading
from doublerbay.fleet.aircraft import Aircraft
from doublerbay.fleet.catalogue import TypeCatalogue
from doublerbay.fleet.check import DiscoverySource
from doublerbay.fleet.model import Series
from doublerbay.fleet.utilisation import Snapshot, UtilisationLog
from doublerbay.grid.lattice import Frame, Stringer
from doublerbay.grid.location import FuselagePoint
from doublerbay.grid.zone import Side
from doublerbay.ids import FindingId, Registration, Serial
from doublerbay.manual.book import v320_book
from doublerbay.measure.interval import Interval
from doublerbay.measure.length import Length
from doublerbay.measure.usage import Cycles, Hours, Usage
from doublerbay.service.intake import take_in
from doublerbay.store.unit import UnitOfWork

# Four tails, four different problems.
FLEET = (
    ("5127", "N412VD", 1140, date(2016, 4, 12)),
    ("5240", "N908QA", 1204, date(2017, 9, 3)),
    ("5388", "N551KE", 1310, date(2019, 2, 18)),
    ("5461", "N720LM", 1388, date(2021, 11, 30)),
)
OPENED = date(2024, 1, 1)
LATEST = date(2025, 3, 1)


@dataclass(frozen=True, slots=True)
class Seeded:
    """What the seed put on the database."""

    aircraft: int
    cards: int
    deferrals: int

    def __str__(self) -> str:
        return f"{self.aircraft} aircraft, {self.cards} damage cards, {self.deferrals} deferrals"


def inches(text: str) -> Length:
    return Length.of_inches(text)


def flown(cycles: int, hours: int) -> Usage:
    return Usage(Cycles(cycles), Hours.of_hours(hours))


def cabin(frame: int, stringer: int, down: str = "0.000") -> FuselagePoint:
    return FuselagePoint(
        Frame(frame), inches("10.000"), Stringer(stringer, Side.LEFT), inches(down)
    )


def aircraft_of(serial: str, marks: str, line: int, delivered: date) -> Aircraft:
    return Aircraft(
        serial=Serial(serial),
        registration=Registration.parse(marks),
        series=Series.STANDARD,
        line_number=line,
        delivered=delivered,
    )


def _enrol(unit: UnitOfWork) -> None:
    """Put the four tails on with two readings each, so every one has a rate."""
    for step, (serial, marks, line, delivered) in enumerate(FLEET):
        held = aircraft_of(serial, marks, line, delivered)
        unit.aircraft.add(held)
        log = UtilisationLog.opened(held.serial, held.delivered)
        log = log.posted(Snapshot(OPENED, flown(11000 + step * 400, 19000 + step * 700)))
        log = log.posted(Snapshot(LATEST, flown(13800 + step * 400, 24200 + step * 700)))
        unit.aircraft.open_log(log)


def _card(
    reference: FindingId,
    serial: str,
    kind: DamageKind,
    geometry,
    at: FuselagePoint,
    found: date,
    origin: DamageOrigin,
) -> Finding:
    return Finding(
        reference=reference,
        serial=Serial(serial),
        kind=kind,
        geometry=geometry,
        point=at,
        found_on=found,
        source=DiscoverySource.SCHEDULED_CHECK,
        origin=origin,
        at_usage=flown(13800, 24200),
    )


def _dent(depth: str = "0.020") -> Dent:
    return Dent(inches(depth), inches("3.000"), inches("2.000"))


def _rust(depth: str = "0.004") -> Corrosion:
    return Corrosion(inches("2.000"), inches("1.000"), inches(depth))


def seed(unit: UnitOfWork, today: date = LATEST) -> Seeded:
    """Build the demonstration fleet, refusing a database that already holds one."""
    if unit.aircraft.all():
        raise BadReading("this database already holds a fleet, seed an empty one")

    _enrol(unit)
    unit.flush()
    book, catalogue = v320_book(), TypeCatalogue()

    cards = 0
    # 5127 carries a shallow dent and a deep one, so it has both answers on it.
    for step, depth in enumerate(("0.010", "0.220"), start=1):
        take_in(
            unit,
            book,
            catalogue,
            _card(
                FindingId(2025, step),
                "5127",
                DamageKind.DENT,
                _dent(depth),
                cabin(15 + step * 4, 6),
                today - timedelta(days=20 * step),
                DamageOrigin.IMPACT,
            ),
        )
        cards += 1

    # 5240 has corrosion that came back in the same place, which is a level two.
    for step, when in enumerate((date(2023, 6, 1), date(2025, 1, 12)), start=3):
        take_in(
            unit,
            book,
            catalogue,
            _card(
                FindingId(2025, step),
                "5240",
                DamageKind.CORROSION,
                _rust("0.030"),
                cabin(22, 9),
                when,
                DamageOrigin.ENVIRONMENT,
            ),
        )
        cards += 1

    # 5388 has one dent, deferred, which puts it on the due list.
    reference = FindingId(2025, 5)
    take_in(
        unit,
        book,
        catalogue,
        _card(
            reference,
            "5388",
            DamageKind.DENT,
            _dent("0.120"),
            cabin(30, 4),
            date(2025, 2, 2),
            DamageOrigin.HANDLING,
        ),
    )
    cards += 1
    unit.flush()
    record = unit.dispositions.get(reference)
    record = record.moved_to(DispositionState.ASSESSED, date(2025, 2, 3), "a.okonkwo")
    unit.dispositions.save(record.moved_to(DispositionState.DEFERRED, date(2025, 2, 4), "j.mercer"))
    unit.deferrals.grant(
        grant(
            reference,
            date(2025, 2, 4),
            flown(14000, 24600),
            "j.mercer",
            engineer_set=Interval.parse("300 FC / 90 DY"),
            reason="awaiting a doubler",
        )
    )

    # 5461 is left clean, because every report needs a tail with nothing on it.
    return Seeded(aircraft=len(FLEET), cards=cards, deferrals=1)
