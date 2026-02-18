"""What the structure is carrying, which is a different question from what is due.

The due list is about time. This is about accumulation. An aeroplane with
thirty repairs on it is not in trouble because any one of them is due, it is
in trouble because the next damage in that area has nowhere to go, and nothing
on the due list will ever say that.

Three numbers carry it. How many repairs are on one panel, because a panel
with four doublers on it is a panel the shop should be arguing about replacing.
How long the temporary repairs have been fitted, because category C is meant
to last until the next check and some of them are five years old. And which
pairs are too close together, because that is the one the audit finds.

None of this is a refusal. Every one of these repairs was approved when it
went on. The report exists so somebody sees the pattern before the fleet audit
does.
"""

from __future__ import annotations

from collections import Counter
from dataclasses import dataclass
from datetime import date
from decimal import ROUND_HALF_UP, Decimal

from doublerbay.errors import BadReading
from doublerbay.ids import PanelId, RepairId, Serial
from doublerbay.repair.record import RepairRecord, clashes
from doublerbay.repair.scheme import RepairCategory

# A panel carrying this many repairs is worth a conversation about replacing it.
CROWDED_PANEL = 3
# How long a temporary repair is meant to last before it is replaced.
TEMPORARY_MONTHS = 18
DAYS_A_MONTH = Decimal("30.44")
PLACES = Decimal("0.1")


@dataclass(frozen=True, slots=True)
class PanelLoad:
    """How much one panel is carrying."""

    part: PanelId
    repairs: int
    temporary: int

    @property
    def is_crowded(self) -> bool:
        return self.repairs >= CROWDED_PANEL

    def __str__(self) -> str:
        tail = f", {self.temporary} temporary" if self.temporary else ""
        return f"{self.part}: {self.repairs} repairs{tail}"


@dataclass(frozen=True, slots=True)
class OverdueTemporary:
    """A category C repair that has outlived what it was fitted for."""

    reference: RepairId
    part: PanelId
    embodied: date
    months_fitted: Decimal

    @property
    def is_overdue(self) -> bool:
        return self.months_fitted > TEMPORARY_MONTHS

    def __str__(self) -> str:
        return f"{self.reference} on {self.part}, {self.months_fitted} months fitted"


@dataclass(frozen=True, slots=True)
class StructureAudit:
    """One tail's accumulated repair picture on one day."""

    serial: Serial
    on: date
    panels: tuple[PanelLoad, ...]
    temporary: tuple[OverdueTemporary, ...]
    clashing: tuple[tuple[RepairId, RepairId], ...]

    @property
    def total_repairs(self) -> int:
        return sum(one.repairs for one in self.panels)

    @property
    def crowded(self) -> tuple[PanelLoad, ...]:
        return tuple(one for one in self.panels if one.is_crowded)

    @property
    def overdue(self) -> tuple[OverdueTemporary, ...]:
        return tuple(one for one in self.temporary if one.is_overdue)

    @property
    def is_clean(self) -> bool:
        return not self.crowded and not self.overdue and not self.clashing

    def load_on(self, part: PanelId) -> int:
        return {one.part: one.repairs for one in self.panels}.get(part, 0)

    def __str__(self) -> str:
        if self.total_repairs == 0:
            return f"{self.serial}: no repairs on file"
        flags = []
        if self.crowded:
            flags.append(f"{len(self.crowded)} crowded panels")
        if self.overdue:
            flags.append(f"{len(self.overdue)} temporary repairs overdue")
        if self.clashing:
            flags.append(f"{len(self.clashing)} clashes")
        state = ", ".join(flags) if flags else "nothing flagged"
        return f"{self.serial}: {self.total_repairs} repairs, {state}"


def months_between(embodied: date, on: date) -> Decimal:
    """How long a repair has been fitted, in months to a tenth."""
    if on < embodied:
        raise BadReading("a repair cannot have been fitted for a negative time")
    days = Decimal((on - embodied).days)
    return (days / DAYS_A_MONTH).quantize(PLACES, rounding=ROUND_HALF_UP)


def panel_loads(fitted: tuple[RepairRecord, ...]) -> tuple[PanelLoad, ...]:
    """How much each panel is carrying, busiest first."""
    counted: Counter[PanelId] = Counter(one.part for one in fitted)
    temporary: Counter[PanelId] = Counter(
        one.part for one in fitted if one.scheme.category is RepairCategory.C
    )
    loads = [
        PanelLoad(part=part, repairs=count, temporary=temporary.get(part, 0))
        for part, count in counted.items()
    ]
    loads.sort(key=lambda one: (-one.repairs, str(one.part)))
    return tuple(loads)


def temporaries(fitted: tuple[RepairRecord, ...], on: date) -> tuple[OverdueTemporary, ...]:
    """Every temporary repair still on, longest fitted first."""
    found = [
        OverdueTemporary(
            reference=one.reference,
            part=one.part,
            embodied=one.embodied,
            months_fitted=months_between(one.embodied, on),
        )
        for one in fitted
        if one.scheme.category is RepairCategory.C
    ]
    found.sort(key=lambda one: (-one.months_fitted, str(one.reference)))
    return tuple(found)


def audit(serial: Serial, fitted: tuple[RepairRecord, ...], on: date) -> StructureAudit:
    """Build one tail's accumulated repair picture."""
    return StructureAudit(
        serial=serial,
        on=on,
        panels=panel_loads(fitted),
        temporary=temporaries(fitted, on),
        clashing=tuple((one.reference, other.reference) for one, other in clashes(fitted)),
    )
