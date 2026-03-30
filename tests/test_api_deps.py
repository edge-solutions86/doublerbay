import pathlib
from datetime import date

import pytest

from doublerbay.api.app import Deps
from doublerbay.api.deps import (
    day_or,
    deps,
    order_from,
    reference_from,
    repair_from,
    serial_from,
)
from doublerbay.disposition.engineering import OrderId
from doublerbay.errors import BadReading
from doublerbay.fleet.catalogue import TypeCatalogue
from doublerbay.ids import FindingId, RepairId, Serial
from doublerbay.manual.book import v320_book
from doublerbay.store.base import build_engine

DAY = date(2025, 3, 14)


class _Request:
    """Enough of a request for the dependency, without standing a server up."""

    def __init__(self, held):
        self.app = _App(held)


class _App:
    def __init__(self, held):
        self.state = _State(held)


class _State:
    def __init__(self, held):
        self.deps = held


def built(today=DAY):
    return Deps(engine=build_engine(), book=v320_book(), catalogue=TypeCatalogue(), today=today)


def test_the_dependency_comes_off_the_application():
    held = built()
    assert deps(_Request(held)) is held


def test_a_report_number_parses():
    assert reference_from("DR-2025-000041") == FindingId(2025, 41)


def test_something_that_is_not_a_report_number_is_refused():
    with pytest.raises(BadReading, match="not a damage report number"):
        reference_from("nonsense")


def test_a_repair_number_parses():
    assert repair_from("RP-53-0001") == RepairId(53, 1)


def test_something_that_is_not_a_repair_number_is_refused():
    with pytest.raises(BadReading):
        repair_from("nonsense")


def test_an_order_number_parses():
    assert order_from("EO-2025-0001") == OrderId(2025, 1)


def test_something_that_is_not_an_order_number_is_refused():
    with pytest.raises(BadReading):
        order_from("nonsense")


def test_a_serial_parses():
    assert serial_from("5127") == Serial("5127")


def test_a_day_that_is_given_is_the_one_used():
    assert day_or(built(), date(2026, 1, 1)) == date(2026, 1, 1)


def test_a_day_left_off_takes_the_service_s_own():
    assert day_or(built(), None) == DAY


def test_a_service_with_no_pinned_day_falls_back_to_today():
    assert day_or(built(today=None), None) == date.today()


def test_nothing_here_imports_a_route():
    """The whole point of the split, asserted rather than trusted."""
    import doublerbay.api.deps as module

    source = module.__doc__ or ""
    assert "route" in source
    written = pathlib.Path(module.__file__).read_text(encoding="utf-8")
    imports = [line for line in written.splitlines() if line.startswith("from doublerbay")]
    assert imports
    assert all("doublerbay.api." not in line for line in imports)
