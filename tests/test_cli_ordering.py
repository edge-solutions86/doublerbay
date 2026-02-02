from datetime import date

import pytest
from click.testing import CliRunner

import sample
from doublerbay.cli.main import cli
from doublerbay.cli.ordering import category_named, conditions_from, read_question
from doublerbay.disposition.engineering import OrderId
from doublerbay.disposition.state import DispositionRecord, DispositionState
from doublerbay.ids import FindingId
from doublerbay.repair.scheme import RepairCategory
from doublerbay.store.base import build_engine, build_schema
from doublerbay.store.unit import UnitOfWork

DAY = "2025-03-14"
SENT = "2025-03-20"
ANSWERED = "2025-06-01"
QUESTION = "dent in the chem mill land at FR15, past the allowable, what may we do"


def invoke(engine, *args):
    return CliRunner().invoke(cli, list(args), obj={"engine": engine})


def stocked():
    engine = build_engine()
    build_schema(engine)
    with UnitOfWork(engine) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(sample.flying_log())
    return engine


def assessed(engine, sequence=1):
    reference = FindingId(2025, sequence)
    with UnitOfWork(engine) as unit:
        unit.findings.raise_finding(sample.card(sequence))
        unit.dispositions.save(
            DispositionRecord.raised(reference).moved_to(
                DispositionState.ASSESSED, date(2025, 3, 14), "a.okonkwo"
            )
        )
    return str(reference)


def question_file(tmp_path, text=QUESTION, name="question.txt"):
    path = tmp_path / name
    path.write_text(text)
    return str(path)


def raise_one(engine, tmp_path, reference, on=DAY):
    return invoke(
        engine,
        "order",
        "raise",
        reference,
        "--question",
        question_file(tmp_path),
        "--on",
        on,
        "--by",
        "a.okonkwo",
    )


def test_a_category_is_read_off_its_letter():
    assert category_named("b") is RepairCategory.B
    assert category_named(" A ") is RepairCategory.A


def test_a_category_that_is_not_one_names_the_ones_that_are():
    with pytest.raises(Exception, match="A, B, C"):
        category_named("D")


def test_an_empty_question_file_is_refused(tmp_path):
    path = tmp_path / "question.txt"
    path.write_text("   \n")
    with pytest.raises(Exception, match="no question in it"):
        read_question(path)


def test_no_conditions_file_means_no_conditions():
    assert conditions_from(None) == ()


def test_conditions_come_off_the_file_one_a_line(tmp_path):
    path = tmp_path / "conditions.txt"
    path.write_text("inspect at every C check\n\ndo not extend past the heavy\n")
    assert conditions_from(path) == (
        "inspect at every C check",
        "do not extend past the heavy",
    )


def test_an_order_can_be_raised(tmp_path):
    engine = stocked()
    answer = raise_one(engine, tmp_path, assessed(engine))
    assert answer.exit_code == 0, answer.output
    assert "EO-2025-0001 raised" in answer.output


def test_raising_holds_the_card(tmp_path):
    engine = stocked()
    raise_one(engine, tmp_path, assessed(engine))
    with UnitOfWork(engine) as unit:
        assert unit.dispositions.get(FindingId(2025, 1)).state is DispositionState.HELD


def test_the_question_off_the_file_is_what_gets_stored(tmp_path):
    engine = stocked()
    raise_one(engine, tmp_path, assessed(engine))
    with UnitOfWork(engine) as unit:
        assert unit.orders.get(OrderId(2025, 1)).question == QUESTION


def test_a_question_file_that_is_not_there_is_refused(tmp_path):
    engine = stocked()
    answer = invoke(
        engine,
        "order",
        "raise",
        assessed(engine),
        "--question",
        str(tmp_path / "missing.txt"),
        "--on",
        DAY,
        "--by",
        "a.okonkwo",
    )
    assert answer.exit_code != 0


def test_an_order_can_be_marked_sent(tmp_path):
    engine = stocked()
    raise_one(engine, tmp_path, assessed(engine))
    answer = invoke(engine, "order", "sent", "EO-2025-0001", "--on", SENT)
    assert "EO-2025-0001 sent" in answer.output


def test_an_order_can_be_acknowledged(tmp_path):
    engine = stocked()
    raise_one(engine, tmp_path, assessed(engine))
    invoke(engine, "order", "sent", "EO-2025-0001", "--on", SENT)
    assert "acknowledged" in invoke(engine, "order", "acknowledged", "EO-2025-0001").output


def test_an_approval_is_recorded(tmp_path):
    engine = stocked()
    raise_one(engine, tmp_path, assessed(engine))
    invoke(engine, "order", "sent", "EO-2025-0001", "--on", SENT)
    answer = invoke(
        engine,
        "order",
        "approved",
        "EO-2025-0001",
        "--sheet",
        "RAS-2025-4417",
        "--category",
        "B",
        "--on",
        ANSWERED,
        "--by",
        "a.okonkwo",
        "--valid-until",
        "2027-01-01",
    )
    assert answer.exit_code == 0, answer.output
    assert "approved on RAS-2025-4417" in answer.output


