from datetime import date

from fastapi.testclient import TestClient

import sample
from doublerbay.api.app import Deps, build_app
from doublerbay.damage.crack import Crack
from doublerbay.damage.kind import DamageKind, DamageOrigin
from doublerbay.fleet.catalogue import TypeCatalogue
from doublerbay.ids import FindingId
from doublerbay.manual.book import v320_book
from doublerbay.measure.angle import Angle
from doublerbay.store.base import build_engine, build_schema
from doublerbay.store.unit import UnitOfWork

DAY = date(2025, 3, 14)
AGAIN = "2025-09-01"
CHIEF = {"x-api-key": "chief-key-0001"}


def built():
    engine = build_engine()
    build_schema(engine)
    app = build_app(Deps(engine=engine, book=v320_book(), catalogue=TypeCatalogue(), today=DAY))
    with UnitOfWork(engine) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(sample.flying_log())
    return engine, TestClient(app, headers=CHIEF)


def crack(length="0.400"):
    return Crack(sample.inch(length), Angle(0))


def raise_crack(engine, sequence=1, length="0.400"):
    with UnitOfWork(engine) as unit:
        unit.findings.raise_finding(
            sample.card(
                sequence,
                DamageKind.CRACK,
                crack(length),
                at=sample.cabin_point(stringer=10),
                origin=DamageOrigin.FATIGUE,
            )
        )
    return str(FindingId(2025, sequence))


def raise_dent(engine, sequence=9):
    with UnitOfWork(engine) as unit:
        unit.findings.raise_finding(sample.card(sequence, at=sample.cabin_point(stringer=10)))
    return str(FindingId(2025, sequence))


def measure(api, reference, length="0.500", cycles=14800, on=AGAIN):
    return api.post(
        f"/findings/{reference}/revisions",
        json={
            "found_on": on,
            "at_usage": {"cycles": cycles, "hours": str(cycles * 2) + ".0"},
            "measurements": {
                "length": length,
                "bearing": "0.0",
                "through_thickness": False,
                "stop_drilled": False,
                "tips_found": 2,
            },
        },
    )


def test_one_reading_gives_no_steps():
    engine, api = built()
    body = api.get(f"/growth/{raise_crack(engine)}").json()
    assert body["readings"] == 1
    assert body["steps"] == []
    assert not body["has_grown"]


def test_two_readings_give_a_step():
    engine, api = built()
    reference = raise_crack(engine)
    measure(api, reference)
    body = api.get(f"/growth/{reference}").json()
    assert body["readings"] == 2
    assert body["has_grown"]
    assert body["total"] == "0.100"


def test_the_step_carries_its_rate():
    engine, api = built()
    reference = raise_crack(engine)
    measure(api, reference)
    step = api.get(f"/growth/{reference}").json()["steps"][0]
    assert step["measurable"]
    assert step["rate"] is not None
    assert step["cycles"] > 0


def test_a_short_step_has_no_rate():
    engine, api = built()
    reference = raise_crack(engine)
    measure(api, reference, cycles=13910)
    body = api.get(f"/growth/{reference}").json()
    assert not body["steps"][0]["measurable"]
    assert body["worst_rate"] is None


def test_two_readings_are_not_a_trend():
    engine, api = built()
    reference = raise_crack(engine)
    measure(api, reference)
    assert not api.get(f"/growth/{reference}").json()["is_a_trend"]


def test_three_readings_are_a_trend():
    engine, api = built()
    reference = raise_crack(engine)
    measure(api, reference, "0.450", 14300, "2025-06-01")
    measure(api, reference, "0.500", 14800, AGAIN)
    assert api.get(f"/growth/{reference}").json()["is_a_trend"]


def test_a_slow_crack_is_not_flagged_as_fast():
    engine, api = built()
    reference = raise_crack(engine)
    measure(api, reference, "0.401", 20000)
    assert not api.get(f"/growth/{reference}").json()["fast"]


def test_a_fast_crack_is_flagged():
    engine, api = built()
    reference = raise_crack(engine)
    measure(api, reference, "0.900", 14800)
    assert api.get(f"/growth/{reference}").json()["fast"]


def test_a_dent_is_not_tracked_for_growth():
    engine, api = built()
    assert api.get(f"/growth/{raise_dent(engine)}").status_code == 422


def test_a_card_that_is_not_on_file_is_refused():
    engine, api = built()
    assert api.get("/growth/DR-2025-000099").status_code == 404


def test_the_fleet_list_counts_what_is_tracked():
    engine, api = built()
    raise_crack(engine, 1)
    raise_dent(engine, 2)
    body = api.get("/growth", params={"on": "2025-09-01"}).json()
    assert body["tracked"] == 1
    assert body["fast"] == []


def test_a_fast_crack_lands_on_the_fleet_list():
    engine, api = built()
    reference = raise_crack(engine, 1)
    measure(api, reference, "0.900", 14800)
    body = api.get("/growth", params={"on": "2025-09-01"}).json()
    assert len(body["fast"]) == 1
    assert body["fast"][0]["reference"] == "DR-2025-000001"


def test_the_fleet_list_puts_the_fastest_first():
    engine, api = built()
    slow = raise_crack(engine, 1)
    fast = raise_crack(engine, 2)
    measure(api, slow, "0.700", 14800)
    measure(api, fast, "1.400", 14800)
    body = api.get("/growth", params={"on": "2025-09-01"}).json()
    assert [one["reference"] for one in body["fast"]] == [fast, slow]


def test_the_fleet_list_needs_a_day():
    engine, api = built()
    assert api.get("/growth").status_code == 422


def test_a_tail_lists_its_tracked_cracks():
    engine, api = built()
    raise_crack(engine, 1)
    raise_dent(engine, 2)
    body = api.get("/growth/aircraft/5127").json()
    assert len(body) == 1


def test_a_tail_with_no_cracks_comes_back_empty():
    engine, api = built()
    raise_dent(engine, 2)
    assert api.get("/growth/aircraft/5127").json() == []


def test_a_tail_that_is_not_on_the_register_is_not_found():
    engine, api = built()
    assert api.get("/growth/aircraft/9999").status_code == 404


def test_a_tail_list_puts_the_fastest_first():
    engine, api = built()
    slow = raise_crack(engine, 1)
    fast = raise_crack(engine, 2)
    measure(api, slow, "0.500", 14800)
    measure(api, fast, "1.400", 14800)
    body = api.get("/growth/aircraft/5127").json()
    assert [one["reference"] for one in body] == [fast, slow]


def test_a_reader_may_pull_the_growth():
    engine, api = built()
    answer = api.get(f"/growth/{raise_crack(engine)}", headers={"x-api-key": "reader-key-0001"})
    assert answer.status_code == 200
