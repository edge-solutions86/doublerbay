"""One tail, as the records office holds it.

The serial is the aeroplane and the registration is what is painted on it this
month. Leases end, marks get handed back to the authority and reissued to
somebody else's aeroplane, and a repair filed under a registration follows the
paint rather than the metal. So the serial is the key and the registration is a
label, and changing the label makes a new record rather than editing the old one.

The configuration is the other half of identity. Two tails off the same line a
month apart can have different structure if one of them has a bulletin embodied
and the other does not, and that is what decides which page of the manual
answers for a damage on it.
"""

from __future__ import annotations

from dataclasses import dataclass, field, replace
from datetime import date
from decimal import Decimal

from doublerbay.errors import BadReading
from doublerbay.fleet.model import Bulletin, Effectivity, Series
from doublerbay.ids import Registration, Serial

DAYS_PER_YEAR = Decimal("365.25")


@dataclass(frozen=True, slots=True)
class Aircraft:
    """A tail, keyed on the serial and labelled with the marks."""

    serial: Serial
    registration: Registration
    series: Series
    line_number: int
    delivered: date
    embodied: frozenset[Bulletin] = field(default_factory=frozenset)
    withdrawn: date | None = None

    def __post_init__(self) -> None:
        if self.line_number < 1:
            raise BadReading(f"{self.serial}: line numbers start at one")
        if self.withdrawn is not None and self.withdrawn < self.delivered:
            raise BadReading(f"{self.serial}: withdrawn before it was delivered")

    @property
    def tail(self) -> str:
        return str(self.registration)

    def in_service_on(self, day: date) -> bool:
        if day < self.delivered:
            return False
        return self.withdrawn is None or day < self.withdrawn

    def age_years(self, on: date) -> Decimal:
        """How old the airframe is, which is what a calendar limit is measured against."""
        if on < self.delivered:
            raise BadReading(f"{self.serial}: {on} is before it was delivered")
        days = (on - self.delivered).days
        return (Decimal(days) / DAYS_PER_YEAR).quantize(Decimal("0.01"))

    def has_embodied(self, bulletin: Bulletin) -> bool:
        return bulletin in self.embodied

    def with_embodied(self, bulletin: Bulletin) -> Aircraft:
        """The same tail after a bulletin goes on, which is a new record."""
        if self.has_embodied(bulletin):
            return self
        return replace(self, embodied=self.embodied | {bulletin})

    def re_registered(self, marks: Registration) -> Aircraft:
        """The same airframe wearing different marks."""
        if marks == self.registration:
            return self
        return replace(self, registration=marks)

    def withdrawn_on(self, day: date) -> Aircraft:
        return replace(self, withdrawn=day)

    def covered_by(self, effectivity: Effectivity) -> bool:
        """Whether a manual page written for a range answers for this tail."""
        return effectivity.covers(self.series, self.serial, self.embodied)

    def __str__(self) -> str:
        return f"{self.registration} ({self.serial}, {self.series})"
