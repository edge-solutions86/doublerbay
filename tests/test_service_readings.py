from datetime import date

import pytest
from click.testing import CliRunner

import sample
from doublerbay.cli.main import cli
from doublerbay.errors import BadReading
from doublerbay.ids import Serial
from doublerbay.service.readings import Line, Taken, lines_from, post_all, snapshot_from
from doublerbay.store.base import build_engine, build_schema
from doublerbay.store.unit import UnitOfWork

HEADER = "serial,taken,cycles,hours\n"
MSN = Serial("5127")
OTHER = Serial("5240")


def csv_of(*rows):
    return HEADER + "".join(f"{one}\n" for one in rows)


@pytest.fixture
def engine():
    made = build_engine()
    build_schema(made)
    with UnitOfWork(made) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(sample.flying_log())
        unit.aircraft.add(sample.aircraft("5240"))
        unit.aircraft.open_log(sample.flying_log("5240"))
    return made


def posted(engine, text):
    with UnitOfWork(engine) as unit:
        return post_all(unit, text)


def test_a_file_with_the_wrong_header_is_refused():
    with pytest.raises(BadReading, match="missing the hours column"):
        lines_from("serial,taken,cycles\n")


def test_a_file_missing_two_columns_names_both():
    with pytest.raises(BadReading, match="missing the cycles, hours column"):
        lines_from("serial,taken\n")


def test_a_file_with_only_a_header_reads_no_lines():
    assert lines_from(HEADER) == ()


def test_a_line_carries_its_position_in_the_file():
    lines = lines_from(csv_of("5127,2025-06-01,14000,24500"))
    assert lines[0].at == 2
    assert lines[0].serial == "5127"


def test_a_line_reads_back_with_its_position():
    line = Line(2, "5127", "2025-06-01", "14000", "24500")
    assert str(line) == "line 2, 5127 on 2025-06-01"


def test_a_good_line_becomes_a_reading():
    snapshot = snapshot_from(Line(2, "5127", "2025-06-01", "14000", "24500"))
    assert snapshot.taken == date(2025, 6, 1)
    assert snapshot.usage.cycles.count == 14000


def test_a_line_with_no_serial_is_refused():
    with pytest.raises(BadReading, match="no serial"):
        snapshot_from(Line(2, "", "2025-06-01", "14000", "24500"))


def test_a_line_with_a_bad_date_is_refused():
    with pytest.raises(BadReading, match="not a date"):
        snapshot_from(Line(2, "5127", "01/06/2025", "14000", "24500"))


def test_a_line_with_bad_cycles_is_refused():
    with pytest.raises(BadReading, match="not a count of cycles"):
        snapshot_from(Line(2, "5127", "2025-06-01", "lots", "24500"))


def test_a_line_with_blank_hours_is_refused():
    with pytest.raises(BadReading, match="not a count of hours"):
        snapshot_from(Line(2, "5127", "2025-06-01", "14000", ""))


def test_one_good_line_lands(engine):
    taken = posted(engine, csv_of("5127,2025-06-01,14000,24500"))
    assert taken.is_clean
    assert taken.posted == (MSN,)
    with UnitOfWork(engine) as unit:
        assert unit.aircraft.log_for(MSN).latest.usage.cycles.count == 14000


def test_two_good_lines_both_land(engine):
    taken = posted(engine, csv_of("5127,2025-06-01,14000,24500", "5240,2025-06-01,14100,24600"))
    assert taken.read == 2
    assert taken.is_clean


def test_a_tail_that_is_not_on_the_register_is_refused_by_itself(engine):
    taken = posted(engine, csv_of("9999,2025-06-01,14000,24500", "5127,2025-06-01,14000,24500"))
    assert taken.posted == (MSN,)
    assert len(taken.refused) == 1
    assert "9999" in str(taken.refused[0])


def test_the_good_lines_after_a_bad_one_still_land(engine):
    taken = posted(
        engine,
        csv_of(
            "9999,2025-06-01,14000,24500",
            "5127,2025-06-01,14000,24500",
            "5240,2025-06-01,14100,24600",
        ),
    )
    assert len(taken.posted) == 2
    with UnitOfWork(engine) as unit:
        assert unit.aircraft.log_for(OTHER).latest.usage.cycles.count == 14100


def test_a_reading_that_runs_a_counter_backwards_is_refused(engine):
    taken = posted(engine, csv_of("5127,2025-06-01,100,200"))
    assert not taken.is_clean
    assert "cycles fell" in taken.refused[0].why


def test_a_refused_line_leaves_the_log_alone(engine):
    before = _latest(engine)
    posted(engine, csv_of("5127,2025-06-01,100,200"))
    assert _latest(engine) == before


def test_a_line_that_cannot_be_read_is_refused_with_its_reason(engine):
    taken = posted(engine, csv_of("5127,01/06/2025,14000,24500"))
    assert "not a date" in taken.refused[0].why


def test_a_blank_serial_is_refused_with_its_reason(engine):
    taken = posted(engine, csv_of(",2025-06-01,14000,24500"))
    assert "no serial" in taken.refused[0].why


def test_a_refusal_reads_back_with_its_line(engine):
    taken = posted(engine, csv_of("9999,2025-06-01,14000,24500"))
    assert str(taken.refused[0]).startswith("line 2, 9999 on 2025-06-01: ")


def test_the_result_counts_what_it_read(engine):
    taken = posted(engine, csv_of("5127,2025-06-01,14000,24500", "9999,2025-06-01,14000,24500"))
    assert taken.read == 2
    assert str(taken) == "1 readings posted, 1 refused"


def test_a_clean_run_reads_back_without_a_refusal_count():
    assert str(Taken((MSN,), ())) == "1 readings posted"


def test_an_empty_file_posts_nothing(engine):
    taken = posted(engine, HEADER)
    assert taken.read == 0
    assert taken.is_clean


def test_the_command_posts_a_file(tmp_path, engine):
    path = tmp_path / "counters.csv"
    path.write_text(csv_of("5127,2025-06-01,14000,24500"))
    answer = CliRunner().invoke(cli, ["fleet", "import", str(path)], obj={"engine": engine})
    assert answer.exit_code == 0, answer.output
    assert "1 readings posted" in answer.output


def test_the_command_prints_every_refusal(tmp_path, engine):
    path = tmp_path / "counters.csv"
    path.write_text(csv_of("9999,2025-06-01,14000,24500"))
    answer = CliRunner().invoke(cli, ["fleet", "import", str(path)], obj={"engine": engine})
    assert "1 refused" in answer.output
    assert "line 2, 9999" in answer.output


def test_the_command_refuses_a_file_with_the_wrong_header(tmp_path, engine):
    path = tmp_path / "counters.csv"
    path.write_text("tail,day\n")
    answer = CliRunner().invoke(cli, ["fleet", "import", str(path)], obj={"engine": engine})
    assert answer.exit_code != 0


def _latest(engine):
    with UnitOfWork(engine) as unit:
        return unit.aircraft.log_for(MSN).latest.usage.cycles.count
