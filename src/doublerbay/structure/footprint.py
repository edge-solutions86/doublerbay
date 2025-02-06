"""The area a part covers on the skin, and whether two of them are in each other's way.

A doubler is not a point, it is a patch with a size, and once one is on the
aeroplane the next repair in that area has to clear it. Not touch it, clear it,
because two repairs whose fastener rows come within a few diameters of each
other transfer load into the same holes and neither one was sized for that.

So the footprint is carried as a rectangle with radiused corners in panel
coordinates, exactly the way it is drawn, and the question anyone asks of it is
either whether a point falls inside or whether another footprint is closer than
the clearance the manual demands.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from decimal import Decimal
from enum import Enum

from doublerbay.errors import BadReading
from doublerbay.grid.surface import Offset, hypotenuse
from doublerbay.measure.length import Length
from doublerbay.structure.material import Material

HALF = Decimal("0.5")


@dataclass(frozen=True, slots=True)
class Footprint:
    """A radiused rectangle on the skin, in panel coordinates."""

    centre: Offset
    along: Length
    around: Length
    corner_radius: Length

    def __post_init__(self) -> None:
        if self.along.mils <= 0 or self.around.mils <= 0:
            raise BadReading("a footprint has a size")
        if self.corner_radius.mils < 0:
            raise BadReading("a corner radius does not run backwards")
        if self.corner_radius > min(self.along, self.around) * HALF:
            raise BadReading("a corner radius past the middle is not a rectangle")

    @classmethod
    def square(cls, centre: Offset, along: Length, around: Length) -> Footprint:
        return cls(centre, along, around, Length(0))

    @property
    def area_bound(self) -> Length:
        """The longer side, which is what a size limit is usually written against."""
        return max(self.along, self.around)

    def _outside_box(self, point: Offset) -> tuple[Length, Length]:
        half_along = self.along * HALF - self.corner_radius
        half_around = self.around * HALF - self.corner_radius
        return (
            max(abs(point.along - self.centre.along) - half_along, Length(0)),
            max(abs(point.around - self.centre.around) - half_around, Length(0)),
        )

    def contains(self, point: Offset) -> bool:
        out_along, out_around = self._outside_box(point)
        return hypotenuse(out_along, out_around) <= self.corner_radius

    def distance_to(self, point: Offset) -> Length:
        out_along, out_around = self._outside_box(point)
        reach = hypotenuse(out_along, out_around)
        if reach <= self.corner_radius:
            return Length(0)
        return reach - self.corner_radius

    def grown(self, by: Length) -> Footprint:
        """The same patch with a margin round it, which is how clearance is tested."""
        if by.mils < 0:
            raise BadReading("a footprint does not grow inwards")
        return Footprint(
            self.centre,
            self.along + by * 2,
            self.around + by * 2,
            self.corner_radius + by,
        )

    def gap_along(self, other: Footprint) -> Length:
        overlap = (self.along + other.along) * HALF
        apart = abs(other.centre.along - self.centre.along)
        return max(apart - overlap, Length(0))

    def gap_around(self, other: Footprint) -> Length:
        overlap = (self.around + other.around) * HALF
        apart = abs(other.centre.around - self.centre.around)
        return max(apart - overlap, Length(0))

    def overlaps(self, other: Footprint) -> bool:
        """Two patches sharing skin, taken on the enclosing rectangles."""
        return self.gap_along(other).mils == 0 and self.gap_around(other).mils == 0

    def clearance_to(self, other: Footprint) -> Length:
        """The shortest gap between the two enclosing rectangles, zero if they touch."""
        along, around = self.gap_along(other), self.gap_around(other)
        if along.mils == 0:
            return around
        if around.mils == 0:
            return along
        return hypotenuse(along, around)

    def clears(self, other: Footprint, by: Length) -> bool:
        return self.clearance_to(other) >= by

    def __str__(self) -> str:
        return f"{self.along.inches} by {self.around.inches} at {self.centre}"


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
