"""The individual checks, each one a question with an answer and a consequence.

A rule here does two things. It says whether the damage got past, and it says
what the disposition becomes if it did not, which is not the same for every
rule. Failing a depth cap on a scratch means grind it out, because the metal
can be taken back and the limit met. Failing the same cap on a dent means
repair it, because there is nothing to grind. Failing an edge margin means
repair it whatever the kind, since moving the damage is not an option.

The one worth reading twice is the depth check. Whether it lands on rework or
repair depends on the cleanup rule for the kind and on the region the damage is
sitting in, so the same 0.010 scratch at the same depth on the same panel comes
out as two different answers two inches apart.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal

from doublerbay.assess.placement import Placement
from doublerbay.assess.verdict import Disposition, Reason
from doublerbay.damage.corrosion import Corrosion
from doublerbay.damage.crack import Crack
from doublerbay.damage.dent import Dent
from doublerbay.damage.finding import Finding
from doublerbay.damage.opening import Disbond
from doublerbay.damage.scoring import Scoring
from doublerbay.damage.strike import LightningBurn
from doublerbay.manual.cleanup import CleanupRule
from doublerbay.manual.limits import LimitSet
from doublerbay.measure.length import Length


@dataclass(frozen=True, slots=True)
class Check:
    """One rule's answer, with what the disposition becomes if it failed."""

    reason: Reason
    on_failure: Disposition

    @property
    def passed(self) -> bool:
        return self.reason.passed

    @property
    def outcome(self) -> Disposition:
        return Disposition.ALLOWABLE if self.passed else self.on_failure

    def __str__(self) -> str:
        return str(self.reason)


@dataclass(frozen=True, slots=True)
class Case:
    """One damage, where it is, and what the book says about it."""

    finding: Finding
    placement: Placement
    limits: LimitSet
    cleanup: CleanupRule

    @property
    def thickness(self) -> Length:
        return self.placement.thickness

    @property
    def depth(self) -> Length | None:
        return self.finding.geometry.limit_depth()

    @property
    def can_be_blended_here(self) -> bool:
        """Whether grinding is on the table at all, given the kind and the region."""
        return self.cleanup.allowed_in(self.placement.region)


def _pass(rule: str, detail: str) -> Reason:
    return Reason(rule, detail, True)


def _fail(rule: str, detail: str) -> Reason:
    return Reason(rule, detail, False)


def allowed_check(case: Case) -> Check:
    """Whether the page allows this damage at any size at all."""
    where = f"{case.placement.region} on {case.placement.panel_kind}"
    if case.limits.allowed:
        return Check(
            _pass("allowable", f"the book allows a {case.finding.kind} {where}"),
            Disposition.REPAIRABLE,
        )
    return Check(
        _fail("allowable", f"no allowable limit for a {case.finding.kind} {where}"),
        Disposition.REPAIRABLE,
    )


def depth_check(case: Case) -> Check | None:
    """Depth against the cap, with rework on the table only where grinding is allowed."""
    depth = case.depth
    cap = case.limits.depth_cap(case.thickness)
    if depth is None or cap is None:
        return None
    detail = f"{depth.inches} deep against a cap of {cap.inches} at {case.thickness.inches}"
    if depth <= cap:
        return Check(_pass("depth", detail), Disposition.REPAIRABLE)
    landing = Disposition.REWORKABLE if case.can_be_blended_here else Disposition.REPAIRABLE
    return Check(_fail("depth", detail), landing)


def residual_check(case: Case) -> Check | None:
    """What would be left under the damage, against the floor the page sets."""
    depth = case.depth
    floor = case.limits.residual_floor(case.thickness)
    if depth is None or floor is None:
        return None
    left = case.thickness - depth
    detail = f"{left.inches} left against a floor of {floor.inches}"
    if left >= floor:
        return Check(_pass("residual", detail), Disposition.REPAIRABLE)
    return Check(_fail("residual", detail), Disposition.REPAIRABLE)


