from click.testing import CliRunner

from doublerbay.cli.main import cli
from doublerbay.cli.output import as_date, columns
from doublerbay.store.base import build_engine, build_schema


def runner():
    return CliRunner()


def invoke(engine, *args):
    return runner().invoke(cli, list(args), obj={"engine": engine})


def stocked():
    engine = build_engine()
    build_schema(engine)
    return engine


def enrol(engine, serial="5127", marks="N412VD"):
    return invoke(
        engine,
        "fleet",
        "enrol",
        serial,
        marks,
        "--line-number",
        "1140",
        "--delivered",
        "2016-04-12",
    )


def test_a_date_is_read_the_one_way():
    assert as_date("2025-03-14").isoformat() == "2025-03-14"


def test_columns_line_up_on_the_widest_cell():
    laid = columns([("a", "bbbb"), ("cccc", "d")])
    assert laid.splitlines()[0] == "a     bbbb"
    assert laid.splitlines()[1] == "cccc  d"


def test_columns_over_nothing_is_nothing():
    assert columns([]) == ""


def test_the_schema_can_be_built_from_the_command_line():
    engine = build_engine()
    answer = invoke(engine, "init")
    assert answer.exit_code == 0
    assert "built" in answer.output


def test_a_tail_can_be_enrolled_and_listed():
    engine = stocked()
    assert enrol(engine).exit_code == 0
    listed = invoke(engine, "fleet", "list")
    assert "5127" in listed.output
    assert "N412VD" in listed.output


def test_the_register_lists_nothing_when_it_is_empty():
    listed = invoke(stocked(), "fleet", "list")
    assert listed.output.strip() == "serial  marks  series  delivered"


def test_the_register_can_be_asked_for_a_day():
    engine = stocked()
    enrol(engine)
    assert "5127" in invoke(engine, "fleet", "list", "--on", "2020-01-01").output
    assert "5127" not in invoke(engine, "fleet", "list", "--on", "2000-01-01").output


def test_a_tail_that_is_enrolled_twice_is_refused():
    engine = stocked()
    enrol(engine)
    again = enrol(engine)
    assert again.exit_code != 0
    assert "already on the register" in str(again.exception)


def test_marks_that_are_not_marks_are_refused():
    engine = stocked()
    answer = enrol(engine, marks="not-a-tail")
    assert answer.exit_code != 0


def test_a_tail_shows_its_latest_reading():
    engine = stocked()
    enrol(engine)
    invoke(engine, "fleet", "post", "5127", "2025-01-01", "--cycles", "13800", "--hours", "24200")
    shown = invoke(engine, "fleet", "show", "5127")
    assert "13800 FC" in shown.output
    assert "2025-01-01" in shown.output


def test_a_tail_with_one_reading_says_it_cannot_give_a_rate():
    engine = stocked()
    enrol(engine)
    assert "not enough readings" in invoke(engine, "fleet", "show", "5127").output


def test_a_tail_with_two_readings_gives_a_rate():
    engine = stocked()
    enrol(engine)
    invoke(engine, "fleet", "post", "5127", "2024-01-01", "--cycles", "12000", "--hours", "21000")
    invoke(engine, "fleet", "post", "5127", "2025-01-01", "--cycles", "13800", "--hours", "24200")
    shown = invoke(engine, "fleet", "show", "5127")
    assert "FC and" in shown.output


def test_a_reading_that_runs_a_counter_backwards_is_refused():
    engine = stocked()
    enrol(engine)
    invoke(engine, "fleet", "post", "5127", "2025-01-01", "--cycles", "13800", "--hours", "24200")
    answer = invoke(
        engine, "fleet", "post", "5127", "2025-06-01", "--cycles", "100", "--hours", "200"
    )
    assert answer.exit_code != 0
    assert "cycles fell" in str(answer.exception)


def test_a_tail_that_is_not_there_is_refused():
    answer = invoke(stocked(), "fleet", "show", "9999")
    assert answer.exit_code != 0
    assert "not on the register" in str(answer.exception)


def test_the_help_names_what_the_desk_does():
    answer = runner().invoke(cli, ["--help"], obj={})
    assert "structures desk" in answer.output
