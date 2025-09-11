from datetime import date

import pytest
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError

from doublerbay.ids import FindingId, PanelId, Registration, RepairId, Serial
from doublerbay.measure.length import Length
from doublerbay.measure.usage import Cycles, Hours
from doublerbay.store.base import build_engine, build_schema, opened, table_names
from doublerbay.store.tables import AircraftRow
from doublerbay.store.work import (
    CorrosionRow,
    DeferralRow,
    InspectionRow,
    OrderRow,
    RepairRow,
)

MSN = Serial("5127")
PART = PanelId(53, 10, 143, "L")
DR = FindingId(2025, 4182)
RP = RepairId(53, 142)


def inch(text):
    return Length.of_inches(text)


def engine():
    made = build_engine()
    build_schema(made)
    with opened(made) as session:
        session.add(
            AircraftRow(
                serial=MSN,
                registration=Registration("N412VD"),
                series="200",
                line_number=1140,
                delivered=date(2016, 4, 12),
            )
        )
    return made


def repair(reference=RP, removed=None):
    return RepairRow(
        reference=reference,
        serial=MSN,
        part=PART,
        against=DR,
        form="external doubler",
        category="A",
        sealed=True,
        page="53-40-01",
        material="2024-T3 CLAD",
        gauge=inch("0.050"),
        along=inch("5.282"),
        around=inch("5.282"),
        filler=None,
        fastener_dash=5,
        rows_each_side=2,
        per_row=4,
        pitch=inch("0.625"),
        row_spacing=inch("0.625"),
        edge_distance=inch("0.391"),
        centre_along=inch("468.000"),
        centre_around=inch("91.000"),
        embodied=date(2025, 4, 2),
        at_cycles=Cycles(13900),
        at_hours=Hours.of_hours(24400),
        removed=removed,
    )


def test_the_work_tables_are_in_the_schema():
    names = table_names()
    for expected in ("repair", "inspection", "deferral", "engineering_order", "corrosion_entry"):
        assert expected in names


def test_a_repair_goes_in_with_its_whole_drawing_on_the_row():
    made = engine()
    with opened(made) as session:
        session.add(repair())
    with opened(made) as session:
        row = session.execute(select(RepairRow)).scalar_one()
    assert row.reference == RP
    assert row.gauge == inch("0.050")
    assert row.rows_each_side == 2
    assert row.category == "A"


def test_a_repair_number_only_goes_in_once():
    made = engine()
    with opened(made) as session:
        session.add(repair())
    with pytest.raises(IntegrityError), opened(made) as session:
        session.add(repair())


def test_a_removed_repair_keeps_the_day_it_came_off():
    made = engine()
    with opened(made) as session:
        session.add(repair(removed=date(2026, 6, 1)))
    with opened(made) as session:
        assert session.execute(select(RepairRow)).scalar_one().removed == date(2026, 6, 1)


def test_an_inspection_hangs_off_the_repair_it_belongs_to():
    made = engine()
    with opened(made) as session:
        session.add(repair())
        session.add(
            InspectionRow(
                against=RP,
                part=PART,
                method="detailed visual",
                threshold_cycles=Cycles(55000),
                repeat_cycles=Cycles(27500),
                embodied=date(2025, 4, 2),
                at_cycles=Cycles(13900),
                at_hours=Hours.of_hours(24400),
            )
        )
    with opened(made) as session:
        row = session.execute(select(RepairRow)).scalar_one()
        assert row.inspection is not None
        assert row.inspection.method == "detailed visual"


def test_a_repair_carries_at_most_one_inspection():
    made = engine()
    with opened(made) as session:
        session.add(repair())
        session.add(
            InspectionRow(
                against=RP,
                part=PART,
                method="detailed visual",
                embodied=date(2025, 4, 2),
                at_cycles=Cycles(13900),
                at_hours=Hours.of_hours(24400),
            )
        )
    with pytest.raises(IntegrityError), opened(made) as session:
        session.add(
            InspectionRow(
                against=RP,
                part=PART,
                method="high frequency eddy current",
                embodied=date(2025, 4, 2),
                at_cycles=Cycles(13900),
                at_hours=Hours.of_hours(24400),
            )
        )


def test_a_deferral_stores_every_clock_the_limit_names():
    made = engine()
    with opened(made) as session:
        session.add(
            DeferralRow(
                against=DR,
                granted=date(2025, 3, 14),
                at_cycles=Cycles(13800),
                at_hours=Hours.of_hours(24200),
                limit_cycles=Cycles(500),
                limit_hours=Hours.of_hours(750),
                limit_days=180,
                method="detailed visual",
                granted_by="structures",
            )
        )
    with opened(made) as session:
        row = session.execute(select(DeferralRow)).scalar_one()
    assert row.limit_cycles == Cycles(500)
    assert row.limit_days == 180
    assert row.limit_months is None


def test_a_card_carries_one_deferral_at_a_time():
    made = engine()
    with opened(made) as session:
        session.add(
            DeferralRow(
                against=DR,
                granted=date(2025, 3, 14),
                at_cycles=Cycles(13800),
                at_hours=Hours.of_hours(24200),
                limit_days=180,
                method="detailed visual",
                granted_by="structures",
            )
        )
    with pytest.raises(IntegrityError), opened(made) as session:
        session.add(
            DeferralRow(
                against=DR,
                granted=date(2025, 6, 1),
                at_cycles=Cycles(14000),
                at_hours=Hours.of_hours(24500),
                limit_days=30,
                method="detailed visual",
                granted_by="structures",
            )
        )


def order(reference="EO-2025-0001", raised=date(2025, 3, 14)):
    return OrderRow(
        reference=reference,
        against=DR,
        question="crack past the book at FR41",
        raised=raised,
        state="raised",
    )


def test_an_order_goes_in_and_comes_back():
    made = engine()
    with opened(made) as session:
        session.add(order())
    with opened(made) as session:
        row = session.execute(select(OrderRow)).scalar_one()
    assert row.reference == "EO-2025-0001"
    assert row.sheet is None


def test_one_card_does_not_get_two_orders_raised_on_one_day():
    made = engine()
    with opened(made) as session:
        session.add(order())
    with pytest.raises(IntegrityError), opened(made) as session:
        session.add(order(reference="EO-2025-0002"))


def test_the_same_card_can_have_a_second_order_on_another_day():
    made = engine()
    with opened(made) as session:
        session.add(order())
        session.add(order(reference="EO-2025-0002", raised=date(2025, 6, 1)))
    with opened(made) as session:
        assert len(session.execute(select(OrderRow)).scalars().all()) == 2


def test_a_corrosion_entry_records_the_level_and_when_it_was_reported():
    made = engine()
    with opened(made) as session:
        session.add(
            CorrosionRow(
                serial=MSN,
                against=DR,
                part=PART,
                level=3,
                found_on=date(2025, 5, 6),
                reported_on=date(2025, 5, 8),
            )
        )
    with opened(made) as session:
        row = session.execute(select(CorrosionRow)).scalar_one()
    assert row.level == 3
    assert row.reported_on == date(2025, 5, 8)


def test_the_work_rows_repr_readably_for_a_log():
    assert repr(repair()) == "<RepairRow RP-53-0142 on 5310-143L>"
    assert repr(order()) == "<OrderRow EO-2025-0001 raised>"
