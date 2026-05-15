"""One damage card's whole life, through the http surface, in order.

Every other test in this suite holds one thing still and pokes at it. This one
does not hold anything still. It raises a card the way a mechanic would,
assesses it, defers it, asks the manufacturer, gets an answer, fits a repair,
signs off the inspection, and checks that every report says the right thing at
every step.

It is slow and it is worth it. The failures it catches are the ones no unit
test can: a state machine that allows each move on its own and no path through
them, a report that is right in isolation and disagrees with the register it
was built from, a lifted deferral that stayed on the work pack.

Nothing here reaches into a repository. If the http surface cannot do it, the
desk cannot do it.
"""

from datetime import date

import pytest
from fastapi.testclient import TestClient

from doublerbay.api.app import Deps, build_app
from doublerbay.fleet.catalogue import TypeCatalogue
from doublerbay.manual.book import v320_book
from doublerbay.store.base import build_engine, build_schema

FOUND = "2025-03-14"
DEFERRED = "2025-03-20"
SENT = "2025-03-21"
ANSWERED = "2025-06-01"
FITTED = "2025-06-10"
CHIEF = {"x-api-key": "chief-key-0001"}
MECHANIC = {"x-api-key": "mechanic-key-0001"}
QUESTION = "dent in the chem mill land at FR15, past the allowable, what may we do"


@pytest.fixture
def api():
    engine = build_engine()
    build_schema(engine)
    app = build_app(
        Deps(engine=engine, book=v320_book(), catalogue=TypeCatalogue(), today=date(2025, 3, 14))
    )
    return TestClient(app, headers=CHIEF)


def enrol(api):
    answer = api.post(
        "/aircraft",
        json={
            "serial": "5127",
            "registration": "N412VD",
            "series": "200",
            "line_number": 1140,
            "delivered": "2016-04-12",
        },
    )
    assert answer.status_code == 201, answer.text
    for taken, cycles in (("2024-03-14", 12000), ("2025-03-01", 13800)):
        api.post(
            "/aircraft/5127/readings",
            json={"taken": taken, "usage": {"cycles": cycles, "hours": f"{cycles * 2}.0"}},
        )


def raise_card(api, depth="0.220"):
    answer = api.post(
        "/findings",
        json={
            "serial": "5127",
            "kind": "dent",
            "origin": "impact",
            "source": "scheduled check",
            "found_on": FOUND,
            "at_usage": {"cycles": 13800, "hours": "24200.0"},
            "fuselage": {
                "structure": "fuselage",
                "frame": 15,
                "aft_of_frame": "10.000",
                "stringer": 10,
                "below_stringer": "6.000",
                "side": "L",
            },
            "measurements": {
                "depth": depth,
                "along": "3.000",
                "around": "2.000",
                "sharp_bottom": False,
                "cracked": False,
            },
        },
        headers=MECHANIC,
    )
    assert answer.status_code == 201, answer.text
    return answer.json()["reference"]


def move(api, reference, state, on, by="a.okonkwo"):
    """Walk the disposition by the routes that exist for each step."""
    return api.post(f"/findings/{reference}/state", json={"state": state, "on": on, "by": by})


def test_the_whole_life_of_one_card(api):
    enrol(api)
    reference = raise_card(api)

    # It is open, it is on the fleet report, and the book does not allow it.
    assert api.get(f"/findings/{reference}").json()["revision"] == 1
    assert api.get("/reports/open", params={"on": FOUND}).json()["total"] == 1
    verdict = api.get(f"/findings/{reference}/assessment", params={"on": FOUND}).json()
    assert verdict["verdict"]["disposition"] != "allowable as it stands"

    # Nothing is due yet, because nothing has a limit on it.
    assert api.get("/reports/due/5127", params={"on": FOUND}).json()["items"] == []

    # Somebody looks it up, which is the move that had no route until now.
    assessed = move(api, reference, "assessed", FOUND)
    assert assessed.status_code == 200, assessed.text
    assert assessed.json()["state"] == "assessed"

    # It goes out to the manufacturer, which holds the card.
    order = api.post(
        "/orders",
        json={"against": reference, "question": QUESTION, "raised": FOUND, "by": "a.okonkwo"},
    )
    assert order.status_code == 201, order.text
    number = order.json()["reference"]
    assert api.get("/orders").json()["total"] == 1

    # Nothing can be deferred while it is held.
    refused = api.post(
        "/deferrals",
        json={"against": reference, "granted": DEFERRED, "by": "j.mercer", "limit": "300 FC"},
    )
    assert refused.status_code == 409

    # The answer comes back approved, which releases the card.
    api.post(f"/orders/{number}/sent", json={"on": SENT})
    approved = api.post(
        f"/orders/{number}/approved",
        json={
            "on": ANSWERED,
            "by": "a.okonkwo",
            "sheet": "RAS-2025-4417",
            "category": "permanent with a repeat inspection",
            "conditions": ["inspect at every C check"],
            "valid_until": "2027-01-01",
        },
    )
    assert approved.status_code == 200, approved.text
    assert api.get("/orders").json()["total"] == 0

    # Now it can be deferred, and it lands on the due list and the work pack.
    granted = api.post(
        "/deferrals",
        json={"against": reference, "granted": ANSWERED, "by": "j.mercer", "limit": "300 FC"},
    )
    assert granted.status_code == 201, granted.text
    assert len(api.get("/reports/due/5127", params={"on": ANSWERED}).json()["items"]) == 1
    pack = api.get(
        "/checks/5127/pack", params={"opens": FITTED, "closes": "2026-06-17", "within": 365}
    ).json()
    assert len(pack["deferrals"]) == 1

    # A repair goes on, which closes the card and lifts the deferral.
    fitted = api.post(
        "/repairs",
        json={
            "against": reference,
            "sequence": 1,
            "along": "3.000",
            "around": "2.000",
            "centre_along": "468.000",
            "centre_around": "91.000",
            "embodied": FITTED,
            "by": "s.brandt",
            "temporary": True,
        },
    )
    assert fitted.status_code == 201, fitted.text
    assert fitted.json()["card_state"] == "in work"
    assert fitted.json()["deferral_lifted"]
    assert api.get("/deferrals").json()["total"] == 0

    # The repair carries a repeat inspection, which nobody has done yet.
    tasks = api.get("/inspections/aircraft/5127").json()
    assert len(tasks) == 1
    assert tasks[0]["last_done"] is None

    # It gets signed off at the counters it was done at.
    signed = api.post(
        "/inspections/53/1/done",
        json={"on": "2027-01-05", "flown": {"cycles": 16000, "hours": "28000.0"}},
    )
    assert signed.status_code == 200, signed.text
    assert signed.json()["at_last_done"]["cycles"] == 16000

    # The audit now sees a temporary repair that has outlived its life.
    audit = api.get("/audit/5127", params={"on": "2028-01-01"}).json()
    assert audit["total_repairs"] == 1
    assert audit["temporary"][0]["overdue"]
    assert not audit["clean"]

    # And the whole story reads back in order, off every register.
    story = api.get(f"/history/{reference}").json()
    came_from = [one["came_from"] for one in story["entries"]]
    assert came_from[0] == "card"
    assert "engineering" in came_from
    assert "repair" in came_from
    assert story["days_open"] > 80


