"""Two damages close together are one damage, and working out which ones is not obvious.

The manual will not let two findings be assessed separately if they are inside
a clear separation of each other, and the separation it asks for is the larger
of the two damages. So whether a small dent has to be taken together with a
big one nearby depends on the big one, not on the small one, and the answer
changes when the big one is re measured.

Interaction is also transitive and that is where it catches people. A and B are
far enough apart, B and C are far enough apart, but A and C are not, and the
result is one cluster of three with an envelope larger than any pair. Nobody
gets that right by eye, which is why it is done here with a union find rather
than by walking down the list comparing neighbours.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal

from doublerbay.damage.finding import Finding
from doublerbay.errors import BadReading
from doublerbay.grid.surface import Offset
from doublerbay.measure.length import Length
from doublerbay.structure.footprint import Footprint

# Nothing is ever assessed separately from something inside this, however small
# both of them are. It is the width of a blend runout on the thinnest skin.
SEPARATION_FLOOR = Length.of_inches("1.000")
_HALF = Decimal("0.5")


@dataclass(frozen=True, slots=True)
class SeparationRule:
    """How much clear skin the book asks for between two damages.

    The multiple is applied to the larger of the two extents and the floor is
    what a pair of very small damages get instead. Both come off a manual page
    rather than out of this module, which only knows how to apply them.
    """

    multiple: Decimal = Decimal("1")
    floor: Length = SEPARATION_FLOOR

    def __post_init__(self) -> None:
        if self.multiple <= 0:
            raise BadReading("a separation multiple is a multiple")
        if self.floor.mils < 0:
            raise BadReading("a separation floor does not run backwards")

    def between(self, first: Length, second: Length) -> Length:
        return max(max(first, second) * self.multiple, self.floor)

    def __str__(self) -> str:
        return f"{self.multiple} times the larger, floor {self.floor.inches}"


DEFAULT_SEPARATION = SeparationRule()


@dataclass(frozen=True, slots=True)
class Placed:
    """A finding with its centre resolved into skin coordinates."""

    finding: Finding
    centre: Offset

    @property
    def footprint(self) -> Footprint:
        return self.finding.footprint(self.centre)

    @property
    def extent(self) -> Length:
        """The longer side of what it covers, which is what separation is written against."""
        return self.footprint.longer_side

    def __str__(self) -> str:
        return f"{self.finding.reference} at {self.centre}"


def separation_required(
    first: Placed, second: Placed, rule: SeparationRule = DEFAULT_SEPARATION
) -> Length:
    """Clear separation asked for between two damages, taken off the larger of them."""
    return rule.between(first.extent, second.extent)


def gap_between(first: Placed, second: Placed) -> Length:
    return first.footprint.clearance_to(second.footprint)


def interacts(first: Placed, second: Placed, rule: SeparationRule = DEFAULT_SEPARATION) -> bool:
    """Whether the two have to be assessed as one damage."""
    return gap_between(first, second) < separation_required(first, second, rule)


@dataclass(frozen=True, slots=True)
class Cluster:
    """One or more findings that have to be taken together."""

    members: tuple[Placed, ...]

    def __post_init__(self) -> None:
        if not self.members:
            raise BadReading("a cluster has something in it")

    @property
    def size(self) -> int:
        return len(self.members)

    @property
    def is_single(self) -> bool:
        return self.size == 1

    @property
    def envelope(self) -> Footprint:
        """The box that covers every member, which is what the limits are read against."""
        boxes = [placed.footprint for placed in self.members]
        first_along = min(box.centre.along - box.along * _HALF for box in boxes)
        last_along = max(box.centre.along + box.along * _HALF for box in boxes)
        first_around = min(box.centre.around - box.around * _HALF for box in boxes)
        last_around = max(box.centre.around + box.around * _HALF for box in boxes)
        centre = Offset(
            first_along + (last_along - first_along) * _HALF,
            first_around + (last_around - first_around) * _HALF,
        )
        return Footprint.square(centre, last_along - first_along, last_around - first_around)

    def references(self) -> tuple[str, ...]:
        return tuple(str(placed.finding.reference) for placed in self.members)

    def deepest(self) -> Placed:
        """The member with the largest extent, which is what governs the cluster."""
        return max(self.members, key=lambda placed: placed.extent.mils)

    def __str__(self) -> str:
        if self.is_single:
            return str(self.members[0])
        return f"{self.size} together, envelope {self.envelope.longer_side.inches}"


def clusters(
    placed: list[Placed], rule: SeparationRule = DEFAULT_SEPARATION
) -> tuple[Cluster, ...]:
    """Group findings into the sets that have to be assessed together."""
    parent = list(range(len(placed)))

    def root(index: int) -> int:
        while parent[index] != index:
            parent[index] = parent[parent[index]]
            index = parent[index]
        return index

    for i, first in enumerate(placed):
        for j in range(i + 1, len(placed)):
            if interacts(first, placed[j], rule):
                parent[root(i)] = root(j)

    grouped: dict[int, list[Placed]] = {}
    for index, entry in enumerate(placed):
        grouped.setdefault(root(index), []).append(entry)
    return tuple(Cluster(tuple(members)) for members in grouped.values())
