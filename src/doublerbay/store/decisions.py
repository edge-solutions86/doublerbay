"""Turning what the desk decided into rows, and back again.

The deferral is the awkward one. Its limit is an Interval, which is four
optional clocks, and there is no honest way to put four optional clocks in one
column. Storing the printed form and parsing it back would work until somebody
writes a limit the parser cannot round trip, and then the aeroplane is flying
against a limit nobody can read.

So the four clocks get four columns. It is wider than it looks like it needs
to be and it means a query can ask which deferrals are against a calendar
clock at all, which is the question the planning desk asks every Monday.

A lifted deferral keeps its row. The record that an engineer once allowed this
aeroplane to fly with this damage does not stop being true because the damage
was repaired afterwards.
"""

from __future__ import annotations

from doublerbay.disposition.deferral import Deferral
from doublerbay.errors import BadReading
from doublerbay.grid.ata import Reference
from doublerbay.grid.surface import Offset
from doublerbay.ids import FindingId, Serial
from doublerbay.measure.fastener import FastenerSize
from doublerbay.measure.interval import Interval
from doublerbay.measure.usage import Usage
from doublerbay.repair.doubler import DoublerPlan
from doublerbay.repair.record import RepairRecord
from doublerbay.repair.scheme import RepairCategory, RepairForm, RepairScheme
from doublerbay.store.work import DeferralRow, RepairRow
from doublerbay.structure.material import material
from doublerbay.tolerance.method import InspectionMethod


def deferral_to_row(deferral: Deferral) -> DeferralRow:
    """One granted deferral, spread across its four clocks."""
    return DeferralRow(
        against=deferral.against,
        granted=deferral.granted,
        at_cycles=deferral.at_usage.cycles,
        at_hours=deferral.at_usage.hours,
        limit_cycles=deferral.limit.cycles,
        limit_hours=deferral.limit.hours,
        limit_months=deferral.limit.months,
        limit_days=deferral.limit.days,
        method=deferral.re_inspect_with.value,
        granted_by=deferral.by,
        reason=deferral.reason,
    )


def row_to_deferral(row: DeferralRow) -> Deferral:
    """Rebuild a deferral, refusing a row whose clocks have all gone."""
    try:
        limit = Interval(
            cycles=row.limit_cycles,
            hours=row.limit_hours,
            months=row.limit_months,
            days=row.limit_days,
        )
    except BadReading:
        raise BadReading(f"the stored deferral against {row.against} has no limit on it") from None
    return Deferral(
        against=row.against,
        granted=row.granted,
        at_usage=Usage(row.at_cycles, row.at_hours),
        limit=limit,
        re_inspect_with=InspectionMethod(row.method),
        by=row.granted_by,
        reason=row.reason or "",
    )


def apply_to_row(row: DeferralRow, deferral: Deferral) -> None:
    """Write a tightened limit onto the row already there.

    Tightening is an amendment of the same decision rather than a new one, so
    it keeps the reference, the day it was granted and the counters it was
    granted at. Only the limit and who set it move.
    """
    if row.against != deferral.against:
        raise BadReading("a deferral row is not amended with another card's deferral")
    row.limit_cycles = deferral.limit.cycles
    row.limit_hours = deferral.limit.hours
    row.limit_months = deferral.limit.months
    row.limit_days = deferral.limit.days
    row.method = deferral.re_inspect_with.value
    row.granted_by = deferral.by
    row.reason = deferral.reason


def is_lifted(row: DeferralRow) -> bool:
    return row.lifted is not None


def reference_of(row: DeferralRow) -> FindingId:
    return row.against


def repair_to_row(record: RepairRecord, serial: Serial) -> RepairRow:
    """One repair with its whole drawing on the row.

    The material goes down as the callout a person writes on the drawing
    rather than as five columns of alloy, temper, clad and two strengths.
    The strengths belong to the alloy, not to this repair, and a repair that
    carried its own copy of them would keep whatever the catalogue said on the
    day it was embodied.

    The tail comes in beside the record because a panel number is a part
    number. The same part hangs on every aeroplane in the fleet, so the panel
    does not say which one this repair is on and the record does not either.
    """
    scheme = record.scheme
    plan = scheme.doubler
    return RepairRow(
        reference=record.reference,
        serial=serial,
        part=record.part,
        against=record.against,
        form=scheme.form.value,
        category=scheme.category.value,
        sealed=scheme.sealed,
        page=str(scheme.reference),
        material=str(plan.material),
        gauge=plan.gauge,
        along=plan.along,
        around=plan.around,
        filler=scheme.filler,
        fastener_dash=plan.size.dash,
        rows_each_side=plan.rows_each_side,
        per_row=plan.per_row,
        pitch=plan.pitch,
        row_spacing=plan.row_spacing,
        edge_distance=plan.edge_distance,
        centre_along=record.centre.along,
        centre_around=record.centre.around,
        embodied=record.embodied,
        at_cycles=record.at_usage.cycles,
        at_hours=record.at_usage.hours,
        removed=record.removed,
    )


def row_to_repair(row: RepairRow) -> RepairRecord:
    """Rebuild a repair, taking every number back through its own validation."""
    plan = DoublerPlan(
        gauge=row.gauge,
        material=material(row.material),
        along=row.along,
        around=row.around,
        size=FastenerSize(row.fastener_dash),
        rows_each_side=row.rows_each_side,
        per_row=row.per_row,
        pitch=row.pitch,
        row_spacing=row.row_spacing,
        edge_distance=row.edge_distance,
    )
    scheme = RepairScheme(
        form=RepairForm(row.form),
        category=RepairCategory(row.category),
        doubler=plan,
        sealed=row.sealed,
        reference=Reference.parse(row.page),
        filler=row.filler,
    )
    return RepairRecord(
        reference=row.reference,
        part=row.part,
        against=row.against,
        scheme=scheme,
        centre=Offset(row.centre_along, row.centre_around),
        embodied=row.embodied,
        at_usage=Usage(row.at_cycles, row.at_hours),
        removed=row.removed,
    )
