"""What the book says about grinding damage out, which is not the same as what it allows.

There are two different questions and they get run together. Is this damage
acceptable as it stands, and if not, can it be blended out until it is. The
second has its own rules and they do not come off the same row: how gently the
blend has to run out, how much of the panel one blend may take, whether a blend
is allowed at all in this part of the structure, and what has to be done
afterwards if it has gone through the cladding.

The one that catches people is the chem mill step. A blend on a step is
stacking one stress raiser on another, and the book will not have it at any
depth. So the answer to can this be blended is not a number, it is a look at
where the damage is sitting before any number is read at all.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal

from doublerbay.damage.kind import DamageKind
from doublerbay.errors import BadReading, NotOnFile
from doublerbay.measure.length import Length
from doublerbay.measure.ratio import Taper
from doublerbay.structure.build import Region

# A blend cannot stop at the bottom of the damage. The metal right under a
# scratch has been worked and has to come out with it, and the shop cannot hold
# a hand tool to a thousandth anyway.
DEFAULT_CUT_BELOW = Length.of_inches("0.002")

# Where the book will not let a blend happen whatever the depth is.
NO_BLEND_REGIONS: frozenset[Region] = frozenset(
    {Region.CHEM_MILL_STEP, Region.FASTENER_ROW, Region.UNDER_DOUBLER}
)


@dataclass(frozen=True, slots=True)
class CleanupRule:
    """How a damage of one kind may be ground out, if it may be at all."""

    taper: Taper
    blendable: bool = True
    min_end_radius: Length | None = None
    max_blend_fraction: Decimal | None = None
    finish_required_through_clad: bool = True
    min_cut_below: Length = DEFAULT_CUT_BELOW

    def __post_init__(self) -> None:
        if not self.blendable and self.min_end_radius is not None:
            raise BadReading("a kind that cannot be blended carries no blend radius")
        if self.max_blend_fraction is not None and not 0 < self.max_blend_fraction <= 1:
            raise BadReading("a blend fraction is a part of the panel")
        if self.min_cut_below.mils < 0:
            raise BadReading("a blend does not cut upwards")

    def cut_depth(self, damage_depth: Length) -> Length:
        """How deep the blend actually goes, which is past the bottom of the damage."""
        if damage_depth.mils < 0:
            raise BadReading("a blend depth does not run backwards")
        if not self.blendable:
            return damage_depth
        return damage_depth + self.min_cut_below

    def runout(self, depth: Length) -> Length:
        """How far out from the deepest point the blend has to feather."""
        if depth.mils < 0:
            raise BadReading("a blend depth does not run backwards")
        return self.taper.runout(depth)

    def blend_extent(self, damage_extent: Length, depth: Length) -> Length:
        """How much skin the finished blend covers along one axis."""
        return damage_extent + self.runout(depth) * 2

    def allowed_in(self, region: Region) -> bool:
        """Whether a blend may happen at all where this damage is sitting."""
        return self.blendable and region not in NO_BLEND_REGIONS

    def within_panel_share(self, blend_extent: Length, panel_extent: Length) -> bool:
        """Whether the blend takes more of the panel than the book allows one to."""
        if self.max_blend_fraction is None:
            return True
        if panel_extent.mils <= 0:
            raise BadReading("a panel extent is a length")
        share = Decimal(blend_extent.mils) / Decimal(panel_extent.mils)
        return share <= self.max_blend_fraction

    def __str__(self) -> str:
        if not self.blendable:
            return "not blendable"
        head = f"blend at {self.taper}"
        if self.max_blend_fraction is not None:
            head = f"{head}, up to {self.max_blend_fraction * 100}% of the panel"
        return head


@dataclass(frozen=True, slots=True)
class CleanupRules:
    """The blend rules for every kind the book has one for."""

    by_kind: dict[DamageKind, CleanupRule]

    def for_kind(self, kind: DamageKind) -> CleanupRule:
        try:
            return self.by_kind[kind]
        except KeyError:
            raise NotOnFile(f"the book has no cleanup rule for a {kind}") from None

    def covers(self, kind: DamageKind) -> bool:
        return kind in self.by_kind

    def kinds(self) -> frozenset[DamageKind]:
        return frozenset(self.by_kind)

    def __str__(self) -> str:
        return f"cleanup rules for {len(self.by_kind)} kinds"


def standard_rules() -> CleanupRules:
    """The V320 cleanup rules, which are the same for every skin chapter."""
    gentle = Taper(20)
    return CleanupRules(
        {
            DamageKind.DENT: CleanupRule(gentle, blendable=False),
            DamageKind.SCRATCH: CleanupRule(
                gentle, min_end_radius=Length.of_inches("0.250"), max_blend_fraction=Decimal("0.25")
            ),
            DamageKind.GOUGE: CleanupRule(
                gentle, min_end_radius=Length.of_inches("0.250"), max_blend_fraction=Decimal("0.25")
            ),
            DamageKind.NICK: CleanupRule(
                gentle, min_end_radius=Length.of_inches("0.125"), max_blend_fraction=Decimal("0.10")
            ),
            DamageKind.CORROSION: CleanupRule(
                Taper(30),
                min_end_radius=Length.of_inches("0.500"),
                max_blend_fraction=Decimal("0.35"),
            ),
            DamageKind.LIGHTNING_BURN: CleanupRule(
                gentle, min_end_radius=Length.of_inches("0.250"), max_blend_fraction=Decimal("0.20")
            ),
            DamageKind.CRACK: CleanupRule(gentle, blendable=False),
            DamageKind.HOLE: CleanupRule(gentle, blendable=False),
            DamageKind.PUNCTURE: CleanupRule(gentle, blendable=False),
            DamageKind.BUCKLE: CleanupRule(gentle, blendable=False),
            DamageKind.DISBOND: CleanupRule(gentle, blendable=False),
        }
    )
