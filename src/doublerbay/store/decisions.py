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
from doublerbay.ids import FindingId
from doublerbay.measure.interval import Interval
from doublerbay.measure.usage import Usage
from doublerbay.store.work import DeferralRow
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
