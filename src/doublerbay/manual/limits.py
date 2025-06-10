"""The numbers on a limit page, and the fact that half of them are not numbers.

A limit page rarely says a damage may be five thousandths deep. It says five
thousandths or ten percent of the skin thickness, whichever is less, and it
says the remaining thickness must not fall below eighty percent, and it says
edge margin is at least two diameters. Every one of those depends on something
that is not on the page: the thickness where the damage actually is, and the
size of the fastener nearest to it.

That is why a limit set is a set of rules rather than a row of numbers, and why
resolving it takes arguments. The rule for combining an absolute cap with a
fractional one is that the tighter of the two wins, and for a floor it is the
higher of the two. Getting that backwards clears damage the manual does not.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal

from doublerbay.errors import BadReading
from doublerbay.measure.fastener import FastenerSize
from doublerbay.measure.length import Length
from doublerbay.measure.ratio import Ratio, Taper


@dataclass(frozen=True, slots=True)
class LimitSet:
    """One page's worth of limits, resolved against a thickness when it is read."""

    allowed: bool = True
    max_depth: Length | None = None
    max_depth_fraction: Ratio | None = None
    max_length: Length | None = None
    max_area: Decimal | None = None
    max_sharpness: Ratio | None = None
    min_residual: Length | None = None
    min_residual_fraction: Ratio | None = None
    min_edge_margin: Length | None = None
    min_edge_margin_diameters: Decimal | None = None
    blend_taper: Taper | None = None

    def __post_init__(self) -> None:
        if not self.allowed and self._carries_a_number():
            raise BadReading("a limit set that allows nothing does not carry limits")
        if self.max_area is not None and self.max_area <= 0:
            raise BadReading("an area limit is an area")
        if self.min_edge_margin_diameters is not None and self.min_edge_margin_diameters <= 0:
            raise BadReading("an edge margin in diameters is a distance")

    def _carries_a_number(self) -> bool:
        return any(
            value is not None
            for value in (
                self.max_depth,
                self.max_depth_fraction,
                self.max_length,
                self.max_area,
                self.max_sharpness,
                self.min_residual,
                self.min_residual_fraction,
            )
        )

    @classmethod
    def nothing_allowable(cls) -> LimitSet:
        """A page that says this damage is never acceptable as it stands."""
        return cls(allowed=False)

    @property
    def is_open(self) -> bool:
        """A page that allows something but caps nothing, which the audit looks for."""
        return self.allowed and not self._carries_a_number()

    def depth_cap(self, thickness: Length) -> Length | None:
        """The deepest the damage may be, taking the tighter of the two forms."""
        caps = [
            cap
            for cap in (self.max_depth, self._fraction_of(self.max_depth_fraction, thickness))
            if cap is not None
        ]
        return min(caps) if caps else None

    def residual_floor(self, thickness: Length) -> Length | None:
        """The least metal that may be left, taking the higher of the two forms."""
        floors = [
            floor
            for floor in (
                self.min_residual,
                self._fraction_of(self.min_residual_fraction, thickness),
            )
            if floor is not None
        ]
        return max(floors) if floors else None

    def edge_margin_floor(self, size: FastenerSize | None = None) -> Length | None:
        """The least distance to an edge, in inches or in diameters of the nearest fastener."""
        floors = [floor for floor in (self.min_edge_margin,) if floor is not None]
        if self.min_edge_margin_diameters is not None:
            if size is None:
                raise BadReading("an edge margin in diameters needs a fastener size to read it")
            floors.append(size.diameters(self.min_edge_margin_diameters))
        return max(floors) if floors else None

    def implied_residual(self, thickness: Length) -> Length | None:
        """What the depth cap leaves behind, which the residual floor has to agree with."""
        cap = self.depth_cap(thickness)
        return None if cap is None else thickness - cap

    @staticmethod
    def _fraction_of(ratio: Ratio | None, thickness: Length) -> Length | None:
        return None if ratio is None else ratio.of(thickness)

    def __str__(self) -> str:
        if not self.allowed:
            return "nothing allowable"
        parts = []
        if self.max_depth is not None:
            parts.append(f"depth {self.max_depth.inches}")
        if self.max_depth_fraction is not None:
            parts.append(f"depth {self.max_depth_fraction}")
        if self.max_length is not None:
            parts.append(f"length {self.max_length.inches}")
        if self.max_area is not None:
            parts.append(f"area {self.max_area}")
        if self.max_sharpness is not None:
            parts.append(f"sharpness {self.max_sharpness}")
        if self.min_residual_fraction is not None:
            parts.append(f"residual {self.min_residual_fraction}")
        return ", ".join(parts) if parts else "no limits stated"
