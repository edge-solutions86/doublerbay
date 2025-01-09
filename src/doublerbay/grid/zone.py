"""Zone numbers, which is how a finding gets written down before anyone measures it.

The mechanic who finds the damage does not know the panel number. What they
write on the card is the zone, because the zone is painted on the access panel
they opened and printed on every page of the maintenance manual. Three digits:
the hundred says which end of the aircraft, the ten says which part of it, and
the unit says which side and which bay.

The side is carried in the last digit and it is a convention, not arithmetic.
On the V320 an odd unit digit is the left hand side and an even one is the
right, which is the same way the type certificate holder numbers its access
panels. Zero is centreline and belongs to neither.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from enum import Enum
from functools import total_ordering

from doublerbay.errors import BadReading

_ZONE = re.compile(r"^(\d{3})$")


class MajorZone(Enum):
    """The hundred digit, which is the coarsest thing anyone says out loud."""

    LOWER_FUSELAGE = 100
    UPPER_FUSELAGE = 200
    EMPENNAGE = 300
    POWERPLANT = 400
    LEFT_WING = 500
    RIGHT_WING = 600
    LANDING_GEAR = 700
    DOORS = 800

    @property
    def title(self) -> str:
        return self.name.replace("_", " ").lower()

    def __str__(self) -> str:
        return str(self.value)


class Side(Enum):
    LEFT = "L"
    RIGHT = "R"
    CENTRE = "C"

    def __str__(self) -> str:
        return self.value


def side_of_digit(unit: int) -> Side:
    if unit == 0:
        return Side.CENTRE
    return Side.LEFT if unit % 2 else Side.RIGHT


@total_ordering
@dataclass(frozen=True, slots=True)
class Zone:
    """A three digit zone, as painted on the panel."""

    number: int

    def __post_init__(self) -> None:
        if not 100 <= self.number <= 899:
            raise BadReading(f"a zone is three digits from 100 to 899, got {self.number}")

    @classmethod
    def parse(cls, text: str) -> Zone:
        match = _ZONE.match(text.strip())
        if match is None:
            raise BadReading(f"{text!r} is not a zone number")
        return cls(int(match.group(1)))

    @property
    def major(self) -> MajorZone:
        return MajorZone(self.number // 100 * 100)

    @property
    def sub(self) -> int:
        """The ten digit, which is the part of the major zone."""
        return self.number // 10 % 10

    @property
    def unit(self) -> int:
        return self.number % 10

    @property
    def side(self) -> Side:
        return side_of_digit(self.unit)

    @property
    def is_fuselage(self) -> bool:
        return self.major in (MajorZone.LOWER_FUSELAGE, MajorZone.UPPER_FUSELAGE)

    @property
    def is_wing(self) -> bool:
        return self.major in (MajorZone.LEFT_WING, MajorZone.RIGHT_WING)

    def opposite(self) -> Zone:
        """The mirror of this zone, for damage reported on the wrong hand.

        Centreline zones have no mirror and come back unchanged, which is the
        right answer and not a refusal.
        """
        if self.side is Side.CENTRE:
            return self
        step = -1 if self.side is Side.RIGHT else 1
        return Zone(self.number + step)

    def shares_bay_with(self, other: Zone) -> bool:
        """Same place on the aircraft, either hand."""
        return self.number // 10 == other.number // 10

    def __lt__(self, other: Zone) -> bool:
        if not isinstance(other, Zone):
            return NotImplemented
        return self.number < other.number

    def __str__(self) -> str:
        return str(self.number)


def zones_in(major: MajorZone, numbers: tuple[int, ...]) -> tuple[Zone, ...]:
    """Build a run of zones inside one major zone, checking they belong to it."""
    built = []
    for number in numbers:
        zone = Zone(number)
        if zone.major is not major:
            raise BadReading(f"zone {zone} is not in {major.title}")
        built.append(zone)
    return tuple(built)