def sharpness_check(case: Case) -> Check | None:
    """A dent is graded on how sharp it is as well as how deep."""
    dent = case.finding.geometry
    cap = case.limits.max_sharpness
    if not isinstance(dent, Dent) or cap is None:
        return None
    detail = f"{dent.sharpness} against a cap of {cap}"
    if dent.sharpness <= cap:
        return Check(_pass("sharpness", detail), Disposition.REPAIRABLE)
    return Check(_fail("sharpness", detail), Disposition.REPAIRABLE)


def smoothness_check(case: Case) -> Check | None:
    """A dent with a sharp bottom or a crack in it is never left alone."""
    dent = case.finding.geometry
    if not isinstance(dent, Dent):
        return None
    if dent.is_smooth:
        return Check(_pass("smoothness", "smooth bottom and no crack"), Disposition.REPAIRABLE)
    fault = "cracked" if dent.cracked else "sharp bottomed"
    return Check(_fail("smoothness", f"the dent is {fault}"), Disposition.REPAIRABLE)


def length_check(case: Case) -> Check | None:
    """Length against the cap, for the kinds that lie along a line."""
    geometry = case.finding.geometry
    cap = case.limits.max_length
    if cap is None or not isinstance(geometry, Scoring | Crack):
        return None
    detail = f"{geometry.length.inches} long against a cap of {cap.inches}"
    if geometry.length <= cap:
        return Check(_pass("length", detail), Disposition.REPAIRABLE)
    return Check(_fail("length", detail), Disposition.REPAIRABLE)


def area_check(case: Case) -> Check | None:
    """Area against the cap, for the kinds measured as a patch."""
    geometry = case.finding.geometry
    cap = case.limits.max_area
    if cap is None or not isinstance(geometry, Corrosion | Disbond | LightningBurn):
        return None
    detail = f"{geometry.area} sq in against a cap of {cap}"
    if geometry.area <= cap:
        return Check(_pass("area", detail), Disposition.REPAIRABLE)
    return Check(_fail("area", detail), Disposition.REPAIRABLE)


def edge_margin_check(case: Case) -> Check | None:
    """Distance to the nearest edge, which no amount of grinding improves."""
    floor = case.limits.edge_margin_floor(case.placement.nearest_fastener)
    if floor is None:
        return None
    margin = case.placement.edge_margin
    detail = f"{margin.inches} to the nearest edge against a floor of {floor.inches}"
    if margin >= floor:
        return Check(_pass("edge margin", detail), Disposition.REPAIRABLE)
    return Check(_fail("edge margin", detail), Disposition.REPAIRABLE)


def exfoliation_check(case: Case) -> Check | None:
    """Corrosion running along the grain, which blending does not reach."""
    corrosion = case.finding.geometry
    if not isinstance(corrosion, Corrosion):
        return None
    if not corrosion.exfoliating:
        return Check(_pass("exfoliation", "no exfoliation reported"), Disposition.REPAIRABLE)
    return Check(
        _fail("exfoliation", "exfoliation is not reached by a blend"), Disposition.REPAIRABLE
    )


def blend_share_check(case: Case) -> Check | None:
    """How much of the panel the cleanup would take, if a cleanup is on the table."""
    depth = case.depth
    if depth is None or not case.can_be_blended_here:
        return None
    covered = case.finding.footprint(case.placement.centre).longer_side
    extent = case.cleanup.blend_extent(covered, depth)
    share = Decimal(extent.mils) / Decimal(case.placement.panel_extent.mils)
    detail = f"a blend covering {extent.inches} of a {case.placement.panel_extent.inches} panel"
    if case.cleanup.within_panel_share(extent, case.placement.panel_extent):
        return Check(_pass("blend share", detail), Disposition.REPAIRABLE)
    return Check(_fail("blend share", f"{detail} is {share:.2f} of it"), Disposition.REPAIRABLE)


# Walked in this order so the trail reads the way an engineer would work it.
CHECKS = (
    allowed_check,
    smoothness_check,
    sharpness_check,
    depth_check,
    residual_check,
    length_check,
    area_check,
    exfoliation_check,
    edge_margin_check,
    blend_share_check,
)
