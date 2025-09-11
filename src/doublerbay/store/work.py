"""The tables for what the desk decides, as opposed to what arrives.

Split from the finding tables on purpose. Everything in the other module is a
record of something that happened to an aeroplane: it was built, it flew, it
was damaged. Everything here is a record of a decision somebody made about
that: repair it this way, defer it until then, ask the manufacturer. The two
have different retention rules and different people arguing about them, and
keeping them apart makes both easier to read.

The repair carries its whole drawing as columns rather than a reference to a
scheme table. There are only so many ways to draw a doubler and the numbers on
one repair are not shared with any other, so a lookup table would be one row
per repair with a join on top of it.
"""

from __future__ import annotations

from datetime import date

from sqlalchemy import Boolean, Date, ForeignKey, Integer, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from doublerbay.ids import FindingId, PanelId, RepairId, Serial
from doublerbay.measure.length import Length
from doublerbay.measure.usage import Cycles, Hours
from doublerbay.store.base import Base, Recorded
from doublerbay.store.types import (
    CyclesColumn,
    FindingIdColumn,
    HoursColumn,
    MilsColumn,
    PanelIdColumn,
    RepairIdColumn,
    SerialColumn,
)


class RepairRow(Base, Recorded):
    """One repair, with its drawing on the row."""

    __tablename__ = "repair"

    reference: Mapped[RepairId] = mapped_column(RepairIdColumn, primary_key=True)
    serial: Mapped[Serial] = mapped_column(SerialColumn, ForeignKey("aircraft.serial"), index=True)
    part: Mapped[PanelId] = mapped_column(PanelIdColumn, index=True)
    against: Mapped[FindingId] = mapped_column(FindingIdColumn, index=True)

    form: Mapped[str] = mapped_column(String(24))
    category: Mapped[str] = mapped_column(String(1), index=True)
    sealed: Mapped[bool] = mapped_column(Boolean)
    page: Mapped[str] = mapped_column(String(16))

    material: Mapped[str] = mapped_column(String(24))
    gauge: Mapped[Length] = mapped_column(MilsColumn)
    along: Mapped[Length] = mapped_column(MilsColumn)
    around: Mapped[Length] = mapped_column(MilsColumn)
    filler: Mapped[Length | None] = mapped_column(MilsColumn, nullable=True)

    fastener_dash: Mapped[int] = mapped_column(Integer)
    rows_each_side: Mapped[int] = mapped_column(Integer)
    per_row: Mapped[int] = mapped_column(Integer)
    pitch: Mapped[Length] = mapped_column(MilsColumn)
    row_spacing: Mapped[Length] = mapped_column(MilsColumn)
    edge_distance: Mapped[Length] = mapped_column(MilsColumn)

    centre_along: Mapped[Length] = mapped_column(MilsColumn)
    centre_around: Mapped[Length] = mapped_column(MilsColumn)

    embodied: Mapped[date] = mapped_column(Date, index=True)
    at_cycles: Mapped[Cycles] = mapped_column(CyclesColumn)
    at_hours: Mapped[Hours] = mapped_column(HoursColumn)
    removed: Mapped[date | None] = mapped_column(Date, nullable=True)

    inspection: Mapped[InspectionRow | None] = relationship(
        back_populates="repair", cascade="all, delete-orphan", uselist=False
    )

    def __repr__(self) -> str:
        return f"<RepairRow {self.reference} on {self.part}>"


