from datetime import date

import pytest
from click.testing import CliRunner

import sample
from conftest import new_engine
from doublerbay.cli.corroding import LEVELS, level_numbered
from doublerbay.cli.main import cli
from doublerbay.damage.corrosion import Corrosion
from doublerbay.damage.kind import DamageKind, DamageOrigin
from doublerbay.fleet.catalogue import TypeCatalogue
from doublerbay.manual.book import v320_book
from doublerbay.service.intake import take_in
from doublerbay.store.unit import UnitOfWork
from doublerbay.tolerance.corrosion import CorrosionLevel

DAY = date(2025, 3, 14)
LATER = date(2026, 3, 14)


def invoke(engine, *args):
    return CliRunner().invoke(cli, list(args), obj={"engine": engine})


def stocked():
    engine = new_engine()
    with UnitOfWork(engine) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(sample.flying_log())
    return engine


def rust(depth="0.030"):
    return Corrosion(sample.inch("2.000"), sample.inch("1.000"), sample.inch(depth))


def graded(engine, sequence=1, found=DAY, urgent=False, at=None, depth="0.030"):
    finding = sample.card(
        sequence,
        DamageKind.CORROSION,
        rust(depth),
        at=at,
        found=found,
        origin=DamageOrigin.ENVIRONMENT,
    )
    with UnitOfWork(engine) as unit:
        return take_in(unit, v320_book(), TypeCatalogue(), finding, urgent)


def test_a_level_is_read_off_its_number():
    assert level_numbered(2) is CorrosionLevel.TWO


def test_a_level_that_is_not_one_names_the_ones_that_are():
    with pytest.raises(Exception, match="1, 2, 3"):
        level_numbered(9)


def test_the_levels_are_the_three_the_programme_grades():
    assert LEVELS == ("1", "2", "3")


def test_a_tail_with_no_corrosion_says_so():
    printed = invoke(stocked(), "corrosion", "list", "--tail", "5127").output
    assert printed.strip() == "nothing graded"


def test_a_graded_finding_is_listed():
    engine = stocked()
    graded(engine)
    printed = invoke(engine, "corrosion", "list", "--tail", "5127").output
    assert "5310" in printed
    assert "found on" in printed


def test_a_tail_that_is_not_on_the_register_is_refused():
    assert invoke(stocked(), "corrosion", "list", "--tail", "9999").exit_code != 0


def test_the_fleet_can_be_asked_for_one_level():
    engine = stocked()
    graded(engine, 1)
    graded(engine, 2, LATER)
    printed = invoke(engine, "corrosion", "list", "--level", "2").output
    assert "5310" in printed


def test_a_level_nobody_has_reached_says_nothing():
    engine = stocked()
    graded(engine)
    printed = invoke(engine, "corrosion", "list", "--level", "3").output
    assert printed.strip() == "nothing graded"


def test_a_level_that_is_not_one_is_refused():
    assert invoke(stocked(), "corrosion", "list", "--level", "9").exit_code != 0


def test_nothing_is_waiting_to_be_reported():
    engine = stocked()
    graded(engine)
    printed = invoke(engine, "corrosion", "unreported", "--on", "2025-03-20").output
    assert printed.strip() == "nothing waiting to be reported"


def test_a_level_three_is_waiting_to_be_reported():
    engine = stocked()
    graded(engine, urgent=True)
    printed = invoke(engine, "corrosion", "unreported", "--on", "2025-03-16").output
    assert "2025-03-17" in printed
    assert "no" in printed


def test_a_level_three_past_its_clock_is_late():
    engine = stocked()
    graded(engine, urgent=True)
    printed = invoke(engine, "corrosion", "unreported", "--on", "2025-06-01").output
    assert "yes" in printed


def test_a_finding_can_be_marked_reported():
    engine = stocked()
    graded(engine, urgent=True)
    answer = invoke(engine, "corrosion", "reported", "DR-2025-000001", "--on", "2025-03-16")
    assert answer.exit_code == 0, answer.output
    assert "reported DR-2025-000001" in answer.output


def test_a_reported_finding_drops_off_the_list():
    engine = stocked()
    graded(engine, urgent=True)
    invoke(engine, "corrosion", "reported", "DR-2025-000001", "--on", "2025-03-16")
    printed = invoke(engine, "corrosion", "unreported", "--on", "2025-06-01").output
    assert "nothing waiting" in printed


def test_reporting_one_that_is_not_on_the_register_is_refused():
    engine = stocked()
    answer = invoke(engine, "corrosion", "reported", "DR-2025-000099", "--on", "2025-03-16")
    assert answer.exit_code != 0


def test_the_programme_report_says_it_is_coping():
    engine = stocked()
    graded(engine, depth="0.002")
    printed = invoke(
        engine,
        "corrosion",
        "programme",
        "--tail",
        "5127",
        "--since",
        "2020-01-01",
        "--until",
        "2026-12-31",
    ).output
    assert "coping" in printed
    assert "yes" in printed


def test_the_programme_report_names_its_denominator():
    engine = stocked()
    printed = invoke(
        engine,
        "corrosion",
        "programme",
        "--tail",
        "5127",
        "--since",
        "2020-01-01",
        "--until",
        "2026-12-31",
    ).output
    assert "not panels inspected" in printed


def test_a_repeat_shows_on_the_programme_report():
    engine = stocked()
    graded(engine, 1)
    graded(engine, 2, LATER)
    printed = invoke(
        engine,
        "corrosion",
        "programme",
        "--tail",
        "5127",
        "--since",
        "2020-01-01",
        "--until",
        "2026-12-31",
    ).output
    assert "repeat" in printed
    assert "2 times over" in printed


def test_a_persistent_location_prints_the_warning():
    engine = stocked()
    graded(engine, 1, date(2025, 1, 1))
    graded(engine, 2, date(2026, 1, 1))
    graded(engine, 3, date(2026, 6, 1))
    printed = invoke(
        engine,
        "corrosion",
        "programme",
        "--tail",
        "5127",
        "--since",
        "2020-01-01",
        "--until",
        "2026-12-31",
    ).output
    assert "keep corroding" in printed


def test_a_window_that_ends_before_it_starts_is_refused():
    engine = stocked()
    answer = invoke(
        engine,
        "corrosion",
        "programme",
        "--tail",
        "5127",
        "--since",
        "2026-12-31",
        "--until",
        "2020-01-01",
    )
    assert answer.exit_code != 0
