"""Which structure definition answers for which tail, and everything about one tail at once.

The assessor needs four things before it can look at a damage: the aeroplane,
what it has flown, the barrel definition for its series, and the wing. Fetching
those in four places is how a report ends up describing a short series barrel
with a standard series tail number on it, so they come out together or not at
all.

The series difference is real and it is only length. All three share the cross
section, the stringer spacing and the gauges, and the short one simply has
fewer frames between the wing box and the tail cone. So the catalogue holds a
frame count per series rather than three transcriptions of the same manual.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from decimal import Decimal

from doublerbay.errors import NotOnFile
from doublerbay.fleet.aircraft import Aircraft
from doublerbay.fleet.model import Effectivity, Series
from doublerbay.fleet.register import Fleet
from doublerbay.ids import Serial
from doublerbay.measure.usage import Usage
from doublerbay.structure.airframe import Airframe
from doublerbay.structure.wingbox import WingBox
from doublerbay.v320 import fuselage, wing

# Frames between the nose bulkhead and the pressure dome, per series. The short
# one loses eight bays out of the tail cone and nothing else changes.
FRAMES_BY_SERIES: dict[Series, int] = {
    Series.SHORT: 53,
    Series.STANDARD: fuselage.FRAME_COUNT,
    Series.LONG_RANGE: fuselage.FRAME_COUNT,
}


@dataclass(frozen=True, slots=True)
class TypeDefinition:
    """One series, with the barrel and the wing that go with it."""

    series: Series
    barrel: Airframe
    wing_box: WingBox

    def __str__(self) -> str:
        return f"{self.series}: {self.barrel}"


class TypeCatalogue:
    """The structure definitions the desk holds, keyed on series."""

    def __init__(self, frames_by_series: dict[Series, int] | None = None) -> None:
        self._frames = dict(frames_by_series or FRAMES_BY_SERIES)

    def carries(self, series: Series) -> bool:
        return series in self._frames

    def definition_for(self, series: Series) -> TypeDefinition:
        try:
            frames = self._frames[series]
        except KeyError:
            raise NotOnFile(f"no structure definition on file for {series}") from None
        return TypeDefinition(series, fuselage.barrel_for(frames), wing.wing_box())

    def frames_on(self, series: Series) -> int:
        try:
            return self._frames[series]
        except KeyError:
            raise NotOnFile(f"no structure definition on file for {series}") from None

    def __len__(self) -> int:
        return len(self._frames)


@dataclass(frozen=True, slots=True)
class TailContext:
    """Everything about one aeroplane on one day, taken in a single pass."""

    aircraft: Aircraft
    on: date
    flown: Usage
    definition: TypeDefinition

    @property
    def serial(self) -> Serial:
        return self.aircraft.serial

    @property
    def barrel(self) -> Airframe:
        return self.definition.barrel

    @property
    def wing_box(self) -> WingBox:
        return self.definition.wing_box

    @property
    def age_years(self) -> Decimal:
        return self.aircraft.age_years(self.on)

    def covered_by(self, effectivity: Effectivity) -> bool:
        return self.aircraft.covered_by(effectivity)

    def __str__(self) -> str:
        return f"{self.aircraft} on {self.on.isoformat()}, {self.flown}"


def context_for(fleet: Fleet, catalogue: TypeCatalogue, serial: Serial, on: date) -> TailContext:
    """The aeroplane, its counters and its structure, resolved together."""
    aircraft = fleet.tail(serial)
    if not aircraft.in_service_on(on):
        raise NotOnFile(f"{serial} was not in service on {on.isoformat()}")
    return TailContext(
        aircraft=aircraft,
        on=on,
        flown=fleet.log_for(serial).at(on),
        definition=catalogue.definition_for(aircraft.series),
    )
