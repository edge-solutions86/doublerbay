from datetime import date

import pytest
from click.testing import CliRunner

from doublerbay.cli.main import cli
from doublerbay.errors import BadReading
from doublerbay.ids import FindingId, Serial
from doublerbay.report.desk import due_on_tail, open_across_fleet
from doublerbay.service.seeding import FLEET, LATEST, Seeded, seed
from doublerbay.store.base import build_engine, build_schema
from doublerbay.store.unit import UnitOfWork
from doublerbay.tolerance.corrosion import CorrosionLevel

CLEAN = Serial("5461")
RUSTY = Serial("5240")
DEFERRED = Serial("5388")


@pytest.fixture
def engine():
    made = build_engine()
    build_schema(made)
    return made


def sown(engine, today=LATEST):
    with UnitOfWork(engine) as unit:
        return seed(unit, today)


def test_the_seed_puts_the_whole_fleet_on(engine):
    answer = sown(engine)
    assert answer.aircraft == len(FLEET)
    with UnitOfWork(engine) as unit:
        assert len(unit.aircraft.all()) == 4


def test_every_tail_has_two_readings_so_it_has_a_rate(engine):
    sown(engine)
    with UnitOfWork(engine) as unit:
        for serial, *_ in FLEET:
            assert len(unit.aircraft.log_for(Serial(serial)).snapshots) == 3


def test_the_seed_reads_back_as_one_line(engine):
    assert str(sown(engine)) == "4 aircraft, 5 damage cards, 1 deferrals"


def test_seeding_twice_is_refused(engine):
    sown(engine)
    with UnitOfWork(engine) as unit, pytest.raises(BadReading, match="already holds a fleet"):
        seed(unit)


def test_one_tail_is_left_clean(engine):
    sown(engine)
    with UnitOfWork(engine) as unit:
        assert unit.findings.on_tail(CLEAN) == ()


def test_the_clean_tail_shows_up_as_clean_on_the_fleet_report(engine):
    sown(engine)
    with UnitOfWork(engine) as unit:
        report = open_across_fleet(unit, LATEST)
    assert CLEAN in report.clean_tails


def test_the_dented_tail_carries_two_cards(engine):
    sown(engine)
    with UnitOfWork(engine) as unit:
        assert len(unit.findings.on_tail(Serial("5127"))) == 2


def test_the_corrosion_comes_back_in_the_same_place_as_a_level_two(engine):
    sown(engine)
    with UnitOfWork(engine) as unit:
        graded = unit.corrosion.on_tail(RUSTY)
    assert len(graded) == 2
    assert graded[1].level is CorrosionLevel.TWO


def test_the_first_corrosion_is_still_a_level_one(engine):
    sown(engine)
    with UnitOfWork(engine) as unit:
        assert unit.corrosion.on_tail(RUSTY)[0].level is CorrosionLevel.ONE


def test_one_tail_has_something_deferred(engine):
    sown(engine)
    with UnitOfWork(engine) as unit:
        live = unit.deferrals.live()
    assert len(live) == 1
    assert live[0].against == FindingId(2025, 5)


def test_the_deferred_tail_lands_on_its_own_due_list(engine):
    sown(engine)
    with UnitOfWork(engine) as unit:
        report = due_on_tail(unit, DEFERRED, LATEST)
    assert len(report.items) == 1


def test_the_deferral_carries_the_reason_it_was_granted_for(engine):
    sown(engine)
    with UnitOfWork(engine) as unit:
        assert unit.deferrals.get(FindingId(2025, 5)).reason == "awaiting a doubler"


def test_the_deferred_card_is_actually_deferred(engine):
    sown(engine)
    with UnitOfWork(engine) as unit:
        assert unit.dispositions.get(FindingId(2025, 5)).state.value == "deferred"


def test_the_fleet_report_counts_every_open_card(engine):
    sown(engine)
    with UnitOfWork(engine) as unit:
        assert open_across_fleet(unit, LATEST).total == 5


def test_a_later_day_still_seeds(engine):
    answer = sown(engine, date(2025, 6, 1))
    assert answer.cards == 5


def test_the_seed_result_can_be_built_directly():
    assert str(Seeded(1, 2, 3)) == "1 aircraft, 2 damage cards, 3 deferrals"


def test_the_command_seeds_an_empty_database(engine):
    answer = CliRunner().invoke(cli, ["seed"], obj={"engine": engine})
    assert answer.exit_code == 0, answer.output
    assert "seeded 4 aircraft" in answer.output


def test_the_command_takes_a_day(engine):
    answer = CliRunner().invoke(cli, ["seed", "--on", "2025-06-01"], obj={"engine": engine})
    assert answer.exit_code == 0


def test_the_command_refuses_a_database_that_already_holds_a_fleet(engine):
    CliRunner().invoke(cli, ["seed"], obj={"engine": engine})
    answer = CliRunner().invoke(cli, ["seed"], obj={"engine": engine})
    assert answer.exit_code != 0


def test_the_seeded_fleet_can_be_listed_from_the_command_line(engine):
    CliRunner().invoke(cli, ["seed"], obj={"engine": engine})
    listed = CliRunner().invoke(cli, ["fleet", "list"], obj={"engine": engine}).output
    assert "N412VD" in listed
    assert len(listed.strip().splitlines()) == 5
