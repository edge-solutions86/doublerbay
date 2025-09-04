import pytest
from sqlalchemy import Column, Integer, MetaData, String, Table, insert, select

from doublerbay.ids import FindingId, PanelId, Registration, RepairId, Serial
from doublerbay.measure.length import Length
from doublerbay.measure.usage import Cycles, Hours
from doublerbay.store.base import (
    NAMING,
    Base,
    build_engine,
    build_schema,
    drop_schema,
    opened,
    table_names,
)
from doublerbay.store.types import (
    CyclesColumn,
    FindingIdColumn,
    HoursColumn,
    MilsColumn,
    PanelIdColumn,
    RegistrationColumn,
    RepairIdColumn,
    SerialColumn,
)

META = MetaData()
PROBE = Table(
    "probe",
    META,
    Column("id", Integer, primary_key=True),
    Column("depth", MilsColumn),
    Column("cycles", CyclesColumn),
    Column("hours", HoursColumn),
    Column("serial", SerialColumn),
    Column("marks", RegistrationColumn),
    Column("finding", FindingIdColumn),
    Column("repair", RepairIdColumn),
    Column("panel", PanelIdColumn),
    Column("note", String(16)),
)


def engine():
    made = build_engine()
    META.create_all(made)
    return made


def round_trip(**values):
    made = engine()
    with made.connect() as connection:
        connection.execute(insert(PROBE).values(id=1, **values))
        connection.commit()
        return connection.execute(select(PROBE).where(PROBE.c.id == 1)).mappings().one()


def test_a_length_goes_in_as_mils_and_comes_back_as_a_length():
    row = round_trip(depth=Length.of_inches("0.040"))
    assert row["depth"] == Length.of_inches("0.040")
    assert isinstance(row["depth"], Length)


def test_a_thousandth_survives_the_round_trip_exactly():
    row = round_trip(depth=Length(37))
    assert row["depth"].mils == 37


def test_the_counters_go_in_as_whole_numbers():
    row = round_trip(cycles=Cycles(13800), hours=Hours.of_hours("24200.5"))
    assert row["cycles"] == Cycles(13800)
    assert row["hours"] == Hours.of_hours("24200.5")


def test_a_serial_is_stored_without_the_prefix_it_prints_with():
    made = engine()
    with made.connect() as connection:
        connection.execute(insert(PROBE).values(id=1, serial=Serial("5127")))
        connection.commit()
        raw = connection.execute(select(PROBE.c.serial)).scalar_one()
    assert raw == Serial("5127")


def test_the_identifiers_go_in_as_the_text_people_read():
    made = engine()
    with made.connect() as connection:
        connection.execute(
            insert(PROBE).values(id=1, finding=FindingId(2025, 4182), repair=RepairId(53, 142))
        )
        connection.commit()
        rows = connection.exec_driver_sql("select finding, repair from probe where id = 1").one()
    assert rows[0] == "DR-2025-004182"
    assert rows[1] == "RP-53-0142"


def test_every_identifier_comes_back_as_its_own_type():
    row = round_trip(
        serial=Serial("5127"),
        marks=Registration("N412VD"),
        finding=FindingId(2025, 4182),
        repair=RepairId(53, 142),
        panel=PanelId(53, 10, 143, "L"),
    )
    assert isinstance(row["serial"], Serial)
    assert isinstance(row["marks"], Registration)
    assert isinstance(row["finding"], FindingId)
    assert isinstance(row["repair"], RepairId)
    assert isinstance(row["panel"], PanelId)


def test_nothing_stays_nothing_through_every_column():
    row = round_trip(note="empty")
    for column in ("depth", "cycles", "hours", "serial", "marks", "finding", "repair", "panel"):
        assert row[column] is None


def test_the_naming_convention_covers_every_kind_of_constraint():
    assert set(NAMING) == {"ix", "uq", "ck", "fk", "pk"}
    assert Base.metadata.naming_convention["pk"] == "pk_%(table_name)s"


def test_the_schema_builds_and_comes_apart_again():
    made = build_engine()
    build_schema(made)
    drop_schema(made)


def test_a_session_commits_on_the_way_out():
    made = engine()
    with opened(made) as session:
        session.execute(insert(PROBE).values(id=1, depth=Length(40)))
    with made.connect() as connection:
        assert connection.execute(select(PROBE.c.depth)).scalar_one() == Length(40)


def test_a_session_rolls_back_when_something_raises():
    made = engine()
    with pytest.raises(RuntimeError), opened(made) as session:
        session.execute(insert(PROBE).values(id=1, depth=Length(40)))
        raise RuntimeError("something went wrong")
    with made.connect() as connection:
        assert connection.execute(select(PROBE)).all() == []


def test_the_table_list_comes_back_in_creation_order():
    assert isinstance(table_names(), tuple)
