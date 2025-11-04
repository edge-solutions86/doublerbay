import json

from click.testing import CliRunner

from doublerbay.cli.assessing import verdict_lines
from doublerbay.cli.cards import template_for
from doublerbay.cli.main import cli
from doublerbay.damage.kind import DamageKind
from doublerbay.store.base import build_engine, build_schema

DAY = "2025-03-14"


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
    invoke(engine, "fleet", "post", "5127", DAY, "--cycles", "13800", "--hours", "24200")
    return engine


def dent(depth="0.030", frame=15, stringer=6):
    card = template_for(DamageKind.DENT, "fuselage")
    card.update(
        serial="5127",
        found_on=DAY,
        origin="impact",
        source="walkaround",
        at_usage={"cycles": 13800, "hours": "24200.0"},
        fuselage={
            "structure": "fuselage",
            "frame": frame,
            "aft_of_frame": "10.000",
            "stringer": stringer,
            "below_stringer": "0.000",
            "side": "L",
        },
        measurements={
            "depth": depth,
            "along": "1.500",
            "around": "1.200",
            "sharp_bottom": False,
            "cracked": False,
        },
    )
    return card


def raise_card(engine, tmp_path, card, name="card.json"):
    path = tmp_path / name
    path.write_text(json.dumps(card))
    answer = invoke(engine, "cards", "raise", str(path))
    assert answer.exit_code == 0, answer.output
    return answer.output.split()[-1]


def test_a_shallow_dent_comes_back_allowable(tmp_path):
    engine = stocked()
    reference = raise_card(engine, tmp_path, dent(depth="0.010"))
    answer = invoke(engine, "assess", reference)
    assert answer.exit_code == 0, answer.output
    assert "allowable" in answer.output


def test_the_reasons_are_printed_under_the_disposition(tmp_path):
    engine = stocked()
    reference = raise_card(engine, tmp_path, dent(depth="0.010"))
    printed = invoke(engine, "assess", reference).output
    assert "  ok " in printed
    assert "disposition" in printed


def test_a_deep_dent_fails_a_named_rule(tmp_path):
    engine = stocked()
    reference = raise_card(engine, tmp_path, dent(depth="0.400"))
    printed = invoke(engine, "assess", reference).output
    assert "  no " in printed
    assert "allowable as it stands" not in printed


def test_the_page_it_came_off_is_printed(tmp_path):
    engine = stocked()
    reference = raise_card(engine, tmp_path, dent(depth="0.010"))
    assert "rev " in invoke(engine, "assess", reference).output


def test_a_day_can_be_asked_for(tmp_path):
    engine = stocked()
    reference = raise_card(engine, tmp_path, dent(depth="0.010"))
    assert invoke(engine, "assess", reference, "--on", "2025-06-01").exit_code == 0


def test_a_day_that_is_not_a_day_is_refused(tmp_path):
    engine = stocked()
    reference = raise_card(engine, tmp_path, dent(depth="0.010"))
    answer = invoke(engine, "assess", reference, "--on", "01-06-2025")
    assert answer.exit_code != 0
    assert "yyyy-mm-dd" in str(answer.exception)


def test_a_card_that_is_not_there_cannot_be_assessed():
    answer = invoke(stocked(), "assess", "DR-2025-0099")
    assert answer.exit_code != 0


def test_a_tail_with_nothing_open_says_so():
    answer = invoke(stocked(), "assess-tail", "5127", "--on", DAY)
    assert answer.output.strip() == "nothing open on 5127"


def test_a_tail_assessment_lists_its_panels(tmp_path):
    engine = stocked()
    raise_card(engine, tmp_path, dent(depth="0.010"))
    raise_card(engine, tmp_path, dent(depth="0.010", frame=40, stringer=12), "second.json")
    printed = invoke(engine, "assess-tail", "5127", "--on", DAY).output
    assert "whole tail" in printed
    assert len(printed.strip().splitlines()) >= 4


def test_the_whole_tail_line_carries_the_worst_answer(tmp_path):
    engine = stocked()
    raise_card(engine, tmp_path, dent(depth="0.010"))
    raise_card(engine, tmp_path, dent(depth="0.400", frame=40, stringer=12), "second.json")
    last = invoke(engine, "assess-tail", "5127", "--on", DAY).output.strip().splitlines()[-1]
    assert "allowable as it stands" not in last


def test_verdict_lines_start_with_the_card_and_end_with_reasons(tmp_path):
    engine = stocked()
    reference = raise_card(engine, tmp_path, dent(depth="0.010"))
    from doublerbay.assess.assessor import assess
    from doublerbay.cli.assessing import context_for
    from doublerbay.fleet.catalogue import TypeCatalogue
    from doublerbay.ids import FindingId, Serial
    from doublerbay.manual.book import v320_book
    from doublerbay.store.unit import UnitOfWork

    with UnitOfWork(engine) as unit:
        card = unit.findings.latest(FindingId.parse(reference))
        tail = context_for(unit, TypeCatalogue(), Serial("5127"), card.found_on)
        lines = verdict_lines(assess(v320_book(), tail, card))
    assert lines[0][0] == "card"
    assert lines[-1][0].startswith("  ")


def test_a_tail_that_is_not_on_the_register_cannot_be_assessed():
    answer = invoke(stocked(), "assess-tail", "9999", "--on", DAY)
    assert answer.exit_code != 0
