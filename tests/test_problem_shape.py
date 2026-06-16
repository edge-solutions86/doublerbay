"""The shape of a refusal, which is a promise to every client that reads one.

A client branches on something in this body. Whatever it branches on cannot
change once it has shipped, and the thing most likely to change is the class
name, because somebody will rename a class for tidiness one afternoon. So
there is a slug, it is fixed, and this file is where breaking it fails.
"""

import pytest
from pydantic import ValidationError

from conftest import client_for, new_engine
from doublerbay.api.app import (
    SLUG_FOR,
    UNKNOWN_SLUG,
    Problem,
    is_retryable,
    problem_of,
    slug_for,
    status_for,
)
from doublerbay.errors import BadReading, DoublerbayError, NotOnFile, OutOfSequence, Refused


def api():
    return client_for(new_engine())


def test_every_refusal_this_service_raises_has_a_slug():
    for kind in (BadReading, Refused, NotOnFile, OutOfSequence):
        assert slug_for(kind("x"))


def test_the_slugs_are_all_different():
    assert len(set(SLUG_FOR.values())) == len(SLUG_FOR)


def test_the_slugs_are_lower_case_and_hyphenated():
    for slug in SLUG_FOR.values():
        assert slug == slug.lower()
        assert " " not in slug


def test_a_refusal_nobody_mapped_still_gets_a_slug():
    class Unheard(DoublerbayError):
        pass

    assert slug_for(Unheard("x")) == UNKNOWN_SLUG


def test_a_lookup_miss_could_work_later():
    assert is_retryable(NotOnFile("x"))


def test_a_move_out_of_sequence_could_work_later():
    assert is_retryable(OutOfSequence("x"))


def test_a_bad_reading_will_never_work():
    assert not is_retryable(BadReading("x"))


def test_a_refusal_of_permission_will_never_work():
    assert not is_retryable(Refused("x", "y"))


def test_a_problem_carries_its_slug_and_its_class():
    problem = problem_of(NotOnFile("nothing here"))
    assert problem.type == "not-on-file"
    assert problem.kind == "NotOnFile"


def test_a_problem_carries_where_it_happened():
    assert problem_of(NotOnFile("x"), "/aircraft/9999").where == "/aircraft/9999"


def test_a_problem_with_no_path_says_nothing_about_where():
    assert problem_of(NotOnFile("x")).where == ""


def test_the_status_follows_the_kind():
    assert status_for(NotOnFile("x")) == 404
    assert status_for(BadReading("x")) == 422
    assert status_for(OutOfSequence("x")) == 409


def test_a_refusal_nobody_mapped_is_a_server_problem():
    class Unheard(DoublerbayError):
        pass

    assert status_for(Unheard("x")) == 500


def test_a_lookup_miss_over_the_wire_carries_the_slug():
    body = api().get("/aircraft/9999").json()
    assert body["type"] == "not-on-file"
    assert body["retryable"]


def test_a_lookup_miss_over_the_wire_names_the_path():
    assert api().get("/aircraft/9999").json()["where"] == "/aircraft/9999"


def test_a_bad_reading_over_the_wire_is_not_retryable():
    body = (
        api()
        .post(
            "/aircraft",
            json={
                "serial": "5240",
                "registration": "not-a-tail",
                "series": "200",
                "line_number": 1140,
                "delivered": "2016-04-12",
            },
        )
        .json()
    )
    assert body["type"] == "bad-reading"
    assert not body["retryable"]


def test_a_permission_refusal_carries_the_refused_slug():
    body = (
        api()
        .post(
            "/aircraft",
            json={
                "serial": "5240",
                "registration": "N908QA",
                "series": "200",
                "line_number": 1140,
                "delivered": "2016-04-12",
            },
            headers={"x-api-key": "mechanic-key-0001"},
        )
        .json()
    )
    assert body["type"] == "refused"


def test_every_field_a_client_reads_is_always_present():
    body = api().get("/aircraft/9999").json()
    assert set(body) == {"type", "kind", "detail", "where", "retryable"}


def test_a_problem_can_be_built_directly():
    made = Problem(type="x", kind="X", detail="y")
    assert made.where == ""
    assert not made.retryable


def test_a_problem_needs_a_type():
    with pytest.raises(ValidationError):
        Problem(kind="X", detail="y")
