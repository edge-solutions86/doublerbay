"""The area a part covers on the skin, and whether two of them are in each other's way.

A doubler is not a point, it is a patch with a size, and once one is on the
aeroplane the next repair in that area has to clear it. Not touch it, clear it,
because two repairs whose fastener rows come within a few diameters of each
other transfer load into the same holes and neither one was sized for that.

The geometry is the rounded rectangle every other shape on the skin uses. What
a footprint adds is the two things a repair drawing asks for: the square patch
the shop cuts when the corners do not matter, and the longer side, which is
what a size limit is written against.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from enum import Enum

from doublerbay.errors import BadReading
from doublerbay.grid.surface import Offset
from doublerbay.measure.length import Length
from doublerbay.structure.material import Material
from doublerbay.structure.region import RoundedRect


@dataclass(frozen=True, slots=True)
class Footprint(RoundedRect):
    """The skin a part covers."""

    @classmethod
    def square(cls, centre: Offset, along: Length, around: Length) -> Footprint:
        """A patch with no corner radius, which is how a small doubler is cut."""
        return cls(centre, along, around, Length(0))

    @property
    def area_bound(self) -> Length:
        return self.longer_side

    def distance_to(self, point: Offset) -> Length:
        return self.edge_distance(point)


class DoublerForm(Enum):
    """How the patch sits on the skin, which decides what it does to the airflow."""

    EXTERNAL = "external"
    INTERNAL = "internal"
    FLUSH = "flush"

    @property
    def is_in_airflow(self) -> bool:
        """An external patch stands proud of the skin and the aerodynamicists care."""
        return self is DoublerForm.EXTERNAL

    @property
    def is_flush(self) -> bool:
        return self is DoublerForm.FLUSH

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True, slots=True)
class InstalledDoubler:
    """A repair patch already on the aeroplane, which the next one has to work around."""

    part: str
    form: DoublerForm
    footprint: Footprint
    material: Material
    gauge: Length
    fitted: date

    def __post_init__(self) -> None:
        if not self.part.strip():
            raise BadReading("an installed doubler has a part number")
        if self.gauge.mils <= 0:
            raise BadReading(f"{self.part}: a doubler has a thickness")

    def __str__(self) -> str:
        return f"{self.part} {self.form.value} {self.gauge.inches}"
