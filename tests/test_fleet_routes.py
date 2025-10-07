from datetime import date

from fastapi.testclient import TestClient

from doublerbay.api.app import Deps, build_app
from doublerbay.fleet.catalogue import TypeCatalogue
from doublerbay.manual.book import v320_book
from doublerbay.store.base import build_engine, build_schema

DAY = date(2025, 3, 14)


def client():
    engine = build_engine()
    build_schema(engine)
    built = build_app(Deps(engine=engine, book=v320_book(), catalogue=TypeCatalogue(), today=DAY))
    return TestClient(built)


def tail_body(serial="5127", marks="N412VD", series="200"):
    return {
        "serial": serial,
        "registration": marks,
        "series": series,
        "line_number": 1140,
        "delivered": "2016-04-12",
    }


def enrolled(api, **overrides):
    answer = api.post("/aircraft", json=tail_body(**overrides))
    assert answer.status_code == 201, answer.text
    return answer.json()


def test_a_tail_goes_on_the_register_and_comes_back():
    api = client()
    body = enrolled(api)
    assert body["serial"] == "5127"
    assert body["registration"] == "N412VD"
    assert api.get("/aircraft/5127").json()["line_number"] == 1140


def test_enrolling_a_tail_opens_its_log_at_delivery():
    api = client()
    enrolled(api)
    log = api.get("/aircraft/5127/utilisation").json()
    assert log["opened_on"] == "2016-04-12"
    assert log["latest"]["usage"]["cycles"] == 0


def test_a_tail_enrolled_twice_is_refused():
    api = client()
    enrolled(api)
    answer = api.post("/aircraft", json=tail_body())
    assert answer.status_code == 422
    assert "already on the register" in answer.json()["detail"]


def test_a_tail_that_is_not_there_comes_back_as_not_found():
    api = client()
    assert api.get("/aircraft/9999").status_code == 404


def test_marks_that_are_not_marks_are_refused():
    api = client()
    answer = api.post("/aircraft", json=tail_body(marks="not-a-tail"))
    assert answer.status_code == 422
    assert "not a registration" in answer.json()["detail"]


def test_a_tail_can_be_found_by_the_marks_it_is_wearing():
    api = client()
    enrolled(api)
    assert api.get("/aircraft-by-marks/N412VD").json()["serial"] == "5127"
    assert api.get("/aircraft-by-marks/N1").status_code == 404


def test_the_register_lists_what_is_on_it():
    api = client()
    enrolled(api)
    enrolled(api, serial="5240", marks="N908QA")
    assert len(api.get("/aircraft").json()) == 2


def test_the_register_can_be_asked_for_a_day():
    api = client()
    enrolled(api)
    assert api.get("/aircraft", params={"in_service_on": "2020-01-01"}).json()
    assert api.get("/aircraft", params={"in_service_on": "2000-01-01"}).json() == []


def test_a_posted_reading_lands_on_the_log():
    api = client()
    enrolled(api)
    answer = api.post(
        "/aircraft/5127/readings",
        json={"taken": "2025-01-01", "usage": {"cycles": 13800, "hours": "24200.0"}},
    )
    assert answer.status_code == 201
    assert answer.json()["latest"]["usage"]["cycles"] == 13800
    assert len(answer.json()["readings"]) == 2


def test_a_reading_that_runs_a_counter_backwards_is_refused():
    api = client()
    enrolled(api)
    api.post(
        "/aircraft/5127/readings",
        json={"taken": "2025-01-01", "usage": {"cycles": 13800, "hours": "24200.0"}},
    )
    answer = api.post(
        "/aircraft/5127/readings",
        json={"taken": "2025-06-01", "usage": {"cycles": 100, "hours": "200.0"}},
    )
    assert answer.status_code == 422
    assert "cycles fell" in answer.json()["detail"]


def test_a_refused_reading_leaves_the_log_alone():
    api = client()
    enrolled(api)
    api.post(
        "/aircraft/5127/readings",
        json={"taken": "2025-06-01", "usage": {"cycles": 100, "hours": "-1"}},
    )
    assert len(api.get("/aircraft/5127/utilisation").json()["readings"]) == 1


def test_two_readings_on_one_day_are_refused():
    api = client()
    enrolled(api)
    body = {"taken": "2025-01-01", "usage": {"cycles": 13800, "hours": "24200.0"}}
    api.post("/aircraft/5127/readings", json=body)
    assert api.post("/aircraft/5127/readings", json=body).status_code == 422


def test_the_hours_cross_the_wire_as_text_and_come_back_as_text():
    api = client()
    enrolled(api)
    answer = api.post(
        "/aircraft/5127/readings",
        json={"taken": "2025-01-01", "usage": {"cycles": 13800, "hours": "24200.5"}},
    )
    assert answer.json()["latest"]["usage"]["hours"] == "24200.5"


def test_the_rate_comes_off_the_recent_stretch_of_the_log():
    api = client()
    enrolled(api)
    api.post(
        "/aircraft/5127/readings",
        json={"taken": "2024-01-01", "usage": {"cycles": 12000, "hours": "21000.0"}},
    )
    api.post(
        "/aircraft/5127/readings",
        json={"taken": "2025-01-01", "usage": {"cycles": 13800, "hours": "24200.0"}},
    )
    rate = api.get("/aircraft/5127/rate").json()
    assert rate["as_of"] == "2025-01-01"
    assert float(rate["cycles_a_day"]) > 0
    assert float(rate["average_leg"]) > 1


def test_a_tail_that_has_only_ever_been_read_once_has_no_rate():
    api = client()
    enrolled(api)
    answer = api.get("/aircraft/5127/rate")
    assert answer.status_code == 404
    assert "one reading is not a rate" in answer.json()["detail"]


def test_a_reading_against_a_tail_that_is_not_there_is_refused():
    api = client()
    answer = api.post(
        "/aircraft/9999/readings",
        json={"taken": "2025-01-01", "usage": {"cycles": 1, "hours": "1.0"}},
    )
    assert answer.status_code == 404
