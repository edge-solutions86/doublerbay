from datetime import date

import sample
from conftest import client_for, enrolled, new_engine
from doublerbay.disposition.state import DispositionRecord, DispositionState
from doublerbay.ids import FindingId
from doublerbay.store.unit import UnitOfWork

DAY = date(2025, 3, 14)
FITTED = "2025-04-02"
CHIEF = {"x-api-key": "chief-key-0001"}


def built():
    """One engine and a chief engineer's client, off the shared fixtures."""
    engine = new_engine()
    enrolled(engine)
    return engine, client_for(engine)


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


def fit(api, against, sequence=1, temporary=True, centre=("468.000", "91.000")):
    return api.post(
        "/repairs",
        json={
            "against": against,
            "sequence": sequence,
            "along": "2.000",
            "around": "2.000",
            "centre_along": centre[0],
            "centre_around": centre[1],
            "embodied": FITTED,
            "by": "s.brandt",
            "temporary": temporary,
        },
    )


def test_a_tail_with_no_repairs_carries_no_tasks():
    engine, api = built()
    assert api.get("/inspections/aircraft/5127").json() == []


def test_a_temporary_repair_puts_a_task_on_the_tail():
    engine, api = built()
    fit(api, assessed(engine))
    body = api.get("/inspections/aircraft/5127").json()
    assert len(body) == 1
    assert body[0]["against"] == "RP-53-0001"
    assert body[0]["one_off"]


def test_a_task_carries_its_threshold_as_written():
    engine, api = built()
    fit(api, assessed(engine))
    body = api.get("/inspections/aircraft/5127").json()
    assert "MO" in body[0]["threshold"]
    assert body[0]["repeat"] is None


def test_a_new_task_has_never_been_done():
    engine, api = built()
    fit(api, assessed(engine))
    body = api.get("/inspections/aircraft/5127").json()
    assert body[0]["last_done"] is None
    assert len(api.get("/inspections/aircraft/5127/never-done").json()) == 1


def test_a_task_says_when_it_next_falls_due():
    engine, api = built()
    fit(api, assessed(engine))
    due = api.get("/inspections/aircraft/5127").json()[0]["due"]
    assert due["on"] is not None
    assert due["written"]


def test_one_task_can_be_read_back():
    engine, api = built()
    fit(api, assessed(engine))
    assert api.get("/inspections/53/1").json()["part"].startswith("53")


def test_a_repair_carrying_no_task_is_not_found():
    engine, api = built()
    assert api.get("/inspections/53/1").status_code == 404


def test_a_tail_that_is_not_on_the_register_is_not_found():
    engine, api = built()
    assert api.get("/inspections/aircraft/9999").status_code == 404


def test_nothing_is_due_the_day_the_repair_goes_on():
    engine, api = built()
    fit(api, assessed(engine))
    assert api.get("/inspections/aircraft/5127/due", params={"on": FITTED}).json() == []


def test_a_task_past_its_threshold_is_due():
    engine, api = built()
    fit(api, assessed(engine))
    body = api.get("/inspections/aircraft/5127/due", params={"on": "2030-01-01"}).json()
    assert len(body) == 1


def test_the_due_route_needs_a_day():
    engine, api = built()
    assert api.get("/inspections/aircraft/5127/due").status_code == 422


def test_a_task_can_be_signed_off():
    engine, api = built()
    fit(api, assessed(engine))
    answer = api.post(
        "/inspections/53/1/done",
        json={"on": "2026-01-05", "flown": {"cycles": 15000, "hours": "26000.0"}},
    )
    assert answer.status_code == 200, answer.text
    assert answer.json()["last_done"] == "2026-01-05"
    assert answer.json()["at_last_done"]["cycles"] == 15000


def test_a_signed_off_one_off_has_no_next_due():
    engine, api = built()
    fit(api, assessed(engine))
    answer = api.post(
        "/inspections/53/1/done",
        json={"on": "2026-01-05", "flown": {"cycles": 15000, "hours": "26000.0"}},
    )
    assert answer.json()["due"] is None


def test_a_signed_off_task_drops_off_the_never_done_list():
    engine, api = built()
    fit(api, assessed(engine))
    api.post(
        "/inspections/53/1/done",
        json={"on": "2026-01-05", "flown": {"cycles": 15000, "hours": "26000.0"}},
    )
    assert api.get("/inspections/aircraft/5127/never-done").json() == []


def test_signing_off_before_the_repair_went_on_is_refused():
    engine, api = built()
    fit(api, assessed(engine))
    answer = api.post(
        "/inspections/53/1/done",
        json={"on": "2024-01-01", "flown": {"cycles": 15000, "hours": "26000.0"}},
    )
    assert answer.status_code == 422


def test_signing_off_a_task_that_is_not_there_is_not_found():
    engine, api = built()
    answer = api.post(
        "/inspections/53/9/done",
        json={"on": "2026-01-05", "flown": {"cycles": 15000, "hours": "26000.0"}},
    )
    assert answer.status_code == 404


def test_a_replacement_limit_is_only_signed_off_once():
    engine, api = built()
    fit(api, assessed(engine))
    body = {"on": "2026-01-05", "flown": {"cycles": 15000, "hours": "26000.0"}}
    api.post("/inspections/53/1/done", json=body)
    assert api.post("/inspections/53/1/done", json=body).status_code == 422


def test_a_mechanic_may_not_sign_off_an_inspection():
    engine, api = built()
    fit(api, assessed(engine))
    answer = api.post(
        "/inspections/53/1/done",
        json={"on": "2026-01-05", "flown": {"cycles": 15000, "hours": "26000.0"}},
        headers={"x-api-key": "mechanic-key-0001"},
    )
    assert answer.status_code == 422


def test_a_reader_may_look_at_the_tasks():
    engine, api = built()
    answer = api.get("/inspections/aircraft/5127", headers={"x-api-key": "reader-key-0001"})
    assert answer.status_code == 200


def test_removing_the_repair_takes_the_task_off_the_service():
    engine, api = built()
    fit(api, assessed(engine))
    api.post("/repairs/53/1/remove", json={"on": "2026-01-05"})
    assert api.get("/inspections/aircraft/5127").json() == []
    assert api.get("/inspections/53/1").status_code == 404
