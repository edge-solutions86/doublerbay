from datetime import date

import pytest
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError

from doublerbay.ids import FindingId, Registration, Serial
from doublerbay.measure.length import Length
from doublerbay.measure.usage import Cycles, Hours
from doublerbay.store.base import build_engine, build_schema, opened, table_names
from doublerbay.store.tables import (
    AircraftRow,
    DispositionRow,
    EmbodimentRow,
    FindingRow,
    MoveRow,
    ReadingRow,
)

MSN = Serial("5127")


def inch(text):
    return Length.of_inches(text)


def engine():
    made = build_engine()
    build_schema(made)
    return made


def aircraft(serial=MSN, marks="N412VD"):
    return AircraftRow(
        serial=serial,
        registration=Registration(marks),
        series="200",
        line_number=1140,
        delivered=date(2016, 4, 12),
    )


def finding(sequence=4182, revision=1, serial=MSN):
    return FindingRow(
        reference=FindingId(2025, sequence),
        revision=revision,
        serial=serial,
        kind="dent",
        found_on=date(2025, 3, 14),
        source="scheduled check",
        origin="impact",
        at_cycles=Cycles(13900),
        at_hours=Hours.of_hours(24400),
        structure="fuselage",
        frame=15,
        aft_of_frame=inch("10.000"),
        stringer=6,
        below_stringer=inch("0.000"),
        side="L",
        measurements={"depth": 20, "along": 3000, "around": 2000},
    )


def test_the_schema_names_every_table_it_defines():
    names = table_names()
    for expected in ("aircraft", "embodiment", "reading", "finding", "disposition"):
        assert expected in names


def test_a_tail_goes_in_and_comes_back_with_its_own_types():
    made = engine()
    with opened(made) as session:
        session.add(aircraft())
    with opened(made) as session:
        row = session.execute(select(AircraftRow)).scalar_one()
    assert row.serial == MSN
    assert row.registration == Registration("N412VD")
    assert row.delivered == date(2016, 4, 12)
    assert row.recorded_at is not None


def test_a_tail_is_keyed_on_the_serial_and_not_the_marks():
    made = engine()
    with opened(made) as session:
        session.add(aircraft())
    with pytest.raises(IntegrityError), opened(made) as session:
        session.add(aircraft(marks="N908QA"))


def test_two_tails_can_wear_the_same_marks_at_different_times():
    made = engine()
    with opened(made) as session:
        session.add(aircraft())
        session.add(aircraft(serial=Serial("5240")))
    with opened(made) as session:
        assert len(session.execute(select(AircraftRow)).scalars().all()) == 2


def test_a_bulletin_only_goes_on_a_tail_once():
    made = engine()
    with opened(made) as session:
        session.add(aircraft())
        session.add(EmbodimentRow(serial=MSN, chapter=53, number=18))
    with pytest.raises(IntegrityError), opened(made) as session:
        session.add(EmbodimentRow(serial=MSN, chapter=53, number=18))


def test_two_readings_on_one_day_are_refused_by_the_key():
    made = engine()
    with opened(made) as session:
        session.add(aircraft())
        session.add(
            ReadingRow(
                serial=MSN, taken=date(2025, 1, 1), cycles=Cycles(13800), hours=Hours(1452000)
            )
        )
    with pytest.raises(IntegrityError), opened(made) as session:
        session.add(
            ReadingRow(
                serial=MSN, taken=date(2025, 1, 1), cycles=Cycles(13900), hours=Hours(1460000)
            )
        )


def test_the_counters_come_back_as_counters():
    made = engine()
    with opened(made) as session:
        session.add(aircraft())
        session.add(
            ReadingRow(
                serial=MSN, taken=date(2025, 1, 1), cycles=Cycles(13800), hours=Hours(1452000)
            )
        )
    with opened(made) as session:
        row = session.execute(select(ReadingRow)).scalar_one()
    assert row.cycles == Cycles(13800)
    assert row.hours == Hours(1452000)


def test_a_card_goes_in_with_its_location_as_it_was_written():
    made = engine()
    with opened(made) as session:
        session.add(aircraft())
        session.add(finding())
    with opened(made) as session:
        row = session.execute(select(FindingRow)).scalar_one()
    assert row.reference == FindingId(2025, 4182)
    assert row.frame == 15
    assert row.aft_of_frame == inch("10.000")
    assert row.rib is None
    assert row.structure == "fuselage"


def test_the_measurements_ride_along_as_a_document():
    made = engine()
    with opened(made) as session:
        session.add(aircraft())
        session.add(finding())
    with opened(made) as session:
        row = session.execute(select(FindingRow)).scalar_one()
    assert row.measurements["depth"] == 20
    assert row.measurements["along"] == 3000


def test_a_card_is_keyed_on_its_number_and_its_revision_together():
    made = engine()
    with opened(made) as session:
        session.add(aircraft())
        session.add(finding(revision=1))
        session.add(finding(revision=2))
    with opened(made) as session:
        rows = session.execute(select(FindingRow)).scalars().all()
    assert {row.revision for row in rows} == {1, 2}


def test_the_same_revision_of_a_card_cannot_go_in_twice():
    made = engine()
    with opened(made) as session:
        session.add(aircraft())
        session.add(finding())
    with pytest.raises(IntegrityError), opened(made) as session:
        session.add(finding())


def test_a_cards_moves_hang_off_its_disposition():
    made = engine()
    with opened(made) as session:
        session.add(aircraft())
        session.add(
            DispositionRow(
                reference=FindingId(2025, 4182),
                state="assessed",
                moves=[
                    MoveRow(
                        reference=FindingId(2025, 4182),
                        sequence=1,
                        was="raised",
                        now="assessed",
                        on=date(2025, 3, 14),
                        by="structures",
                    )
                ],
            )
        )
    with opened(made) as session:
        row = session.execute(select(DispositionRow)).scalar_one()
        assert len(row.moves) == 1
        assert row.moves[0].by == "structures"


def test_two_moves_cannot_share_a_sequence_on_one_card():
    made = engine()
    reference = FindingId(2025, 4182)
    with opened(made) as session:
        session.add(DispositionRow(reference=reference, state="assessed"))
        session.add(
            MoveRow(
                reference=reference,
                sequence=1,
                was="raised",
                now="assessed",
                on=date(2025, 3, 14),
                by="structures",
            )
        )
    with pytest.raises(IntegrityError), opened(made) as session:
        session.add(
            MoveRow(
                reference=reference,
                sequence=1,
                was="assessed",
                now="deferred",
                on=date(2025, 4, 1),
                by="structures",
            )
        )


def test_the_rows_repr_readably_for_a_log():
    assert repr(aircraft()) == "<AircraftRow MSN 5127 N412VD>"
    assert repr(finding()) == "<FindingRow DR-2025-004182 rev 1>"