def test_an_approval_releases_the_card(tmp_path):
    engine = stocked()
    raise_one(engine, tmp_path, assessed(engine))
    invoke(engine, "order", "sent", "EO-2025-0001", "--on", SENT)
    invoke(
        engine,
        "order",
        "approved",
        "EO-2025-0001",
        "--sheet",
        "RAS-2025-4417",
        "--category",
        "B",
        "--on",
        ANSWERED,
        "--by",
        "a.okonkwo",
    )
    with UnitOfWork(engine) as unit:
        assert unit.dispositions.get(FindingId(2025, 1)).state is DispositionState.ASSESSED


def test_the_conditions_file_reaches_the_stored_approval(tmp_path):
    engine = stocked()
    raise_one(engine, tmp_path, assessed(engine))
    invoke(engine, "order", "sent", "EO-2025-0001", "--on", SENT)
    conditions = tmp_path / "conditions.txt"
    conditions.write_text("inspect at every C check\n")
    invoke(
        engine,
        "order",
        "approved",
        "EO-2025-0001",
        "--sheet",
        "RAS-2025-4417",
        "--category",
        "B",
        "--on",
        ANSWERED,
        "--by",
        "a.okonkwo",
        "--conditions",
        str(conditions),
    )
    with UnitOfWork(engine) as unit:
        held = unit.orders.get(OrderId(2025, 1)).approval
    assert held.conditions == ("inspect at every C check",)


def test_a_rejection_is_recorded(tmp_path):
    engine = stocked()
    raise_one(engine, tmp_path, assessed(engine))
    invoke(engine, "order", "sent", "EO-2025-0001", "--on", SENT)
    answer = invoke(
        engine, "order", "rejected", "EO-2025-0001", "--on", ANSWERED, "--by", "a.okonkwo"
    )
    assert "rejected" in answer.output


def test_a_question_can_be_withdrawn(tmp_path):
    engine = stocked()
    raise_one(engine, tmp_path, assessed(engine))
    answer = invoke(
        engine, "order", "withdraw", "EO-2025-0001", "--on", ANSWERED, "--by", "a.okonkwo"
    )
    assert "withdrawn" in answer.output


def test_nothing_out_says_so():
    engine = stocked()
    assert invoke(engine, "order", "list").output.strip() == "nothing out to the manufacturer"


def test_the_open_list_carries_what_is_out(tmp_path):
    engine = stocked()
    raise_one(engine, tmp_path, assessed(engine))
    listed = invoke(engine, "order", "list").output
    assert "EO-2025-0001" in listed
    assert "DR-2025-000001" in listed


def test_nothing_needs_chasing_the_day_it_goes_out(tmp_path):
    engine = stocked()
    raise_one(engine, tmp_path, assessed(engine))
    invoke(engine, "order", "sent", "EO-2025-0001", "--on", SENT)
    assert "nothing waiting" in invoke(engine, "order", "chase", "--on", SENT).output


def test_an_order_out_a_long_time_needs_chasing(tmp_path):
    engine = stocked()
    raise_one(engine, tmp_path, assessed(engine))
    invoke(engine, "order", "sent", "EO-2025-0001", "--on", SENT)
    printed = invoke(engine, "order", "chase", "--on", "2026-01-01").output
    assert "EO-2025-0001" in printed
    assert "days waiting" in printed


def test_one_order_can_be_shown(tmp_path):
    engine = stocked()
    raise_one(engine, tmp_path, assessed(engine))
    printed = invoke(engine, "order", "show", "EO-2025-0001").output
    assert QUESTION in printed
    assert "not yet" in printed


def test_a_shown_approval_lists_its_conditions(tmp_path):
    engine = stocked()
    raise_one(engine, tmp_path, assessed(engine))
    invoke(engine, "order", "sent", "EO-2025-0001", "--on", SENT)
    conditions = tmp_path / "conditions.txt"
    conditions.write_text("inspect at every C check\n")
    invoke(
        engine,
        "order",
        "approved",
        "EO-2025-0001",
        "--sheet",
        "RAS-2025-4417",
        "--category",
        "B",
        "--on",
        ANSWERED,
        "--by",
        "a.okonkwo",
        "--conditions",
        str(conditions),
    )
    printed = invoke(engine, "order", "show", "EO-2025-0001").output
    assert "  condition" in printed
    assert "no end" in printed


def test_an_order_that_is_not_on_file_cannot_be_shown():
    engine = stocked()
    assert invoke(engine, "order", "show", "EO-2025-0099").exit_code != 0
