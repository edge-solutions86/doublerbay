"""The damage report itself, which is the card the whole desk works off.

A finding is a number, a tail, a kind, the measurements that kind needs, where
it is, when it was found and by whom, and what the counters read at the time.
The last of those is the one people leave off, and without it the repeat
inspection has nothing to count from.

The kind and the measurements have to agree. A dent card carrying a crack
length is a form filled in wrong, and it is caught here rather than three
modules later where the error reads as a missing limit table. The same goes for
the cause: a corrosion finding raised off a ground handling report is either
the wrong kind or the wrong report, and either way somebody should look at it
before an engineer does.
"""

from __future__ import annotations

from dataclasses import dataclass, replace
from datetime import date

from doublerbay.damage.corrosion import Corrosion
from doublerbay.damage.crack import Crack
from doublerbay.damage.dent import Dent
from doublerbay.damage.geometry import Geometry
from doublerbay.damage.kind import DamageKind, DamageOrigin, check_pairing
from doublerbay.damage.opening import Buckle, Disbond, Opening
from doublerbay.damage.scoring import Scoring
from doublerbay.damage.strike import LightningBurn
from doublerbay.errors import BadReading
from doublerbay.fleet.check import DiscoverySource
from doublerbay.grid.location import FuselagePoint, WingPoint
from doublerbay.grid.surface import Offset
from doublerbay.ids import FindingId, Serial
from doublerbay.measure.length import Length
from doublerbay.measure.usage import Usage
from doublerbay.structure.footprint import Footprint

Point = FuselagePoint | WingPoint

# Which measurement set each kind is reported with. One entry per kind, and the
# absence of an entry is a kind nobody has written a form for yet.
_GEOMETRY_FOR: dict[DamageKind, type] = {
    DamageKind.DENT: Dent,
    DamageKind.SCRATCH: Scoring,
    DamageKind.GOUGE: Scoring,
    DamageKind.NICK: Scoring,
    DamageKind.CRACK: Crack,
    DamageKind.CORROSION: Corrosion,
    DamageKind.LIGHTNING_BURN: LightningBurn,
    DamageKind.HOLE: Opening,
    DamageKind.PUNCTURE: Opening,
    DamageKind.BUCKLE: Buckle,
    DamageKind.DISBOND: Disbond,
}


@dataclass(frozen=True, slots=True)
class Finding:
    """One damage report, as raised and as it stands now."""

    reference: FindingId
    serial: Serial
    kind: DamageKind
    geometry: Geometry
    point: Point
    found_on: date
    source: DiscoverySource
    origin: DamageOrigin
    at_usage: Usage
    revision: int = 1
    superseded_by: FindingId | None = None
    note: str = ""

    def __post_init__(self) -> None:
        expected = _GEOMETRY_FOR.get(self.kind)
        if expected is None:
            raise BadReading(f"there is no report form for a {self.kind}")
        if not isinstance(self.geometry, expected):
            raise BadReading(
                f"a {self.kind} is reported as {expected.__name__.lower()} measurements"
            )
        check_pairing(self.kind, self.origin)
        if self.source.carries_an_event and not self.origin.is_an_event:
            raise BadReading(
                f"a {self.source} names something that happened, and {self.origin} is not"
            )
        if self.revision < 1:
            raise BadReading("a finding starts at revision one")

    @property
    def is_open(self) -> bool:
        return self.superseded_by is None

    @property
    def is_never_allowable(self) -> bool:
        return self.kind.is_never_allowable

    @property
    def is_on_the_wing(self) -> bool:
        return isinstance(self.point, WingPoint)

    def footprint(self, centre: Offset) -> Footprint:
        """The skin the damage covers, whatever kind it is."""
        return self.geometry.footprint(centre)

    def governing_depth(self) -> Length:
        """The depth the limit tables read, asked of the measurements themselves."""
        depth = self.geometry.limit_depth()
        if depth is None:
            raise BadReading(f"a {self.kind} is not measured with a depth")
        return depth

    def re_measured(self, geometry: Geometry, on: date, at_usage: Usage) -> Finding:
        """The same damage looked at again, which is a new revision of one record."""
        if not isinstance(geometry, _GEOMETRY_FOR[self.kind]):
            raise BadReading(f"a {self.kind} is not re measured as something else")
        if on < self.found_on:
            raise BadReading("a re measurement does not happen before the finding")
        return replace(
            self,
            geometry=geometry,
            found_on=on,
            at_usage=at_usage,
            revision=self.revision + 1,
        )

    def superseded(self, by: FindingId) -> Finding:
        """Closed out because another record now describes this damage."""
        if by == self.reference:
            raise BadReading(f"{self.reference} cannot supersede itself")
        return replace(self, superseded_by=by)

    def __str__(self) -> str:
        head = f"{self.reference} {self.serial} {self.kind} at {self.point}"
        return head if self.revision == 1 else f"{head} rev {self.revision}"
