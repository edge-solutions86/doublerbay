from datetime import date

import pytest
from click.testing import CliRunner

import sample
from conftest import new_engine
from doublerbay.assess.verdict import Disposition
from doublerbay.cli.main import cli
from doublerbay.errors import BadReading
from doublerbay.fleet.catalogue import TypeCatalogue
from doublerbay.ids import FindingId, Serial
from doublerbay.manual.book import v320_book
from doublerbay.report.revision import Moved, Sweep, sweep, worse_on
from doublerbay.store.unit import UnitOfWork

BEFORE = date(2025, 3, 14)
AFTER = date(2026, 3, 14)
MSN = Serial("5127")


@pytest.fixture
def engine():
    made = new_engine()
    with UnitOfWork(made) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(sample.flying_log())
    return made


def raised(engine, sequence=1):
    with UnitOfWork(engine) as unit:
        unit.findings.raise_finding(sample.card(sequence))
    return FindingId(2025, sequence)


def swept(engine, before=BEFORE, after=AFTER):
    with UnitOfWork(engine) as unit:
        return sweep(unit, v320_book(), TypeCatalogue(), before, after)


def test_a_fleet_with_no_cards_looks_at_nothing(engine):
    built = swept(engine)
    assert built.looked_at == 0
    assert built.is_quiet


def test_a_quiet_sweep_reads_back_as_quiet(engine):
    assert str(swept(engine)) == "0 cards looked at, nothing moved"


def test_every_open_card_is_looked_at(engine):
    raised(engine, 1)
    raised(engine, 2)
    assert swept(engine).looked_at == 2


def test_a_revision_that_changes_nothing_moves_nothing(engine):
    raised(engine)
    built = swept(engine)
    assert built.moved == ()
    assert built.is_quiet


def test_a_superseded_card_is_not_looked_at(engine):
    raised(engine, 1)
    raised(engine, 2)
    with UnitOfWork(engine) as unit:
        unit.findings.supersede(FindingId(2025, 1), FindingId(2025, 2))
    assert swept(engine).looked_at == 1


def test_a_day_after_that_is_not_after_is_refused(engine):
    with pytest.raises(BadReading, match="comes after the day before it"):
        swept(engine, AFTER, BEFORE)


def test_the_same_day_twice_is_refused(engine):
    with pytest.raises(BadReading):
        swept(engine, BEFORE, BEFORE)


def test_a_card_that_cannot_be_assessed_is_listed_rather_than_dropped(engine):
    raised(engine)
    built = swept(engine, BEFORE, date(2050, 1, 1))
    assert built.looked_at == 1
    assert built.moved == ()


def test_a_tail_not_in_service_on_the_later_day_is_skipped(engine):
    """A window before delivery looks at nothing rather than refusing.

    The register is asked which tails were in service, and a sweep over a
    window the fleet did not exist in is an empty sweep, not an error.
    """
    raised(engine)
    built = swept(engine, date(2000, 1, 1), date(2000, 1, 2))
    assert built.looked_at == 0
    assert built.is_quiet


def test_a_move_that_got_worse_says_so():
    made = Moved(FindingId(2025, 1), MSN, Disposition.ALLOWABLE, Disposition.REPAIRABLE)
    assert made.got_worse
    assert not made.got_better
    assert "(worse)" in str(made)


def test_a_move_that_got_better_says_so():
    made = Moved(FindingId(2025, 1), MSN, Disposition.OUT_OF_LIMITS, Disposition.ALLOWABLE)
    assert made.got_better
    assert "(better)" in str(made)


def test_a_sweep_counts_both_directions():
    built = Sweep(
        before=BEFORE,
        after=AFTER,
        looked_at=3,
        moved=(
            Moved(FindingId(2025, 1), MSN, Disposition.ALLOWABLE, Disposition.REPAIRABLE),
            Moved(FindingId(2025, 2), MSN, Disposition.OUT_OF_LIMITS, Disposition.ALLOWABLE),
        ),
        unreadable=(),
    )
    assert len(built.worse) == 1
    assert len(built.better) == 1
    assert not built.is_quiet


def test_a_sweep_line_names_both_directions():
    built = Sweep(
        before=BEFORE,
        after=AFTER,
        looked_at=3,
        moved=(
            Moved(FindingId(2025, 1), MSN, Disposition.ALLOWABLE, Disposition.REPAIRABLE),
            Moved(FindingId(2025, 2), MSN, Disposition.OUT_OF_LIMITS, Disposition.ALLOWABLE),
        ),
        unreadable=(),
    )
    assert "1 worse" in str(built)
    assert "1 better" in str(built)


def test_a_sweep_with_something_unreadable_says_so():
    built = Sweep(BEFORE, AFTER, 1, (), (FindingId(2025, 1),))
    assert "1 could not be assessed" in str(built)
    assert not built.is_quiet


def test_the_moves_on_one_tail_can_be_picked_out():
    other = Serial("5240")
    built = Sweep(
        before=BEFORE,
        after=AFTER,
        looked_at=2,
        moved=(
            Moved(FindingId(2025, 1), MSN, Disposition.ALLOWABLE, Disposition.REPAIRABLE),
            Moved(FindingId(2025, 2), other, Disposition.ALLOWABLE, Disposition.REPAIRABLE),
        ),
        unreadable=(),
    )
    assert len(worse_on(built, MSN)) == 1


def test_the_command_reports_a_quiet_sweep(engine):
    answer = CliRunner().invoke(
        cli,
        ["report", "revision", "--before", "2025-03-14", "--after", "2026-03-14"],
        obj={"engine": engine},
    )
    assert answer.exit_code == 0, answer.output
    assert "nothing moved" in answer.output


def test_the_command_counts_what_it_looked_at(engine):
    raised(engine)
    answer = CliRunner().invoke(
        cli,
        ["report", "revision", "--before", "2025-03-14", "--after", "2026-03-14"],
        obj={"engine": engine},
    )
    assert "1 cards looked at" in answer.output


def test_the_command_refuses_days_in_the_wrong_order(engine):
    answer = CliRunner().invoke(
        cli,
        ["report", "revision", "--before", "2026-03-14", "--after", "2025-03-14"],
        obj={"engine": engine},
    )
    assert answer.exit_code != 0


def test_the_command_refuses_a_day_that_is_not_one(engine):
    answer = CliRunner().invoke(
        cli,
        ["report", "revision", "--before", "14/03/2025", "--after", "2026-03-14"],
        obj={"engine": engine},
    )
    assert answer.exit_code != 0