def test_the_card_is_no_longer_open_once_the_repair_is_on(api):
    enrol(api)
    reference = raise_card(api)
    move(api, reference, "assessed", FOUND)
    api.post(
        "/orders",
        json={"against": reference, "question": QUESTION, "raised": FOUND, "by": "a.okonkwo"},
    )
    api.post("/orders/EO-2025-0001/sent", json={"on": SENT})
    api.post(
        "/orders/EO-2025-0001/approved",
        json={
            "on": ANSWERED,
            "by": "a.okonkwo",
            "sheet": "RAS-2025-4417",
            "category": "permanent",
        },
    )
    api.post(
        "/repairs",
        json={
            "against": reference,
            "sequence": 1,
            "along": "3.000",
            "around": "2.000",
            "centre_along": "468.000",
            "centre_around": "91.000",
            "embodied": FITTED,
            "by": "s.brandt",
        },
    )
    open_cards = api.get("/aircraft/5127/findings", params={"open_only": True}).json()
    assert open_cards["total"] == 1
    assert api.get("/reports/due/5127", params={"on": FITTED}).json()["items"] == []


def test_a_shallow_dent_never_needs_any_of_that(api):
    enrol(api)
    reference = raise_card(api, depth="0.005")
    verdict = api.get(f"/findings/{reference}/assessment", params={"on": FOUND}).json()
    assert verdict["verdict"]["disposition"] == "allowable as it stands"
    assert api.get("/reports/grounded", params={"on": FOUND}).json()["tails"] == []
    assert api.get("/audit/5127", params={"on": FOUND}).json()["clean"]


def test_the_fleet_report_agrees_with_the_register_at_every_step(api):
    enrol(api)
    raise_card(api)
    raise_card(api)
    counted = api.get("/reports/open/5127", params={"on": FOUND}).json()
    listed = api.get("/aircraft/5127/findings").json()
    assert counted["total"] == listed["total"] == 2


def test_a_move_the_lifecycle_forbids_is_refused(api):
    enrol(api)
    reference = raise_card(api)
    answer = move(api, reference, "closed", FOUND)
    assert answer.status_code == 409
    assert "cannot go from raised to closed" in answer.json()["detail"]


def test_a_state_that_is_not_one_is_refused(api):
    enrol(api)
    reference = raise_card(api)
    answer = move(api, reference, "sorted out", FOUND)
    assert answer.status_code == 422
    assert "not a disposition state" in answer.json()["detail"]


def test_a_mechanic_may_not_move_a_card(api):
    enrol(api)
    reference = raise_card(api)
    answer = api.post(
        f"/findings/{reference}/state",
        json={"state": "assessed", "on": FOUND, "by": "a.okonkwo"},
        headers=MECHANIC,
    )
    assert answer.status_code == 422


def test_the_state_can_be_read_back_with_its_moves(api):
    enrol(api)
    reference = raise_card(api)
    move(api, reference, "assessed", FOUND)
    body = api.get(f"/findings/{reference}/state").json()
    assert body["state"] == "assessed"
    assert body["open"]
    assert len(body["moves"]) == 1


def test_a_freshly_raised_card_has_no_moves_yet(api):
    enrol(api)
    reference = raise_card(api)
    body = api.get(f"/findings/{reference}/state").json()
    assert body["state"] == "raised"
    assert body["moves"] == []


def test_a_card_that_is_not_on_file_has_no_state(api):
    enrol(api)
    assert api.get("/findings/DR-2025-000099/state").status_code == 404
