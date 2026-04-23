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
SENT = "2025-03-20"
ANSWERED = "2025-06-01"
CHIEF = {"x-api-key": "chief-key-0001"}
QUESTION = "dent in the chem mill land at FR15, past the allowable, what may we do"


def built():
    engine = build_engine()
    build_schema(engine)
    app = build_app(
        Deps(engine=engine, book=v320_book(), catalogue=TypeCatalogue(), today=date(2025, 3, 14))
    )
    with UnitOfWork(engine) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(sample.flying_log())
    return engine, TestClient(app, headers=CHIEF)


def assessed(engine, sequence=1):
    reference = FindingId(2025, sequence)
    with UnitOfWork(engine) as unit:
        unit.findings.raise_finding(sample.card(sequence))
        unit.dispositions.save(
            DispositionRecord.raised(reference).moved_to(
                DispositionState.ASSESSED, date(2025, 3, 14), "a.okonkwo"
            )
        )
    return str(reference)


def raise_one(api, against, question=QUESTION, on=DAY):
    return api.post(
        "/orders",
        json={"against": against, "question": question, "raised": on, "by": "a.okonkwo"},
    )


def approval(category="permanent with a repeat inspection", until="2027-01-01"):
    return {
        "on": ANSWERED,
        "by": "a.okonkwo",
        "sheet": "RAS-2025-4417",
        "category": category,
        "conditions": ["inspect at every C check"],
        "valid_until": until,
    }


def test_an_order_can_be_raised():
    engine, api = built()
    answer = raise_one(api, assessed(engine))
    assert answer.status_code == 201, answer.text
    assert answer.json()["reference"] == "EO-2025-0001"
    assert answer.json()["open"]


def test_raising_holds_the_card():
    engine, api = built()
    raise_one(api, assessed(engine))
    with UnitOfWork(engine) as unit:
        assert unit.dispositions.get(FindingId(2025, 1)).state is DispositionState.HELD


def test_a_question_that_says_nothing_is_refused():
    engine, api = built()
    answer = raise_one(api, assessed(engine), question="help")
    assert answer.status_code == 422
    assert "says what is being asked" in answer.json()["detail"]


def test_a_card_that_is_not_on_file_cannot_go_out():
    engine, api = built()
    assert raise_one(api, "DR-2025-000099").status_code == 404


def test_a_card_already_out_cannot_go_out_again():
    engine, api = built()
    reference = assessed(engine)
    raise_one(api, reference)
    assert raise_one(api, reference).status_code == 409


def test_a_mechanic_may_not_raise_an_order():
    engine, api = built()
    answer = api.post(
        "/orders",
        json={
            "against": assessed(engine),
            "question": QUESTION,
            "raised": DAY,
            "by": "a.okonkwo",
        },
        headers={"x-api-key": "mechanic-key-0001"},
    )
    assert answer.status_code == 422


def test_the_open_list_carries_what_is_out():
    engine, api = built()
    raise_one(api, assessed(engine))
    assert api.get("/orders").json()["total"] == 1


def test_the_open_list_is_empty_before_anything_goes_out():
    engine, api = built()
    assert api.get("/orders").json()["rows"] == []


def test_one_order_can_be_read_back():
    engine, api = built()
    raise_one(api, assessed(engine))
    assert api.get("/orders/EO-2025-0001").json()["question"] == QUESTION


def test_an_order_that_is_not_on_file_is_not_found():
    engine, api = built()
    assert api.get("/orders/EO-2025-0099").status_code == 404


def test_a_reference_that_is_not_one_is_refused():
    engine, api = built()
    assert api.get("/orders/nonsense").status_code == 422


def test_sending_records_the_day():
    engine, api = built()
    raise_one(api, assessed(engine))
    answer = api.post("/orders/EO-2025-0001/sent", json={"on": SENT})
    assert answer.json()["state"] == "sent"
    assert answer.json()["sent"] == SENT


def test_sending_one_that_has_already_gone_is_refused():
    engine, api = built()
    raise_one(api, assessed(engine))
    api.post("/orders/EO-2025-0001/sent", json={"on": SENT})
    assert api.post("/orders/EO-2025-0001/sent", json={"on": SENT}).status_code == 409


def test_acknowledging_moves_it_on():
    engine, api = built()
    raise_one(api, assessed(engine))
    api.post("/orders/EO-2025-0001/sent", json={"on": SENT})
    assert api.post("/orders/EO-2025-0001/acknowledged").json()["state"] == "acknowledged"


def test_an_approval_comes_back_with_its_conditions():
    engine, api = built()
    raise_one(api, assessed(engine))
    api.post("/orders/EO-2025-0001/sent", json={"on": SENT})
    body = api.post("/orders/EO-2025-0001/approved", json=approval()).json()
    assert body["approval"]["sheet"] == "RAS-2025-4417"
    assert body["approval"]["conditions"] == ["inspect at every C check"]
    assert not body["open"]


def test_an_approval_releases_the_card():
    engine, api = built()
    raise_one(api, assessed(engine))
    api.post("/orders/EO-2025-0001/sent", json={"on": SENT})
    api.post("/orders/EO-2025-0001/approved", json=approval())
    with UnitOfWork(engine) as unit:
        assert unit.dispositions.get(FindingId(2025, 1)).state is DispositionState.ASSESSED


def test_a_category_that_is_not_one_is_refused():
    engine, api = built()
    raise_one(api, assessed(engine))
    api.post("/orders/EO-2025-0001/sent", json={"on": SENT})
    answer = api.post("/orders/EO-2025-0001/approved", json=approval(category="whatever"))
    assert answer.status_code == 422
    assert "not a repair category" in answer.json()["detail"]


def test_a_sheet_number_that_is_not_one_is_refused():
    engine, api = built()
    raise_one(api, assessed(engine))
    api.post("/orders/EO-2025-0001/sent", json={"on": SENT})
    sent = approval()
    sent["sheet"] = "4417"
    assert api.post("/orders/EO-2025-0001/approved", json=sent).status_code == 422


def test_a_rejection_releases_the_card():
    engine, api = built()
    raise_one(api, assessed(engine))
    api.post("/orders/EO-2025-0001/sent", json={"on": SENT})
    answer = api.post("/orders/EO-2025-0001/rejected", json={"on": ANSWERED, "by": "a.okonkwo"})
    assert answer.json()["state"] == "rejected"
    with UnitOfWork(engine) as unit:
        assert unit.dispositions.get(FindingId(2025, 1)).state is DispositionState.ASSESSED


def test_a_question_can_be_withdrawn():
    engine, api = built()
    raise_one(api, assessed(engine))
    answer = api.post("/orders/EO-2025-0001/withdrawn", json={"on": ANSWERED, "by": "a.okonkwo"})
    assert answer.json()["state"] == "withdrawn"


def test_nothing_needs_chasing_the_day_it_goes_out():
    engine, api = built()
    raise_one(api, assessed(engine))
    api.post("/orders/EO-2025-0001/sent", json={"on": SENT})
    assert api.get("/orders/chase", params={"on": SENT}).json() == []


def test_an_order_out_a_long_time_needs_chasing():
    engine, api = built()
    raise_one(api, assessed(engine))
    api.post("/orders/EO-2025-0001/sent", json={"on": SENT})
    body = api.get("/orders/chase", params={"on": "2026-01-01"}).json()
    assert body[0]["order"]["reference"] == "EO-2025-0001"
    assert body[0]["days_waiting"] > 200


def test_the_chase_list_needs_a_day():
    engine, api = built()
    assert api.get("/orders/chase").status_code == 422