class InspectionRow(Base, Recorded):
    """The repeat inspection one repair carries, and when it was last done."""

    __tablename__ = "inspection"

    against: Mapped[RepairId] = mapped_column(
        RepairIdColumn, ForeignKey("repair.reference"), primary_key=True
    )
    part: Mapped[PanelId] = mapped_column(PanelIdColumn)
    method: Mapped[str] = mapped_column(String(32))

    threshold_cycles: Mapped[Cycles | None] = mapped_column(CyclesColumn, nullable=True)
    threshold_months: Mapped[int | None] = mapped_column(Integer, nullable=True)
    repeat_cycles: Mapped[Cycles | None] = mapped_column(CyclesColumn, nullable=True)

    embodied: Mapped[date] = mapped_column(Date)
    at_cycles: Mapped[Cycles] = mapped_column(CyclesColumn)
    at_hours: Mapped[Hours] = mapped_column(HoursColumn)

    last_done: Mapped[date | None] = mapped_column(Date, nullable=True, index=True)
    last_cycles: Mapped[Cycles | None] = mapped_column(CyclesColumn, nullable=True)
    last_hours: Mapped[Hours | None] = mapped_column(HoursColumn, nullable=True)

    repair: Mapped[RepairRow] = relationship(back_populates="inspection")

    def __repr__(self) -> str:
        return f"<InspectionRow {self.against} {self.method}>"


class DeferralRow(Base, Recorded):
    """A decision to keep flying with a damage, and the limit on it."""

    __tablename__ = "deferral"

    against: Mapped[FindingId] = mapped_column(FindingIdColumn, primary_key=True)
    granted: Mapped[date] = mapped_column(Date, index=True)
    at_cycles: Mapped[Cycles] = mapped_column(CyclesColumn)
    at_hours: Mapped[Hours] = mapped_column(HoursColumn)

    limit_cycles: Mapped[Cycles | None] = mapped_column(CyclesColumn, nullable=True)
    limit_hours: Mapped[Hours | None] = mapped_column(HoursColumn, nullable=True)
    limit_months: Mapped[int | None] = mapped_column(Integer, nullable=True)
    limit_days: Mapped[int | None] = mapped_column(Integer, nullable=True)

    method: Mapped[str] = mapped_column(String(32))
    granted_by: Mapped[str] = mapped_column(String(64))
    reason: Mapped[str] = mapped_column(String(256), default="")
    lifted: Mapped[date | None] = mapped_column(Date, nullable=True)

    def __repr__(self) -> str:
        return f"<DeferralRow {self.against} granted {self.granted}>"


class OrderRow(Base, Recorded):
    """One question out to the manufacturer and whatever came back."""

    __tablename__ = "engineering_order"
    __table_args__ = (UniqueConstraint("against", "raised"),)

    reference: Mapped[str] = mapped_column(String(16), primary_key=True)
    against: Mapped[FindingId] = mapped_column(FindingIdColumn, index=True)
    question: Mapped[str] = mapped_column(String(512))
    raised: Mapped[date] = mapped_column(Date, index=True)
    state: Mapped[str] = mapped_column(String(16), index=True)
    sent: Mapped[date | None] = mapped_column(Date, nullable=True)
    answered: Mapped[date | None] = mapped_column(Date, nullable=True)

    sheet: Mapped[str | None] = mapped_column(String(16), nullable=True)
    approved_category: Mapped[str | None] = mapped_column(String(1), nullable=True)
    valid_until: Mapped[date | None] = mapped_column(Date, nullable=True)

    def __repr__(self) -> str:
        return f"<OrderRow {self.reference} {self.state}>"


class CorrosionRow(Base, Recorded):
    """One graded corrosion finding, which the programme reports on separately."""

    __tablename__ = "corrosion_entry"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    serial: Mapped[Serial] = mapped_column(SerialColumn, ForeignKey("aircraft.serial"), index=True)
    against: Mapped[FindingId] = mapped_column(FindingIdColumn, index=True)
    part: Mapped[PanelId] = mapped_column(PanelIdColumn, index=True)
    level: Mapped[int] = mapped_column(Integer, index=True)
    found_on: Mapped[date] = mapped_column(Date, index=True)
    reported_on: Mapped[date | None] = mapped_column(Date, nullable=True)

    def __repr__(self) -> str:
        return f"<CorrosionRow {self.part} level {self.level}>"
