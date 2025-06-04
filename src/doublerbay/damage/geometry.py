"""What every set of damage measurements has in common, which is not much but is enough.

Whatever kind it is, two things get asked of a measurement set. What skin does
it cover, so its neighbours can be found, and what depth do the limit tables
read off it, if any. Everything else about a dent and a crack is different and
should stay different.

Those two used to be asked with a chain of isinstance checks in the finding
module, which meant the finding module had to be edited every time a new kind
of measurement was added and the knowledge about a dent lived somewhere other
than the dent. It answers itself now, and the chain is gone.

The depth is deliberately allowed to be nothing. A crack has a length and no
depth worth reading, and a caller that wants a depth off one is asking the
wrong question rather than getting a zero it will divide by.
"""

from __future__ import annotations

from typing import Protocol, runtime_checkable

from doublerbay.damage.corrosion import Corrosion
from doublerbay.damage.crack import Crack
from doublerbay.damage.dent import Dent
from doublerbay.damage.opening import Buckle, Disbond, Opening
from doublerbay.damage.scoring import Scoring
from doublerbay.damage.strike import LightningBurn
from doublerbay.grid.surface import Offset
from doublerbay.measure.length import Length
from doublerbay.structure.footprint import Footprint


@runtime_checkable
class Measured(Protocol):
    """A set of damage measurements, whatever kind it belongs to."""

    def footprint(self, centre: Offset) -> Footprint:
        """The skin it covers, placed at a point."""
        ...

    def limit_depth(self) -> Length | None:
        """The depth the limit tables read, or nothing for the kinds that have none."""
        ...


Geometry = Dent | Scoring | Crack | Corrosion | LightningBurn | Opening | Buckle | Disbond


def has_a_depth(geometry: Measured) -> bool:
    return geometry.limit_depth() is not None
