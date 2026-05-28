import csv
from datetime import date
from decimal import Decimal
from io import StringIO

import pytest
from click.testing import CliRunner

import sample
from conftest import new_engine
from doublerbay.cli.main import cli
from doublerbay.damage.crack import Crack
from doublerbay.damage.kind import DamageKind, DamageOrigin
from doublerbay.disposition.deferral import grant
from doublerbay.disposition.state import DispositionRecord, DispositionState
from doublerbay.errors import BadReading
from doublerbay.fleet.forecast import Forecast
from doublerbay.ids import FindingId, Serial
from doublerbay.measure.angle import Angle
from doublerbay.measure.interval import Interval
from doublerbay.report.due import due_list
from doublerbay.report.export import (
    DUE_HEADER,
    GROWTH_HEADER,
    PACK_HEADER,
    as_csv,
    due_as_csv,
    growth_as_csv,
    line_count,
    pack_as_csv,
)
from doublerbay.report.growth import growth_of
from doublerbay.report.workpack import build
from doublerbay.store.unit import UnitOfWork

DAY = date(2025, 3, 14)
MSN = Serial("5127")


def read_back(written):
    return list(csv.reader(StringIO(written)))


def test_a_file_carries_its_header():
    assert read_back(as_csv(("a", "b"), []))[0] == ["a", "b"]


def test_a_file_with_no_header_is_refused():
    with pytest.raises(BadReading, match="has a header on it"):
        as_csv((), [])


def test_a_row_of_the_wrong_width_is_refused_by_position():
    with pytest.raises(BadReading, match="row 3 has 1 columns"):
        as_csv(("a", "b"), [("x", "y"), ("z",)])


def test_a_value_with_a_comma_in_it_is_quoted():
    written = as_csv(("a",), [("one, two",)])
    assert read_back(written)[1] == ["one, two"]


def test_a_value_with_a_quote_in_it_survives():
    written = as_csv(("a",), [('he said "no"',)])
    assert read_back(written)[1] == ['he said "no"']


def test_the_rows_are_counted_without_the_header():
    assert line_count(as_csv(("a",), [("x",), ("y",)])) == 2
    assert line_count(as_csv(("a",), [])) == 0


def test_a_due_list_exports_its_columns():
    items = _due()
    written = read_back(due_as_csv(items))
    assert written[0] == list(DUE_HEADER)
    assert written[1][0] == "5127"
    assert written[1][1] == "DR-2025-000041"


def test_an_empty_due_list_is_a_header_and_nothing_else():
    assert line_count(due_as_csv(())) == 0


def test_a_due_line_says_whether_it_is_over():
    assert read_back(due_as_csv(_due()))[1][-1] in ("yes", "no")


def test_a_work_pack_exports_the_block_as_a_column():
    engine = _stocked()
    _defer(engine)
    with UnitOfWork(engine) as unit:
        pack = build(unit, MSN, date(2025, 4, 1), date(2025, 4, 8), 400)
    written = read_back(pack_as_csv(pack))
    assert written[0] == list(PACK_HEADER)
    assert written[1][0] == "deferral"


def test_an_empty_pack_exports_a_header_and_nothing_else():
    engine = _stocked()
    with UnitOfWork(engine) as unit:
        pack = build(unit, MSN, date(2025, 4, 1), date(2025, 4, 8))
    assert line_count(pack_as_csv(pack)) == 0


def test_growth_exports_one_row_per_card():
    written = read_back(growth_as_csv((_growth(),)))
    assert written[0] == list(GROWTH_HEADER)
    assert written[1][1] == "2"


def test_growth_with_no_measurable_rate_leaves_the_column_blank():
    written = read_back(growth_as_csv((_growth(cycles=13810),)))
    assert written[1][3] == ""


def test_growth_says_whether_it_is_a_trend():
    assert read_back(growth_as_csv((_growth(),)))[1][-1] == "no"


def test_the_due_command_writes_a_file(tmp_path):
    engine = _stocked()
    _defer(engine)
    path = tmp_path / "due.csv"
    answer = CliRunner().invoke(
        cli,
        ["report", "due", "--on", "2025-03-14", "--csv", str(path)],
        obj={"engine": engine},
    )
    assert answer.exit_code == 0, answer.output
    assert "wrote 1 rows" in answer.output
    assert read_back(path.read_text())[0] == list(DUE_HEADER)


def test_the_due_command_still_prints_when_no_file_is_asked_for():
    engine = _stocked()
    _defer(engine)
    answer = CliRunner().invoke(
        cli, ["report", "due", "--on", "2025-03-14"], obj={"engine": engine}
    )
    assert "DR-2025-000001" in answer.output


def test_the_pack_command_writes_a_file(tmp_path):
    engine = _stocked()
    _defer(engine)
    path = tmp_path / "pack.csv"
    answer = CliRunner().invoke(
        cli,
        [
            "check",
            "pack",
            "--tail",
            "5127",
            "--opens",
            "2025-04-01",
            "--closes",
            "2025-04-08",
            "--within",
            "400",
            "--csv",
            str(path),
        ],
        obj={"engine": engine},
    )
    assert answer.exit_code == 0, answer.output
    assert "wrote 1 rows" in answer.output
    assert read_back(path.read_text())[1][0] == "deferral"


def test_the_pack_command_still_prints_when_no_file_is_asked_for():
    engine = _stocked()
    answer = CliRunner().invoke(
        cli,
        ["check", "pack", "--tail", "5127", "--opens", "2025-04-01", "--closes", "2025-04-08"],
        obj={"engine": engine},
    )
    assert "nothing for this check" in answer.output


def _stocked():
    engine = new_engine()
    with UnitOfWork(engine) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(sample.flying_log())
    return engine


def _defer(engine, sequence=1):
    reference = FindingId(2025, sequence)
    with UnitOfWork(engine) as unit:
        unit.findings.raise_finding(sample.card(sequence))
        unit.dispositions.save(
            DispositionRecord.raised(reference).moved_to(
                DispositionState.ASSESSED, DAY, "a.okonkwo"
            )
        )
    with UnitOfWork(engine) as unit:
        unit.deferrals.grant(
            grant(
                reference,
                DAY,
                sample.flown(13800, 24200),
                "j.mercer",
                engineer_set=Interval.parse("1 FC"),
            )
        )
        held = unit.dispositions.get(reference)
        unit.dispositions.save(held.moved_to(DispositionState.DEFERRED, DAY, "j.mercer"))


def _due():
    deferral = grant(
        FindingId(2025, 41),
        DAY,
        sample.flown(13800, 24200),
        "j.mercer",
        engineer_set=Interval.parse("500 FC"),
    )
    rate = Forecast(sample.flying_log(), Decimal("4"), Decimal("7"))
    return due_list((deferral,), MSN, sample.flown(13800, 24200), rate, DAY)


def _growth(cycles=14800):
    from dataclasses import replace

    def one(length, at, revision):
        made = sample.card(
            1, DamageKind.CRACK, Crack(sample.inch(length), Angle(0)), origin=DamageOrigin.FATIGUE
        )
        return replace(made, revision=revision, at_usage=sample.flown(at, at * 2))

    return growth_of((one("0.400", 13800, 1), one("0.500", cycles, 2)))
