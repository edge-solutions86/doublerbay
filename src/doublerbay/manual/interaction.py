"""The limits that count damages rather than measure them.

Every rule up to here has been about one damage and how big it is. These are
about how many, and they are the ones a desk working card by card never sees.
The book allows a dent of a size. It separately allows two of them in a bay,
six on a panel, and it says two inside a clear separation are assessed as one
damage rather than two.

Those three interact in a way that is easy to get wrong. Clustering happens
first, because it changes how many damages there are. A panel with eight dents
on it may be over the panel count at eight and inside it at five once three of
them have been taken together as one, and the count that matters is the one
after clustering.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal

from doublerbay.damage.cluster import SeparationRule
from doublerbay.errors import BadReading
from doublerbay.measure.length import Length


@dataclass(frozen=True, slots=True)
class CountLimits:
    """How many separate damages the book allows in each unit of structure."""

    per_bay: int | None = None
    per_panel: int | None = None
    per_cluster: int | None = None

    def __post_init__(self) -> None:
        for name, value in (
            ("per bay", self.per_bay),
            ("per panel", self.per_panel),
            ("per cluster", self.per_cluster),
        ):
            if value is not None and value < 1:
                raise BadReading(f"a count limit {name} allows at least one")
        if (
            self.per_bay is not None
            and self.per_panel is not None
            and self.per_bay > self.per_panel
        ):
            raise BadReading("a bay cannot hold more than the panel it is in")

    @property
    def counts_anything(self) -> bool:
        return any(limit is not None for limit in (self.per_bay, self.per_panel, self.per_cluster))

    def bay_exceeded(self, count: int) -> bool:
        return self.per_bay is not None and count > self.per_bay

    def panel_exceeded(self, count: int) -> bool:
        return self.per_panel is not None and count > self.per_panel

    def cluster_exceeded(self, count: int) -> bool:
        return self.per_cluster is not None and count > self.per_cluster

    def room_left_on_panel(self, count: int) -> int | None:
        """How many more the panel can take, or nothing if the book does not count."""
        if self.per_panel is None:
            return None
        return max(self.per_panel - count, 0)

    def __str__(self) -> str:
        parts = []
        if self.per_cluster is not None:
            parts.append(f"{self.per_cluster} in a group")
        if self.per_bay is not None:
            parts.append(f"{self.per_bay} a bay")
        if self.per_panel is not None:
            parts.append(f"{self.per_panel} a panel")
        return ", ".join(parts) if parts else "not counted"


@dataclass(frozen=True, slots=True)
class InteractionRules:
    """What the book says about damages near each other, as one object."""

    separation: SeparationRule
    counts: CountLimits

    @classmethod
    def from_book(
        cls,
        multiple: str = "1",
        floor: str = "1.000",
        per_bay: int | None = None,
        per_panel: int | None = None,
        per_cluster: int | None = None,
    ) -> InteractionRules:
        """Build the rules the way a limit page states them."""
        return cls(
            SeparationRule(Decimal(multiple), Length.of_inches(floor)),
            CountLimits(per_bay, per_panel, per_cluster),
        )

    def separation_between(self, first: Length, second: Length) -> Length:
        return self.separation.between(first, second)

    def __str__(self) -> str:
        return f"separation {self.separation}; counts {self.counts}"


# What the V320 book asks for where a page does not say otherwise.
DEFAULT_RULES = InteractionRules.from_book(
    multiple="1", floor="1.000", per_bay=2, per_panel=6, per_cluster=3
)
