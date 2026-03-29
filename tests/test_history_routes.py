from datetime import date

from click.testing import CliRunner
from fastapi.testclient import TestClient

import sample
from doublerbay.api.app import Deps, build_app
from doublerbay.cli.main import cli
from doublerbay.disposition.state import DispositionRecord, DispositionState
from doublerbay.fleet.catalogue import TypeCatalogue
from doublerbay.ids import FindingId
from doublerbay.manual.book import v320_book
from doublerbay.store.base import build_engine, build_schema
from doublerbay.store.unit import UnitOfWork

DAY = date(2025, 3, 14)
CHIEF = {"x-api-key": "chief-key-0001"}
QUESTION = "dent in the chem mill land at FR15, past the allowable, what may we do"


def built():
    engine = build_engine()
    build_schema(engine)
    app = build_app(Deps(engine=engine, book=v320_book(), catalogue=TypeCatalogue(), today=DAY))
    with UnitOfWork(engine) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(sample.flying_log())
    return engine, TestClient(app, headers=CHIEF)


def invoke(engine, *args):
    return CliRunner().invoke(cli, list(args), obj={"engine": engine})


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


def defer(api, against, limit="500 FC"):
    return api.post(
        "/deferrals",
        json={"against": against, "granted": "2025-03-14", "by": "j.mercer", "limit": limit},
    )


def test_the_registers_are_listed_in_reading_order():
    engine, api = built()
    body = api.get("/history/registers").json()
    assert body["registers"][0] == "card"
    assert body["registers"][-1] == "repair"


def test_a_freshly_raised_card_has_two_lines():
    engine, api = built()
    body = api.get(f"/history/{raised(engine)}").json()
    assert len(body["entries"]) == 2
    assert body["entries"][0]["came_from"] == "card"


def test_the_story_names_its_tail():
    engine, api = built()
    assert api.get(f"/history/{raised(engine)}").json()["serial"] == "5127"


def test_a_deferral_lands_on_the_story():
    engine, api = built()
    reference = raised(engine)
    defer(api, reference)
    body = api.get(f"/history/{reference}").json()
    assert any(one["came_from"] == "deferral" for one in body["entries"])


def test_an_order_lands_on_the_story():
    engine, api = built()
    reference = raised(engine)
    api.post(
        "/orders",
        json={
            "against": reference,
            "question": QUESTION,
            "raised": "2025-03-14",
            "by": "a.okonkwo",
        },
    )
    body = api.get(f"/history/{reference}").json()
    assert any(one["came_from"] == "engineering" for one in body["entries"])


def test_a_repair_lands_on_the_story():
    engine, api = built()
    reference = raised(engine)
    api.post(
        "/repairs",
        json={
            "against": reference,
            "sequence": 1,
            "along": "2.000",
            "around": "2.000",
            "centre_along": "468.000",
            "centre_around": "91.000",
            "embodied": "2025-04-02",
            "by": "s.brandt",
        },
    )
    body = api.get(f"/history/{reference}").json()
    assert any(one["came_from"] == "repair" for one in body["entries"])


def test_the_story_spans_from_the_first_day_to_the_last():
    engine, api = built()
    reference = raised(engine)
    api.post(
        "/repairs",
        json={
            "against": reference,
            "sequence": 1,
            "along": "2.000",
            "around": "2.000",
            "centre_along": "468.000",
            "centre_around": "91.000",
            "embodied": "2025-04-02",
            "by": "s.brandt",
        },
    )
    body = api.get(f"/history/{reference}").json()
    assert body["opened"] == "2025-03-14"
    assert body["latest"] == "2025-04-02"
    assert body["days_open"] == 19


def test_a_card_that_is_not_on_file_is_not_found():
    engine, api = built()
    assert api.get("/history/DR-2025-000099").status_code == 404


def test_a_reference_that_is_not_one_is_refused():
    engine, api = built()
    assert api.get("/history/nonsense").status_code == 422


def test_a_reader_may_pull_a_story():
    engine, api = built()
    answer = api.get(f"/history/{raised(engine)}", headers={"x-api-key": "reader-key-0001"})
    assert answer.status_code == 200


def test_the_command_prints_the_story():
    engine, api = built()
    reference = raised(engine)
    printed = invoke(engine, "cards", "story", reference).output
    assert "entries over" in printed
    assert "card" in printed


def test_the_command_prints_one_line_per_entry():
    engine, api = built()
    reference = raised(engine)
    printed = invoke(engine, "cards", "story", reference).output.strip().splitlines()
    assert len(printed) == 4


def test_the_command_refuses_a_card_that_is_not_there():
    engine, api = built()
    assert invoke(engine, "cards", "story", "DR-2025-000099").exit_code != 0


def test_the_command_shows_the_deferral_line():
    engine, api = built()
    reference = raised(engine)
    defer(api, reference)
    assert "deferral" in invoke(engine, "cards", "story", reference).output
