"""The rules that follow from where on the panel a damage landed.

The limit tables already vary by region, so a dent on a chem mill step is
measured against a different depth cap than one in open field. What the tables
do not carry is the handful of rules that are about the feature itself rather
than about the size of the damage.

Three of them. A chem mill step is where the skin changes thickness, and the
residual left after a blend has to be measured against the thin side rather
than the nominal, because that is what is actually there. A cutout corner is a
stress concentration and a crack pointing into one is a different problem from
a crack pointing away. And a damage under an existing doubler cannot be
inspected without taking the doubler off, so the disposition has to say so
rather than quietly assume somebody looked.

Like the joint rules, these only ever make the answer worse.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal

from doublerbay.assess.rules import Case, Check, _fail, _pass
from doublerbay.assess.verdict import Disposition
from doublerbay.damage.crack import Crack
from doublerbay.measure.length import Length
from doublerbay.structure.build import Region

# How much thinner the thin side of a step is, as a share of the nominal.
STEP_SHARE = Decimal("0.75")
# A crack this long at a cutout corner goes straight to the manufacturer.
CORNER_CRACK = Length(200)

# The regions each rule has anything to say about.
STEP_REGIONS = frozenset({Region.CHEM_MILL_STEP, Region.LAND})
CORNER_REGIONS = frozenset({Region.CUTOUT_CORNER, Region.CUTOUT_EDGE})


@dataclass(frozen=True, slots=True)
class RegionCase:
    """One damage and the feature of the panel it landed on."""

    case: Case

    @property
    def region(self) -> Region:
        return self.case.placement.region

    @property
    def thin_side(self) -> Length:
        """What is actually under the damage on the thin side of a step."""
        nominal = self.case.thickness
        return Length(int(Decimal(nominal.mils) * STEP_SHARE))

    @property
    def is_a_crack(self) -> bool:
        return isinstance(self.case.finding.geometry, Crack)

    def __str__(self) -> str:
        return f"{self.case.finding.reference} {self.region}"


def step_residual_check(held: RegionCase) -> Check | None:
    """On a step, a blend is measured against the thin side, not the nominal."""
    if held.region not in STEP_REGIONS:
        return None
    cut = held.case.cut_depth
    if cut is None:
        return None
    left = Length(held.thin_side.mils - cut.mils)
    written = f"{left.inches} in left of {held.thin_side.inches} in on the thin side"
    if left.mils <= 0:
        return Check(
            _fail("step residual", f"a blend here goes through, {written}"),
            Disposition.REPAIRABLE,
        )
    return Check(_pass("step residual", written), Disposition.REPAIRABLE)


def corner_crack_check(held: RegionCase) -> Check | None:
    """A crack at a cutout corner is a different problem from one anywhere else."""
    if held.region not in CORNER_REGIONS or not held.is_a_crack:
        return None
    geometry = held.case.finding.geometry
    assert isinstance(geometry, Crack)
    where = "at the corner" if held.region is Region.CUTOUT_CORNER else "in the keep out"
    if geometry.length >= CORNER_CRACK:
        return Check(
            _fail(
                "cutout corner",
                f"{geometry.length.inches} in crack {where}, past what the book covers",
            ),
            Disposition.OUT_OF_LIMITS,
        )
    return Check(
        _pass("cutout corner", f"{geometry.length.inches} in crack {where}"),
        Disposition.OUT_OF_LIMITS,
    )


def under_doubler_check(held: RegionCase) -> Check | None:
    """Nothing under a fitted doubler was measured without taking it off."""
    if held.region is not Region.UNDER_DOUBLER:
        return None
    return Check(
        _fail(
            "under a doubler",
            "the doubler comes off before this can be measured or blended",
        ),
        Disposition.REPAIRABLE,
    )


REGION_CHECKS = (
    step_residual_check,
    corner_crack_check,
    under_doubler_check,
)


def checks_for(case: Case) -> tuple[Check, ...]:
    """Every region rule that has something to say about this damage."""
    held = RegionCase(case)
    return tuple(one for one in (check(held) for check in REGION_CHECKS) if one is not None)
