from datetime import date

import pytest
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
    return TestClient(built, headers={"x-api-key": "chief-key-0001"})


def card_body(**overrides):
    body = {
        "serial": "5127",
        "kind": "dent",
        "origin": "impact",
        "source": "scheduled check",
        "found_on": "2025-03-14",
        "at_usage": {"cycles": 13900, "hours": "24400.0"},
        "fuselage": {
            "structure": "fuselage",
            "frame": 15,
            "aft_of_frame": "10.000",
            "stringer": 10,
            "below_stringer": "0.000",
            "side": "L",
        },
        "measurements": {
            "depth": "0.020",
            "along": "3.000",
            "around": "2.000",
            "sharp_bottom": False,
            "cracked": False,
        },
    }
    body.update(overrides)
    return body


def raised(api, **overrides):
    answer = api.post("/findings", json=card_body(**overrides))
    assert answer.status_code == 201, answer.text
    return answer.json()["reference"]


def test_raising_a_card_allocates_its_number():
    api = client()
    first = raised(api)
    second = raised(api)
    assert first == "DR-2025-000001"
    assert second == "DR-2025-000002"


def test_a_raised_card_starts_out_raised():
    api = client()
    answer = api.post("/findings", json=card_body())
    assert answer.json()["state"] == "raised"


def test_a_raised_card_can_be_read_back():
    api = client()
    reference = raised(api)
    body = api.get(f"/findings/{reference}").json()
    assert body["reference"] == reference
    assert body["kind"] == "dent"
    assert body["measurements"]["depth"] == "0.020"
    assert body["location"]["structure"] == "fuselage"


def test_a_card_that_is_not_there_comes_back_as_not_found():
    api = client()
    answer = api.get("/findings/DR-2025-009999")
    assert answer.status_code == 404
    assert answer.json()["kind"] == "NotOnFile"


def test_a_reference_that_is_not_a_reference_comes_back_as_unprocessable():
    api = client()
    answer = api.get("/findings/not-a-number")
    assert answer.status_code == 422
    assert answer.json()["kind"] == "BadReading"


def test_a_card_with_no_location_is_refused():
    api = client()
    body = card_body()
    body.pop("fuselage")
    answer = api.post("/findings", json=body)
    assert answer.status_code == 422
    assert "says where it is" in answer.json()["detail"]


def test_a_card_with_a_measurement_that_is_not_a_number_is_refused():
    api = client()
    answer = api.post(
        "/findings",
        json=card_body(
            measurements={
                "depth": "deep",
                "along": "3.000",
                "around": "2.000",
                "sharp_bottom": False,
                "cracked": False,
            }
        ),
    )
    assert answer.status_code == 422


def test_a_card_whose_kind_and_cause_do_not_go_together_is_refused():
    api = client()
    answer = api.post("/findings", json=card_body(origin="lightning"))
    assert answer.status_code == 422
    assert "does not come from lightning" in answer.json()["detail"]


def test_nothing_is_written_when_a_card_is_refused():
    api = client()
    api.post("/findings", json=card_body(origin="lightning"))
    assert api.get("/aircraft/5127/findings").json() == []


def test_a_re_measurement_makes_a_new_revision():
    api = client()
    reference = raised(api)
    answer = api.post(
        f"/findings/{reference}/revisions",
        json={
            "found_on": "2025-06-01",
            "at_usage": {"cycles": 14200, "hours": "24900.0"},
            "measurements": {
                "depth": "0.040",
                "along": "3.000",
                "around": "2.000",
                "sharp_bottom": False,
                "cracked": False,
            },
        },
    )
    assert answer.status_code == 201
    assert answer.json()["revision"] == 2
    assert answer.json()["measurements"]["depth"] == "0.040"


def test_the_history_carries_every_revision():
    api = client()
    reference = raised(api)
    api.post(
        f"/findings/{reference}/revisions",
        json={
            "found_on": "2025-06-01",
            "at_usage": {"cycles": 14200, "hours": "24900.0"},
            "measurements": {
                "depth": "0.040",
                "along": "3.000",
                "around": "2.000",
                "sharp_bottom": False,
                "cracked": False,
            },
        },
    )
    history = api.get(f"/findings/{reference}/history").json()
    assert [one["revision"] for one in history] == [1, 2]


def test_a_re_measurement_before_the_finding_is_refused():
    api = client()
    reference = raised(api)
    answer = api.post(
        f"/findings/{reference}/revisions",
        json={
            "found_on": "2024-01-01",
            "at_usage": {"cycles": 13000, "hours": "24000.0"},
            "measurements": {
                "depth": "0.040",
                "along": "3.000",
                "around": "2.000",
                "sharp_bottom": False,
                "cracked": False,
            },
        },
    )
    assert answer.status_code == 422


def test_superseding_closes_a_card_and_names_what_replaced_it():
    api = client()
    first = raised(api)
    second = raised(api)
    answer = api.post(f"/findings/{first}/supersede", json={"by": second})
    assert answer.status_code == 200
    assert answer.json()["superseded_by"] == second


def test_a_superseded_card_drops_off_the_open_list():
    api = client()
    first = raised(api)
    second = raised(api)
    api.post(f"/findings/{first}/supersede", json={"by": second})
    open_cards = api.get("/aircraft/5127/findings", params={"open_only": True}).json()
    assert [one["reference"] for one in open_cards] == [second]


def test_superseding_by_a_card_that_is_not_there_is_refused():
    api = client()
    first = raised(api)
    answer = api.post(f"/findings/{first}/supersede", json={"by": "DR-2025-009999"})
    assert answer.status_code == 404


def test_the_cards_on_a_tail_come_back_in_number_order():
    api = client()
    raised(api)
    raised(api)
    listed = api.get("/aircraft/5127/findings").json()
    assert [one["reference"] for one in listed] == ["DR-2025-000001", "DR-2025-000002"]


def test_a_tail_with_nothing_on_it_comes_back_empty():
    api = client()
    assert api.get("/aircraft/5127/findings").json() == []


def test_cards_can_be_pulled_out_by_kind():
    api = client()
    raised(api)
    assert len(api.get("/findings-of-kind/dent").json()) == 1
    assert api.get("/findings-of-kind/crack").json() == []


@pytest.mark.parametrize("kind", ["dent", "crack", "corrosion"])
def test_every_kind_the_desk_measures_is_a_valid_query(kind):
    api = client()
    assert api.get(f"/findings-of-kind/{kind}").status_code == 200
