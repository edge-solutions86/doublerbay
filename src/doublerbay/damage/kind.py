"""What kinds of damage there are, and what each one has to be measured with.

The kind is not a label, it is the thing that decides which numbers the report
has to carry and which table the assessor reads. A dent needs a depth and a
diameter and nothing else. A crack needs a length and a bearing and a note of
whether it has broken through. Corrosion needs an area. Asking for a diameter
on a crack gets a number nobody can use, and reading a dent table for a crack
gets an answer that is wrong in the dangerous direction, because dents have
allowable limits and cracks do not.

The other thing carried here is what could have caused it. A crack found where
a bulletin says cracks appear is a fleet problem being managed. The same crack
reported the morning after a belt loader hit the aeroplane is an impact and the
whole assessment changes, because impact damage does not stop at the surface.
"""

from __future__ import annotations

from enum import Enum

from doublerbay.errors import BadReading


class DamageOrigin(Enum):
    """What put it there, as far as anyone can tell."""

    IMPACT = "impact"
    FATIGUE = "fatigue"
    ENVIRONMENT = "environment"
    HANDLING = "ground handling"
    LIGHTNING = "lightning"
    MANUFACTURING = "manufacturing"
    UNKNOWN = "unknown"

    @property
    def is_an_event(self) -> bool:
        """Causes that happened at a moment, which the flight log can be searched for."""
        return self in (DamageOrigin.IMPACT, DamageOrigin.HANDLING, DamageOrigin.LIGHTNING)

    @property
    def is_progressive(self) -> bool:
        """Causes that get worse on their own between inspections."""
        return self in (DamageOrigin.FATIGUE, DamageOrigin.ENVIRONMENT)

    def __str__(self) -> str:
        return self.value


class DamageKind(Enum):
    """The kinds the report form offers, which is the same list the manual is filed under."""

    DENT = "dent"
    SCRATCH = "scratch"
    GOUGE = "gouge"
    NICK = "nick"
    CRACK = "crack"
    CORROSION = "corrosion"
    LIGHTNING_BURN = "lightning burn"
    HOLE = "hole"
    PUNCTURE = "puncture"
    BUCKLE = "buckle"
    DISBOND = "disbond"

    @property
    def is_deformation(self) -> bool:
        """Metal moved but not removed."""
        return self in (DamageKind.DENT, DamageKind.BUCKLE)

    @property
    def is_material_loss(self) -> bool:
        """Metal gone, which is what a residual thickness is worked out from."""
        return self in (
            DamageKind.SCRATCH,
            DamageKind.GOUGE,
            DamageKind.NICK,
            DamageKind.CORROSION,
            DamageKind.LIGHTNING_BURN,
            DamageKind.HOLE,
            DamageKind.PUNCTURE,
        )

    @property
    def is_through_thickness(self) -> bool:
        """Damage that has gone all the way through, so there is nothing left to blend."""
        return self in (DamageKind.HOLE, DamageKind.PUNCTURE)

    @property
    def is_never_allowable(self) -> bool:
        """Kinds the manual has no allowable limit for at any size."""
        return self in (
            DamageKind.CRACK,
            DamageKind.HOLE,
            DamageKind.PUNCTURE,
            DamageKind.BUCKLE,
        )

    @property
    def has_a_bearing(self) -> bool:
        """Kinds that lie along a direction, so the report has to carry one."""
        return self in (
            DamageKind.CRACK,
            DamageKind.SCRATCH,
            DamageKind.GOUGE,
            DamageKind.BUCKLE,
        )

    @property
    def needs_depth(self) -> bool:
        return self.is_deformation or self.is_material_loss

    @property
    def needs_area(self) -> bool:
        """Kinds measured as a patch rather than as a line or a point."""
        return self in (
            DamageKind.CORROSION,
            DamageKind.DISBOND,
            DamageKind.LIGHTNING_BURN,
        )

    @property
    def section(self) -> int:
        """The manual section the limits for this kind are filed under."""
        return _SECTIONS[self]

    def __str__(self) -> str:
        return self.value


# Sections inside the allowable damage chapter. Deformation, material loss and
# cracking are three different arguments and the manual keeps them apart.
_SECTIONS = {
    DamageKind.DENT: 10,
    DamageKind.BUCKLE: 10,
    DamageKind.SCRATCH: 20,
    DamageKind.GOUGE: 20,
    DamageKind.NICK: 20,
    DamageKind.CORROSION: 30,
    DamageKind.LIGHTNING_BURN: 30,
    DamageKind.CRACK: 40,
    DamageKind.HOLE: 40,
    DamageKind.PUNCTURE: 40,
    DamageKind.DISBOND: 50,
}

# What could plausibly have caused each kind. A report pairing a kind with an
# origin outside this is either a misread form or something worth a phone call.
_PLAUSIBLE: dict[DamageKind, frozenset[DamageOrigin]] = {
    DamageKind.DENT: frozenset({DamageOrigin.IMPACT, DamageOrigin.HANDLING}),
    DamageKind.SCRATCH: frozenset({DamageOrigin.IMPACT, DamageOrigin.HANDLING}),
    DamageKind.GOUGE: frozenset({DamageOrigin.IMPACT, DamageOrigin.HANDLING}),
    DamageKind.NICK: frozenset({DamageOrigin.IMPACT, DamageOrigin.HANDLING}),
    DamageKind.CRACK: frozenset(
        {DamageOrigin.FATIGUE, DamageOrigin.IMPACT, DamageOrigin.MANUFACTURING}
    ),
    DamageKind.CORROSION: frozenset({DamageOrigin.ENVIRONMENT}),
    DamageKind.LIGHTNING_BURN: frozenset({DamageOrigin.LIGHTNING}),
    DamageKind.HOLE: frozenset({DamageOrigin.IMPACT, DamageOrigin.HANDLING}),
    DamageKind.PUNCTURE: frozenset({DamageOrigin.IMPACT, DamageOrigin.HANDLING}),
    DamageKind.BUCKLE: frozenset({DamageOrigin.IMPACT, DamageOrigin.MANUFACTURING}),
    DamageKind.DISBOND: frozenset(
        {DamageOrigin.ENVIRONMENT, DamageOrigin.MANUFACTURING, DamageOrigin.IMPACT}
    ),
}


def plausible_origins(kind: DamageKind) -> frozenset[DamageOrigin]:
    return _PLAUSIBLE[kind] | {DamageOrigin.UNKNOWN}


def is_plausible(kind: DamageKind, origin: DamageOrigin) -> bool:
    return origin in plausible_origins(kind)


def check_pairing(kind: DamageKind, origin: DamageOrigin) -> None:
    """Refuse a kind and a cause that do not go together."""
    if not is_plausible(kind, origin):
        raise BadReading(f"a {kind} does not come from {origin}")
