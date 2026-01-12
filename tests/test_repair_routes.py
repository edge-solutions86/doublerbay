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
FITTED = "2025-04-02"
CHIEF = {"x-api-key": "chief-key-0001"}


def built():
    engine = build_engine()
    build_schema(engine)
    app = build_app(Deps(engine=engine, book=v320_book(), catalogue=TypeCatalogue(), today=DAY))
    with UnitOfWork(engine) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(sample.flying_log())
    return engine, TestClient(app, headers=CHIEF)


def assessed(engine, sequence=1):
    reference = FindingId(2025, sequence)
    with UnitOfWork(engine) as unit:
        unit.findings.raise_finding(sample.card(sequence, at=sample.cabin_point(stringer=10)))
        unit.dispositions.save(
            DispositionRecord.raised(reference).moved_to(
                DispositionState.ASSESSED, DAY, "a.okonkwo"
            )
        )
    return str(reference)


def body(against, sequence=1, centre=("468.000", "91.000"), **rest):
    sent = {
        "against": against,
        "sequence": sequence,
        "along": "2.000",
        "around": "2.000",
        "centre_along": centre[0],
        "centre_around": centre[1],
        "embodied": FITTED,
        "by": "s.brandt",
    }
    sent.update(rest)
    return sent


def test_a_repair_is_drawn_and_fitted():
    engine, api = built()
    answer = api.post("/repairs", json=body(assessed(engine)))
    assert answer.status_code == 201, answer.text
    assert answer.json()["repair"]["reference"] == "RP-53-0001"


def test_the_drawing_comes_back_with_the_service_s_own_numbers():
    engine, api = built()
    drawn = api.post("/repairs", json=body(assessed(engine))).json()["repair"]["doubler"]
    assert drawn["fastener_count"] == drawn["rows_each_side"] * drawn["per_row"] * 4
    assert drawn["material"].startswith("2024")


def test_fitting_closes_the_card():
    engine, api = built()
    answer = api.post("/repairs", json=body(assessed(engine)))
    assert answer.json()["card_state"] == "closed"


def test_a_temporary_repair_leaves_the_card_in_work():
    engine, api = built()
    answer = api.post("/repairs", json=body(assessed(engine), temporary=True))
    assert answer.json()["card_state"] == "in work"
    assert answer.json()["repair"]["category"] == "temporary, to be replaced"


def test_a_fastener_size_can_be_asked_for():
    engine, api = built()
    answer = api.post("/repairs", json=body(assessed(engine), fastener_dash=6))
    assert answer.json()["repair"]["doubler"]["fastener_dash"] == 6


def test_a_clean_fit_reports_no_clashes():
    engine, api = built()
    assert api.post("/repairs", json=body(assessed(engine))).json()["clashes"] == []


def test_a_second_repair_too_close_is_reported_as_a_clash():
    engine, api = built()
    api.post("/repairs", json=body(assessed(engine, 1), 1, ("468.000", "91.000")))
    answer = api.post("/repairs", json=body(assessed(engine, 2), 2, ("469.000", "91.000")))
    assert answer.json()["clashes"] == [["RP-53-0001", "RP-53-0002"]]


def test_a_repair_against_a_card_that_is_not_on_file_is_not_found():
    engine, api = built()
    assert api.post("/repairs", json=body("DR-2025-000099")).status_code == 404


def test_a_repair_number_used_twice_is_refused():
    engine, api = built()
    api.post("/repairs", json=body(assessed(engine, 1), 1))
    answer = api.post("/repairs", json=body(assessed(engine, 2), 1))
    assert answer.status_code == 422


def test_a_length_that_is_not_a_length_is_refused():
    engine, api = built()
    answer = api.post("/repairs", json=body(assessed(engine), along="big"))
    assert answer.status_code == 422
    assert "not a repair length" in answer.json()["detail"]


def test_a_page_that_is_not_a_reference_is_refused():
    engine, api = built()
    answer = api.post("/repairs", json=body(assessed(engine), page="page four"))
    assert answer.status_code == 422


def test_a_mechanic_may_not_fit_a_repair():
    engine, api = built()
    answer = api.post(
        "/repairs", json=body(assessed(engine)), headers={"x-api-key": "mechanic-key-0001"}
    )
    assert answer.status_code == 422


def test_the_repairs_on_a_tail_come_back():
    engine, api = built()
    api.post("/repairs", json=body(assessed(engine)))
    assert len(api.get("/repairs/aircraft/5127").json()) == 1


def test_a_tail_with_no_repairs_comes_back_empty():
    engine, api = built()
    assert api.get("/repairs/aircraft/5127").json() == []


def test_a_tail_that_is_not_on_the_register_is_not_found():
    engine, api = built()
    assert api.get("/repairs/aircraft/9999").status_code == 404


def test_one_repair_can_be_read_back():
    engine, api = built()
    api.post("/repairs", json=body(assessed(engine)))
    body_back = api.get("/repairs/53/1").json()
    assert body_back["against"] == "DR-2025-000001"
    assert body_back["removed"] is None


def test_a_repair_that_is_not_on_file_is_not_found():
    engine, api = built()
    assert api.get("/repairs/53/99").status_code == 404


def test_a_repair_can_be_taken_off():
    engine, api = built()
    api.post("/repairs", json=body(assessed(engine)))
    assert api.post("/repairs/53/1/remove", json={"on": "2026-01-05"}).status_code == 204
    assert api.get("/repairs/aircraft/5127").json() == []


def test_a_removed_repair_is_still_on_the_history():
    engine, api = built()
    api.post("/repairs", json=body(assessed(engine)))
    api.post("/repairs/53/1/remove", json={"on": "2026-01-05"})
    every = api.get("/repairs/aircraft/5127", params={"fitted_only": False}).json()
    assert every[0]["removed"] == "2026-01-05"


def test_removing_one_that_is_not_on_file_is_not_found():
    engine, api = built()
    assert api.post("/repairs/53/99/remove", json={"on": "2026-01-05"}).status_code == 404


def test_a_repair_cannot_come_off_before_it_went_on():
    engine, api = built()
    api.post("/repairs", json=body(assessed(engine)))
    assert api.post("/repairs/53/1/remove", json={"on": "2024-01-01"}).status_code == 422
