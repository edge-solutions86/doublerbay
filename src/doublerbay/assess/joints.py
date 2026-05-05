"""The rules that only bite when the damage is near a fastener row.

Away from a joint a scratch is a scratch. In a fastener row it is a scratch in
the most highly loaded strip of skin on the aeroplane, sitting in the stress
field of a hole, and the same measurement means something entirely different.

Three rules, and none of them are about size. Whether the damage sits inside
the edge margin, because a cut between the hole and the panel edge has taken
the material that was carrying the load past the hole. Whether it runs along
the row, because a crack parallel to a rivet line links holes and a crack
across one does not. And whether the joint is fatigue critical, because a
longitudinal lap joint is where the fleet's cracks actually come from.

These are additions rather than replacements. A damage in a joint still has to
pass every ordinary limit, and this module can only make the answer worse.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal

from doublerbay.assess.rules import Case, Check, _fail, _pass
from doublerbay.assess.verdict import Disposition
from doublerbay.damage.crack import Crack
from doublerbay.damage.finding import Finding
from doublerbay.damage.scoring import Scoring
from doublerbay.measure.angle import Angle
from doublerbay.measure.length import Length
from doublerbay.structure.joint import Joint

# How near a row counts as being in it, in fastener diameters.
IN_THE_ROW = Decimal("2.0")
# A cut this far off the row's own line still counts as running along it.
ALONG_WITHIN_DEGREES = 20


@dataclass(frozen=True, slots=True)
class JointCase:
    """One damage that landed on a joint, with the joint it landed on."""

    case: Case
    joint: Joint

    @property
    def margin(self) -> Length:
        return self.case.placement.edge_margin

    @property
    def to_the_row(self) -> Length:
        return self.joint.distance_to_nearest_row(self.margin)

    @property
    def in_diameters(self) -> Decimal | None:
        """How far off the row, in fastener diameters, if a size is known."""
        size = self.case.placement.nearest_fastener
        if size is None:
            return None
        return Decimal(self.to_the_row.mils) / Decimal(size.diameter.mils)

    @property
    def is_in_the_row(self) -> bool:
        held = self.in_diameters
        return held is not None and held <= IN_THE_ROW

    def __str__(self) -> str:
        return f"{self.case.finding.reference} on {self.joint}"


def bearing_of(finding: Finding) -> Angle | None:
    """Which way the damage runs, for the kinds that have a direction."""
    geometry = finding.geometry
    if isinstance(geometry, Crack | Scoring):
        return geometry.bearing
    return None


def runs_along(finding: Finding, joint: Joint) -> bool | None:
    """Whether the damage lies along the row rather than across it.

    A longitudinal joint runs along the body, so a damage at or near zero
    bearing is running with the rivet line. Nothing is claimed for a damage
    with no direction, which is why this can answer nothing.
    """
    bearing = bearing_of(finding)
    if bearing is None:
        return None
    line = 0 if joint.is_longitudinal else 900
    off = abs(bearing.tenths - line)
    return min(off, 1800 - off) <= ALONG_WITHIN_DEGREES * 10


def inside_margin_check(held: JointCase) -> Check | None:
    """A damage between the fastener and the panel edge has taken load path."""
    if not held.is_in_the_row:
        return None
    written = f"{held.to_the_row.inches} in from the nearest row"
    if held.margin > held.to_the_row:
        return Check(
            _fail("joint edge margin", f"inside the fastener row, {written}"),
            Disposition.REPAIRABLE,
        )
    return Check(_pass("joint edge margin", f"clear of the row, {written}"), Disposition.REPAIRABLE)


def along_the_row_check(held: JointCase) -> Check | None:
    """A cut running along a rivet line links holes, and one across it does not."""
    along = runs_along(held.case.finding, held.joint)
    if along is None or not held.is_in_the_row:
        return None
    if along:
        return Check(
            _fail("along the row", f"runs with the rivet line on {held.joint.kind}"),
            Disposition.REPAIRABLE,
        )
    return Check(
        _pass("along the row", "runs across the rivet line rather than with it"),
        Disposition.REPAIRABLE,
    )


def fatigue_critical_check(held: JointCase) -> Check | None:
    """A damage in a fatigue critical joint is never left as it stands."""
    if not held.is_in_the_row:
        return None
    if held.joint.is_fatigue_critical:
        return Check(
            _fail("fatigue critical", f"{held.joint.kind} carries the pressurisation cycle"),
            Disposition.REWORKABLE,
        )
    return Check(
        _pass("fatigue critical", f"{held.joint.kind} is not a fatigue critical joint"),
        Disposition.REWORKABLE,
    )


JOINT_CHECKS = (
    inside_margin_check,
    along_the_row_check,
    fatigue_critical_check,
)


def checks_for(case: Case, joint: Joint | None) -> tuple[Check, ...]:
    """Every joint rule that has something to say about this damage."""
    if joint is None:
        return ()
    held = JointCase(case, joint)
    return tuple(one for one in (check(held) for check in JOINT_CHECKS) if one is not None)
