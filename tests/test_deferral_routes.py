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

DAY = date(2025, 3, 14)
CHIEF = {"x-api-key": "chief-key-0001"}
MECHANIC = {"x-api-key": "mechanic-key-0001"}


def built():
    engine = build_engine()
    build_schema(engine)
    app = build_app(Deps(engine=engine, book=v320_book(), catalogue=TypeCatalogue(), today=DAY))
    return engine, TestClient(app, headers=CHIEF)


def stocked():
    engine, api = built()
    api.post(
        "/aircraft",
        json={
            "serial": "5127",
            "registration": "N412VD",
            "series": "200",
            "line_number": 1140,
            "delivered": "2016-04-12",
        },
    )
    api.post(
        "/aircraft/5127/readings",
        json={"taken": "2025-03-01", "usage": {"cycles": 13800, "hours": "24200.0"}},
    )
    return engine, api


def assessed(engine, sequence=1):
    reference = FindingId(2025, sequence)
    with UnitOfWork(engine) as unit:
        unit.findings.raise_finding(sample.card(sequence))
        unit.dispositions.save(
            DispositionRecord.raised(reference).moved_to(
                DispositionState.ASSESSED, DAY, "a.okonkwo"
            )
        )
    return str(reference)


def body(against, **rest):
    sent = {"against": against, "granted": "2025-03-14", "by": "j.mercer"}
    sent.update(rest)
    return sent


def test_a_card_can_be_deferred():
    engine, api = stocked()
    answer = api.post("/deferrals", json=body(assessed(engine)))
    assert answer.status_code == 201, answer.text
    assert answer.json()["against"] == "DR-2025-000001"


def test_the_deferral_comes_back_with_what_it_is_due_against():
    engine, api = stocked()
    answer = api.post("/deferrals", json=body(assessed(engine), limit="500 FC"))
    due = answer.json()["due"]
    assert due["cycles"] == 14300
    assert due["on"] is None
    assert "14300 FC" in due["written"]


def test_a_deferral_moves_the_disposition_too():
    engine, api = stocked()
    api.post("/deferrals", json=body(assessed(engine)))
    with UnitOfWork(engine) as unit:
        assert unit.dispositions.get(FindingId(2025, 1)).state is DispositionState.DEFERRED


def test_a_card_that_has_not_been_assessed_cannot_be_deferred():
    engine, api = stocked()
    with UnitOfWork(engine) as unit:
        unit.findings.raise_finding(sample.card(1))
        unit.dispositions.save(DispositionRecord.raised(FindingId(2025, 1)))
    answer = api.post("/deferrals", json=body("DR-2025-000001"))
    assert answer.status_code == 409
    assert "cannot go from raised to deferred" in answer.json()["detail"]


def test_a_refused_deferral_writes_nothing():
    engine, api = stocked()
    with UnitOfWork(engine) as unit:
        unit.findings.raise_finding(sample.card(1))
        unit.dispositions.save(DispositionRecord.raised(FindingId(2025, 1)))
    api.post("/deferrals", json=body("DR-2025-000001"))
    with UnitOfWork(engine) as unit:
        assert unit.deferrals.live() == ()


def test_a_limit_arrives_as_the_text_the_manual_prints():
    engine, api = stocked()
    answer = api.post("/deferrals", json=body(assessed(engine), limit="300 FC / 90 DY"))
    assert answer.json()["limit"] == "300 FC / 90 DY"


def test_a_limit_that_cannot_be_read_is_refused():
    engine, api = stocked()
    answer = api.post("/deferrals", json=body(assessed(engine), limit="soon"))
    assert answer.status_code == 422


def test_a_deferral_to_a_check_is_cut_at_the_check():
    engine, api = stocked()
    answer = api.post("/deferrals", json=body(assessed(engine), next_check="2025-04-01"))
    assert answer.json()["due"]["on"] == "2025-04-01"


def test_the_method_crosses_the_wire():
    engine, api = stocked()
    answer = api.post(
        "/deferrals", json=body(assessed(engine), method="high frequency eddy current")
    )
    assert answer.json()["re_inspect_with"] == "high frequency eddy current"


def test_a_method_that_is_not_one_is_refused():
    engine, api = stocked()
    answer = api.post("/deferrals", json=body(assessed(engine), method="a good hard look"))
    assert answer.status_code == 422


def test_a_mechanic_may_not_defer():
    engine, api = stocked()
    reference = assessed(engine)
    answer = api.post("/deferrals", json=body(reference), headers=MECHANIC)
    assert answer.status_code == 422
    assert "may not grant a deferral" in answer.json()["detail"]


def test_a_request_with_no_key_is_refused():
    engine, api = stocked()
    answer = api.post("/deferrals", json=body(assessed(engine)), headers={"x-api-key": ""})
    assert answer.status_code == 422


def test_a_card_that_is_not_on_file_cannot_be_deferred():
    engine, api = stocked()
    assert api.post("/deferrals", json=body("DR-2025-000099")).status_code == 404


def test_deferring_a_card_twice_is_refused():
    engine, api = stocked()
    reference = assessed(engine)
    api.post("/deferrals", json=body(reference))
    assert api.post("/deferrals", json=body(reference)).status_code == 422


def test_the_live_list_carries_what_is_deferred():
    engine, api = stocked()
    api.post("/deferrals", json=body(assessed(engine)))
    assert api.get("/deferrals").json()["total"] == 1


def test_the_live_list_is_empty_before_anything_is_deferred():
    engine, api = stocked()
    assert api.get("/deferrals").json()["rows"] == []


def test_one_deferral_can_be_read_back():
    engine, api = stocked()
    reference = assessed(engine)
    api.post("/deferrals", json=body(reference))
    assert api.get(f"/deferrals/{reference}").json()["by"] == "j.mercer"


def test_a_card_that_is_not_deferred_comes_back_as_not_found():
    engine, api = stocked()
    assert api.get("/deferrals/DR-2025-000099").status_code == 404


def test_a_deferral_can_be_tightened():
    engine, api = stocked()
    reference = assessed(engine)
    api.post("/deferrals", json=body(reference, limit="500 FC"))
    answer = api.post(f"/deferrals/{reference}/tighten", json={"limit": "50 FC", "by": "a.okonkwo"})
    assert answer.status_code == 200
    assert answer.json()["limit"] == "50 FC"
    assert answer.json()["by"] == "a.okonkwo"


def test_tightening_keeps_the_day_it_was_granted():
    engine, api = stocked()
    reference = assessed(engine)
    api.post("/deferrals", json=body(reference, limit="500 FC"))
    answer = api.post(f"/deferrals/{reference}/tighten", json={"limit": "50 FC", "by": "a.okonkwo"})
    assert answer.json()["granted"] == "2025-03-14"


def test_a_deferral_can_be_lifted():
    engine, api = stocked()
    reference = assessed(engine)
    api.post("/deferrals", json=body(reference))
    assert api.post(f"/deferrals/{reference}/lift", json={"on": "2025-06-01"}).status_code == 204
    assert api.get("/deferrals").json()["rows"] == []


def test_lifting_one_that_was_never_granted_is_refused():
    engine, api = stocked()
    answer = api.post("/deferrals/DR-2025-000099/lift", json={"on": "2025-06-01"})
    assert answer.status_code == 404


def test_a_reader_may_not_lift_a_deferral():
    engine, api = stocked()
    reference = assessed(engine)
    api.post("/deferrals", json=body(reference))
    answer = api.post(
        f"/deferrals/{reference}/lift",
        json={"on": "2025-06-01"},
        headers={"x-api-key": "reader-key-0001"},
    )
    assert answer.status_code == 422
