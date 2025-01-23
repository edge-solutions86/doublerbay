"""What the skin actually measures where the damage is.

A panel has one part number and several thicknesses. It leaves the mill at full
gauge and then gets chemically milled away in the middle of every bay, leaving
a full thickness land along the members where the fasteners go and a thinner
pocket in between. Between the two there is a taper, because a chem mill step
with a sharp corner is a stress raiser and would crack before anything else on
the panel did.

This is the reason a damage assessment cannot start from the panel drawing. A
dent 0.020 deep is a third of the way through a 0.063 land and half way
through the 0.040 pocket two inches away from it, and the limit table is keyed
on the thickness at the damage. Read the wrong one and the disposition is
wrong in a direction nobody catches, because it clears rather than holds.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from decimal import Decimal

from doublerbay.errors import BadReading
from doublerbay.grid.lattice import Bay
from doublerbay.measure.length import Length
from doublerbay.measure.ratio import Taper

# The step the mills hold unless a drawing says otherwise.
STANDARD_STEP = Taper(20)


@dataclass(frozen=True, slots=True)
class Pad:
    """A local thickening, usually where a fitting or an earlier repair sits."""

    name: str
    bay: Bay
    gauge: Length

    def __post_init__(self) -> None:
        if not self.name.strip():
            raise BadReading("a pad has a name")
        if self.gauge.mils <= 0:
            raise BadReading(f"{self.name}: a pad has a thickness")

    def __str__(self) -> str:
        return f"{self.name} {self.gauge.inches} at {self.bay}"


@dataclass(frozen=True, slots=True)
class ThicknessMap:
    """Land, pocket and the taper between them, plus anything padded locally."""

    land: Length
    pocket: Length
    land_width: Length
    step: Taper = STANDARD_STEP
    pads: tuple[Pad, ...] = field(default_factory=tuple)

    def __post_init__(self) -> None:
        if self.land.mils <= 0:
            raise BadReading("a land has a thickness")
        if self.pocket.mils <= 0:
            raise BadReading("a pocket has a thickness")
        if self.pocket > self.land:
            raise BadReading(f"pocket {self.pocket} is thicker than land {self.land}")
        if self.land_width.mils < 0:
            raise BadReading("a land width does not run backwards")
        for pad in self.pads:
            if pad.gauge < self.land:
                raise BadReading(f"{pad.name} is thinner than the land it sits on")

    @classmethod
    def flat(cls, gauge: Length) -> ThicknessMap:
        """A panel that was never milled, which is most of the tail cone."""
        return cls(land=gauge, pocket=gauge, land_width=Length(0))

    @property
    def is_flat(self) -> bool:
        return self.land == self.pocket

    @property
    def step_depth(self) -> Length:
        return self.land - self.pocket

    @property
    def step_run(self) -> Length:
        """How far in from the land the taper takes to reach the pocket floor."""
        return self.step.runout(self.step_depth)

    @property
    def pocket_starts_at(self) -> Length:
        return self.land_width + self.step_run

    def at(self, margin: Length, bay: Bay | None = None) -> Length:
        """The thickness this far in from the nearest bounding member.

        A pad on the bay overrides everything, because a pad is bonded or
        riveted on top of the whole bay and the mill never touched it.
        """
        if margin.mils < 0:
            raise BadReading("a margin is measured in from a member, not out")
        padded = self.pad_on(bay)
        if padded is not None:
            return padded.gauge
        if self.is_flat or margin <= self.land_width:
            return self.land
        if margin >= self.pocket_starts_at:
            return self.pocket
        into_taper = margin - self.land_width
        fallen = Decimal(into_taper.mils) / Decimal(self.step.run)
        return Length(self.land.mils - int(fallen.to_integral_value()))

    def on_the_step(self, margin: Length) -> bool:
        """Whether a point is on the taper, where the manual will not allow a blend."""
        if self.is_flat:
            return False
        return self.land_width < margin < self.pocket_starts_at

    def pad_on(self, bay: Bay | None) -> Pad | None:
        if bay is None:
            return None
        for pad in self.pads:
            if pad.bay == bay:
                return pad
        return None

    def with_pad(self, pad: Pad) -> ThicknessMap:
        return ThicknessMap(
            land=self.land,
            pocket=self.pocket,
            land_width=self.land_width,
            step=self.step,
            pads=(*self.pads, pad),
        )

    def __str__(self) -> str:
        if self.is_flat:
            return f"flat {self.land.inches}"
        return f"{self.land.inches} land / {self.pocket.inches} pocket"
