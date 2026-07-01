"""The fixtures the route tests were each building for themselves.

Ten test modules had the same eight lines at the top: build an engine, build
the schema, build the application with the book and the catalogue and a pinned
day, wrap it in a client with the chief engineer's key. Ten copies of that is
not a problem until the application grows a dependency, and then it is ten
edits and one of them gets missed.

The pinned day is the part worth keeping in one place. Every one of those
copies chose the same fourteenth of March, and a test that quietly picked a
different one would be a test whose answers nobody could compare against the
others.

Nothing here builds domain objects. Those live in sample.py, which is what a
test imports when it needs an aeroplane rather than a service.
"""

from __future__ import annotations

from datetime import date

import pytest
from fastapi.testclient import TestClient

import sample
from doublerbay.api.app import Deps, build_app
from doublerbay.disposition.state import DispositionRecord, DispositionState
from doublerbay.fleet.catalogue import TypeCatalogue
from doublerbay.ids import FindingId
from doublerbay.manual.book import v320_book
from doublerbay.store.base import build_engine, build_schema
from doublerbay.store.unit import UnitOfWork

# The day every test in this suite answers as of.
TODAY = date(2025, 3, 14)

KEYS = {
    "reader": {"x-api-key": "reader-key-0001"},
    "mechanic": {"x-api-key": "mechanic-key-0001"},
    "engineer": {"x-api-key": "engineer-key-0001"},
    "chief": {"x-api-key": "chief-key-0001"},
}


def new_engine():
    """An empty database with the schema on it."""
    made = build_engine()
    build_schema(made)
    return made


def enrolled(engine, serial: str = "5127") -> None:
    """One tail on the register with a log open, which most tests need."""
    with UnitOfWork(engine) as unit:
        unit.aircraft.add(sample.aircraft(serial))
        unit.aircraft.open_log(sample.flying_log(serial))


def deps_for(engine, today: date | None = TODAY) -> Deps:
    return Deps(engine=engine, book=v320_book(), catalogue=TypeCatalogue(), today=today)


def client_for(engine, role: str = "chief", today: date | None = TODAY) -> TestClient:
    """A client against one engine, acting as one role."""
    return TestClient(build_app(deps_for(engine, today)), headers=KEYS[role])


@pytest.fixture
def engine():
    """An empty database, for a test that wants to stock it itself."""
    return new_engine()


@pytest.fixture
def stocked():
    """A database with one tail on it, which is where most tests start."""
    made = new_engine()
    enrolled(made)
    return made


@pytest.fixture
def api(stocked):
    """A chief engineer's client against a database holding one tail."""
    return client_for(stocked)


@pytest.fixture
def reader(stocked):
    """A client that can only look, for testing what a role may not do."""
    return client_for(stocked, "reader")


def raise_card(engine, sequence: int = 1, serial: str = "5127", **rest):
    """Put one damage card on the register, without dispositioning it.

    Nineteen test modules were each writing their own version of this and its
    assessed sibling, with the same three lines and slightly different names.
    They are here so a change to how a card is raised is one edit.
    """
    with UnitOfWork(engine) as unit:
        unit.findings.raise_finding(sample.card(sequence, serial=serial, **rest))
        unit.dispositions.save(DispositionRecord.raised(FindingId(2025, sequence)))
    return FindingId(2025, sequence)


def assess_card(engine, reference: FindingId, on: date = TODAY, by: str = "a.okonkwo"):
    """Move a card to assessed, which is where every decision starts."""
    with UnitOfWork(engine) as unit:
        held = unit.dispositions.get(reference)
        unit.dispositions.save(held.moved_to(DispositionState.ASSESSED, on, by))
    return reference


def assessed_card(engine, sequence: int = 1, serial: str = "5127", **rest):
    """A card on file that has been looked up, which is what a deferral needs."""
    return assess_card(engine, raise_card(engine, sequence, serial, **rest))
