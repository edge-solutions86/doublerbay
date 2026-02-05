"""Embodying a repair, which closes three things that were left open.

The repair itself is the obvious write. The other two are the ones that get
forgotten, and both of them are what a fleet audit finds two years later.

A card that has been repaired is no longer open damage. If the disposition is
not moved, the card sits on every open damage report forever and the desk
learns to ignore that report. And a card that was deferred is flying against a
limit that no longer applies, so the deferral has to be lifted on the day the
repair went on, not whenever somebody notices.

A category B repair also carries a repeat inspection, and that gets written
here too. The schedule is not something the shop chooses. It falls out of the
crack growth basis for the panel and the smallest crack the chosen method can
be relied on to find, so a repair fitted without its task is a repair nobody
will ever look at again.

Removing a repair is not the reverse of embodying it. The card does not
reopen, because taking a doubler off to fit a bigger one is not the damage
coming back. But the repeat inspection goes with it, because a work card
against a doubler that is no longer on the aeroplane wastes an afternoon.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from doublerbay.assess.placement import place
from doublerbay.damage.finding import Finding
from doublerbay.disposition.state import DispositionState
from doublerbay.errors import BadReading, DoublerbayError, NotOnFile
from doublerbay.fleet.catalogue import TypeCatalogue
from doublerbay.ids import RepairId, Serial
from doublerbay.repair.record import RepairRecord
from doublerbay.store.context import context_for
from doublerbay.store.unit import UnitOfWork
from doublerbay.tolerance.method import InspectionMethod
from doublerbay.tolerance.programme import InspectionTask
from doublerbay.tolerance.schedule import InspectionSchedule, schedule_for

# What a repair leaves the card as, which depends on whether it is permanent.
PERMANENT_STATE = DispositionState.CLOSED
TEMPORARY_STATE = DispositionState.IN_WORK

# What the shop reaches for unless somebody says otherwise. Eddy current
# finds a shorter crack than a detailed visual, which buys a longer interval.
DEFAULT_METHOD = InspectionMethod.HFEC


@dataclass(frozen=True, slots=True)
class Embodiment:
    """What fitting one repair changed."""

    reference: RepairId
    state: DispositionState
    deferral_lifted: bool
    clashes: tuple[tuple[RepairId, RepairId], ...] = ()
    inspection: InspectionSchedule | None = None

    @property
    def is_clean(self) -> bool:
        return not self.clashes

    @property
    def carries_inspection(self) -> bool:
        return self.inspection is not None

    def __str__(self) -> str:
        tail = f", {len(self.clashes)} clashes" if self.clashes else ""
        return f"{self.reference} fitted, card {self.state}{tail}"


def state_after(record: RepairRecord) -> DispositionState:
    """Where a repair leaves the card it was raised against."""
    return PERMANENT_STATE if record.scheme.category.is_permanent else TEMPORARY_STATE


def embody(
    unit: UnitOfWork,
    record: RepairRecord,
    serial: Serial,
    by: str,
    on: date | None = None,
    method: InspectionMethod = DEFAULT_METHOD,
    catalogue: TypeCatalogue | None = None,
) -> Embodiment:
    """Fit a repair, move the card, and lift whatever it was flying against."""
    if not by.strip():
        raise BadReading("a repair is embodied by somebody")
    day = on if on is not None else record.embodied
    if day < record.embodied:
        raise BadReading(f"{record.reference} cannot be signed off before it was fitted")

    finding = unit.findings.latest(record.against)
    if finding.serial != serial:
        raise BadReading(f"{record.against} is not on {serial}")

    unit.repairs.embody(record, serial)
    lifted = _lift_if_deferred(unit, record, day)
    state = state_after(record)
    _walk_to(unit, record, state, day, by)
    unit.flush()
    return Embodiment(
        reference=record.reference,
        state=state,
        deferral_lifted=lifted,
        clashes=_clash_pairs(unit, serial, record),
        inspection=_schedule_task(unit, record, finding, method, catalogue),
    )


def _schedule_task(
    unit: UnitOfWork,
    record: RepairRecord,
    finding: Finding,
    method: InspectionMethod,
    catalogue: TypeCatalogue | None,
) -> InspectionSchedule | None:
    """Put the repeat inspection on the repair, if its category carries one.

    The schedule is worked out rather than chosen. It comes off the crack
    growth basis for the panel kind and the smallest crack the method finds,
    so two engineers fitting the same repair on the same panel get the same
    interval, which is the entire point of having a programme.
    """
    if not record.needs_inspection:
        return None
    placement = place(
        context_for(unit, catalogue or TypeCatalogue(), finding.serial, record.embodied),
        finding,
    )
    schedule = schedule_for(record.scheme.category, placement.panel_kind, method)
    if schedule is None:
        return None
    unit.inspections.add(
        InspectionTask(
            against=record.reference,
            part=record.part,
            schedule=schedule,
            embodied=record.embodied,
            at_embodiment=record.at_usage,
        )
    )
    return schedule


def _walk_to(
    unit: UnitOfWork, record: RepairRecord, state: DispositionState, day: date, by: str
) -> None:
    """Move the card to where the repair leaves it, through in work if it has to.

    A deferred card does not go straight to closed, and that is not an
    obstruction to work around. A repair puts the aeroplane in the shop and
    then signs the card off, so both moves happened and both are recorded on
    the day the doubler went on.
    """
    held = unit.dispositions.get(record.against)
    note = f"repaired by {record.reference}"
    if held.state is state:
        return
    if not held.state.can_move_to(state) and held.state.can_move_to(TEMPORARY_STATE):
        held = held.moved_to(TEMPORARY_STATE, day, by, note)
        if held.state is state:
            unit.dispositions.save(held)
            return
    unit.dispositions.save(held.moved_to(state, day, by, note))


def _lift_if_deferred(unit: UnitOfWork, record: RepairRecord, day: date) -> bool:
    """Lift the deferral this repair answers, if there was one."""
    try:
        unit.deferrals.lift(record.against, day)
    except DoublerbayError:
        return False
    return True


def _clash_pairs(
    unit: UnitOfWork, serial: Serial, record: RepairRecord
) -> tuple[tuple[RepairId, RepairId], ...]:
    """The pairs on this panel that are now too close, this repair included."""
    return tuple(
        (one.reference, other.reference)
        for one, other in unit.repairs.clashing_on(serial, record.part)
    )


def remove(unit: UnitOfWork, reference: RepairId, on: date) -> None:
    """Take a repair off, and take its repeat inspection with it."""
    unit.repairs.remove(reference, on)
    try:
        unit.inspections.drop(reference)
    except NotOnFile:
        return
