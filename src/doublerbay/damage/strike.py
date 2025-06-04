"""A lightning strike, which is a burn and a hundred small questions.

The current has to get on and off the aeroplane, and it does that at whatever
is sharpest and most conductive, which on a skin panel means a fastener head or
a panel edge. What is left is a burn mark and a cluster of pits, each of them a
tiny local melt with a heat affected zone under it that is harder and more
brittle than the metal around it.

So a strike report is a pit count, the worst pit, and where it attached. A
strike on the open skin is usually a blend job. The same strike on a fastener
means that fastener came out of its hole with an arc across the joint, and the
hole under it has to be opened up and looked at whatever the surface says.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal

from doublerbay.damage.corrosion import square_inches
from doublerbay.errors import BadReading
from doublerbay.grid.surface import Offset
from doublerbay.measure.length import Length
from doublerbay.structure.footprint import Footprint

# Strikes with more attachment points than this are a swept stroke rather than
# a single attachment, and the whole zone gets looked at rather than each pit.
SWEPT_STROKE_PITS = 12


@dataclass(frozen=True, slots=True)
class LightningBurn:
    """One attachment area, with the pits found in it."""

    along: Length
    around: Length
    pit_count: int
    worst_pit: Length
    on_a_fastener: bool = False
    is_entry: bool = True

    def __post_init__(self) -> None:
        if self.along.mils <= 0 or self.around.mils <= 0:
            raise BadReading("a burn covers an area")
        if self.pit_count < 1:
            raise BadReading("a strike with no pits is a scorch mark, not a strike")
        if self.worst_pit.mils <= 0:
            raise BadReading("a pit has a depth")

    @property
    def area(self) -> Decimal:
        return square_inches(self.along, self.around)

    @property
    def is_swept(self) -> bool:
        """Enough attachment points to be a stroke dragged across the skin."""
        return self.pit_count >= SWEPT_STROKE_PITS

    @property
    def needs_the_fastener_out(self) -> bool:
        """A strike that attached on a fastener arced across the joint under it."""
        return self.on_a_fastener

    @property
    def pit_density(self) -> Decimal:
        """Pits per square inch, which is how a swept stroke is told from a cluster."""
        if self.area == 0:
            raise ZeroDivisionError("a burn with no area has no density")
        return (Decimal(self.pit_count) / self.area).quantize(Decimal("0.01"))

    def footprint(self, centre: Offset) -> Footprint:
        return Footprint.square(centre, self.along, self.around)

    def limit_depth(self) -> Length | None:
        """The deepest pit in the attachment area."""
        return self.worst_pit

    def worse_than(self, other: LightningBurn) -> bool:
        if self.on_a_fastener != other.on_a_fastener:
            return self.on_a_fastener
        return self.worst_pit > other.worst_pit

    def __str__(self) -> str:
        end = "entry" if self.is_entry else "exit"
        head = f"{end}, {self.pit_count} pits, worst {self.worst_pit.inches}"
        return f"{head}, on a fastener" if self.on_a_fastener else head
