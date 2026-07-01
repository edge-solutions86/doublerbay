from datetime import date

from click.testing import CliRunner

import sample
from conftest import assessed_card
from doublerbay.cli.checking import pack_rows
from doublerbay.cli.main import cli
from doublerbay.fleet.utilisation import Snapshot, UtilisationLog
from doublerbay.ids import RepairId, Serial
from doublerbay.report.workpack import PackLine, WorkPack
from doublerbay.store.base import build_engine, build_schema
from doublerbay.store.unit import UnitOfWork

DAY = date(2025, 3, 14)
FITTED = "2025-04-02"
OPENS = "2025-04-01"
CLOSES = "2025-04-08"
MSN = Serial("5127")


def invoke(engine, *args):
    return CliRunner().invoke(cli, list(args), obj={"engine": engine})


def log_with_two_readings():
    opened = UtilisationLog.opened(MSN, date(2016, 4, 12))
    opened = opened.posted(Snapshot(date(2024, 3, 14), sample.flown(12000, 21000)))
    return opened.posted(Snapshot(date(2025, 3, 1), sample.flown(13800, 24200)))


def stocked():
    engine = build_engine()
    build_schema(engine)
    with UnitOfWork(engine) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(log_with_two_readings())
    return engine


def assessed(engine, sequence=1):
    """One card on a fastener row, raised and looked up."""
    return str(assessed_card(engine, sequence, at=sample.cabin_point(stringer=10)))


def fit(engine, reference, sequence=1, centre=("468.000", "91.000")):
    return invoke(
        engine,
        "repair",
        "fit",
        reference,
        "--sequence",
        str(sequence),
        "--along",
        "2.000",
        "--around",
        "2.000",
        "--centre-along",
        centre[0],
        "--centre-around",
        centre[1],
        "--on",
        FITTED,
        "--by",
        "s.brandt",
        "--temporary",
    )


def defer(engine, reference, limit="1 FC"):
    return invoke(
        engine,
        "defer",
        "grant",
        reference,
        "--by",
        "j.mercer",
        "--on",
        "2025-03-14",
        "--limit",
        limit,
    )


def pack_of(engine, *extra):
    return invoke(
        engine, "check", "pack", "--tail", "5127", "--opens", OPENS, "--closes", CLOSES, *extra
    )


def test_an_empty_pack_says_so():
    assert "nothing for this check" in pack_of(stocked()).output


def test_a_pack_prints_its_blocks():
    engine = stocked()
    defer(engine, assessed(engine))
    printed = pack_of(engine).output
    assert "DEFERRALS" in printed
    assert "DR-2025-000001" in printed


def test_a_pack_marks_what_is_already_over():
    engine = stocked()
    reference = assessed(engine)
    invoke(
        engine,
        "defer",
        "grant",
        reference,
        "--by",
        "j.mercer",
        "--on",
        "2025-03-15",
        "--limit",
        "1 DY",
    )
    assert "OVER" in pack_of(engine).output


def test_a_pack_can_pull_work_in_from_ahead():
    engine = stocked()
    defer(engine, assessed(engine), "500 FC")
    assert "nothing for this check" in pack_of(engine).output
    assert "DEFERRALS" in pack_of(engine, "--within", "400").output


def test_a_pack_on_a_tail_that_is_not_there_is_refused():
    engine = stocked()
    answer = invoke(engine, "check", "pack", "--tail", "9999", "--opens", OPENS, "--closes", CLOSES)
    assert answer.exit_code != 0


def test_a_check_that_closes_before_it_opens_is_refused():
    engine = stocked()
    answer = invoke(engine, "check", "pack", "--tail", "5127", "--opens", CLOSES, "--closes", OPENS)
    assert answer.exit_code != 0


def test_an_empty_programme_says_so():
    printed = invoke(stocked(), "check", "programme", "--tail", "5127").output
    assert printed.strip() == "nothing on the programme for 5127"


def test_a_fitted_repair_shows_on_the_programme():
    engine = stocked()
    fit(engine, assessed(engine))
    printed = invoke(engine, "check", "programme", "--tail", "5127").output
    assert "RP-53-0001" in printed
    assert "once only" in printed
    assert "never" in printed


def test_the_programme_can_be_cut_to_what_is_never_done():
    engine = stocked()
    fit(engine, assessed(engine))
    printed = invoke(engine, "check", "programme", "--tail", "5127", "--never-done").output
    assert "RP-53-0001" in printed


def test_a_programme_on_a_tail_that_is_not_there_is_refused():
    assert invoke(stocked(), "check", "programme", "--tail", "9999").exit_code != 0


def test_nothing_is_due_the_day_the_repair_goes_on():
    engine = stocked()
    fit(engine, assessed(engine))
    printed = invoke(engine, "check", "due", "--tail", "5127", "--on", FITTED).output
    assert printed.strip() == "nothing due on 5127"


def test_a_task_past_its_threshold_is_due():
    engine = stocked()
    fit(engine, assessed(engine))
    printed = invoke(engine, "check", "due", "--tail", "5127", "--on", "2030-01-01").output
    assert "RP-53-0001" in printed


def test_a_task_can_be_signed_off():
    engine = stocked()
    fit(engine, assessed(engine))
    answer = invoke(
        engine,
        "check",
        "done",
        "53",
        "1",
        "--on",
        "2026-01-05",
        "--cycles",
        "15000",
        "--hours",
        "26000",
    )
    assert answer.exit_code == 0, answer.output
    assert "signed off RP-53-0001" in answer.output


def test_a_one_off_prints_no_next_due():
    engine = stocked()
    fit(engine, assessed(engine))
    printed = invoke(
        engine,
        "check",
        "done",
        "53",
        "1",
        "--on",
        "2026-01-05",
        "--cycles",
        "15000",
        "--hours",
        "26000",
    ).output
    assert "next due" not in printed


def test_a_signed_off_task_carries_its_reading():
    engine = stocked()
    fit(engine, assessed(engine))
    invoke(
        engine,
        "check",
        "done",
        "53",
        "1",
        "--on",
        "2026-01-05",
        "--cycles",
        "15000",
        "--hours",
        "26000",
    )
    with UnitOfWork(engine) as unit:
        assert unit.inspections.get(RepairId(53, 1)).at_last_done.cycles.count == 15000


def test_signing_off_one_that_is_not_there_is_refused():
    engine = stocked()
    answer = invoke(
        engine,
        "check",
        "done",
        "53",
        "9",
        "--on",
        "2026-01-05",
        "--cycles",
        "15000",
        "--hours",
        "26000",
    )
    assert answer.exit_code != 0


def test_the_pack_rows_carry_a_heading_per_block():
    pack = WorkPack(
        serial=MSN,
        opens=date(2025, 4, 1),
        closes=date(2025, 4, 8),
        deferrals=(PackLine("deferral", "DR-2025-000001", "detailed visual"),),
        inspections=(PackLine("inspection", "RP-53-0001", "eddy current"),),
        waiting=(),
    )
    rows = pack_rows(pack)
    assert rows[0][0] == "DEFERRALS"
    assert ("", "", "") in rows
    assert any(row[0] == "INSPECTIONS" for row in rows)


def test_an_empty_pack_has_no_rows():
    pack = WorkPack(MSN, date(2025, 4, 1), date(2025, 4, 8), (), (), ())
    assert pack_rows(pack) == []
