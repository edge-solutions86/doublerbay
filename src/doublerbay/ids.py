"""The numbers that name things, and the shapes they have to come in.

A registration identifies an aeroplane today and a different aeroplane in six
years, because marks get reassigned when a tail leaves the register. A serial
number identifies the airframe for as long as it exists. Records that outlive a
lease are keyed on the serial and displayed by the registration, and mixing the
two is how a repair ends up filed against somebody else's aircraft.

Everything else here is a document number off a form, and the only interesting
thing about them is that the form has a fixed shape and the shop will type
whatever it likes if nothing checks.
"""

from __future__ import annotations

import re
from dataclasses import dataclass

from doublerbay.errors import BadReading

_REGISTRATION = re.compile(r"^N[1-9]\d{0,4}[A-Z]{0,2}$")
_SERIAL = re.compile(r"^\d{3,5}$")
_FINDING = re.compile(r"^DR-(\d{4})-(\d{6})$")
_REPAIR = re.compile(r"^RP-(\d{2})-(\d{4})$")
_PANEL = re.compile(r"^(\d{2})(\d{2})-(\d{3})([LRC])$")


@dataclass(frozen=True, slots=True)
class Registration:
    """The marks painted on the tail. Reassignable, so never a primary key."""

    marks: str

    def __post_init__(self) -> None:
        if not _REGISTRATION.match(self.marks):
            raise BadReading(f"{self.marks!r} is not a registration")

    @classmethod
    def parse(cls, text: str) -> Registration:
        return cls(text.strip().upper())

    def __str__(self) -> str:
        return self.marks


@dataclass(frozen=True, slots=True)
class Serial:
    """The manufacturer serial number, which the airframe keeps for life."""

    number: str

    def __post_init__(self) -> None:
        if not _SERIAL.match(self.number):
            raise BadReading(f"{self.number!r} is not a serial number")

    @classmethod
    def parse(cls, text: str) -> Serial:
        return cls(text.strip())

    @property
    def as_int(self) -> int:
        return int(self.number)

    def __str__(self) -> str:
        return f"MSN {self.number}"


@dataclass(frozen=True, slots=True)
class FindingId:
    """A damage report number, which is the year it was raised and a counter."""

    year: int
    sequence: int

    def __post_init__(self) -> None:
        if not 2000 <= self.year <= 2099:
            raise BadReading(f"{self.year} is not a report year")
        if not 1 <= self.sequence <= 999999:
            raise BadReading(f"{self.sequence} is not a report sequence")

    @classmethod
    def parse(cls, text: str) -> FindingId:
        match = _FINDING.match(text.strip().upper())
        if match is None:
            raise BadReading(f"{text!r} is not a damage report number")
        return cls(int(match.group(1)), int(match.group(2)))

    def next_in_year(self) -> FindingId:
        return FindingId(self.year, self.sequence + 1)

    def __str__(self) -> str:
        return f"DR-{self.year:04d}-{self.sequence:06d}"


@dataclass(frozen=True, slots=True)
class RepairId:
    """A repair number, filed under the chapter the repair is in."""

    chapter: int
    sequence: int

    def __post_init__(self) -> None:
        if not 50 <= self.chapter <= 59:
            raise BadReading(f"chapter {self.chapter} does not carry repairs")
        if not 1 <= self.sequence <= 9999:
            raise BadReading(f"{self.sequence} is not a repair sequence")

    @classmethod
    def parse(cls, text: str) -> RepairId:
        match = _REPAIR.match(text.strip().upper())
        if match is None:
            raise BadReading(f"{text!r} is not a repair number")
        return cls(int(match.group(1)), int(match.group(2)))

    def __str__(self) -> str:
        return f"RP-{self.chapter:02d}-{self.sequence:04d}"


@dataclass(frozen=True, slots=True)
class PanelId:
    """A skin panel part number, as chapter, section, sequence and hand."""

    chapter: int
    section: int
    sequence: int
    hand: str

    def __post_init__(self) -> None:
        if not 50 <= self.chapter <= 59:
            raise BadReading(f"chapter {self.chapter} does not carry skin panels")
        if not 0 <= self.section <= 99:
            raise BadReading(f"{self.section} is not a section")
        if not 0 <= self.sequence <= 999:
            raise BadReading(f"{self.sequence} is not a panel sequence")
        if self.hand not in ("L", "R", "C"):
            raise BadReading(f"{self.hand!r} is not a hand")

    @classmethod
    def parse(cls, text: str) -> PanelId:
        match = _PANEL.match(text.strip().upper())
        if match is None:
            raise BadReading(f"{text!r} is not a panel number")
        return cls(int(match.group(1)), int(match.group(2)), int(match.group(3)), match.group(4))

    def mirrored(self) -> PanelId:
        if self.hand == "C":
            return self
        flipped = "R" if self.hand == "L" else "L"
        return PanelId(self.chapter, self.section, self.sequence, flipped)

    def __str__(self) -> str:
        return f"{self.chapter:02d}{self.section:02d}-{self.sequence:03d}{self.hand}"
