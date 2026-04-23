from datetime import date

import pytest
from fastapi.testclient import TestClient

import sample
from doublerbay.api.app import Deps, build_app
from doublerbay.api.paging import (
    DEFAULT_LIMIT,
    HIGHEST_LIMIT,
    Page,
    Window,
    paged,
    window,
)
from doublerbay.errors import BadReading
from doublerbay.fleet.catalogue import TypeCatalogue
from doublerbay.manual.book import v320_book
from doublerbay.store.base import build_engine, build_schema
from doublerbay.store.unit import UnitOfWork

DAY = date(2025, 3, 14)
CHIEF = {"x-api-key": "chief-key-0001"}
LETTERS = tuple("abcdefghij")


def built():
    engine = build_engine()
    build_schema(engine)
    app = build_app(Deps(engine=engine, book=v320_book(), catalogue=TypeCatalogue(), today=DAY))
    with UnitOfWork(engine) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(sample.flying_log())
    return engine, TestClient(app, headers=CHIEF)


def cards(engine, how_many):
    with UnitOfWork(engine) as unit:
        for step in range(1, how_many + 1):
            unit.findings.raise_finding(sample.card(step))


def test_a_window_defaults_to_the_first_page():
    asked = window()
    assert asked.offset == 0
    assert asked.limit == DEFAULT_LIMIT


def test_a_window_takes_what_it_is_given():
    asked = window(10, 5)
    assert asked.offset == 10
    assert asked.stop == 15


def test_a_window_reads_back_as_the_rows_it_covers():
    assert str(Window(10, 5)) == "rows 10 to 15"


def test_an_offset_before_the_start_is_refused():
    with pytest.raises(BadReading, match="not before it"):
        Window(-1, 5)


def test_a_page_of_nothing_is_refused():
    with pytest.raises(BadReading, match="at least one row"):
        Window(0, 0)


def test_a_page_past_the_ceiling_is_refused():
    with pytest.raises(BadReading, match=f"at most {HIGHEST_LIMIT}"):
        Window(0, HIGHEST_LIMIT + 1)


def test_a_page_at_the_ceiling_is_allowed():
    assert Window(0, HIGHEST_LIMIT).limit == HIGHEST_LIMIT


def test_a_window_cuts_the_rows_it_covers():
    assert Window(2, 3).cut(LETTERS) == ("c", "d", "e")


def test_a_window_past_the_end_cuts_nothing():
    assert Window(50, 5).cut(LETTERS) == ()


def test_a_window_over_the_end_cuts_what_is_left():
    assert Window(8, 5).cut(LETTERS) == ("i", "j")


def test_a_page_counts_the_whole_list_not_the_page():
    made = paged(LETTERS, Window(0, 3), str)
    assert made["total"] == 10
    assert len(made["rows"]) == 3


def test_a_page_shapes_every_row():
    made = paged((1, 2, 3), Window(0, 3), lambda one: one * 2)
    assert made["rows"] == [2, 4, 6]


def test_a_page_says_there_is_more():
    made = Page[str](**paged(LETTERS, Window(0, 3), str))
    assert made.has_more
    assert made.next_offset == 3


def test_the_last_page_says_there_is_no_more():
    made = Page[str](**paged(LETTERS, Window(8, 5), str))
    assert not made.has_more
    assert made.next_offset is None


def test_an_empty_list_has_no_more():
    made = Page[str](**paged((), Window(0, 5), str))
    assert not made.has_more
    assert made.total == 0


def test_the_findings_route_pages():
    engine, api = built()
    cards(engine, 5)
    body = api.get("/aircraft/5127/findings", params={"limit": 2}).json()
    assert body["total"] == 5
    assert len(body["rows"]) == 2
    assert body["limit"] == 2


def test_the_findings_route_takes_an_offset():
    engine, api = built()
    cards(engine, 5)
    body = api.get("/aircraft/5127/findings", params={"offset": 4, "limit": 2}).json()
    assert len(body["rows"]) == 1
    assert body["offset"] == 4


def test_the_findings_route_defaults_to_the_whole_short_list():
    engine, api = built()
    cards(engine, 5)
    body = api.get("/aircraft/5127/findings").json()
    assert len(body["rows"]) == 5


def test_a_limit_of_nothing_is_refused():
    engine, api = built()
    answer = api.get("/aircraft/5127/findings", params={"limit": 0})
    assert answer.status_code == 422
    assert "at least one row" in answer.json()["detail"]


def test_a_limit_past_the_ceiling_is_refused():
    engine, api = built()
    answer = api.get("/aircraft/5127/findings", params={"limit": HIGHEST_LIMIT + 1})
    assert answer.status_code == 422


def test_an_offset_before_the_start_is_refused_over_the_wire():
    engine, api = built()
    assert api.get("/aircraft/5127/findings", params={"offset": -1}).status_code == 422


def test_a_tail_that_is_not_on_the_register_is_not_found():
    engine, api = built()
    assert api.get("/aircraft/9999/findings").status_code == 404


def test_the_deferral_list_pages():
    engine, api = built()
    body = api.get("/deferrals", params={"limit": 2}).json()
    assert body["total"] == 0
    assert body["rows"] == []


def test_the_order_list_pages():
    engine, api = built()
    body = api.get("/orders", params={"limit": 2}).json()
    assert body["limit"] == 2


def test_the_repair_list_pages():
    engine, api = built()
    body = api.get("/repairs/aircraft/5127", params={"limit": 2}).json()
    assert body["total"] == 0
    assert body["offset"] == 0
