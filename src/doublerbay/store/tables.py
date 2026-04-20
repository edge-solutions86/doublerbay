"""The tables, with the columns spelled out rather than inferred.

Two decisions worth knowing about. The serial is the primary key on everything
about an aeroplane, because registrations move between airframes and a foreign
key onto one would follow the paint. And the location of a damage is stored as
its written form, member and offset, in one set of columns per structure with a
discriminator, rather than as a resolved coordinate. A resolved coordinate is
correct only against the grid it was resolved with, and grids get corrected.

The measurement set is the one thing stored as a document. Eight kinds of
damage carry eight different sets of numbers, and a table with forty nullable
columns in it is a table where nobody can tell which of them are supposed to be
filled in. The kind on the row says how to read it, and the domain type
validates it on the way back.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import JSON, Boolean, Date, ForeignKey, Integer, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from doublerbay.ids import FindingId, Registration, Serial
from doublerbay.measure.length import Length
from doublerbay.measure.usage import Cycles, Hours
from doublerbay.store.base import Base, Recorded
from doublerbay.store.types import (
    CyclesColumn,
    FindingIdColumn,
    HoursColumn,
    MilsColumn,
    RegistrationColumn,
    SerialColumn,
)


class AircraftRow(Base, Recorded):
    """One tail, keyed on the serial the airframe keeps for life."""

    __tablename__ = "aircraft"

    serial: Mapped[Serial] = mapped_column(SerialColumn, primary_key=True)
    registration: Mapped[Registration] = mapped_column(RegistrationColumn, index=True)
    series: Mapped[str] = mapped_column(String(8))
    line_number: Mapped[int] = mapped_column(Integer)
    delivered: Mapped[date] = mapped_column(Date)
    withdrawn: Mapped[date | None] = mapped_column(Date, nullable=True)

    embodiments: Mapped[list[EmbodimentRow]] = relationship(
        back_populates="aircraft", cascade="all, delete-orphan"
    )
    readings: Mapped[list[ReadingRow]] = relationship(
        back_populates="aircraft", cascade="all, delete-orphan"
    )

    def __repr__(self) -> str:
        return f"<AircraftRow {self.serial} {self.registration}>"


class EmbodimentRow(Base, Recorded):
    """A service bulletin that has gone on one tail."""

    __tablename__ = "embodiment"
    __table_args__ = (UniqueConstraint("serial", "chapter", "number"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    serial: Mapped[Serial] = mapped_column(SerialColumn, ForeignKey("aircraft.serial"))
    chapter: Mapped[int] = mapped_column(Integer)
    number: Mapped[int] = mapped_column(Integer)

    aircraft: Mapped[AircraftRow] = relationship(back_populates="embodiments")

    def __repr__(self) -> str:
        return f"<EmbodimentRow {self.serial} SB {self.chapter:02d}-{self.number:03d}>"


class ReadingRow(Base, Recorded):
    """One posted counter reading. Two on one day is a mistake and the key says so."""

    __tablename__ = "reading"

    serial: Mapped[Serial] = mapped_column(
        SerialColumn, ForeignKey("aircraft.serial"), primary_key=True
    )
    taken: Mapped[date] = mapped_column(Date, primary_key=True)
    cycles: Mapped[Cycles] = mapped_column(CyclesColumn)
    hours: Mapped[Hours] = mapped_column(HoursColumn)

    aircraft: Mapped[AircraftRow] = relationship(back_populates="readings")

    def __repr__(self) -> str:
        return f"<ReadingRow {self.serial} {self.taken}>"


class FindingRow(Base, Recorded):
    # Every open damage report on the fleet runs the same query: the cards on
    # one tail that nothing has superseded. On the serial alone that reads
    # every revision of every card the aeroplane has ever carried and throws
    # most of them away.
    """One damage card, at one revision, with its location as it was written."""

    __tablename__ = "finding"

    reference: Mapped[FindingId] = mapped_column(FindingIdColumn, primary_key=True)
    revision: Mapped[int] = mapped_column(Integer, primary_key=True)
    serial: Mapped[Serial] = mapped_column(SerialColumn, ForeignKey("aircraft.serial"), index=True)
    kind: Mapped[str] = mapped_column(String(24), index=True)
    found_on: Mapped[date] = mapped_column(Date, index=True)
    source: Mapped[str] = mapped_column(String(32))
    origin: Mapped[str] = mapped_column(String(24))
    at_cycles: Mapped[Cycles] = mapped_column(CyclesColumn)
    at_hours: Mapped[Hours] = mapped_column(HoursColumn)

    # Where it is, written the way the mechanic wrote it. One of the two sets is
    # filled in and the discriminator says which.
    structure: Mapped[str] = mapped_column(String(8))
    frame: Mapped[int | None] = mapped_column(Integer, nullable=True)
    aft_of_frame: Mapped[Length | None] = mapped_column(MilsColumn, nullable=True)
    stringer: Mapped[int | None] = mapped_column(Integer, nullable=True)
    below_stringer: Mapped[Length | None] = mapped_column(MilsColumn, nullable=True)
    rib: Mapped[int | None] = mapped_column(Integer, nullable=True)
    outboard_of_rib: Mapped[Length | None] = mapped_column(MilsColumn, nullable=True)
    spar: Mapped[str | None] = mapped_column(String(8), nullable=True)
    aft_of_spar: Mapped[Length | None] = mapped_column(MilsColumn, nullable=True)
    surface: Mapped[str | None] = mapped_column(String(8), nullable=True)
    side: Mapped[str] = mapped_column(String(1))

    measurements: Mapped[dict[str, object]] = mapped_column(JSON)
    superseded_by: Mapped[FindingId | None] = mapped_column(FindingIdColumn, nullable=True)
    note: Mapped[str] = mapped_column(String(256), default="")

    def __repr__(self) -> str:
        return f"<FindingRow {self.reference} rev {self.revision}>"


class DispositionRow(Base, Recorded):
    """Where a card is now. Its history is the moves table."""

    __tablename__ = "disposition"

    reference: Mapped[FindingId] = mapped_column(FindingIdColumn, primary_key=True)
    state: Mapped[str] = mapped_column(String(24), index=True)

    moves: Mapped[list[MoveRow]] = relationship(
        back_populates="disposition", cascade="all, delete-orphan"
    )

    def __repr__(self) -> str:
        return f"<DispositionRow {self.reference} {self.state}>"


class MoveRow(Base, Recorded):
    """One move in a card's life, with who made it."""

    __tablename__ = "disposition_move"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    reference: Mapped[FindingId] = mapped_column(
        FindingIdColumn, ForeignKey("disposition.reference")
    )
    sequence: Mapped[int] = mapped_column(Integer)
    was: Mapped[str] = mapped_column(String(24))
    now: Mapped[str] = mapped_column(String(24))
    on: Mapped[date] = mapped_column(Date)
    by: Mapped[str] = mapped_column(String(64))
    note: Mapped[str] = mapped_column(String(256), default="")
    reopened: Mapped[bool] = mapped_column(Boolean, default=False)

    disposition: Mapped[DispositionRow] = relationship(back_populates="moves")

    __table_args__ = (UniqueConstraint("reference", "sequence"),)

    def __repr__(self) -> str:
        return f"<MoveRow {self.reference} {self.was} to {self.now}>"
