"""What is actually at one spot on a panel, once everything on it is taken together.

A panel is not a rectangle of aluminium. It is a rectangle of aluminium with a
chem mill pattern in it, a lap joint down one edge, three rows of fasteners in
that joint, a window in the middle, and somebody's doubler from four years ago
over in the corner. Damage lands on one spot and every one of those features
has an opinion about it.

They do not all get an equal say. The tightest rule wins, and the order they
win in is fixed here rather than argued about in the assessor: what is under an
existing repair, then what is at a cutout corner, then the rest of the cutout
band, then a fastener row, then the joint band around it, then the chem mill
step, then the land, and only what is left over is open field. The thickness
comes out of the same call, because it is read at the same spot and reading it
separately is how the two end up describing different places.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum

from doublerbay.errors import BadReading
from doublerbay.grid.lattice import Bay
from doublerbay.grid.surface import Offset
from doublerbay.measure.length import Length
from doublerbay.structure.cutout import Cutout
from doublerbay.structure.footprint import InstalledDoubler
from doublerbay.structure.joint import Joint, JointMember
from doublerbay.structure.panel import FuselagePanel
from doublerbay.structure.thickness import ThicknessMap


class Region(Enum):
    """Which of the panel's features a spot belongs to, tightest first."""

    UNDER_DOUBLER = "under an installed doubler"
    CUTOUT_CORNER = "at a cutout corner"
    CUTOUT_EDGE = "in a cutout keep out"
    FASTENER_ROW = "on a fastener row"
    JOINT_BAND = "in a joint band"
    CHEM_MILL_STEP = "on a chem mill step"
    LAND = "on a land"
    OPEN_FIELD = "in open field"

    @property
    def rank(self) -> int:
        return _RANKS[self]

    @property
    def is_open_field(self) -> bool:
        return self is Region.OPEN_FIELD

    def tighter_than(self, other: Region) -> bool:
        return self.rank < other.rank

    def __str__(self) -> str:
        return self.value


_RANKS = {region: index for index, region in enumerate(Region)}


@dataclass(frozen=True, slots=True)
class SkinSpot:
    """One place on a panel, with the reason it is called what it is called."""

    region: Region
    thickness: Length
    feature: str

    def __str__(self) -> str:
        return f"{self.region} ({self.feature}), {self.thickness.inches}"


@dataclass(frozen=True, slots=True)
class PanelBuild:
    """A panel with everything that has been done to it."""

    panel: FuselagePanel
    thickness: ThicknessMap
    joints: tuple[Joint, ...] = field(default_factory=tuple)
    cutouts: tuple[Cutout, ...] = field(default_factory=tuple)
    doublers: tuple[InstalledDoubler, ...] = field(default_factory=tuple)

    def __post_init__(self) -> None:
        if self.thickness.land < self.panel.nominal:
            raise BadReading(f"{self.panel.part}: the land is thinner than the panel nominal gauge")
        names = [cutout.name for cutout in self.cutouts]
        if len(set(names)) != len(names):
            raise BadReading(f"{self.panel.part}: two cutouts with the same name")

    def joint_on(self, member: JointMember) -> Joint | None:
        for joint in self.joints:
            if joint.follows == member:
                return joint
        return None

    def cutout_over(self, local: Offset) -> Cutout | None:
        """The cutout whose keep out band a spot is in, nearest edge first."""
        touching = [cutout for cutout in self.cutouts if cutout.in_keep_out(local)]
        if not touching:
            return None
        return min(touching, key=lambda cutout: cutout.edge_distance(local).mils)

    def doubler_over(self, local: Offset) -> InstalledDoubler | None:
        for doubler in self.doublers:
            if doubler.footprint.contains(local):
                return doubler
        return None

    def thickness_at(self, margin: Length, bay: Bay | None = None) -> Length:
        return self.thickness.at(margin, bay)

    def at(
        self,
        local: Offset,
        member: JointMember,
        margin: Length,
        bay: Bay | None = None,
    ) -> SkinSpot:
        """Classify a spot, taking the tightest feature that reaches it."""
        gauge = self.thickness_at(margin, bay)

        doubler = self.doubler_over(local)
        if doubler is not None:
            return SkinSpot(Region.UNDER_DOUBLER, gauge + doubler.gauge, doubler.part)

        cutout = self.cutout_over(local)
        if cutout is not None:
            region = Region.CUTOUT_CORNER if cutout.near_a_corner(local) else Region.CUTOUT_EDGE
            return SkinSpot(region, gauge, cutout.name)

        joint = self.joint_on(member)
        if joint is not None and joint.rows:
            row = joint.row_nearest(margin)
            if joint.distance_to_nearest_row(margin) <= row.size.min_edge_distance:
                return SkinSpot(Region.FASTENER_ROW, gauge, f"{joint.kind} {row}")
            if joint.is_within(margin):
                return SkinSpot(Region.JOINT_BAND, gauge, str(joint.kind))

        if self.thickness.on_the_step(margin):
            return SkinSpot(Region.CHEM_MILL_STEP, gauge, "chem mill step")
        if margin <= self.thickness.land_width and not self.thickness.is_flat:
            return SkinSpot(Region.LAND, gauge, f"land at {member}")
        return SkinSpot(Region.OPEN_FIELD, gauge, f"bay off {member}")
