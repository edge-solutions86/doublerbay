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
LATER = "2026-11-02"
CHIEF = {"x-api-key": "chief-key-0001"}


def built():
    engine = build_engine()
    build_schema(engine)
    app = build_app(Deps(engine=engine, book=v320_book(), catalogue=TypeCatalogue(), today=DAY))
    with UnitOfWork(engine) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(sample.flying_log())
    return engine, TestClient(app, headers=CHIEF)


def second_tail(engine):
    with UnitOfWork(engine) as unit:
        unit.aircraft.add(sample.aircraft("5240"))
        unit.aircraft.open_log(sample.flying_log("5240"))


def assessed(engine, sequence=1, serial="5127"):
    reference = FindingId(2025, sequence)
    with UnitOfWork(engine) as unit:
        unit.findings.raise_finding(
            sample.card(sequence, serial=serial, at=sample.cabin_point(stringer=10))
        )
        unit.dispositions.save(
            DispositionRecord.raised(reference).moved_to(
                DispositionState.ASSESSED, DAY, "a.okonkwo"
            )
        )
    return str(reference)


def fit(api, against, sequence=1, centre=("468.000", "91.000"), temporary=False, on=FITTED):
    return api.post(
        "/repairs",
        json={
            "against": against,
            "sequence": sequence,
            "along": "2.000",
            "around": "2.000",
            "centre_along": centre[0],
            "centre_around": centre[1],
            "embodied": on,
            "by": "s.brandt",
            "temporary": temporary,
        },
    )


def test_a_tail_with_no_repairs_is_clean():
    engine, api = built()
    body = api.get("/audit/5127", params={"on": FITTED}).json()
    assert body["total_repairs"] == 0
    assert body["clean"]
    assert body["panels"] == []


def test_one_repair_lands_on_its_panel():
    engine, api = built()
    fit(api, assessed(engine))
    body = api.get("/audit/5127", params={"on": FITTED}).json()
    assert body["total_repairs"] == 1
    assert body["panels"][0]["repairs"] == 1
    assert not body["panels"][0]["crowded"]


def test_three_repairs_on_one_panel_read_as_crowded():
    engine, api = built()
    for step in range(3):
        fit(
            api,
            assessed(engine, step + 1),
            step + 1,
            (f"{468 + step * 200}.000", "91.000"),
        )
    body = api.get("/audit/5127", params={"on": FITTED}).json()
    assert body["panels"][0]["crowded"]
    assert not body["clean"]


def test_a_temporary_repair_is_listed_with_its_age():
    engine, api = built()
    fit(api, assessed(engine), temporary=True)
    body = api.get("/audit/5127", params={"on": FITTED}).json()
    assert len(body["temporary"]) == 1
    assert body["temporary"][0]["months_fitted"] == "0.0"
    assert not body["temporary"][0]["overdue"]


def test_a_temporary_repair_past_its_life_is_overdue():
    engine, api = built()
    fit(api, assessed(engine), temporary=True)
    body = api.get("/audit/5127", params={"on": LATER}).json()
    assert body["temporary"][0]["overdue"]
    assert not body["clean"]


def test_two_repairs_too_close_come_back_as_a_clash():
    engine, api = built()
    fit(api, assessed(engine, 1), 1, ("468.000", "91.000"))
    fit(api, assessed(engine, 2), 2, ("469.000", "91.000"))
    body = api.get("/audit/5127", params={"on": FITTED}).json()
    assert body["clashing"] == [["RP-53-0001", "RP-53-0002"]]


def test_a_removed_repair_drops_off_the_audit():
    engine, api = built()
    fit(api, assessed(engine))
    api.post("/repairs/53/1/remove", json={"on": "2026-01-05"})
    body = api.get("/audit/5127", params={"on": LATER}).json()
    assert body["total_repairs"] == 0


def test_a_tail_that_is_not_on_the_register_is_not_found():
    engine, api = built()
    assert api.get("/audit/9999", params={"on": FITTED}).status_code == 404


def test_the_day_is_required():
    engine, api = built()
    assert api.get("/audit/5127").status_code == 422


def test_the_fleet_audit_covers_the_register():
    engine, api = built()
    second_tail(engine)
    body = api.get("/audit", params={"on": FITTED}).json()
    assert len(body["tails"]) == 2
    assert body["flagged"] == 0


def test_a_flagged_tail_sorts_to_the_top():
    engine, api = built()
    second_tail(engine)
    fit(api, assessed(engine, 1), 1, ("468.000", "91.000"))
    fit(api, assessed(engine, 2), 2, ("469.000", "91.000"))
    body = api.get("/audit", params={"on": FITTED}).json()
    assert body["tails"][0]["serial"] == "5127"
    assert body["tails"][0]["clashes"] == 1
    assert body["flagged"] == 1


def test_two_clean_tails_sort_by_serial():
    engine, api = built()
    second_tail(engine)
    body = api.get("/audit", params={"on": FITTED}).json()
    assert [one["serial"] for one in body["tails"]] == ["5127", "5240"]


def test_a_tail_not_yet_delivered_is_left_out():
    engine, api = built()
    body = api.get("/audit", params={"on": "2000-01-01"}).json()
    assert body["tails"] == []


def test_a_reader_may_pull_the_audit():
    engine, api = built()
    answer = api.get("/audit/5127", params={"on": FITTED}, headers={"x-api-key": "reader-key-0001"})
    assert answer.status_code == 200


def test_the_fleet_line_counts_the_overdue_temporaries():
    engine, api = built()
    fit(api, assessed(engine), temporary=True)
    body = api.get("/audit", params={"on": LATER}).json()
    assert body["tails"][0]["overdue_temporary"] == 1
    assert not body["tails"][0]["clean"]
