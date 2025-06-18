"""Finding the right row, which is where most of a manual page actually lives.

A limit page is a table with three keys: what kind of damage, what kind of skin
it is on, and what part of the panel it is in. Almost every row leaves at least
one of those blank, meaning any, and a page that spelt out every combination
would run to hundreds of rows nobody could check.

So the lookup is a specificity match, and the three keys do not weigh the same.
Where the damage is sitting beats what the skin is, because a fastener row is a
fastener row on every panel on the aeroplane, and a page that says nothing is
allowable on a row means it whether the row is in the crown or the belly. So a
row keyed on region alone outranks a row keyed on panel kind alone, and a row
keyed on both outranks either.

Two rows that still match equally specifically is not a tie to be broken by
ordering. It is a contradiction in the book, and it is raised rather than
silently resolved by whichever happened to be typed in first.
"""

from __future__ import annotations

from dataclasses import dataclass, field

from doublerbay.damage.kind import DamageKind
from doublerbay.errors import BadReading, NotOnFile
from doublerbay.fleet.model import Effectivity
from doublerbay.grid.ata import Reference
from doublerbay.manual.limits import LimitSet
from doublerbay.structure.build import Region
from doublerbay.structure.panel import PanelKind

# Region outranks panel kind, so a row keyed on where the damage sits beats one
# keyed on what the skin is, and a row keyed on both beats either.
DAMAGE_WEIGHT = 1
PANEL_WEIGHT = 2
REGION_WEIGHT = 4


@dataclass(frozen=True, slots=True)
class LimitKey:
    """What a row is keyed on. A field left out means any."""

    damage_kind: DamageKind
    panel_kind: PanelKind | None = None
    region: Region | None = None

    @property
    def specificity(self) -> int:
        """How tightly the row is keyed, with region weighing more than panel kind."""
        score = DAMAGE_WEIGHT
        if self.panel_kind is not None:
            score += PANEL_WEIGHT
        if self.region is not None:
            score += REGION_WEIGHT
        return score

    def matches(self, damage: DamageKind, panel: PanelKind, region: Region) -> bool:
        if self.damage_kind is not damage:
            return False
        if self.panel_kind is not None and self.panel_kind is not panel:
            return False
        return not (self.region is not None and self.region is not region)

    def __str__(self) -> str:
        panel = self.panel_kind.value if self.panel_kind else "any skin"
        region = self.region.value if self.region else "anywhere"
        return f"{self.damage_kind} on {panel} {region}"


@dataclass(frozen=True, slots=True)
class LimitRow:
    """One row of one table, with the limits it carries and who it applies to."""

    key: LimitKey
    limits: LimitSet
    reference: Reference
    effectivity: Effectivity = field(default_factory=Effectivity.all_aircraft)

    def __str__(self) -> str:
        return f"{self.reference} {self.key}: {self.limits}"


@dataclass(frozen=True, slots=True)
class LimitTable:
    """The allowable damage table for one subject, at one revision."""

    reference: Reference
    revision: int
    rows: tuple[LimitRow, ...]

    def __post_init__(self) -> None:
        if self.revision < 1:
            raise BadReading(f"{self.reference} is issued at a revision")
        if not self.rows:
            raise BadReading(f"{self.reference} has rows in it")

    def candidates(
        self, damage: DamageKind, panel: PanelKind, region: Region
    ) -> tuple[LimitRow, ...]:
        """Every row that could answer, most specific first."""
        matching = [row for row in self.rows if row.key.matches(damage, panel, region)]
        return tuple(sorted(matching, key=lambda row: -row.key.specificity))

    def lookup(self, damage: DamageKind, panel: PanelKind, region: Region) -> LimitRow:
        """The row that answers, refusing if the book contradicts itself."""
        matching = self.candidates(damage, panel, region)
        if not matching:
            raise NotOnFile(
                f"{self.reference} has no row for {damage} on {panel.value} {region.value}"
            )
        best = matching[0].key.specificity
        tied = [row for row in matching if row.key.specificity == best]
        if len(tied) > 1:
            raise BadReading(
                f"{self.reference} has {len(tied)} rows that answer for "
                f"{damage} on {panel.value} {region.value}"
            )
        return tied[0]

    def has_a_row_for(self, damage: DamageKind, panel: PanelKind, region: Region) -> bool:
        return bool(self.candidates(damage, panel, region))

    def kinds_covered(self) -> frozenset[DamageKind]:
        return frozenset(row.key.damage_kind for row in self.rows)

    def with_row(self, row: LimitRow) -> LimitTable:
        return LimitTable(self.reference, self.revision, (*self.rows, row))

    def __len__(self) -> int:
        return len(self.rows)

    def __str__(self) -> str:
        return f"{self.reference} rev {self.revision}, {len(self.rows)} rows"
