from datetime import date

import pytest
from fastapi.testclient import TestClient

import sample
from doublerbay.api.app import Deps, build_app
from doublerbay.api.security import Action, ApiKey, KeyRing, Role, check, open_ring
from doublerbay.errors import BadReading, NotOnFile, Refused
from doublerbay.fleet.catalogue import TypeCatalogue
from doublerbay.manual.book import v320_book
from doublerbay.store.base import build_engine, build_schema
from doublerbay.store.unit import UnitOfWork

DAY = date(2025, 3, 14)


def client(key=None):
    engine = build_engine()
    build_schema(engine)
    with UnitOfWork(engine) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(sample.flying_log())
    built = build_app(Deps(engine=engine, book=v320_book(), catalogue=TypeCatalogue(), today=DAY))
    headers = {"x-api-key": key} if key else {}
    return TestClient(built, headers=headers)


def card_body():
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
            "aft_of_frame": "10.000",
            "stringer": 10,
            "below_stringer": "4.000",
            "side": "L",
        },
        "measurements": {
            "depth": "0.010",
            "along": "6.000",
            "around": "4.000",
            "sharp_bottom": False,
            "cracked": False,
        },
    }


def test_a_reader_may_only_read():
    assert Role.READER.can(Action.READ)
    for action in (Action.RAISE_CARD, Action.DISPOSITION, Action.AMEND_FLEET):
        assert not Role.READER.can(action)


def test_a_mechanic_raises_cards_and_posts_readings_and_nothing_else():
    assert Role.MECHANIC.can(Action.RAISE_CARD)
    assert Role.MECHANIC.can(Action.POST_READING)
    assert not Role.MECHANIC.can(Action.DISPOSITION)
    assert not Role.MECHANIC.can(Action.DEFER)


def test_an_engineer_dispositions_but_does_not_grant_the_long_deferrals():
    assert Role.ENGINEER.can(Action.DISPOSITION)
    assert not Role.ENGINEER.can(Action.DEFER)
    assert not Role.ENGINEER.can(Action.AMEND_FLEET)


def test_the_chief_may_do_everything():
    for action in Action:
        assert Role.CHIEF.can(action)


def test_a_key_belongs_to_somebody_and_is_long_enough():
    with pytest.raises(BadReading, match="at least eight characters"):
        ApiKey("short", "somebody", Role.READER)
    with pytest.raises(BadReading, match="belongs to somebody"):
        ApiKey("longenoughkey", "  ", Role.READER)


def test_a_key_reads_back_who_is_behind_it():
    assert str(ApiKey("longenoughkey", "the duty engineer", Role.ENGINEER)) == (
        "the duty engineer (engineer)"
    )


def test_a_ring_will_not_issue_the_same_key_twice():
    ring = KeyRing()
    ring.issue(ApiKey("longenoughkey", "somebody", Role.READER))
    with pytest.raises(BadReading, match="already been issued"):
        ring.issue(ApiKey("longenoughkey", "somebody else", Role.CHIEF))


def test_a_revoked_key_stops_working():
    ring = open_ring()
    before = len(ring)
    ring.revoke("reader-key-0001")
    assert len(ring) == before - 1
    with pytest.raises(NotOnFile, match="not one this service accepts"):
        ring.holder_of("reader-key-0001")


def test_revoking_a_key_that_was_never_issued_is_refused():
    with pytest.raises(NotOnFile, match="never issued"):
        KeyRing().revoke("nothing")


def test_a_request_with_no_key_is_refused():
    with pytest.raises(Refused, match="every request says who it is from"):
        check(open_ring(), None, Action.READ)


def test_a_request_with_the_wrong_role_is_refused_with_the_reason():
    with pytest.raises(Refused, match="a reader may not raise a damage card"):
        check(open_ring(), "reader-key-0001", Action.RAISE_CARD)


def test_the_development_ring_carries_one_key_of_each_role():
    assert len(open_ring()) == len(Role)


def test_raising_a_card_with_no_key_comes_back_refused():
    api = client()
    answer = api.post("/findings", json=card_body())
    assert answer.status_code == 422
    assert "says who it is from" in answer.json()["detail"]


def test_raising_a_card_as_a_reader_comes_back_refused():
    api = client("reader-key-0001")
    answer = api.post("/findings", json=card_body())
    assert answer.status_code == 422
    assert "may not raise a damage card" in answer.json()["detail"]


def test_raising_a_card_as_a_mechanic_goes_through():
    api = client("mechanic-key-0001")
    assert api.post("/findings", json=card_body()).status_code == 201


def test_a_mechanic_cannot_supersede_a_card():
    api = client("mechanic-key-0001")
    first = api.post("/findings", json=card_body()).json()["reference"]
    second = api.post("/findings", json=card_body()).json()["reference"]
    answer = api.post(f"/findings/{first}/supersede", json={"by": second})
    assert answer.status_code == 422
    assert "may not disposition" in answer.json()["detail"]


def test_an_engineer_can_supersede_a_card():
    api = client("engineer-key-0001")
    first = api.post("/findings", json=card_body()).json()["reference"]
    second = api.post("/findings", json=card_body()).json()["reference"]
    assert api.post(f"/findings/{first}/supersede", json={"by": second}).status_code == 200


def test_only_the_chief_amends_the_register():
    body = {
        "serial": "5240",
        "registration": "N908QA",
        "series": "200",
        "line_number": 1200,
        "delivered": "2018-09-03",
    }
    assert client("engineer-key-0001").post("/aircraft", json=body).status_code == 422
    assert client("chief-key-0001").post("/aircraft", json=body).status_code == 201


def test_reading_needs_no_key_at_all():
    api = client()
    assert api.get("/aircraft/5127").status_code == 200
    assert api.get("/health").status_code == 200


def test_a_key_nobody_issued_is_refused():
    api = client("not-a-real-key")
    answer = api.post("/findings", json=card_body())
    assert answer.status_code == 404
