"""What changed between two revisions, and which direction it moved.

When a revision lands somebody has to work out what it means for the damage
already on the aeroplanes. A limit that got looser is a nuisance at worst, and
half the deferrals against it could probably be closed. A limit that got
tighter is the other thing entirely: every open finding assessed under the old
number has to be looked at again, and some of them are now out of limits on an
aeroplane that is flying.

So a diff is not a list of differences, it is a list of differences with a
direction on each one. Tighter and looser are decided per field, because a
smaller number is tighter on a depth cap and looser on a residual floor, and
mixing those up produces exactly the wrong work list.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

from doublerbay.manual.table import LimitKey, LimitTable

# Each field, and whether a smaller value means a tighter limit.
_CAPS = (
    "max_depth",
    "max_depth_fraction",
    "max_length",
    "max_area",
    "max_sharpness",
)
_FLOORS = (
    "min_residual",
    "min_residual_fraction",
    "min_edge_margin",
    "min_edge_margin_diameters",
)


class Direction(Enum):
    """Which way a limit moved."""

    TIGHTER = "tighter"
    LOOSER = "looser"
    SIDEWAYS = "sideways"

    @property
    def needs_review(self) -> bool:
        """Anything not looser puts open findings back on the desk."""
        return self is not Direction.LOOSER

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True, slots=True)
class LimitChange:
    """One field of one row, and what it moved from and to."""

    key: LimitKey
    field: str
    was: object | None
    now: object | None
    direction: Direction

    def __str__(self) -> str:
        return f"{self.key}: {self.field} {self.was} to {self.now} ({self.direction})"


def _direction(field: str, was: object | None, now: object | None) -> Direction:
    if was is None or now is None:
        # A limit appearing where there was none is tighter. One disappearing is
        # looser, because nothing now caps what that field used to cap.
        return Direction.TIGHTER if was is None else Direction.LOOSER
    if field in _CAPS:
        return Direction.TIGHTER if now < was else Direction.LOOSER  # type: ignore[operator]
    if field in _FLOORS:
        return Direction.TIGHTER if now > was else Direction.LOOSER  # type: ignore[operator]
    return Direction.SIDEWAYS


@dataclass(frozen=True, slots=True)
class RevisionDiff:
    """Everything that moved between two issues of one table."""

    reference: str
    was_revision: int
    now_revision: int
    added: tuple[LimitKey, ...]
    removed: tuple[LimitKey, ...]
    changed: tuple[LimitChange, ...]

    @property
    def is_empty(self) -> bool:
        return not (self.added or self.removed or self.changed)

    def tightenings(self) -> tuple[LimitChange, ...]:
        return tuple(change for change in self.changed if change.direction is Direction.TIGHTER)

    def loosenings(self) -> tuple[LimitChange, ...]:
        return tuple(change for change in self.changed if change.direction is Direction.LOOSER)

    def needs_review(self) -> tuple[LimitChange, ...]:
        """The changes that put already dispositioned findings back on the desk."""
        return tuple(change for change in self.changed if change.direction.needs_review)

    def touches(self, key: LimitKey) -> bool:
        if key in self.added or key in self.removed:
            return True
        return any(change.key == key for change in self.changed)

    def __str__(self) -> str:
        if self.is_empty:
            return f"{self.reference} rev {self.was_revision} to {self.now_revision}: no change"
        return (
            f"{self.reference} rev {self.was_revision} to {self.now_revision}: "
            f"{len(self.added)} added, {len(self.removed)} removed, "
            f"{len(self.tightenings())} tightened, {len(self.loosenings())} loosened"
        )


def diff_tables(before: LimitTable, after: LimitTable) -> RevisionDiff:
    """Work out what moved between two issues of the same table."""
    was = {row.key: row.limits for row in before.rows}
    now = {row.key: row.limits for row in after.rows}

    added = tuple(sorted((key for key in now if key not in was), key=str))
    removed = tuple(sorted((key for key in was if key not in now), key=str))

    changes: list[LimitChange] = []
    for key in sorted(set(was) & set(now), key=str):
        old, new = was[key], now[key]
        if old.allowed != new.allowed:
            changes.append(
                LimitChange(
                    key,
                    "allowed",
                    old.allowed,
                    new.allowed,
                    Direction.TIGHTER if old.allowed else Direction.LOOSER,
                )
            )
            continue
        for field in (*_CAPS, *_FLOORS):
            old_value = getattr(old, field)
            new_value = getattr(new, field)
            if old_value != new_value:
                changes.append(
                    LimitChange(
                        key, field, old_value, new_value, _direction(field, old_value, new_value)
                    )
                )
    return RevisionDiff(
        str(before.reference), before.revision, after.revision, added, removed, tuple(changes)
    )
