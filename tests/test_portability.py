"""The assumptions that hold on the machine this was written on and nowhere else.

Every check in this file exists because the desk runs on Windows and the
service runs on Linux, and the defects that follow from that are all silent.
Nothing crashes. A card written on the desk with a degree sign in the note
reads back as something else on the server, and the only sign of it is a
measurement nobody can match to the paper form six months later.

The encoding one is not hypothetical. Four file reads in this package were
taking the platform default, which is cp1252 here and utf-8 there.
"""

import json
from pathlib import Path

import pytest
from click.testing import CliRunner

from conftest import assessed_card, enrolled, new_engine
from doublerbay.cli.cards import read_document, template_for
from doublerbay.cli.main import cli
from doublerbay.cli.ordering import conditions_from, read_question
from doublerbay.damage.kind import DamageKind

ROOT = Path(__file__).resolve().parents[1]
SOURCE = sorted((ROOT / "src").rglob("*.py"))

# Characters a structures desk actually types: a degree sign in a bearing, a
# micro sign in a coating thickness, and a name with an accent in it.
AWKWARD = "60\u00b0 bearing, 25\u00b5m primer, raised by j.mu\u00f1oz"


def test_there_is_source_to_check():
    assert len(SOURCE) > 100


def test_every_file_read_in_the_package_names_its_encoding():
    """A read with no encoding takes the platform default, which differs."""
    offenders = []
    for path in SOURCE:
        for at, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
            touches_a_file = ".read_text(" in line or ".write_text(" in line
            if touches_a_file and "encoding=" not in line:
                offenders.append(f"{path.relative_to(ROOT)}:{at}")
    assert not offenders, f"reads with no encoding: {offenders}"


def test_no_source_file_reaches_for_a_windows_only_module():
    banned = ("winreg", "ctypes.windll", "os.startfile", "msvcrt")
    offenders = [
        str(path.relative_to(ROOT))
        for path in SOURCE
        if any(one in path.read_text(encoding="utf-8") for one in banned)
    ]
    assert not offenders


def test_no_source_file_carries_an_absolute_windows_path():
    offenders = [
        str(path.relative_to(ROOT)) for path in SOURCE if "C:\\" in path.read_text(encoding="utf-8")
    ]
    assert not offenders


def test_every_source_file_is_readable_as_utf_eight():
    for path in SOURCE:
        path.read_text(encoding="utf-8")


def test_a_card_document_with_awkward_characters_reads_back(tmp_path):
    card = template_for(DamageKind.DENT, "fuselage")
    card.update(
        serial="5127",
        found_on="2025-03-14",
        origin="impact",
        source="walkaround",
        at_usage={"cycles": 13800, "hours": "24200.0"},
        fuselage={
            "structure": "fuselage",
            "frame": 15,
            "aft_of_frame": "10.000",
            "stringer": 6,
            "below_stringer": "0.000",
            "side": "L",
        },
        measurements={
            "depth": "0.020",
            "along": "3.000",
            "around": "2.000",
            "sharp_bottom": False,
            "cracked": False,
        },
        note=AWKWARD,
    )
    path = tmp_path / "card.json"
    path.write_text(json.dumps(card, ensure_ascii=False), encoding="utf-8")
    assert read_document(path).note == AWKWARD


def test_a_card_raised_from_such_a_document_keeps_its_note(tmp_path):
    engine = new_engine()
    enrolled(engine)
    card = template_for(DamageKind.DENT, "fuselage")
    card.update(
        serial="5127",
        found_on="2025-03-14",
        origin="impact",
        source="walkaround",
        at_usage={"cycles": 13800, "hours": "24200.0"},
        fuselage={
            "structure": "fuselage",
            "frame": 15,
            "aft_of_frame": "10.000",
            "stringer": 6,
            "below_stringer": "0.000",
            "side": "L",
        },
        measurements={
            "depth": "0.020",
            "along": "3.000",
            "around": "2.000",
            "sharp_bottom": False,
            "cracked": False,
        },
        note=AWKWARD,
    )
    path = tmp_path / "card.json"
    path.write_text(json.dumps(card, ensure_ascii=False), encoding="utf-8")
    answer = CliRunner().invoke(cli, ["cards", "raise", str(path)], obj={"engine": engine})
    assert answer.exit_code == 0, answer.output


def test_a_question_with_awkward_characters_reads_back(tmp_path):
    path = tmp_path / "question.txt"
    path.write_text(AWKWARD, encoding="utf-8")
    assert read_question(path) == AWKWARD


def test_conditions_with_awkward_characters_read_back(tmp_path):
    path = tmp_path / "conditions.txt"
    path.write_text(f"{AWKWARD}\ninspect at every C check\n", encoding="utf-8")
    assert conditions_from(path)[0] == AWKWARD


def test_an_order_carries_such_a_question_through(tmp_path):
    engine = new_engine()
    enrolled(engine)
    reference = assessed_card(engine)
    path = tmp_path / "question.txt"
    path.write_text(f"why is there a {AWKWARD} here and what may we do", encoding="utf-8")
    answer = CliRunner().invoke(
        cli,
        [
            "order",
            "raise",
            str(reference),
            "--question",
            str(path),
            "--on",
            "2025-03-14",
            "--by",
            "a.okonkwo",
        ],
        obj={"engine": engine},
    )
    assert answer.exit_code == 0, answer.output


def test_the_csv_export_writes_the_line_ending_a_spreadsheet_expects():
    from doublerbay.report.export import LINE_END, as_csv

    assert LINE_END == "\r\n"
    assert as_csv(("a",), [("x",)]).endswith("\r\n")


def test_the_repository_pins_its_line_endings():
    """Without this a checkout on Linux and one here disagree byte for byte."""
    assert "eol=lf" in (ROOT / ".gitattributes").read_text(encoding="utf-8")


@pytest.mark.parametrize("name", ["pyproject.toml", "alembic.ini", ".gitattributes"])
def test_the_files_ci_depends_on_are_all_there(name):
    assert (ROOT / name).exists()
