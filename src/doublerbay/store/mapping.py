"""Turning domain objects into rows and back, in one place and both directions.

Mapping in one direction only is how a schema and a domain drift apart. A field
gets added to the domain object, the writer is updated, the reader is not, and
the value comes back as a default that looks plausible. So every mapper here
has its opposite next to it, and the tests go one way and back and compare the
whole object rather than a field at a time.

The measurement sets are dispatched on the damage kind. Adding a ninth kind of
damage means adding a line to two tables in this file, and forgetting the
second one fails loudly on the way back rather than quietly on the way in.
"""

from __future__ import annotations

from typing import Any

from doublerbay.damage.corrosion import Corrosion
from doublerbay.damage.crack import Crack
from doublerbay.damage.dent import Dent
from doublerbay.damage.finding import Finding, Geometry
from doublerbay.damage.kind import DamageKind, DamageOrigin
from doublerbay.damage.opening import Buckle, Disbond, Opening
from doublerbay.damage.scoring import Scoring
from doublerbay.damage.strike import LightningBurn
from doublerbay.disposition.state import DispositionRecord, DispositionState, Transition
from doublerbay.errors import BadReading
from doublerbay.fleet.aircraft import Aircraft
from doublerbay.fleet.check import DiscoverySource
from doublerbay.fleet.model import Bulletin, Series
from doublerbay.fleet.utilisation import Snapshot, UtilisationLog
from doublerbay.grid.lattice import Frame, Stringer
from doublerbay.grid.location import FuselagePoint, WingPoint
from doublerbay.grid.wing import Rib, Spar, Surface
from doublerbay.grid.zone import Side
from doublerbay.measure.angle import Angle
from doublerbay.measure.length import Length
from doublerbay.measure.usage import Usage
from doublerbay.store.tables import (
    AircraftRow,
    DispositionRow,
    EmbodimentRow,
    FindingRow,
    MoveRow,
    ReadingRow,
)


def _mils(value: object) -> Length:
    if not isinstance(value, int):
        raise BadReading(f"{value!r} is not a stored length")
    return Length(value)


def _tenths(value: object) -> Angle:
    if not isinstance(value, int):
        raise BadReading(f"{value!r} is not a stored angle")
    return Angle(value)


def _flag(value: object) -> bool:
    return bool(value)


def _count(value: object) -> int:
    if not isinstance(value, int):
        raise BadReading(f"{value!r} is not a stored count")
    return value


def measurements_of(geometry: Geometry) -> dict[str, Any]:
    """The numbers off one measurement set, as the document that gets stored."""
    if isinstance(geometry, Dent):
        return {
            "depth": geometry.depth.mils,
            "along": geometry.along.mils,
            "around": geometry.around.mils,
            "sharp_bottom": geometry.sharp_bottom,
            "cracked": geometry.cracked,
        }
    if isinstance(geometry, Scoring):
        return {
            "depth": geometry.depth.mils,
            "length": geometry.length.mils,
            "width": geometry.width.mils,
            "bearing": geometry.bearing.tenths,
            "sharp_bottom": geometry.sharp_bottom,
        }
    if isinstance(geometry, Crack):
        return {
            "length": geometry.length.mils,
            "bearing": geometry.bearing.tenths,
            "through_thickness": geometry.through_thickness,
            "stop_drilled": geometry.stop_drilled,
            "tips_found": geometry.tips_found,
        }
    if isinstance(geometry, Corrosion):
        return {
            "along": geometry.along.mils,
            "around": geometry.around.mils,
            "worst_depth": geometry.worst_depth.mils,
            "pitted": geometry.pitted,
            "exfoliating": geometry.exfoliating,
        }
    if isinstance(geometry, LightningBurn):
        return {
            "along": geometry.along.mils,
            "around": geometry.around.mils,
            "pit_count": geometry.pit_count,
            "worst_pit": geometry.worst_pit.mils,
            "on_a_fastener": geometry.on_a_fastener,
            "is_entry": geometry.is_entry,
        }
    if isinstance(geometry, Opening):
        return {
            "along": geometry.along.mils,
            "around": geometry.around.mils,
            "torn_edges": geometry.torn_edges,
            "was_a_fastener_hole": geometry.was_a_fastener_hole,
        }
    if isinstance(geometry, Buckle):
        return {
            "length": geometry.length.mils,
            "height": geometry.height.mils,
            "bearing": geometry.bearing.tenths,
            "confirmed_permanent": geometry.confirmed_permanent,
        }
    return {
        "along": geometry.along.mils,
        "around": geometry.around.mils,
        "tap_tested": geometry.tap_tested,
        "reaches_an_edge": geometry.reaches_an_edge,
    }


