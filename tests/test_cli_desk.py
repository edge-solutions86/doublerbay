from datetime import date

import pytest
from click.testing import CliRunner

import sample
from doublerbay.cli.desk import method_named, today_or
from doublerbay.cli.main import cli
from doublerbay.disposition.state import DispositionRecord, DispositionState
from doublerbay.ids import FindingId, Serial
from doublerbay.store.base import build_engine, build_schema
from doublerbay.store.unit import UnitOfWork
from doublerbay.tolerance.method import InspectionMethod

DAY = "2025-03-14"
LATER = "2025-06-01"


def invoke(engine, *args):
    return CliRunner().invoke(cli, list(args), obj={"engine": engine})


def stocked(serial="5127", marks="N412VD"):
    engine = build_engine()
    build_schema(engine)
    enrol(engine, serial, marks)
    return engine


def enrol(engine, serial="5127", marks="N412VD"):
    invoke(
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
    invoke(engine, "fleet", "post", serial, "2024-03-14", "--cycles", "12000", "--hours", "21000")
    invoke(engine, "fleet", "post", serial, "2025-03-01", "--cycles", "13800", "--hours", "24200")


def assessed(engine, sequence=1, serial="5127"):
    """A card on file that has been looked up, which is what a deferral needs."""
    reference = FindingId(2025, sequence)
    with UnitOfWork(engine) as unit:
        unit.findings.raise_finding(sample.card(sequence, serial=serial))
        record = DispositionRecord.raised(reference)
        unit.dispositions.save(
            record.moved_to(DispositionState.ASSESSED, date(2025, 3, 14), "a.okonkwo")
        )
    return str(reference)


def granted(engine, reference, *extra):
    return invoke(engine, "defer", "grant", reference, "--by", "j.mercer", "--on", DAY, *extra)


def test_a_method_is_read_off_its_short_name():
    assert method_named("hfec") is InspectionMethod.HFEC
    assert method_named(" DVI ") is InspectionMethod.DVI


def test_a_method_that_is_not_one_names_the_ones_that_are():
    with pytest.raises(Exception, match="lfec"):
        method_named("magic")


def test_a_day_left_off_falls_back_to_the_real_one():
    assert today_or(None) == date.today()
    assert today_or("2025-03-14") == date(2025, 3, 14)


def test_a_card_can_be_deferred(capsys):
    engine = stocked()
    answer = granted(engine, assessed(engine))
    assert answer.exit_code == 0, answer.output
    assert "deferred DR-2025-000001" in answer.output


def test_deferring_moves_the_disposition_as_well_as_writing_the_limit():
    engine = stocked()
    granted(engine, assessed(engine))
    with UnitOfWork(engine) as unit:
        assert unit.dispositions.get(FindingId(2025, 1)).state is DispositionState.DEFERRED
        assert unit.deferrals.get(FindingId(2025, 1)).by == "j.mercer"


def test_a_card_that_has_not_been_assessed_cannot_be_deferred():
    engine = stocked()
    with UnitOfWork(engine) as unit:
        unit.findings.raise_finding(sample.card(1))
        unit.dispositions.save(DispositionRecord.raised(FindingId(2025, 1)))
    answer = granted(engine, "DR-2025-000001")
    assert answer.exit_code != 0
    assert "cannot go from raised to deferred" in str(answer.exception)


def test_a_refused_deferral_leaves_no_limit_behind():
    engine = stocked()
    with UnitOfWork(engine) as unit:
        unit.findings.raise_finding(sample.card(1))
        unit.dispositions.save(DispositionRecord.raised(FindingId(2025, 1)))
    granted(engine, "DR-2025-000001")
    with UnitOfWork(engine) as unit:
        assert unit.deferrals.live() == ()


def test_an_engineer_set_limit_is_used():
    engine = stocked()
    granted(engine, assessed(engine), "--limit", "50 FC")
    with UnitOfWork(engine) as unit:
        assert unit.deferrals.get(FindingId(2025, 1)).limit.cycles.count == 50


def test_a_deferral_to_a_check_is_cut_at_the_check():
    engine = stocked()
    granted(engine, assessed(engine), "--until-check", "2025-04-01")
    with UnitOfWork(engine) as unit:
        assert unit.deferrals.get(FindingId(2025, 1)).limit.days == 18


def test_the_method_reaches_the_stored_deferral():
    engine = stocked()
    granted(engine, assessed(engine), "--method", "hfec")
    with UnitOfWork(engine) as unit:
        held = unit.deferrals.get(FindingId(2025, 1))
    assert held.re_inspect_with is InspectionMethod.HFEC


def test_a_deferral_shows_what_it_is_due_against():
    engine = stocked()
    reference = assessed(engine)
    granted(engine, reference, "--limit", "500 FC", "--reason", "awaiting parts")
    shown = invoke(engine, "defer", "show", reference).output
    assert "awaiting parts" in shown
    assert "14300 FC" in shown


def test_a_deferral_with_no_reason_says_so():
    engine = stocked()
    reference = assessed(engine)
    granted(engine, reference)
    assert "not given" in invoke(engine, "defer", "show", reference).output


def test_a_live_deferral_can_be_tightened():
    engine = stocked()
    reference = assessed(engine)
    granted(engine, reference, "--limit", "500 FC")
    answer = invoke(engine, "defer", "tighten", reference, "--limit", "50 FC", "--by", "a.okonkwo")
    assert answer.exit_code == 0
    with UnitOfWork(engine) as unit:
        assert unit.deferrals.get(FindingId(2025, 1)).limit.cycles.count == 50


def test_tightening_one_that_is_not_deferred_is_refused():
    engine = stocked()
    answer = invoke(
        engine, "defer", "tighten", "DR-2025-000009", "--limit", "50 FC", "--by", "a.okonkwo"
    )
    assert answer.exit_code != 0


def test_a_deferral_can_be_lifted():
    engine = stocked()
    reference = assessed(engine)
    granted(engine, reference)
    assert invoke(engine, "defer", "lift", reference, "--on", LATER).exit_code == 0
    with UnitOfWork(engine) as unit:
        assert unit.deferrals.live() == ()


def test_lifting_one_that_was_never_granted_is_refused():
    engine = stocked()
    answer = invoke(engine, "defer", "lift", "DR-2025-000009", "--on", LATER)
    assert answer.exit_code != 0


def test_a_fleet_with_nothing_due_says_so():
    engine = stocked()
    assert invoke(engine, "report", "due", "--on", DAY).output.strip() == "nothing due"


def test_the_due_report_lists_the_deferral():
    engine = stocked()
    granted(engine, assessed(engine), "--limit", "500 FC")
    printed = invoke(engine, "report", "due", "--on", DAY).output
    assert "DR-2025-000001" in printed
    assert "days on" in printed


def test_the_due_report_can_be_asked_for_one_tail():
    engine = stocked()
    enrol(engine, "5240", "N908QA")
    granted(engine, assessed(engine, 1, "5127"), "--limit", "500 FC")
    printed = invoke(engine, "report", "due", "--tail", "5240", "--on", DAY).output
    assert printed.strip() == "nothing due"


def test_the_due_report_takes_a_horizon():
    engine = stocked()
    granted(engine, assessed(engine), "--limit", "500 FC")
    printed = invoke(engine, "report", "due", "--on", DAY, "--within", "5").output
    assert printed.strip() == "nothing due"


def test_nothing_grounded_says_so():
    engine = stocked()
    assert invoke(engine, "report", "grounded", "--on", DAY).output.strip() == "nothing grounded"


def test_a_tail_past_its_limit_is_listed_as_grounded():
    engine = stocked()
    granted(engine, assessed(engine), "--limit", "30 DY")
    printed = invoke(engine, "report", "grounded", "--on", "2026-06-01").output
    assert "5127" in printed
    assert "measured" in printed


def test_the_open_report_counts_the_fleet():
    engine = stocked()
    assessed(engine, 1)
    printed = invoke(engine, "report", "open", "--on", DAY).output
    assert "fleet" in printed
    assert "5127" in printed


def test_the_open_report_on_one_tail_breaks_it_down():
    engine = stocked()
    assessed(engine, 1)
    printed = invoke(engine, "report", "open", "--tail", "5127", "--on", DAY).output
    assert "  dent" in printed
    assert "  chapter 53" in printed


def test_the_open_report_takes_a_recent_window():
    engine = stocked()
    assessed(engine, 1)
    printed = invoke(
        engine, "report", "open", "--tail", "5127", "--on", DAY, "--recent", "1"
    ).output
    assert "raised recently" in printed


def test_a_tail_that_is_not_on_the_register_cannot_be_reported_on():
    engine = stocked()
    answer = invoke(engine, "report", "open", "--tail", "9999", "--on", DAY)
    assert answer.exit_code != 0


def test_the_serial_on_the_due_report_is_the_one_asked_for():
    engine = stocked()
    granted(engine, assessed(engine), "--limit", "500 FC")
    with UnitOfWork(engine) as unit:
        assert unit.findings.latest(FindingId(2025, 1)).serial == Serial("5127")
