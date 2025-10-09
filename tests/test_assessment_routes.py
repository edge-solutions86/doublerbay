from datetime import date

from fastapi.testclient import TestClient

import sample
from doublerbay.api.app import Deps, build_app
from doublerbay.fleet.catalogue import TypeCatalogue
from doublerbay.manual.book import v320_book
from doublerbay.store.base import build_engine, build_schema
from doublerbay.store.unit import UnitOfWork

DAY = date(2025, 3, 14)


def client():
    engine = build_engine()
    build_schema(engine)
    with UnitOfWork(engine) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(sample.flying_log())
    built = build_app(Deps(engine=engine, book=v320_book(), catalogue=TypeCatalogue(), today=DAY))
    return TestClient(built)


def card_body(depth="0.010", along="6.000", around="4.000", stringer=10, aft="10.000"):
    return {
        "serial": "5127",
        "kind": "dent",
        "origin": "impact",
        "source": "scheduled check",
        "found_on": "2025-03-14",
        "at_usage": {"cycles": 13900, "hours": "24400.0"},
        "fuselage": {
            "structure": "fuselage",
            "frame": 15,
            "aft_of_frame": aft,
            "stringer": stringer,
            "below_stringer": "4.000",
            "side": "L",
        },
        "measurements": {
            "depth": depth,
            "along": along,
            "around": around,
            "sharp_bottom": False,
            "cracked": False,
        },
    }


def raised(api, **overrides):
    answer = api.post("/findings", json=card_body(**overrides))
    assert answer.status_code == 201, answer.text
    return answer.json()["reference"]


def test_a_small_dent_assesses_as_allowable():
    api = client()
    reference = raised(api)
    body = api.get(f"/findings/{reference}/assessment").json()
    assert body["verdict"]["disposition"] == "allowable as it stands"
    assert body["part"].startswith("5310-")


def test_the_assessment_cites_the_page_and_the_revision():
    api = client()
    reference = raised(api)
    verdict = api.get(f"/findings/{reference}/assessment").json()["verdict"]
    assert verdict["page"] == "53-10-01"
    assert verdict["revision"] == 12


def test_a_deep_dent_assesses_as_a_repair():
    api = client()
    reference = raised(api, depth="0.090")
    verdict = api.get(f"/findings/{reference}/assessment").json()["verdict"]
    assert verdict["disposition"] == "repair from the manual"
    assert any(not one["passed"] for one in verdict["reasons"])


def test_the_whole_trail_comes_back_with_the_answer():
    api = client()
    reference = raised(api)
    verdict = api.get(f"/findings/{reference}/assessment").json()["verdict"]
    rules = {one["rule"] for one in verdict["reasons"]}
    assert "allowable" in rules
    assert "depth" in rules


def test_the_same_card_assesses_differently_before_the_campaign_revision():
    api = client()
    reference = raised(api, depth="0.070")
    before = api.get(f"/findings/{reference}/assessment", params={"on": "2024-06-01"}).json()
    after = api.get(f"/findings/{reference}/assessment", params={"on": "2025-03-14"}).json()
    assert before["verdict"]["disposition"] == "allowable as it stands"
    assert after["verdict"]["disposition"] == "repair from the manual"


def test_the_thickness_and_the_margin_come_back_as_text():
    api = client()
    reference = raised(api)
    body = api.get(f"/findings/{reference}/assessment").json()
    assert body["thickness"].startswith("0.0")
    assert "." in body["edge_margin"]


def test_a_card_that_is_not_there_has_no_assessment():
    api = client()
    assert api.get("/findings/DR-2025-009999/assessment").status_code == 404


def test_a_tail_with_nothing_open_assesses_as_allowable():
    api = client()
    body = api.get("/aircraft/5127/assessment").json()
    assert body["disposition"] == "allowable as it stands"
    assert body["panels"] == []


def test_a_tail_assessment_groups_the_cards_by_panel():
    api = client()
    raised(api, aft="2.000")
    raised(api, aft="16.000")
    body = api.get("/aircraft/5127/assessment").json()
    assert len(body["panels"]) == 1
    assert body["panels"][0]["card_count"] == 2


def test_the_tail_answer_is_the_worst_of_every_panel():
    api = client()
    raised(api, aft="2.000")
    raised(api, aft="16.000", depth="0.090")
    body = api.get("/aircraft/5127/assessment").json()
    assert body["disposition"] == "repair from the manual"


def test_the_damage_count_is_taken_after_clustering():
    api = client()
    raised(api, aft="8.000")
    raised(api, aft="8.500")
    panel = api.get("/aircraft/5127/assessment").json()["panels"][0]
    assert panel["card_count"] == 2
    assert panel["damage_count"] == 1


def test_a_tail_assessment_can_be_asked_for_a_day():
    api = client()
    raised(api, depth="0.070")
    early = api.get("/aircraft/5127/assessment", params={"on": "2024-06-01"}).json()
    assert early["on"] == "2024-06-01"
    assert early["disposition"] == "allowable as it stands"


def test_a_tail_that_is_not_on_the_register_has_no_assessment():
    api = client()
    assert api.get("/aircraft/9999/assessment").status_code == 404