def geometry_from(kind: DamageKind, stored: dict[str, Any]) -> Geometry:
    """Rebuild a measurement set from what was stored, for the kind on the row."""
    try:
        return _rebuild(kind, stored)
    except KeyError as missing:
        raise BadReading(f"a stored {kind} is missing its {missing.args[0]}") from None


def _rebuild(kind: DamageKind, stored: dict[str, Any]) -> Geometry:
    if kind is DamageKind.DENT:
        return Dent(
            _mils(stored["depth"]),
            _mils(stored["along"]),
            _mils(stored["around"]),
            _flag(stored["sharp_bottom"]),
            _flag(stored["cracked"]),
        )
    if kind in (DamageKind.SCRATCH, DamageKind.GOUGE, DamageKind.NICK):
        return Scoring(
            _mils(stored["depth"]),
            _mils(stored["length"]),
            _mils(stored["width"]),
            _tenths(stored["bearing"]),
            _flag(stored["sharp_bottom"]),
        )
    if kind is DamageKind.CRACK:
        return Crack(
            _mils(stored["length"]),
            _tenths(stored["bearing"]),
            _flag(stored["through_thickness"]),
            _flag(stored["stop_drilled"]),
            _count(stored["tips_found"]),
        )
    if kind is DamageKind.CORROSION:
        return Corrosion(
            _mils(stored["along"]),
            _mils(stored["around"]),
            _mils(stored["worst_depth"]),
            _flag(stored["pitted"]),
            _flag(stored["exfoliating"]),
        )
    if kind is DamageKind.LIGHTNING_BURN:
        return LightningBurn(
            _mils(stored["along"]),
            _mils(stored["around"]),
            _count(stored["pit_count"]),
            _mils(stored["worst_pit"]),
            _flag(stored["on_a_fastener"]),
            _flag(stored["is_entry"]),
        )
    if kind in (DamageKind.HOLE, DamageKind.PUNCTURE):
        return Opening(
            _mils(stored["along"]),
            _mils(stored["around"]),
            _flag(stored["torn_edges"]),
            _flag(stored["was_a_fastener_hole"]),
        )
    if kind is DamageKind.BUCKLE:
        return Buckle(
            _mils(stored["length"]),
            _mils(stored["height"]),
            _tenths(stored["bearing"]),
            _flag(stored["confirmed_permanent"]),
        )
    if kind is DamageKind.DISBOND:
        return Disbond(
            _mils(stored["along"]),
            _mils(stored["around"]),
            _flag(stored["tap_tested"]),
            _flag(stored["reaches_an_edge"]),
        )
    raise BadReading(f"nothing on file says how to rebuild a {kind}")


def aircraft_to_row(aircraft: Aircraft) -> AircraftRow:
    return AircraftRow(
        serial=aircraft.serial,
        registration=aircraft.registration,
        series=aircraft.series.value,
        line_number=aircraft.line_number,
        delivered=aircraft.delivered,
        withdrawn=aircraft.withdrawn,
        embodiments=[
            EmbodimentRow(serial=aircraft.serial, chapter=one.chapter, number=one.number)
            for one in sorted(aircraft.embodied, key=str)
        ],
    )


def row_to_aircraft(row: AircraftRow) -> Aircraft:
    return Aircraft(
        serial=row.serial,
        registration=row.registration,
        series=Series(row.series),
        line_number=row.line_number,
        delivered=row.delivered,
        embodied=frozenset(Bulletin(one.chapter, one.number) for one in row.embodiments),
        withdrawn=row.withdrawn,
    )


