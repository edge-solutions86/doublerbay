"""Everything one aeroplane owes the desk at one check, on one page.

Planning does not want three lists. It wants to know what has to happen while
the aeroplane is in the hangar, and that comes off three different registers
with three different clocks: deferrals counting down against flying, repeat
inspections counting down against the repair they hang on, and engineering
orders that have no clock at all and are waiting on somebody else.

The orders are the awkward one. Nothing about an order falls due, so a check
cannot clear it, but leaving it off the pack means the aeroplane comes out of
the hangar with a card the desk still cannot close. So it goes on the pack
under its own heading, marked as waiting rather than due, which is what it is.

The pack is built for a window rather than a day. A check is a week, and
something falling due on the Thursday is something to do while the panel is
already off, not something to defer to the next one.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from doublerbay.errors import BadReading
from doublerbay.ids import FindingId, RepairId, Serial
from doublerbay.report.desk import due_on_tail
from doublerbay.report.due import DueItem
from doublerbay.store.unit import UnitOfWork
from doublerbay.tolerance.programme import InspectionTask


@dataclass(frozen=True, slots=True)
class PackLine:
    """One thing to do, whatever register it came off."""

    what: str
    reference: str
    detail: str
    overrun: bool = False

    def __str__(self) -> str:
        mark = "OVER " if self.overrun else ""
        return f"{mark}{self.what} {self.reference}: {self.detail}"


@dataclass(frozen=True, slots=True)
class WorkPack:
    """One tail's work for one check."""

    serial: Serial
    opens: date
    closes: date
    deferrals: tuple[PackLine, ...]
    inspections: tuple[PackLine, ...]
    waiting: tuple[PackLine, ...]

    @property
    def due_lines(self) -> tuple[PackLine, ...]:
        """The ones the check can actually clear."""
        return self.deferrals + self.inspections

    @property
    def total(self) -> int:
        return len(self.due_lines) + len(self.waiting)

    @property
    def is_empty(self) -> bool:
        return self.total == 0

    @property
    def overrun(self) -> tuple[PackLine, ...]:
        return tuple(one for one in self.due_lines if one.overrun)

    @property
    def days(self) -> int:
        return (self.closes - self.opens).days + 1

    def __str__(self) -> str:
        if self.is_empty:
            return f"{self.serial}: nothing for this check"
        over = f", {len(self.overrun)} already over" if self.overrun else ""
        return f"{self.serial}: {self.total} items over {self.days} days{over}"


def deferral_line(item: DueItem) -> PackLine:
    return PackLine(
        what="deferral",
        reference=str(item.against),
        detail=f"{item.what}, {item.margin}",
        overrun=item.is_overrun,
    )


def inspection_line(task: InspectionTask, over: bool) -> PackLine:
    return PackLine(
        what="inspection",
        reference=str(task.against),
        detail=f"{task.schedule.method} on {task.part}, due {task.due()}",
        overrun=over,
    )


def waiting_line(reference: FindingId, order: str, days: int) -> PackLine:
    return PackLine(
        what="waiting",
        reference=str(reference),
        detail=f"{order} out {days} days, no answer yet",
    )


def build(
    unit: UnitOfWork, serial: Serial, opens: date, closes: date, horizon_days: int | None = None
) -> WorkPack:
    """Everything due on one tail between two days, plus what is waiting."""
    if closes < opens:
        raise BadReading("a check closes after it opens")
    span = (closes - opens).days
    reach = span if horizon_days is None else horizon_days
    due = due_on_tail(unit, serial, opens, reach)

    flown = unit.aircraft.log_for(serial).at(closes)
    tasks = unit.inspections.due_on(serial, flown, closes)
    already = {one.against for one in unit.inspections.due_on(serial, flown, opens)}

    return WorkPack(
        serial=serial,
        opens=opens,
        closes=closes,
        deferrals=tuple(deferral_line(one) for one in due.items),
        inspections=tuple(inspection_line(one, one.against in already) for one in tasks),
        waiting=tuple(_waiting(unit, serial, closes)),
    )


def _waiting(unit: UnitOfWork, serial: Serial, on: date) -> list[PackLine]:
    """The orders out against this tail, which no check can clear."""
    lines = []
    for order in unit.orders.open_orders():
        if unit.findings.latest(order.against).serial != serial:
            continue
        lines.append(waiting_line(order.against, str(order.reference), order.days_waiting(on)))
    return lines


def references_on(pack: WorkPack) -> tuple[str, ...]:
    """Every reference the pack names, for a planner cross checking a printout."""
    return tuple(one.reference for one in pack.due_lines + pack.waiting)


def repairs_touched(pack: WorkPack) -> tuple[RepairId, ...]:
    """The repairs whose inspections fall in this pack."""
    return tuple(RepairId.parse(one.reference) for one in pack.inspections)
