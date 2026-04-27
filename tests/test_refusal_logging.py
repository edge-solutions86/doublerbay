import json
import logging
from datetime import date

import pytest
from fastapi.testclient import TestClient

import sample
from doublerbay.api.app import Deps, _role_on, build_app
from doublerbay.fleet.catalogue import TypeCatalogue
from doublerbay.logging import NAME, NEVER, configure
from doublerbay.manual.book import v320_book
from doublerbay.store.base import build_engine, build_schema
from doublerbay.store.unit import UnitOfWork

DAY = date(2025, 3, 14)
CHIEF = {"x-api-key": "chief-key-0001"}
MECHANIC = {"x-api-key": "mechanic-key-0001"}


class Collected(logging.Handler):
    def __init__(self):
        super().__init__()
        self.lines: list[str] = []

    def emit(self, record: logging.LogRecord) -> None:
        self.lines.append(record.getMessage())


@pytest.fixture
def collected():
    held = configure()
    handler = Collected()
    held.addHandler(handler)
    yield handler
    held.removeHandler(handler)


def built():
    engine = build_engine()
    build_schema(engine)
    app = build_app(Deps(engine=engine, book=v320_book(), catalogue=TypeCatalogue(), today=DAY))
    with UnitOfWork(engine) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(sample.flying_log())
    return engine, TestClient(app, headers=CHIEF)


def last(collected):
    return json.loads(collected.lines[-1])


def test_a_clean_read_writes_nothing(collected):
    engine, api = built()
    api.get("/aircraft/5127")
    assert collected.lines == []


def test_a_clean_write_writes_nothing(collected):
    engine, api = built()
    api.post(
        "/aircraft/5127/readings",
        json={"taken": "2025-06-01", "usage": {"cycles": 14000, "hours": "24500.0"}},
    )
    assert collected.lines == []


def test_a_lookup_miss_is_logged(collected):
    engine, api = built()
    api.get("/aircraft/9999")
    assert last(collected)["event"] == "refused"
    assert last(collected)["kind"] == "NotOnFile"


def test_the_logged_path_says_what_was_asked_for(collected):
    engine, api = built()
    api.get("/aircraft/9999")
    assert last(collected)["reference"] == "/aircraft/9999"


def test_the_reason_is_logged(collected):
    engine, api = built()
    api.get("/aircraft/9999")
    assert "not on the register" in last(collected)["detail"]


def test_a_bad_reading_is_logged_with_its_kind(collected):
    engine, api = built()
    api.post(
        "/aircraft",
        json={
            "serial": "5240",
            "registration": "not-a-tail",
            "series": "200",
            "line_number": 1140,
            "delivered": "2016-04-12",
        },
    )
    assert last(collected)["kind"] == "BadReading"


def test_a_role_that_may_not_do_something_is_logged_by_role(collected):
    engine, api = built()
    api.post(
        "/aircraft",
        json={
            "serial": "5240",
            "registration": "N908QA",
            "series": "200",
            "line_number": 1140,
            "delivered": "2016-04-12",
        },
        headers=MECHANIC,
    )
    assert last(collected)["role"] == "mechanic"


def test_the_key_itself_is_never_logged(collected):
    engine, api = built()
    api.get("/aircraft/9999", headers=MECHANIC)
    assert "mechanic-key-0001" not in collected.lines[-1]


def test_no_forbidden_field_reaches_the_line(collected):
    engine, api = built()
    api.get("/aircraft/9999")
    assert not (set(last(collected)) & NEVER)


def test_a_request_with_no_key_logs_no_role(collected):
    engine, api = built()
    api.get("/aircraft/9999", headers={"x-api-key": ""})
    assert "role" not in last(collected)


def test_a_key_that_is_not_on_the_ring_logs_no_role(collected):
    engine, api = built()
    api.get("/aircraft/9999", headers={"x-api-key": "not-a-real-key"})
    assert "role" not in last(collected)


def test_two_refusals_write_two_lines(collected):
    engine, api = built()
    api.get("/aircraft/9999")
    api.get("/aircraft/8888")
    assert len(collected.lines) == 2


def test_the_logger_is_the_one_this_package_owns(collected):
    engine, api = built()
    api.get("/aircraft/9999")
    assert logging.getLogger(NAME).handlers


def test_the_role_helper_reads_a_known_key():
    engine, api = built()
    request = _FakeRequest(api.app, {"x-api-key": "chief-key-0001"})
    assert _role_on(request) == "chief engineer"


def test_the_role_helper_says_nothing_for_an_unknown_key():
    engine, api = built()
    assert _role_on(_FakeRequest(api.app, {"x-api-key": "nope"})) is None


def test_the_role_helper_says_nothing_when_no_key_is_sent():
    engine, api = built()
    assert _role_on(_FakeRequest(api.app, {})) is None


class _FakeRequest:
    """Enough of a request for the role helper, without a live connection."""

    def __init__(self, app, headers):
        self.app = app
        self.headers = headers