def log_to_rows(log: UtilisationLog) -> list[ReadingRow]:
    return [
        ReadingRow(
            serial=log.serial,
            taken=snapshot.taken,
            cycles=snapshot.usage.cycles,
            hours=snapshot.usage.hours,
        )
        for snapshot in log.snapshots
    ]


def rows_to_log(serial: Any, rows: list[ReadingRow]) -> UtilisationLog:
    return UtilisationLog(
        serial,
        tuple(
            Snapshot(row.taken, Usage(row.cycles, row.hours))
            for row in sorted(rows, key=lambda row: row.taken)
        ),
    )


def finding_to_row(finding: Finding) -> FindingRow:
    point = finding.point
    common: dict[str, Any] = {
        "reference": finding.reference,
        "revision": finding.revision,
        "serial": finding.serial,
        "kind": finding.kind.value,
        "found_on": finding.found_on,
        "source": finding.source.value,
        "origin": finding.origin.value,
        "at_cycles": finding.at_usage.cycles,
        "at_hours": finding.at_usage.hours,
        "measurements": measurements_of(finding.geometry),
        "superseded_by": finding.superseded_by,
        "note": finding.note,
        "side": point.side.value,
    }
    if isinstance(point, FuselagePoint):
        common.update(
            structure="fuselage",
            frame=point.frame.number,
            aft_of_frame=point.aft_of_frame,
            stringer=point.stringer.number,
            below_stringer=point.below_stringer,
        )
    else:
        common.update(
            structure="wing",
            rib=point.rib.number,
            outboard_of_rib=point.outboard_of_rib,
            spar=point.spar.value,
            aft_of_spar=point.aft_of_spar,
            surface=point.surface.value,
        )
    return FindingRow(**common)


def row_to_finding(row: FindingRow) -> Finding:
    side = Side(row.side)
    if row.structure == "fuselage":
        stringer_number = _count(row.stringer)
        point: FuselagePoint | WingPoint = FuselagePoint(
            Frame(_count(row.frame)),
            _mils(row.aft_of_frame.mils if row.aft_of_frame else 0),
            Stringer(stringer_number, side) if stringer_number else Stringer(0),
            _mils(row.below_stringer.mils if row.below_stringer else 0),
        )
    elif row.structure == "wing":
        point = WingPoint(
            Rib(_count(row.rib), side),
            _mils(row.outboard_of_rib.mils if row.outboard_of_rib else 0),
            Spar(row.spar),
            _mils(row.aft_of_spar.mils if row.aft_of_spar else 0),
            Surface(row.surface),
        )
    else:
        raise BadReading(f"{row.reference}: {row.structure!r} is not a structure on file")

    kind = DamageKind(row.kind)
    return Finding(
        reference=row.reference,
        serial=row.serial,
        kind=kind,
        geometry=geometry_from(kind, dict(row.measurements)),
        point=point,
        found_on=row.found_on,
        source=DiscoverySource(row.source),
        origin=DamageOrigin(row.origin),
        at_usage=Usage(row.at_cycles, row.at_hours),
        revision=row.revision,
        superseded_by=row.superseded_by,
        note=row.note,
    )


def disposition_to_row(record: DispositionRecord) -> DispositionRow:
    return DispositionRow(
        reference=record.reference,
        state=record.state.value,
        moves=[
            MoveRow(
                reference=record.reference,
                sequence=index + 1,
                was=move.was.value,
                now=move.now.value,
                on=move.on,
                by=move.by,
                note=move.note,
            )
            for index, move in enumerate(record.history)
        ],
    )


def row_to_disposition(row: DispositionRow) -> DispositionRecord:
    return DispositionRecord(
        reference=row.reference,
        state=DispositionState(row.state),
        history=tuple(
            Transition(
                DispositionState(move.was),
                DispositionState(move.now),
                move.on,
                move.by,
                move.note,
            )
            for move in sorted(row.moves, key=lambda move: move.sequence)
        ),
    )
