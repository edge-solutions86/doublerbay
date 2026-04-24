import json
import logging
from datetime import UTC, datetime

import pytest

from doublerbay.logging import (
    ALLOWED,
    NAME,
    NEVER,
    Line,
    as_json,
    configure,
    decided,
    logger,
    refused,
    scrubbed,
    write,
)

AT = datetime(2025, 3, 14, 6, 30, tzinfo=UTC)


def test_a_line_says_what_happened():
    assert Line("decided").event == "decided"


def test_a_line_with_no_event_is_refused():
    with pytest.raises(ValueError, match="says what happened"):
        Line("   ")


def test_a_line_drops_the_fields_that_carry_nothing():
    assert Line("decided").fields() == {"event": "decided"}


def test_a_line_keeps_the_fields_that_carry_something():
    held = Line("decided", reference="DR-2025-000001", role="engineer").fields()
    assert held == {"event": "decided", "reference": "DR-2025-000001", "role": "engineer"}


def test_a_line_reads_back_as_key_value_pairs():
    made = Line("refused", reference="DR-2025-000001", outcome="no")
    assert str(made) == "event=refused reference=DR-2025-000001 outcome=no"


def test_the_fields_come_out_in_a_fixed_order():
    made = Line("decided", detail="x", reference="DR-2025-000001")
    assert list(made.fields()) == ["event", "reference", "detail"]


def test_a_serial_is_never_written():
    assert "serial" in NEVER
    assert scrubbed({"event": "decided", "serial": "5127"}) == {"event": "decided"}


def test_a_key_is_never_written():
    assert scrubbed({"event": "refused", "key": "chief-key-0001"}) == {"event": "refused"}


def test_a_measurement_is_never_written():
    assert scrubbed({"event": "decided", "depth": "0.040"}) == {"event": "decided"}


def test_nothing_allowed_is_also_forbidden():
    assert not (ALLOWED & NEVER)


def test_an_unknown_field_is_dropped_too():
    assert scrubbed({"event": "decided", "something": "else"}) == {"event": "decided"}


def test_a_line_becomes_json_with_a_timestamp():
    written = json.loads(as_json(Line("decided", reference="DR-2025-000001"), AT))
    assert written["at"].startswith("2025-03-14T06:30")
    assert written["reference"] == "DR-2025-000001"


def test_the_json_carries_no_forbidden_field():
    written = json.loads(as_json(Line("decided", reference="DR-2025-000001"), AT))
    assert not (set(written) & NEVER)


def test_the_json_gets_a_timestamp_of_its_own_when_none_is_given():
    written = json.loads(as_json(Line("decided")))
    assert written["at"]


def test_a_refusal_line_carries_its_reason():
    made = refused("DR-2025-000001", "deferral", "a mechanic may not", role="mechanic")
    assert made.event == "refused"
    assert made.outcome == "no"
    assert "mechanic may not" in str(made)


def test_a_decision_line_carries_who_made_it():
    made = decided("DR-2025-000001", "deferral", "chief engineer")
    assert made.event == "decided"
    assert made.role == "chief engineer"
    assert made.outcome == "yes"


def test_the_logger_is_this_package_s_own():
    assert logger().name == NAME


def test_configuring_attaches_exactly_one_handler():
    held = configure()
    first = len(held.handlers)
    configure()
    assert len(held.handlers) == first == 1


def test_configuring_does_not_reach_the_root_logger():
    held = configure()
    assert not held.propagate


def test_the_level_can_be_set():
    assert configure(logging.WARNING).level == logging.WARNING
    configure(logging.INFO)


class Collected(logging.Handler):
    """A handler that keeps what it was given, for asserting on."""

    def __init__(self):
        super().__init__()
        self.lines: list[str] = []

    def emit(self, record: logging.LogRecord) -> None:
        self.lines.append(record.getMessage())


@pytest.fixture
def collected():
    """Attach a handler to this package's logger and take it off again.

    caplog attaches to root and this logger deliberately does not propagate
    there, so a test that used caplog would be testing nothing and passing.
    """
    held = configure()
    handler = Collected()
    held.addHandler(handler)
    yield handler
    held.removeHandler(handler)


def test_a_written_line_lands_on_the_logger(collected):
    write(decided("DR-2025-000001", "deferral", "chief engineer"), AT)
    assert "DR-2025-000001" in collected.lines[-1]


def test_a_written_line_is_json(collected):
    write(refused("DR-2025-000001", "deferral", "no key"), AT)
    assert json.loads(collected.lines[-1])["outcome"] == "no"


def test_a_written_line_carries_no_forbidden_field(collected):
    write(decided("DR-2025-000001", "deferral", "chief engineer"), AT)
    assert not (set(json.loads(collected.lines[-1])) & NEVER)


def test_two_writes_land_as_two_lines(collected):
    write(decided("DR-2025-000001", "deferral", "chief engineer"), AT)
    write(refused("DR-2025-000002", "deferral", "no key"), AT)
    assert len(collected.lines) == 2
