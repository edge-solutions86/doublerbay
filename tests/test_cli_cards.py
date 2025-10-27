import json

from click.testing import CliRunner

from doublerbay.cli.cards import read_document, template_for
from doublerbay.cli.main import cli
from doublerbay.cli.output import say_nothing_found
from doublerbay.damage.kind import DamageKind
from doublerbay.store.base import build_engine, build_schema


def invoke(engine, *args):
    return CliRunner().invoke(cli, list(args), obj={"engine": engine})


def stocked():
    engine = build_engine()
    build_schema(engine)
    invoke(
        engine,
        "fleet",
        "enrol",
        "5127",
        "N412VD",
        "--line-number",
        "1140",
        "--delivered",
        "2016-04-12",
    )
    return engine


def dent_document(serial="5127", depth="0.030"):
    card = template_for(DamageKind.DENT, "fuselage")
    card["serial"] = serial
    card["found_on"] = "2025-03-14"
    card["origin"] = "impact"
    card["source"] = "walkaround"
    card["at_usage"] = {"cycles": 13800, "hours": "24200.0"}
    card["fuselage"] = {
        "structure": "fuselage",
        "frame": 15,
        "aft_of_frame": "10.000",
        "stringer": 6,
        "below_stringer": "0.000",
        "side": "L",
    }
    card["measurements"] = {
        "depth": depth,
        "along": "1.500",
        "around": "1.200",
        "sharp_bottom": False,
        "cracked": False,
    }
    return card


def written(tmp_path, card, name="card.json"):
    path = tmp_path / name
    path.write_text(json.dumps(card))
    return str(path)


def raised(engine, tmp_path, **overrides):
    answer = invoke(engine, "cards", "raise", written(tmp_path, dent_document(**overrides)))
    assert answer.exit_code == 0, answer.output
    return answer.output.split()[-1]


def test_a_template_carries_only_what_the_kind_measures():
    dent = template_for(DamageKind.DENT, "fuselage")
    assert set(dent["measurements"]) == {"depth", "along", "around", "sharp_bottom", "cracked"}
    crack = template_for(DamageKind.CRACK, "wing")
    assert "tips_found" in crack["measurements"]
    assert "depth" not in crack["measurements"]


def test_a_template_puts_the_location_on_the_structure_asked_for():
    assert "wing" in template_for(DamageKind.CRACK, "wing")
    assert "fuselage" not in template_for(DamageKind.CRACK, "wing")


def test_a_kind_with_no_template_says_so():
    answer = CliRunner().invoke(cli, ["cards", "template", "disbond"], obj={})
    assert answer.exit_code != 0
    assert "no card template" in str(answer.exception)


def test_a_structure_that_is_not_one_is_refused():
    answer = CliRunner().invoke(cli, ["cards", "template", "dent", "--structure", "tail"], obj={})
    assert answer.exit_code != 0


def test_the_printed_template_is_json_that_parses():
    answer = CliRunner().invoke(cli, ["cards", "template", "dent"], obj={})
    assert json.loads(answer.output)["kind"] == "dent"


def test_a_document_that_is_not_json_is_refused_by_name(tmp_path):
    path = tmp_path / "card.json"
    path.write_text("{not json")
    try:
        read_document(path)
    except Exception as refusal:
        assert "readable json" in str(refusal)
    else:
        raise AssertionError("that should not have parsed")


def test_a_document_that_is_a_list_is_refused(tmp_path):
    path = tmp_path / "card.json"
    path.write_text("[]")
    try:
        read_document(path)
    except Exception as refusal:
        assert "it is a list" in str(refusal)
    else:
        raise AssertionError("that should not have parsed")


def test_a_card_is_raised_and_gets_a_reference(tmp_path):
    engine = stocked()
    reference = raised(engine, tmp_path)
    assert reference.startswith("DR-2025-")


def test_two_cards_get_different_references(tmp_path):
    engine = stocked()
    first = raised(engine, tmp_path)
    second = invoke(
        engine, "cards", "raise", written(tmp_path, dent_document(), "second.json")
    ).output.split()[-1]
    assert first != second


def test_a_raised_card_shows_its_measurements_as_written(tmp_path):
    engine = stocked()
    reference = raised(engine, tmp_path)
    shown = invoke(engine, "cards", "show", reference).output
    assert "0.030" in shown
    assert "dent" in shown
    assert "FR15" in shown


def test_a_card_that_is_not_there_is_refused():
    answer = invoke(stocked(), "cards", "show", "DR-2025-0099")
    assert answer.exit_code != 0


def test_a_reference_that_is_not_a_reference_is_refused():
    answer = invoke(stocked(), "cards", "show", "nonsense")
    assert answer.exit_code != 0


def test_the_cards_on_a_tail_are_listed(tmp_path):
    engine = stocked()
    reference = raised(engine, tmp_path)
    listed = invoke(engine, "cards", "list", "--tail", "5127").output
    assert reference in listed
    assert "dent" in listed


def test_a_tail_with_no_cards_lists_only_the_heading():
    listed = invoke(stocked(), "cards", "list", "--tail", "5127").output
    assert listed.strip() == "reference  kind  found on  where"


def test_a_history_starts_at_one_revision(tmp_path):
    engine = stocked()
    reference = raised(engine, tmp_path)
    history = invoke(engine, "cards", "history", reference).output
    assert len(history.strip().splitlines()) == 2


def test_columns_pad_a_short_row_out(tmp_path):
    from doublerbay.cli.output import columns

    assert columns([("a", "b"), ("c",)]).splitlines()[1] == "c"


def test_an_empty_report_has_a_sentence_for_it():
    assert say_nothing_found("open on 5127") == "nothing open on 5127"
