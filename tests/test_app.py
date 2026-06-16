from datetime import date

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from doublerbay.api.app import Deps, Problem, build_app, default_app, problem_of, status_for
from doublerbay.errors import (
    BadReading,
    DoublerbayError,
    NotOnFile,
    OutOfSequence,
    Refused,
)
from doublerbay.fleet.catalogue import TypeCatalogue
from doublerbay.manual.book import v320_book
from doublerbay.store.base import build_engine, build_schema

DAY = date(2025, 3, 14)


def engine():
    made = build_engine()
    build_schema(made)
    return made


def deps(today=DAY):
    return Deps(engine=engine(), book=v320_book(), catalogue=TypeCatalogue(), today=today)


def app_with_a_route(error):
    built = build_app(deps())

    @built.get("/boom")
    async def boom() -> None:
        raise error

    return built


def test_the_service_answers_that_it_is_up():
    client = TestClient(build_app(deps()))
    body = client.get("/health").json()
    assert body["service"] == "doublerbay"
    assert "53" in body["chapters"]
    assert "57" in body["chapters"]


def test_the_default_application_has_the_v320_book_on_the_shelf():
    built = default_app(engine())
    assert isinstance(built, FastAPI)
    assert len(built.state.deps.book) == 2


def test_a_bad_reading_comes_back_as_unprocessable():
    client = TestClient(app_with_a_route(BadReading("0.0405 in is finer than a thousandth")))
    answer = client.get("/boom")
    assert answer.status_code == 422
    assert answer.json()["kind"] == "BadReading"
    assert "finer than a thousandth" in answer.json()["detail"]


def test_a_lookup_miss_comes_back_as_not_found():
    client = TestClient(app_with_a_route(NotOnFile("MSN 5127 is not on the register")))
    answer = client.get("/boom")
    assert answer.status_code == 404
    assert answer.json()["kind"] == "NotOnFile"


def test_a_move_the_lifecycle_refuses_comes_back_as_a_conflict():
    client = TestClient(app_with_a_route(OutOfSequence("cannot go from raised to closed")))
    assert client.get("/boom").status_code == 409


def test_a_refusal_comes_back_as_unprocessable():
    client = TestClient(app_with_a_route(Refused("no page", "the book has run out")))
    answer = client.get("/boom")
    assert answer.status_code == 422
    assert answer.json()["detail"] == "no page: the book has run out"


def test_a_refusal_nobody_mapped_comes_back_as_a_server_error():
    class Odd(DoublerbayError):
        pass

    client = TestClient(
        app_with_a_route(Odd("something nobody expected")), raise_server_exceptions=False
    )
    assert client.get("/boom").status_code == 500


def test_the_status_table_covers_every_refusal_the_package_raises():
    assert status_for(BadReading("x")) == 422
    assert status_for(NotOnFile("x")) == 404
    assert status_for(OutOfSequence("x")) == 409
    assert status_for(Refused("x")) == 422


def test_every_failure_comes_back_in_the_same_shape():
    problem = problem_of(NotOnFile("nothing here"))
    assert isinstance(problem, Problem)
    assert set(problem.model_dump()) == {"type", "kind", "detail", "where", "retryable"}


def test_the_day_a_request_is_answered_as_of_can_be_pinned():
    assert deps().on() == DAY


def test_with_no_day_pinned_the_service_answers_as_of_today():
    assert deps(today=None).on() == date.today()


def test_the_dependencies_are_handed_in_rather_than_imported():
    built = build_app(deps())
    catalogue = built.state.deps.catalogue
    assert catalogue.series()
    assert all(catalogue.carries(one) for one in catalogue.series())


@pytest.mark.parametrize("path", ["/health"])
def test_the_documented_routes_are_reachable(path):
    client = TestClient(build_app(deps()))
    assert client.get(path).status_code == 200
