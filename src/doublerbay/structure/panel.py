"""Skin panels, and the bays they are cut into.

A panel is a single piece of sheet with a part number, bounded by the members
it is fastened to. It usually covers several bays, which matters because limits
are written per bay and repeat damage counts are written per panel. Two dents
in one bay and two dents in two bays of the same panel are different questions
with different answers.

A panel also carries the kind it is, and that is the thing the limit tables are
keyed on. Pressurised barrel skin, unpressurised tail cone, belly, wing upper,
wing lower: the geometry is nearly the same and the numbers are not close.
"""

from __future__ import annotations

from collections.abc import Iterator
from dataclasses import dataclass
from enum import Enum

from doublerbay.errors import BadReading
from doublerbay.grid.ata import Chapter
from doublerbay.grid.lattice import Bay, Frame, Stringer
from doublerbay.grid.wing import Rib, Spar, Surface, WingBay
from doublerbay.grid.zone import Side, Zone
from doublerbay.ids import PanelId
from doublerbay.measure.length import Length
from doublerbay.structure.material import Material


class PanelKind(Enum):
    """What the limit tables are keyed on, which is not the same as where it is."""

    PRESSURISED_SKIN = "pressurised skin"
    UNPRESSURISED_SKIN = "unpressurised skin"
    BELLY_SKIN = "belly skin"
    CROWN_SKIN = "crown skin"
    WING_UPPER = "wing upper"
    WING_LOWER = "wing lower"
    FAIRING = "fairing"

    @property
    def is_pressurised(self) -> bool:
        return self in (
            PanelKind.PRESSURISED_SKIN,
            PanelKind.BELLY_SKIN,
            PanelKind.CROWN_SKIN,
        )

    @property
    def is_primary(self) -> bool:
        """Fairings carry nothing. Everything else is structure."""
        return self is not PanelKind.FAIRING

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True, slots=True)
class FuselagePanel:
    """One piece of barrel skin, bounded by two frames and two stringers."""

    part: PanelId
    forward: Frame
    aft: Frame
    upper: Stringer
    lower: Stringer
    zone: Zone
    kind: PanelKind
    material: Material
    nominal: Length

    def __post_init__(self) -> None:
        if self.forward >= self.aft:
            raise BadReading(f"{self.part}: {self.forward} is not forward of {self.aft}")
        if not self.upper.is_above(self.lower):
            raise BadReading(f"{self.part}: {self.upper} is not above {self.lower}")
        if self.upper.side is not self.lower.side and not self.upper.is_crown:
            raise BadReading(f"{self.part}: a panel does not straddle the crown")
        if self.nominal.mils <= 0:
            raise BadReading(f"{self.part}: a panel has a thickness")
        if self.kind in (PanelKind.WING_UPPER, PanelKind.WING_LOWER):
            raise BadReading(f"{self.part}: {self.kind} is not a fuselage panel")

    @property
    def chapter(self) -> Chapter:
        return Chapter.FUSELAGE

    @property
    def side(self) -> Side:
        return self.lower.side

    @property
    def frame_pitches(self) -> int:
        return self.forward.spans_to(self.aft)

    @property
    def stringer_pitches(self) -> int:
        return self.upper.spans_to(self.lower)

    @property
    def bay_count(self) -> int:
        return self.frame_pitches * self.stringer_pitches

    def bays(self) -> Iterator[Bay]:
        """Every single bay the panel covers, forward to aft and crown down."""
        for frame in range(self.forward.number, self.aft.number):
            for offset in range(self.stringer_pitches):
                upper = self._stringer(self.upper.number + offset)
                lower = self._stringer(self.upper.number + offset + 1)
                yield Bay(Frame(frame), Frame(frame + 1), upper, lower)

    def _stringer(self, number: int) -> Stringer:
        if number == 0:
            return Stringer(0)
        return Stringer(number, self.side)

    def spans_frame(self, frame: Frame) -> bool:
        return self.forward <= frame <= self.aft

    def spans_stringer(self, stringer: Stringer) -> bool:
        if stringer.side is not self.side and not stringer.is_crown:
            return False
        return self.upper.number <= stringer.number <= self.lower.number

    def covers(self, bay: Bay) -> bool:
        return (
            self.spans_frame(bay.forward)
            and self.spans_frame(bay.aft)
            and self.spans_stringer(bay.upper)
            and self.spans_stringer(bay.lower)
        )

    def __str__(self) -> str:
        return f"{self.part} ({self.kind})"


@dataclass(frozen=True, slots=True)
class WingPanel:
    """One piece of wing skin, bounded by two ribs and two spars, on one surface."""

    part: PanelId
    inboard: Rib
    outboard: Rib
    forward: Spar
    aft: Spar
    surface: Surface
    zone: Zone
    kind: PanelKind
    material: Material
    nominal: Length

    def __post_init__(self) -> None:
        if self.inboard.side is not self.outboard.side:
            raise BadReading(f"{self.part}: a panel does not cross the centreline")
        if not self.inboard.is_inboard_of(self.outboard):
            raise BadReading(f"{self.part}: {self.inboard} is not inboard of {self.outboard}")
        if not self.forward.is_forward_of(self.aft):
            raise BadReading(f"{self.part}: {self.forward} is not forward of {self.aft}")
        if self.nominal.mils <= 0:
            raise BadReading(f"{self.part}: a panel has a thickness")
        expected = PanelKind.WING_UPPER if self.surface is Surface.UPPER else PanelKind.WING_LOWER
        if self.kind is not expected and self.kind is not PanelKind.FAIRING:
            raise BadReading(f"{self.part}: {self.kind} is not on the {self.surface} surface")

    @property
    def chapter(self) -> Chapter:
        return Chapter.WINGS

    @property
    def side(self) -> Side:
        return self.inboard.side

    @property
    def rib_pitches(self) -> int:
        return self.outboard.number - self.inboard.number

    @property
    def bay_count(self) -> int:
        return self.rib_pitches

    def bays(self) -> Iterator[WingBay]:
        for number in range(self.inboard.number, self.outboard.number):
            yield WingBay(
                Rib(number, self.side),
                Rib(number + 1, self.side),
                self.forward,
                self.aft,
                self.surface,
            )

    def spans_rib(self, rib: Rib) -> bool:
        if rib.side is not self.side:
            return False
        return self.inboard.number <= rib.number <= self.outboard.number

    def __str__(self) -> str:
        return f"{self.part} ({self.kind})"


Panel = FuselagePanel | WingPanel
