"""The blend out, written as an instruction somebody can take to the aeroplane.

A rework disposition is not an answer on its own. What goes on the work card is
how deep to cut, how far to feather it out, what has to be left when you are
done, and what to do about the finish. The last one is the part that gets
missed. Clad sheet has a layer of nearly pure aluminium on it that is there to
corrode instead of the alloy, and a blend that goes through it has taken that
protection off. The metal is fine and the corrosion protection is gone, and
nothing about the depth reading says so.

So the plan says whether the cut breaks the cladding, and if it does it carries
the finish that has to go back on. That is a line on the work card and it is
the difference between a repair that lasts and a corrosion finding in four
years at the same spot.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal

from doublerbay.assess.placement import Placement
from doublerbay.damage.corrosion import square_inches
from doublerbay.damage.finding import Finding
from doublerbay.errors import Refused
from doublerbay.manual.cleanup import CleanupRule
from doublerbay.manual.limits import LimitSet
from doublerbay.measure.length import Length
from doublerbay.measure.ratio import Taper


@dataclass(frozen=True, slots=True)
class BlendPlan:
    """What the shop is being asked to grind, and what has to be left."""

    cut_depth: Length
    along: Length
    around: Length
    taper: Taper
    residual: Length
    breaches_clad: bool
    finish_required: bool

    @property
    def area(self) -> Decimal:
        return square_inches(self.along, self.around)

    @property
    def longer_side(self) -> Length:
        return max(self.along, self.around)

    def leaves_enough(self, limits: LimitSet, thickness: Length) -> bool:
        floor = limits.residual_floor(thickness)
        return floor is None or self.residual >= floor

    def within_area(self, limits: LimitSet) -> bool:
        return limits.max_area is None or self.area <= limits.max_area

    def __str__(self) -> str:
        head = (
            f"cut {self.cut_depth.inches} at {self.taper}, "
            f"{self.along.inches} by {self.around.inches}, "
            f"{self.residual.inches} left"
        )
        return f"{head}, refinish" if self.finish_required else head


def plan_blend(
    finding: Finding, placement: Placement, cleanup: CleanupRule, limits: LimitSet
) -> BlendPlan:
    """Work out the cleanup for one damage, or refuse to."""
    depth = finding.geometry.limit_depth()
    if depth is None:
        raise Refused("nothing to blend", f"a {finding.kind} is not measured with a depth")
    if not cleanup.allowed_in(placement.region):
        raise Refused("no blend here", f"a blend is not allowed {placement.region}")

    cut = cleanup.cut_depth(depth)
    residual = placement.thickness - cut
    if residual.mils <= 0:
        raise Refused(
            "nothing left", f"a {cut.inches} cut goes through {placement.thickness.inches}"
        )

    covered = finding.footprint(placement.centre)
    clad = placement.material.clad_thickness(placement.thickness)
    breaches = cut > clad

    return BlendPlan(
        cut_depth=cut,
        along=cleanup.blend_extent(covered.along, cut),
        around=cleanup.blend_extent(covered.around, cut),
        taper=cleanup.taper,
        residual=residual,
        breaches_clad=breaches,
        finish_required=breaches and cleanup.finish_required_through_clad,
    )


def blend_would_clear(
    finding: Finding, placement: Placement, cleanup: CleanupRule, limits: LimitSet
) -> bool:
    """Whether grinding it out actually gets the damage inside the book."""
    try:
        plan = plan_blend(finding, placement, cleanup, limits)
    except Refused:
        return False
    if not plan.leaves_enough(limits, placement.thickness):
        return False
    if not plan.within_area(limits):
        return False
    return cleanup.within_panel_share(plan.longer_side, placement.panel_extent)
