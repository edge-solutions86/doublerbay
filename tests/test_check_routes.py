from datetime import date

from fastapi.testclient import TestClient

import sample
from conftest import assessed_card
from doublerbay.api.app import Deps, build_app
from doublerbay.fleet.catalogue import TypeCatalogue
from doublerbay.fleet.utilisation import Snapshot, UtilisationLog
from doublerbay.ids import Serial
from doublerbay.manual.book import v320_book
from doublerbay.store.base import build_engine, build_schema
from doublerbay.store.unit import UnitOfWork

DAY = date(2025, 3, 14)
FITTED = "2025-04-02"
OPENS = "2025-04-01"
CLOSES = "2025-04-08"
CHIEF = {"x-api-key": "chief-key-0001"}
MSN = Serial("5127")
QUESTION = "dent in the chem mill land at FR15, past the allowable, what may we do"


def log_with_two_readings():
    opened = UtilisationLog.opened(MSN, date(2016, 4, 12))
    opened = opened.posted(Snapshot(date(2024, 3, 14), sample.flown(12000, 21000)))
    return opened.posted(Snapshot(date(2025, 3, 1), sample.flown(13800, 24200)))


def built():
    engine = build_engine()
    build_schema(engine)
    app = build_app(Deps(engine=engine, book=v320_book(), catalogue=TypeCatalogue(), today=DAY))
    with UnitOfWork(engine) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(log_with_two_readings())
    return engine, TestClient(app, headers=CHIEF)


def assessed(engine, sequence=1):
    """One card on a fastener row, raised and looked up."""
    return str(assessed_card(engine, sequence, at=sample.cabin_point(stringer=10)))


def defer(api, against, limit="1 FC", granted="2025-03-14"):
    return api.post(
        "/deferrals",
        json={"against": against, "granted": granted, "by": "j.mercer", "limit": limit},
    )


def fit(api, against, sequence=1):
    return api.post(
        "/repairs",
        json={
            "against": against,
            "sequence": sequence,
            "along": "2.000",
            "around": "2.000",
            "centre_along": "468.000",
            "centre_around": "91.000",
            "embodied": FITTED,
            "by": "s.brandt",
            "temporary": True,
        },
    )


def pack(api, serial="5127", opens=OPENS, closes=CLOSES, **rest):
    params = {"opens": opens, "closes": closes}
    params.update(rest)
    return api.get(f"/checks/{serial}/pack", params=params)


def test_an_empty_pack_comes_back_empty():
    engine, api = built()
    body = pack(api).json()
    assert body["total"] == 0
    assert body["deferrals"] == []
    assert body["references"] == []


def test_the_pack_carries_its_own_span():
    engine, api = built()
    body = pack(api).json()
    assert body["days"] == 8
    assert body["opens"] == OPENS


def test_a_deferral_falling_due_lands_on_the_pack():
    engine, api = built()
    defer(api, assessed(engine))
    body = pack(api).json()
    assert len(body["deferrals"]) == 1
    assert body["deferrals"][0]["reference"] == "DR-2025-000001"
    assert body["references"] == ["DR-2025-000001"]


def test_a_deferral_already_over_is_counted_as_overrun():
    engine, api = built()
    defer(api, assessed(engine), limit="1 DY", granted="2025-03-15")
    body = pack(api).json()
    assert body["overrun"] == 1
    assert body["deferrals"][0]["overrun"]


def test_an_order_out_lands_under_waiting():
    engine, api = built()
    api.post(
        "/orders",
        json={
            "against": assessed(engine),
            "question": QUESTION,
            "raised": "2025-03-14",
            "by": "a.okonkwo",
        },
    )
    body = pack(api).json()
    assert len(body["waiting"]) == 1
    assert not body["waiting"][0]["overrun"]


def test_a_repair_puts_nothing_on_a_pack_before_its_threshold():
    engine, api = built()
    fit(api, assessed(engine))
    assert pack(api).json()["inspections"] == []


def test_a_repair_past_its_threshold_lands_on_a_later_pack():
    engine, api = built()
    fit(api, assessed(engine))
    body = pack(api, opens="2030-01-01", closes="2030-01-08").json()
    assert len(body["inspections"]) == 1
    assert body["inspections"][0]["reference"] == "RP-53-0001"


def test_the_pack_can_pull_work_in_from_ahead():
    engine, api = built()
    defer(api, assessed(engine), limit="500 FC")
    assert pack(api).json()["deferrals"] == []
    assert len(pack(api, within=200).json()["deferrals"]) == 1


def test_a_reach_that_runs_backwards_is_refused():
    engine, api = built()
    answer = pack(api, within=-1)
    assert answer.status_code == 422
    assert "reaches forward" in answer.json()["detail"]


def test_a_reach_past_a_year_is_refused():
    engine, api = built()
    answer = pack(api, within=400)
    assert answer.status_code == 422
    assert "does not pull work in" in answer.json()["detail"]


def test_a_check_that_closes_before_it_opens_is_refused():
    engine, api = built()
    answer = pack(api, opens=CLOSES, closes=OPENS)
    assert answer.status_code == 422


def test_a_tail_that_is_not_on_the_register_is_not_found():
    engine, api = built()
    assert pack(api, serial="9999").status_code == 404


def test_the_days_are_required():
    engine, api = built()
    assert api.get("/checks/5127/pack").status_code == 422


def test_a_reader_may_pull_a_pack():
    engine, api = built()
    answer = api.get(
        "/checks/5127/pack",
        params={"opens": OPENS, "closes": CLOSES},
        headers={"x-api-key": "reader-key-0001"},
    )
    assert answer.status_code == 200


def test_a_pack_with_two_kinds_of_work_keeps_them_apart():
    engine, api = built()
    defer(api, assessed(engine, 1))
    api.post(
        "/orders",
        json={
            "against": assessed(engine, 2),
            "question": QUESTION,
            "raised": "2025-03-14",
            "by": "a.okonkwo",
        },
    )
    body = pack(api).json()
    assert len(body["deferrals"]) == 1
    assert len(body["waiting"]) == 1
    assert body["total"] == 2
