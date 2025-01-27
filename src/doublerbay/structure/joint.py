"""Joints and their fastener rows, which is where the skin is most likely to crack.

Two sheets of skin meet either by overlapping, which is a lap joint and runs
along a stringer, or by butting up against a strap behind them, which is a butt
joint and runs around a frame. Either way there are two or three rows of
fasteners holding it, and the row nearest the free edge of the upper sheet is
the one that carries the load first and cracks first.

That is why the limits at a joint are tighter than the limits in the middle of
a bay, and why finding which row a damage is nearest to is a step in its own
right. A quarter inch scratch two inches into a bay is nothing. The same
scratch through the upper row of a lap joint is a hold, because it is sitting
in the place the fleet already cracks without any help.
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from enum import Enum

from doublerbay.errors import BadReading
from doublerbay.grid.lattice import Frame, Stringer
from doublerbay.measure.fastener import FastenerSize
from doublerbay.measure.length import Length


class JointKind(Enum):
    """How two sheets are joined, which decides how the limits are written."""

    LAP = "lap"
    BUTT = "butt"
    SPLICE = "splice"
    BONDED = "bonded"

    @property
    def is_fatigue_critical(self) -> bool:
        """Anything that carries load through fasteners in shear."""
        return self in (JointKind.LAP, JointKind.SPLICE)

    @property
    def is_riveted(self) -> bool:
        return self is not JointKind.BONDED

    def __str__(self) -> str:
        return f"{self.value} joint"


@dataclass(frozen=True, slots=True)
class FastenerRow:
    """One line of fasteners, offset from the member the joint follows."""

    size: FastenerSize
    pitch: Length
    offset: Length
    countersunk: bool = False

    def __post_init__(self) -> None:
        if self.pitch.mils <= 0:
            raise BadReading("a row has a pitch")
        if self.pitch < self.size.min_pitch:
            raise BadReading(
                f"{self.pitch} is closer than the {self.size.min_pitch} minimum for {self.size}"
            )

    @property
    def pitch_in_diameters(self) -> Decimal:
        return self.size.in_diameters(self.pitch)

    def fastener_count(self, run: Length) -> int:
        """How many fasteners a run of this length carries, both ends included."""
        if run.mils < 0:
            raise BadReading("a run does not go backwards")
        return run.mils // self.pitch.mils + 1

    def run_for(self, count: int) -> Length:
        """How long a run of that many fasteners reaches."""
        if count < 1:
            raise BadReading("a row holds at least one fastener")
        return self.pitch * (count - 1)

    def __str__(self) -> str:
        head = f"{self.size} at {self.pitch.inches}"
        return f"{head} csk" if self.countersunk else head


JointMember = Frame | Stringer


@dataclass(frozen=True, slots=True)
class Joint:
    """A run of joined skin, following one member, with its rows."""

    kind: JointKind
    follows: JointMember
    rows: tuple[FastenerRow, ...]

    def __post_init__(self) -> None:
        if self.kind.is_riveted and not self.rows:
            raise BadReading(f"a {self.kind} has fastener rows")
        offsets = [row.offset for row in self.rows]
        if offsets != sorted(offsets):
            raise BadReading("rows are listed from the member outwards")
        if len(set(offsets)) != len(offsets):
            raise BadReading("two rows cannot sit on the same line")
        if self.kind is JointKind.LAP and not isinstance(self.follows, Stringer):
            raise BadReading("a lap joint runs along a stringer")
        if self.kind is JointKind.BUTT and not isinstance(self.follows, Frame):
            raise BadReading("a butt joint runs around a frame")
        self._check_row_spacing()

    def _check_row_spacing(self) -> None:
        for near, far in zip(self.rows, self.rows[1:], strict=False):
            spacing = far.offset - near.offset
            required = max(near.size.min_row_spacing, far.size.min_row_spacing)
            if spacing < required:
                raise BadReading(f"rows {spacing} apart are inside the {required} minimum")

    @property
    def is_longitudinal(self) -> bool:
        return isinstance(self.follows, Stringer)

    @property
    def is_fatigue_critical(self) -> bool:
        return self.kind.is_fatigue_critical

    @property
    def row_count(self) -> int:
        return len(self.rows)

    @property
    def width(self) -> Length:
        """From the member out to the last row, which is the band the tighter limits cover."""
        if not self.rows:
            return Length(0)
        return self.rows[-1].offset

    @property
    def critical_row(self) -> FastenerRow:
        """The outermost row, which is the one that carries load first and cracks first."""
        if not self.rows:
            raise BadReading(f"a {self.kind} has no rows to be critical")
        return self.rows[-1]

    def row_nearest(self, margin: Length) -> FastenerRow:
        """Whichever row a damage this far off the member is closest to."""
        if not self.rows:
            raise BadReading(f"a {self.kind} has no rows to measure to")
        return min(self.rows, key=lambda row: abs(row.offset - margin).mils)

    def distance_to_nearest_row(self, margin: Length) -> Length:
        return abs(self.row_nearest(margin).offset - margin)

    def is_within(self, margin: Length) -> bool:
        """Whether a point this far off the member is inside the joint band."""
        return Length(0) <= margin <= self.width

    def __str__(self) -> str:
        return f"{self.kind} on {self.follows}, {self.row_count} rows"
