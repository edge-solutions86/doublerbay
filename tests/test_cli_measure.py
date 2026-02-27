import json
from datetime import date

from click.testing import CliRunner

import sample
from doublerbay.cli.main import cli
from doublerbay.disposition.state import DispositionRecord, DispositionState
from doublerbay.ids import FindingId
from doublerbay.store.base import build_engine, build_schema
from doublerbay.store.unit import UnitOfWork

DAY = date(2025, 3, 14)
AGAIN = "2025-09-01"
AGAINST = FindingId(2025, 1)


def invoke(engine, *args):
    return CliRunner().invoke(cli, list(args), obj={"engine": engine})


def stocked():
    engine = build_engine()
    build_schema(engine)
    with UnitOfWork(engine) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(sample.flying_log())
    return engine


def raised(engine, sequence=1):
    reference = FindingId(2025, sequence)
    with UnitOfWork(engine) as unit:
        unit.findings.raise_finding(sample.card(sequence, at=sample.cabin_point(stringer=10)))
        unit.dispositions.save(
            DispositionRecord.raised(reference).moved_to(
                DispositionState.ASSESSED, DAY, "a.okonkwo"
            )
        )
    return str(reference)


def deferred(engine, reference):
    invoke(
        engine,
        "defer",
        "grant",
        reference,
        "--by",
        "j.mercer",
        "--on",
        "2025-03-14",
        "--limit",
        "500 FC",
    )


def numbers(tmp_path, depth="0.025", name="again.json"):
    path = tmp_path / name
    path.write_text(
        json.dumps(
            {
                "measurements": {
                    "depth": depth,
                    "along": "3.000",
                    "around": "2.000",
                    "sharp_bottom": False,
                    "cracked": False,
                }
            }
        )
    )
    return str(path)


def measure(engine, tmp_path, reference, depth="0.025", on=AGAIN):
    return invoke(engine, "cards", "measure", reference, numbers(tmp_path, depth), "--on", on)


def test_a_card_can_be_measured_again(tmp_path):
    engine = stocked()
    answer = measure(engine, tmp_path, raised(engine))
    assert answer.exit_code == 0, answer.output
    assert "revision 2" in answer.output


def test_the_new_numbers_land_on_the_card(tmp_path):
    engine = stocked()
    measure(engine, tmp_path, raised(engine))
    with UnitOfWork(engine) as unit:
        assert unit.findings.latest(AGAINST).geometry.depth == sample.inch("0.025")


def test_the_first_reading_is_still_there(tmp_path):
    engine = stocked()
    measure(engine, tmp_path, raised(engine))
    with UnitOfWork(engine) as unit:
        assert len(unit.findings.history(AGAINST)) == 2


def test_a_document_with_no_measurements_is_refused(tmp_path):
    engine = stocked()
    reference = raised(engine)
    path = tmp_path / "empty.json"
    path.write_text("{}")
    answer = invoke(engine, "cards", "measure", reference, str(path), "--on", AGAIN)
    assert answer.exit_code != 0
    assert "no measurements block" in str(answer.exception)


def test_a_document_that_is_a_list_is_refused(tmp_path):
    engine = stocked()
    reference = raised(engine)
    path = tmp_path / "list.json"
    path.write_text("[]")
    answer = invoke(engine, "cards", "measure", reference, str(path), "--on", AGAIN)
    assert answer.exit_code != 0


def test_a_card_that_is_not_on_file_cannot_be_measured(tmp_path):
    engine = stocked()
    answer = measure(engine, tmp_path, "DR-2025-000099")
    assert answer.exit_code != 0


def test_a_day_before_it_was_found_is_refused(tmp_path):
    engine = stocked()
    answer = measure(engine, tmp_path, raised(engine), on="2024-01-01")
    assert answer.exit_code != 0
    assert "before it was found" in str(answer.exception)


def test_a_measurement_that_is_not_one_is_refused(tmp_path):
    engine = stocked()
    answer = measure(engine, tmp_path, raised(engine), depth="deep")
    assert answer.exit_code != 0


def test_a_damage_that_has_not_moved_says_only_its_answer(tmp_path):
    engine = stocked()
    printed = measure(engine, tmp_path, raised(engine), depth="0.020").output
    assert " to " not in printed


def test_a_damage_that_grew_names_both_answers(tmp_path):
    engine = stocked()
    printed = measure(engine, tmp_path, raised(engine), depth="0.400").output
    assert " to " in printed


def test_a_deferred_card_that_grew_prints_the_warning(tmp_path):
    engine = stocked()
    reference = raised(engine)
    deferred(engine, reference)
    printed = measure(engine, tmp_path, reference, depth="0.400").output
    assert "tell an engineer" in printed


def test_a_deferred_card_that_did_not_grow_prints_no_warning(tmp_path):
    engine = stocked()
    reference = raised(engine)
    deferred(engine, reference)
    printed = measure(engine, tmp_path, reference, depth="0.020").output
    assert "tell an engineer" not in printed


def test_a_card_that_was_never_deferred_prints_no_warning(tmp_path):
    engine = stocked()
    printed = measure(engine, tmp_path, raised(engine), depth="0.400").output
    assert "tell an engineer" not in printed


def test_the_history_command_shows_both_revisions(tmp_path):
    engine = stocked()
    reference = raised(engine)
    measure(engine, tmp_path, reference)
    printed = invoke(engine, "cards", "history", reference).output
    assert len(printed.strip().splitlines()) == 3
