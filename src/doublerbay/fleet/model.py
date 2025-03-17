"""Type, model and series, and the effectivity that hangs off them.

A limit is not written for an aeroplane, it is written for a range of them. The
manual says a page applies to serials 5001 through 5240, or to everything with
service bulletin 53-018 embodied, or to everything without it, and the same
damage on two tails a year apart in build gets two different answers because of
it.

Effectivity is therefore a first class thing here and not a comment. It is a
serial range, optionally narrowed by a bulletin being embodied or not, and the
only question anyone asks of it is whether a given tail is inside it.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from enum import Enum

from doublerbay.errors import BadReading
from doublerbay.ids import Serial

_BULLETIN = re.compile(r"^SB\s*(\d{2})-(\d{3})$", re.IGNORECASE)


class Series(Enum):
    """The three the operator flies. They differ in length and in gross weight."""

    SHORT = "100"
    STANDARD = "200"
    LONG_RANGE = "200LR"

    @property
    def is_stretched(self) -> bool:
        return self is not Series.SHORT

    def __str__(self) -> str:
        return f"V320-{self.value}"


@dataclass(frozen=True, slots=True)
class Bulletin:
    """A service bulletin, which a tail either has embodied or has not."""

    chapter: int
    number: int

    def __post_init__(self) -> None:
        if not 20 <= self.chapter <= 80:
            raise BadReading(f"chapter {self.chapter} does not carry bulletins")
        if not 1 <= self.number <= 999:
            raise BadReading(f"{self.number} is not a bulletin number")

    @classmethod
    def parse(cls, text: str) -> Bulletin:
        match = _BULLETIN.match(text.strip())
        if match is None:
            raise BadReading(f"{text!r} is not a bulletin number")
        return cls(int(match.group(1)), int(match.group(2)))

    def __str__(self) -> str:
        return f"SB {self.chapter:02d}-{self.number:03d}"


@dataclass(frozen=True, slots=True)
class Effectivity:
    """Which tails something applies to."""

    series: frozenset[Series] = field(default_factory=lambda: frozenset(Series))
    first_serial: int | None = None
    last_serial: int | None = None
    requires: Bulletin | None = None
    excludes: Bulletin | None = None

    def __post_init__(self) -> None:
        if not self.series:
            raise BadReading("effectivity against no series applies to nothing")
        if (
            self.first_serial is not None
            and self.last_serial is not None
            and self.first_serial > self.last_serial
        ):
            raise BadReading(f"serials {self.first_serial} to {self.last_serial} run backwards")
        if self.requires is not None and self.requires == self.excludes:
            raise BadReading(f"{self.requires} cannot be both required and excluded")

    @classmethod
    def all_aircraft(cls) -> Effectivity:
        return cls()

    def covers(self, series: Series, serial: Serial, embodied: frozenset[Bulletin]) -> bool:
        """Whether one tail, in the configuration it is in today, is inside this."""
        if series not in self.series:
            return False
        number = serial.as_int
        if self.first_serial is not None and number < self.first_serial:
            return False
        if self.last_serial is not None and number > self.last_serial:
            return False
        if self.requires is not None and self.requires not in embodied:
            return False
        return not (self.excludes is not None and self.excludes in embodied)

    @property
    def is_open_ended(self) -> bool:
        return self.last_serial is None

    def __str__(self) -> str:
        parts = [",".join(sorted(series.value for series in self.series))]
        if self.first_serial is not None or self.last_serial is not None:
            first = self.first_serial if self.first_serial is not None else "first"
            last = self.last_serial if self.last_serial is not None else "on"
            parts.append(f"MSN {first} to {last}")
        if self.requires is not None:
            parts.append(f"with {self.requires}")
        if self.excludes is not None:
            parts.append(f"without {self.excludes}")
        return "; ".join(parts)
