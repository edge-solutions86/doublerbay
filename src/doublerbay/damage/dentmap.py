"""The dent map, which is every open damage on one aeroplane put in its place.

A card on its own says where it is in words. The map turns all of them into
coordinates on the same skin at the same time, which is the only way to answer
the two questions that need every card at once: what else is near this damage,
and how many are on this panel.

The second one is a limit in its own right. The manual allows a dent within a
size, and it separately allows only so many of them in one panel before the
panel comes off whatever size they are. A desk that assesses cards one at a
time will pass every one of them and miss that.
"""

from __future__ import annotations

from dataclasses import dataclass

from doublerbay.damage.cluster import Cluster, Placed, clusters
from doublerbay.damage.finding import Finding
from doublerbay.damage.kind import DamageKind
from doublerbay.errors import BadReading, NotOnFile
from doublerbay.fleet.catalogue import TypeDefinition
from doublerbay.grid.lattice import Bay
from doublerbay.grid.location import FuselagePoint, WingPoint
from doublerbay.ids import PanelId, Serial


def place(definition: TypeDefinition, finding: Finding) -> Placed:
    """Resolve one card onto the skin of the aeroplane it was raised against."""
    point = finding.point
    if isinstance(point, FuselagePoint):
        return Placed(finding, definition.barrel.grid.absolute(point))
    return Placed(finding, definition.wing_box.grid_for(point.side).absolute(point))


def panel_of(definition: TypeDefinition, finding: Finding) -> PanelId:
    """Which panel a card sits on, on either structure."""
    point = finding.point
    if isinstance(point, FuselagePoint):
        return definition.barrel.panel_at(point).panel.part
    return definition.wing_box.panel_at(point).panel.part


@dataclass(frozen=True, slots=True)
class DentMap:
    """Every open card on one tail, placed and grouped by the panel it is on."""

    serial: Serial
    definition: TypeDefinition
    placed: tuple[Placed, ...]
    panels: dict[PanelId, tuple[Placed, ...]]

    @classmethod
    def of(
        cls, serial: Serial, definition: TypeDefinition, findings: tuple[Finding, ...]
    ) -> DentMap:
        """Build the map, skipping nothing and refusing cards from another tail."""
        placed: list[Placed] = []
        grouped: dict[PanelId, list[Placed]] = {}
        for finding in findings:
            if finding.serial != serial:
                raise BadReading(f"{finding.reference} is not a card on {serial}")
            landed = place(definition, finding)
            placed.append(landed)
            grouped.setdefault(panel_of(definition, finding), []).append(landed)
        return cls(
            serial=serial,
            definition=definition,
            placed=tuple(placed),
            panels={part: tuple(cards) for part, cards in grouped.items()},
        )

    @property
    def count(self) -> int:
        return len(self.placed)

    @property
    def parts(self) -> tuple[PanelId, ...]:
        return tuple(sorted(self.panels, key=str))

    def on_panel(self, part: PanelId) -> tuple[Placed, ...]:
        return self.panels.get(part, ())

    def count_on(self, part: PanelId) -> int:
        """How many cards are on one panel, which is what a repeat damage limit reads."""
        return len(self.on_panel(part))

    def count_of_kind_on(self, part: PanelId, kind: DamageKind) -> int:
        return sum(1 for landed in self.on_panel(part) if landed.finding.kind is kind)

    def clusters_on(self, part: PanelId) -> tuple[Cluster, ...]:
        """The groups on one panel that have to be assessed together."""
        cards = self.on_panel(part)
        if not cards:
            return ()
        return clusters(list(cards))

    def cluster_holding(self, finding: Finding) -> Cluster:
        """Whichever group a given card ends up in once its neighbours are taken in."""
        part = panel_of(self.definition, finding)
        for group in self.clusters_on(part):
            if any(member.finding.reference == finding.reference for member in group.members):
                return group
        raise NotOnFile(f"{finding.reference} is not on this map")

    def in_bay(self, bay: Bay) -> tuple[Placed, ...]:
        """Cards falling in one bay, which is the unit most limits are written per."""
        found = []
        for landed in self.placed:
            point = landed.finding.point
            if isinstance(point, WingPoint):
                continue
            if self.definition.barrel.bay_at(point) == bay:
                found.append(landed)
        return tuple(found)

    def __str__(self) -> str:
        return f"{self.serial}: {self.count} open on {len(self.panels)} panels"
