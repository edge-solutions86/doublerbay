from datetime import date

from fastapi.testclient import TestClient

import sample
from doublerbay.api.app import Deps, build_app
from doublerbay.disposition.state import DispositionRecord, DispositionState
from doublerbay.fleet.catalogue import TypeCatalogue
from doublerbay.ids import FindingId
from doublerbay.manual.book import v320_book
from doublerbay.store.base import build_engine, build_schema
from doublerbay.store.unit import UnitOfWork

DAY = "2025-03-14"
CHIEF = {"x-api-key": "chief-key-0001"}


def built():
    engine = build_engine()
    build_schema(engine)
    app = build_app(
        Deps(engine=engine, book=v320_book(), catalogue=TypeCatalogue(), today=date(2025, 3, 14))
    )
    return engine, TestClient(app, headers=CHIEF)


def enrol(api, serial="5127", marks="N412VD"):
    api.post(
        "/aircraft",
        json={
            "serial": serial,
            "registration": marks,
            "series": "200",
            "line_number": 1140,
            "delivered": "2016-04-12",
        },
    )
    api.post(
        f"/aircraft/{serial}/readings",
        json={"taken": "2024-03-14", "usage": {"cycles": 12000, "hours": "21000.0"}},
    )
    api.post(
        f"/aircraft/{serial}/readings",
        json={"taken": "2025-03-01", "usage": {"cycles": 13800, "hours": "24200.0"}},
    )


def stocked():
    engine, api = built()
    enrol(api)
    return engine, api


def assessed(engine, sequence=1, serial="5127"):
    reference = FindingId(2025, sequence)
    with UnitOfWork(engine) as unit:
        unit.findings.raise_finding(sample.card(sequence, serial=serial))
        unit.dispositions.save(
            DispositionRecord.raised(reference).moved_to(
                DispositionState.ASSESSED, date(2025, 3, 14), "a.okonkwo"
            )
        )
    return str(reference)


def defer(api, reference, **rest):
    sent = {"against": reference, "granted": DAY, "by": "j.mercer"}
    sent.update(rest)
    return api.post("/deferrals", json=sent)


def test_a_fleet_with_nothing_due_reports_nothing():
    engine, api = stocked()
    body = api.get("/reports/due", params={"on": DAY}).json()
    assert body["overrun"] == 0
    assert body["tails"][0]["items"] == []


def test_a_deferral_lands_on_the_fleet_due_report():
    engine, api = stocked()
    defer(api, assessed(engine), limit="500 FC")
    body = api.get("/reports/due", params={"on": DAY}).json()
    assert body["tails"][0]["items"][0]["against"] == "DR-2025-000001"
    assert body["tails"][0]["items"][0]["clock"] == "flight cycles"


def test_the_due_report_says_whether_the_rate_is_measured():
    engine, api = stocked()
    defer(api, assessed(engine))
    assert api.get("/reports/due", params={"on": DAY}).json()["tails"][0]["measured_rate"]


def test_one_tail_can_be_asked_for():
    engine, api = stocked()
    defer(api, assessed(engine), limit="500 FC")
    body = api.get("/reports/due/5127", params={"on": DAY}).json()
    assert body["serial"] == "5127"
    assert len(body["items"]) == 1


def test_a_tail_that_is_not_on_the_register_comes_back_as_not_found():
    engine, api = stocked()
    assert api.get("/reports/due/9999", params={"on": DAY}).status_code == 404


def test_a_horizon_cuts_the_report_down():
    engine, api = stocked()
    defer(api, assessed(engine), limit="500 FC")
    body = api.get("/reports/due/5127", params={"on": DAY, "within": 5}).json()
    assert body["items"] == []


def test_a_horizon_that_runs_backwards_is_refused():
    engine, api = stocked()
    answer = api.get("/reports/due", params={"on": DAY, "within": -1})
    assert answer.status_code == 422
    assert "count of days ahead" in answer.json()["detail"]


def test_a_horizon_past_what_the_desk_plans_for_is_refused():
    engine, api = stocked()
    answer = api.get("/reports/due", params={"on": DAY, "within": 99999})
    assert answer.status_code == 422
    assert "does not plan past" in answer.json()["detail"]


def test_nothing_is_grounded_before_a_limit_passes():
    engine, api = stocked()
    defer(api, assessed(engine), limit="500 FC")
    assert api.get("/reports/grounded", params={"on": DAY}).json()["tails"] == []


def test_a_tail_past_a_calendar_limit_is_grounded():
    engine, api = stocked()
    defer(api, assessed(engine), limit="30 DY")
    body = api.get("/reports/grounded", params={"on": "2026-06-01"}).json()
    assert body["tails"][0]["serial"] == "5127"
    assert body["overrun"] == 1


def test_a_grounded_line_carries_the_overrun_flag():
    engine, api = stocked()
    defer(api, assessed(engine), limit="30 DY")
    body = api.get("/reports/grounded", params={"on": "2026-06-01"}).json()
    assert body["tails"][0]["items"][0]["overrun"]
    assert body["tails"][0]["items"][0]["days"] < 0


def test_the_open_report_counts_the_fleet():
    engine, api = stocked()
    assessed(engine, 1)
    body = api.get("/reports/open", params={"on": DAY}).json()
    assert body["total"] == 1
    assert body["clean_tails"] == []


def test_a_clean_tail_is_named():
    engine, api = stocked()
    body = api.get("/reports/open", params={"on": DAY}).json()
    assert body["clean_tails"] == ["5127"]


def test_the_open_report_breaks_one_tail_down():
    engine, api = stocked()
    assessed(engine, 1)
    body = api.get("/reports/open/5127", params={"on": DAY}).json()
    assert body["by_kind"][0] == {"name": "dent", "count": 1}
    assert body["by_chapter"][0] == {"name": "53", "count": 1}


def test_the_open_report_carries_the_origin_split():
    engine, api = stocked()
    assessed(engine, 1)
    body = api.get("/reports/open/5127", params={"on": DAY}).json()
    assert body["by_origin"][0]["name"] == "impact"


def test_the_recent_window_can_be_widened():
    engine, api = stocked()
    assessed(engine, 1)
    narrow = api.get("/reports/open/5127", params={"on": "2025-12-01"}).json()
    wide = api.get("/reports/open/5127", params={"on": "2025-12-01", "recent": 365}).json()
    assert narrow["raised_recently"] == 0
    assert wide["raised_recently"] == 1


def test_the_open_report_on_a_tail_that_is_not_there_is_not_found():
    engine, api = stocked()
    assert api.get("/reports/open/9999", params={"on": DAY}).status_code == 404


def test_a_day_is_required():
    engine, api = stocked()
    assert api.get("/reports/due").status_code == 422


def test_a_reader_may_pull_the_reports():
    engine, api = stocked()
    answer = api.get("/reports/open", params={"on": DAY}, headers={"x-api-key": "reader-key-0001"})
    assert answer.status_code == 200
