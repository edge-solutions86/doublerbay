"""Reading and writing domain objects, with the session kept on the other side of it.

A repository here takes a session and hands back domain objects. Nothing above
this layer sees a row, a query or a column name, which is what lets the rules
be tested without a database and lets the schema move without touching them.

Every read comes back as the domain type, which means every read goes through
the domain validation. A row that got into the database with a depth deeper
than its diameter fails on the way out, at the point where somebody can be
told which record it was, rather than three modules later as a limit that
cannot be resolved.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from doublerbay.damage.finding import Finding
from doublerbay.damage.kind import DamageKind
from doublerbay.disposition.deferral import Deferral
from doublerbay.disposition.state import DispositionRecord
from doublerbay.errors import BadReading, NotOnFile
from doublerbay.fleet.aircraft import Aircraft
from doublerbay.fleet.utilisation import Snapshot, UtilisationLog
from doublerbay.ids import FindingId, PanelId, Registration, RepairId, Serial
from doublerbay.repair.record import RepairRecord, clashes
from doublerbay.store.decisions import (
    apply_to_row,
    deferral_to_row,
    repair_to_row,
    row_to_deferral,
    row_to_repair,
)
from doublerbay.store.mapping import (
    aircraft_to_row,
    disposition_to_row,
    finding_to_row,
    log_to_rows,
    row_to_aircraft,
    row_to_disposition,
    row_to_finding,
    rows_to_log,
)
from doublerbay.store.tables import (
    AircraftRow,
    DispositionRow,
    FindingRow,
    MoveRow,
    ReadingRow,
)
from doublerbay.store.work import DeferralRow, RepairRow


class AircraftRepository:
    """Tails and their logs, keyed on the serial."""

    def __init__(self, session: Session) -> None:
        self._session = session

    def add(self, aircraft: Aircraft) -> None:
        if self._row(aircraft.serial) is not None:
            raise BadReading(f"{aircraft.serial} is already on the register")
        self._session.add(aircraft_to_row(aircraft))

    def amend(self, aircraft: Aircraft) -> None:
        row = self._row(aircraft.serial)
        if row is None:
            raise NotOnFile(f"{aircraft.serial} is not on the register")
        self._session.delete(row)
        self._session.flush()
        self._session.add(aircraft_to_row(aircraft))

    def get(self, serial: Serial) -> Aircraft:
        row = self._row(serial)
        if row is None:
            raise NotOnFile(f"{serial} is not on the register")
        return row_to_aircraft(row)

    def by_registration(self, marks: Registration) -> Aircraft:
        row = self._session.execute(
            select(AircraftRow).where(AircraftRow.registration == marks)
        ).scalar_one_or_none()
        if row is None:
            raise NotOnFile(f"{marks} is not on the register")
        return row_to_aircraft(row)

    def all(self) -> tuple[Aircraft, ...]:
        rows = self._session.execute(select(AircraftRow).order_by(AircraftRow.serial)).scalars()
        return tuple(row_to_aircraft(row) for row in rows)

    def in_service_on(self, day: date) -> tuple[Aircraft, ...]:
        return tuple(one for one in self.all() if one.in_service_on(day))

    def post_reading(self, serial: Serial, snapshot: Snapshot) -> None:
        """Add one reading, refusing a counter that has gone backwards."""
        log = self.log_for(serial).posted(snapshot)
        self._session.add(
            ReadingRow(
                serial=serial,
                taken=snapshot.taken,
                cycles=snapshot.usage.cycles,
                hours=snapshot.usage.hours,
            )
        )
        assert log.latest.taken == snapshot.taken

    def log_for(self, serial: Serial) -> UtilisationLog:
        rows = list(
            self._session.execute(
                select(ReadingRow).where(ReadingRow.serial == serial).order_by(ReadingRow.taken)
            ).scalars()
        )
        if not rows:
            raise NotOnFile(f"{serial} has no log on the register")
        return rows_to_log(serial, rows)

    def open_log(self, log: UtilisationLog) -> None:
        for row in log_to_rows(log):
            self._session.add(row)

    def _row(self, serial: Serial) -> AircraftRow | None:
        return self._session.get(AircraftRow, serial)


class FindingRepository:
    """Damage cards, every revision of every one of them."""

    def __init__(self, session: Session) -> None:
        self._session = session

    def raise_finding(self, finding: Finding) -> None:
        if self._revisions(finding.reference):
            raise BadReading(f"{finding.reference} has already been raised")
        if finding.revision != 1:
            raise BadReading(f"{finding.reference} is raised at revision one")
        self._session.add(finding_to_row(finding))

    def record_revision(self, finding: Finding) -> None:
        """Store a later revision of a card that is already on file."""
        existing = self._revisions(finding.reference)
        if not existing:
            raise NotOnFile(f"{finding.reference} is not a finding on this register")
        if finding.revision <= existing[-1].revision:
            raise BadReading(f"{finding.reference} is already at revision {existing[-1].revision}")
        self._session.add(finding_to_row(finding))

    def supersede(self, reference: FindingId, by: FindingId) -> Finding:
        """Close a card because another one now carries the damage.

        This is not a re measurement and it does not make a revision. The
        latest row is marked in place, because the measurements did not change
        and a revision that says nothing new is a revision nobody can read.
        """
        rows = self._revisions(reference)
        if not rows:
            raise NotOnFile(f"{reference} is not a finding on this register")
        closed = row_to_finding(rows[-1]).superseded(by)
        rows[-1].superseded_by = by
        return closed

    def latest(self, reference: FindingId) -> Finding:
        rows = self._revisions(reference)
        if not rows:
            raise NotOnFile(f"{reference} is not a finding on this register")
        return row_to_finding(rows[-1])

    def history(self, reference: FindingId) -> tuple[Finding, ...]:
        rows = self._revisions(reference)
        if not rows:
            raise NotOnFile(f"{reference} is not a finding on this register")
        return tuple(row_to_finding(row) for row in rows)

    def on_tail(self, serial: Serial) -> tuple[Finding, ...]:
        references = self._session.execute(
            select(FindingRow.reference).where(FindingRow.serial == serial).distinct()
        ).scalars()
        return tuple(self.latest(reference) for reference in sorted(references, key=str))

    def open_on(self, serial: Serial) -> tuple[Finding, ...]:
        return tuple(one for one in self.on_tail(serial) if one.is_open)

    def of_kind(self, kind: DamageKind) -> tuple[Finding, ...]:
        references = self._session.execute(
            select(FindingRow.reference).where(FindingRow.kind == kind.value).distinct()
        ).scalars()
        return tuple(self.latest(reference) for reference in sorted(references, key=str))

    def next_reference(self, year: int) -> FindingId:
        used = self._session.execute(select(FindingRow.reference).distinct()).scalars()
        sequences = [one.sequence for one in used if one.year == year]
        return FindingId(year, max(sequences, default=0) + 1)

    def _revisions(self, reference: FindingId) -> list[FindingRow]:
        return list(
            self._session.execute(
                select(FindingRow)
                .where(FindingRow.reference == reference)
                .order_by(FindingRow.revision)
            ).scalars()
        )


class DispositionRepository:
    """Where each card is, and how it got there."""

    def __init__(self, session: Session) -> None:
        self._session = session

    def save(self, record: DispositionRecord) -> None:
        """Write the state and the whole history, replacing whatever was there.

        The moves go first. A core delete does not walk the orm cascade, so
        deleting the disposition on its own leaves its moves behind and the
        next save collides with them on the sequence.
        """
        self._session.execute(delete(MoveRow).where(MoveRow.reference == record.reference))
        self._session.execute(
            delete(DispositionRow).where(DispositionRow.reference == record.reference)
        )
        self._session.flush()
        self._session.add(disposition_to_row(record))

    def get(self, reference: FindingId) -> DispositionRecord:
        row = self._session.get(DispositionRow, reference)
        if row is None:
            raise NotOnFile(f"{reference} has no disposition on file")
        return row_to_disposition(row)

    def in_state(self, state: str) -> tuple[DispositionRecord, ...]:
        rows = self._session.execute(
            select(DispositionRow).where(DispositionRow.state == state)
        ).scalars()
        return tuple(row_to_disposition(row) for row in rows)

    def all(self) -> tuple[DispositionRecord, ...]:
        rows = self._session.execute(
            select(DispositionRow).order_by(DispositionRow.reference)
        ).scalars()
        return tuple(row_to_disposition(row) for row in rows)


class DeferralRepository:
    """Deferrals, one live one per card, keyed on the card it is against."""

    def __init__(self, session: Session) -> None:
        self._session = session

    def grant(self, deferral: Deferral) -> None:
        """Record a deferral, refusing a second live one against the same card."""
        row = self._row(deferral.against)
        if row is not None and row.lifted is None:
            raise BadReading(f"{deferral.against} is already deferred")
        if row is not None:
            self._session.delete(row)
            self._session.flush()
        self._session.add(deferral_to_row(deferral))

    def tighten(self, deferral: Deferral) -> None:
        """Amend the live deferral against a card to a shorter limit."""
        apply_to_row(self._live(deferral.against), deferral)

    def lift(self, reference: FindingId, on: date) -> None:
        """Close a deferral because the damage under it has been dealt with."""
        row = self._live(reference)
        if on < row.granted:
            raise BadReading(f"{reference}: a deferral cannot be lifted before it was granted")
        row.lifted = on

    def get(self, reference: FindingId) -> Deferral:
        return row_to_deferral(self._live(reference))

    def live(self) -> tuple[Deferral, ...]:
        """Every deferral still being flown against."""
        rows = self._session.scalars(
            select(DeferralRow).where(DeferralRow.lifted.is_(None)).order_by(DeferralRow.granted)
        ).all()
        return tuple(row_to_deferral(row) for row in rows)

    def against(self, references: tuple[FindingId, ...]) -> tuple[Deferral, ...]:
        """The live deferrals against a named set of cards, in the order asked for."""
        held = {one.against: one for one in self.live()}
        return tuple(held[reference] for reference in references if reference in held)

    def granted_between(self, start: date, end: date) -> tuple[Deferral, ...]:
        """Everything granted in a window, lifted or not, for the monthly report."""
        if end < start:
            raise BadReading("a window ends after it starts")
        rows = self._session.scalars(
            select(DeferralRow)
            .where(DeferralRow.granted >= start, DeferralRow.granted <= end)
            .order_by(DeferralRow.granted)
        ).all()
        return tuple(row_to_deferral(row) for row in rows)

    def _row(self, reference: FindingId) -> DeferralRow | None:
        return self._session.get(DeferralRow, reference)

    def _live(self, reference: FindingId) -> DeferralRow:
        row = self._row(reference)
        if row is None or row.lifted is not None:
            raise NotOnFile(f"{reference} is not deferred")
        return row


class RepairRepository:
    """Repairs, keyed on their own number and read back by tail or by panel."""

    def __init__(self, session: Session) -> None:
        self._session = session

    def embody(self, record: RepairRecord, serial: Serial) -> None:
        """Record a repair as fitted to one tail, refusing a number already used."""
        if self._session.get(RepairRow, record.reference) is not None:
            raise BadReading(f"{record.reference} is already on file")
        self._session.add(repair_to_row(record, serial))

    def get(self, reference: RepairId) -> RepairRecord:
        row = self._session.get(RepairRow, reference)
        if row is None:
            raise NotOnFile(f"{reference} is not a repair on file")
        return row_to_repair(row)

    def remove(self, reference: RepairId, on: date) -> None:
        """Mark a repair as taken off, which is not the same as deleting it."""
        row = self._session.get(RepairRow, reference)
        if row is None:
            raise NotOnFile(f"{reference} is not a repair on file")
        if row.removed is not None:
            raise BadReading(f"{reference} was already removed on {row.removed}")
        if on < row.embodied:
            raise BadReading(f"{reference} cannot come off before it went on")
        row.removed = on

    def on_tail(self, serial: Serial, fitted_only: bool = True) -> tuple[RepairRecord, ...]:
        """Every repair on one aeroplane, still fitted or ever fitted."""
        query = select(RepairRow).where(RepairRow.serial == serial)
        if fitted_only:
            query = query.where(RepairRow.removed.is_(None))
        rows = self._session.scalars(query.order_by(RepairRow.embodied)).all()
        return tuple(row_to_repair(row) for row in rows)

    def on_panel(self, serial: Serial, part: PanelId) -> tuple[RepairRecord, ...]:
        """Every repair still fitted to one panel of one tail, oldest first.

        The tail is part of the question. A panel number names a part, and
        asking what is fitted to that part without saying which aeroplane
        gathers up the whole fleet's repairs into one clash check.
        """
        rows = self._session.scalars(
            select(RepairRow)
            .where(
                RepairRow.serial == serial,
                RepairRow.part == part,
                RepairRow.removed.is_(None),
            )
            .order_by(RepairRow.embodied)
        ).all()
        return tuple(row_to_repair(row) for row in rows)

    def against(self, reference: FindingId) -> tuple[RepairRecord, ...]:
        """Every repair raised against one damage card."""
        rows = self._session.scalars(
            select(RepairRow).where(RepairRow.against == reference).order_by(RepairRow.embodied)
        ).all()
        return tuple(row_to_repair(row) for row in rows)

    def clashing_on(
        self, serial: Serial, part: PanelId
    ) -> tuple[tuple[RepairRecord, RepairRecord], ...]:
        """Pairs of fitted repairs on one panel that are too close together."""
        return clashes(self.on_panel(serial, part))

    def needing_inspection(self, serial: Serial) -> tuple[RepairRecord, ...]:
        """The fitted repairs that carry a repeat inspection."""
        return tuple(one for one in self.on_tail(serial) if one.needs_inspection)
