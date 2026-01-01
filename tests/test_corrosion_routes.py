from datetime import date

from fastapi.testclient import TestClient

import sample
from doublerbay.api.app import Deps, build_app
from doublerbay.damage.corrosion import Corrosion
from doublerbay.damage.kind import DamageKind, DamageOrigin
from doublerbay.fleet.catalogue import TypeCatalogue
from doublerbay.manual.book import v320_book
from doublerbay.service.intake import take_in
from doublerbay.store.base import build_engine, build_schema
from doublerbay.store.unit import UnitOfWork

DAY = date(2025, 3, 14)
LATER = date(2026, 3, 14)
CHIEF = {"x-api-key": "chief-key-0001"}


def built():
    engine = build_engine()
    build_schema(engine)
    app = build_app(Deps(engine=engine, book=v320_book(), catalogue=TypeCatalogue(), today=DAY))
    with UnitOfWork(engine) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(sample.flying_log())
    return engine, TestClient(app, headers=CHIEF)


def rust(depth="0.030"):
    return Corrosion(sample.inch("2.000"), sample.inch("1.000"), sample.inch(depth))


def raise_rust(engine, sequence=1, found=DAY, urgent=False, at=None, depth="0.030"):
    finding = sample.card(
        sequence,
        DamageKind.CORROSION,
        rust(depth),
        at=at,
        found=found,
        origin=DamageOrigin.ENVIRONMENT,
    )
    with UnitOfWork(engine) as unit:
        return take_in(unit, v320_book(), TypeCatalogue(), finding, urgent)


def test_a_tail_with_no_corrosion_comes_back_empty():
    engine, api = built()
    assert api.get("/corrosion/aircraft/5127").json() == []


def test_a_graded_finding_comes_back_on_the_tail():
    engine, api = built()
    raise_rust(engine)
    body = api.get("/corrosion/aircraft/5127").json()
    assert len(body) == 1
    assert body[0]["level"] == 1
    assert body[0]["part"].startswith("53")


def test_a_tail_that_is_not_on_the_register_is_not_found():
    engine, api = built()
    assert api.get("/corrosion/aircraft/9999").status_code == 404


def test_a_level_can_be_asked_for_across_the_fleet():
    engine, api = built()
    raise_rust(engine, 1)
    raise_rust(engine, 2, LATER)
    assert len(api.get("/corrosion/level/2").json()) == 1


def test_a_level_that_is_not_one_is_refused():
    engine, api = built()
    answer = api.get("/corrosion/level/9")
    assert answer.status_code == 422
    assert "not a corrosion level" in answer.json()["detail"]


def test_nothing_is_unreported_before_a_level_three():
    engine, api = built()
    raise_rust(engine)
    assert api.get("/corrosion/unreported").json() == []


def test_a_level_three_lands_on_the_unreported_list():
    engine, api = built()
    raise_rust(engine, urgent=True)
    body = api.get("/corrosion/unreported").json()
    assert body[0]["must_be_reported"]
    assert body[0]["report_by"] == "2025-03-17"


def test_marking_one_reported_clears_the_list():
    engine, api = built()
    raise_rust(engine, urgent=True)
    answer = api.post("/corrosion/DR-2025-000001/reported", json={"on": "2025-03-15"})
    assert answer.status_code == 200
    assert answer.json() == []


def test_reporting_one_that_is_not_on_the_register_is_not_found():
    engine, api = built()
    answer = api.post("/corrosion/DR-2025-000099/reported", json={"on": "2025-03-15"})
    assert answer.status_code == 404


def test_a_finding_cannot_be_reported_before_it_was_found():
    engine, api = built()
    raise_rust(engine, urgent=True)
    answer = api.post("/corrosion/DR-2025-000001/reported", json={"on": "2024-01-01"})
    assert answer.status_code == 422


def test_a_mechanic_may_not_mark_one_reported():
    engine, api = built()
    raise_rust(engine, urgent=True)
    answer = api.post(
        "/corrosion/DR-2025-000001/reported",
        json={"on": "2025-03-15"},
        headers={"x-api-key": "mechanic-key-0001"},
    )
    assert answer.status_code == 422


def test_the_programme_report_says_it_is_coping():
    engine, api = built()
    raise_rust(engine, depth="0.002")
    body = api.get(
        "/corrosion/aircraft/5127/programme", params={"since": "2020-01-01", "until": "2026-12-31"}
    ).json()
    assert body["coping"]
    assert body["total"] == 1
    assert body["escalation_rate"] == "0.000"


def test_the_programme_report_names_its_denominator():
    engine, api = built()
    body = api.get(
        "/corrosion/aircraft/5127/programme", params={"since": "2020-01-01", "until": "2026-12-31"}
    ).json()
    assert "not panels inspected" in body["denominator"]


def test_a_repeat_shows_up_on_the_programme_report():
    engine, api = built()
    raise_rust(engine, 1)
    raise_rust(engine, 2, LATER)
    body = api.get(
        "/corrosion/aircraft/5127/programme", params={"since": "2020-01-01", "until": "2026-12-31"}
    ).json()
    assert body["repeats"][0]["times"] == 2
    assert not body["repeats"][0]["persistent"]
    assert not body["coping"]


def test_the_window_cuts_the_programme_report():
    engine, api = built()
    raise_rust(engine)
    body = api.get(
        "/corrosion/aircraft/5127/programme", params={"since": "2026-01-01", "until": "2026-12-31"}
    ).json()
    assert body["total"] == 0


def test_a_window_that_ends_before_it_starts_is_refused():
    engine, api = built()
    answer = api.get(
        "/corrosion/aircraft/5127/programme", params={"since": "2026-12-31", "until": "2020-01-01"}
    )
    assert answer.status_code == 422


def test_the_programme_report_on_a_tail_that_is_not_there_is_not_found():
    engine, api = built()
    answer = api.get(
        "/corrosion/aircraft/9999/programme", params={"since": "2020-01-01", "until": "2026-12-31"}
    )
    assert answer.status_code == 404


def test_a_reader_may_pull_the_programme_report():
    engine, api = built()
    answer = api.get(
        "/corrosion/aircraft/5127/programme",
        params={"since": "2020-01-01", "until": "2026-12-31"},
        headers={"x-api-key": "reader-key-0001"},
    )
    assert answer.status_code == 200
