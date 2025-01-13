"""Alloy, temper and cladding, and the one of those that changes a disposition.

Two thousand series is the tough one and seven thousand series is the strong
one, and the manual treats them differently because a crack in 7075 runs
further before anybody sees it. That much is background. What actually decides
things on a daily basis is the cladding.

Clad sheet carries a thin layer of nearly pure aluminium rolled onto each face
purely to corrode instead of the alloy underneath. It is five percent of the
sheet on each side for thin gauges and less on heavy ones. A blend out that
stops inside the cladding is a cosmetic job. The same blend a few thousandths
deeper is through into bare alloy and needs a protective finish, a fresh
corrosion entry and in some places a repeat inspection. So the cladding
thickness is not a material property here, it is a limit that other limits are
measured against.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from enum import Enum

from doublerbay.errors import BadReading, NotOnFile
from doublerbay.measure.length import Length

# Below this gauge the mills clad at five percent a side, above it at two and a
# half. The step is in the material spec and it is not a rounding of anything.
CLAD_STEP = Length.of_inches("0.062")
CLAD_THIN = Decimal("0.05")
CLAD_HEAVY = Decimal("0.025")


class Family(Enum):
    """The alloy series, which is what the toughness argument turns on."""

    TWO_THOUSAND = "2xxx"
    SEVEN_THOUSAND = "7xxx"

    @property
    def is_damage_tolerant(self) -> bool:
        return self is Family.TWO_THOUSAND

    def __str__(self) -> str:
        return self.value


class Temper(Enum):
    """How the sheet was worked and aged."""

    ANNEALED = "O"
    T3 = "T3"
    T4 = "T4"
    T6 = "T6"
    T42 = "T42"
    T351 = "T351"
    T7451 = "T7451"

    @property
    def is_annealed(self) -> bool:
        return self is Temper.ANNEALED

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True, slots=True)
class Material:
    """One line off a material callout, with the numbers the desk needs from it."""

    alloy: str
    temper: Temper
    clad: bool
    ultimate_ksi: int
    yield_ksi: int

    def __post_init__(self) -> None:
        if not self.alloy.isdigit() or len(self.alloy) != 4:
            raise BadReading(f"{self.alloy!r} is not an alloy designation")
        if self.yield_ksi > self.ultimate_ksi:
            raise BadReading(f"{self} yields above its ultimate")

    @property
    def family(self) -> Family:
        head = self.alloy[0]
        if head == "2":
            return Family.TWO_THOUSAND
        if head == "7":
            return Family.SEVEN_THOUSAND
        raise BadReading(f"{self.alloy} is not a series this desk carries")

    @property
    def is_damage_tolerant(self) -> bool:
        return self.family.is_damage_tolerant

    def clad_thickness(self, nominal: Length) -> Length:
        """How deep the protective layer runs on one face of a sheet of this gauge."""
        if not self.clad:
            return Length(0)
        fraction = CLAD_THIN if nominal <= CLAD_STEP else CLAD_HEAVY
        return nominal * fraction

    def bare_below(self, nominal: Length) -> Length:
        """The alloy left once both faces of cladding are taken off."""
        return nominal - self.clad_thickness(nominal) * 2

    def is_through_clad(self, nominal: Length, depth: Length) -> bool:
        """Whether a cut of that depth has reached bare alloy."""
        return depth > self.clad_thickness(nominal)

    def __str__(self) -> str:
        suffix = " CLAD" if self.clad else ""
        return f"{self.alloy}-{self.temper}{suffix}"


CATALOGUE: dict[str, Material] = {
    str(entry): entry
    for entry in (
        Material("2024", Temper.T3, True, 64, 47),
        Material("2024", Temper.T3, False, 70, 50),
        Material("2024", Temper.T351, False, 68, 47),
        Material("2024", Temper.T42, True, 59, 38),
        Material("7075", Temper.T6, True, 76, 67),
        Material("7075", Temper.T6, False, 83, 73),
        Material("7050", Temper.T7451, False, 76, 67),
        Material("2024", Temper.ANNEALED, True, 26, 11),
    )
}


def material(callout: str) -> Material:
    """Look a callout up the way it is written on the panel drawing."""
    try:
        return CATALOGUE[callout.strip().upper()]
    except KeyError:
        raise NotOnFile(f"{callout!r} is not a material this desk carries") from None


def is_carried(callout: str) -> bool:
    return callout.strip().upper() in CATALOGUE
