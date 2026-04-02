from datetime import date
from pathlib import Path

import pytest

from doublerbay.config import (
    DEFAULT_HOST,
    DEFAULT_PORT,
    DEFAULT_RECENT_DAYS,
    Settings,
    a_day,
    described,
    from_environment,
    named,
    read,
    required,
    whole_number,
)
from doublerbay.errors import BadReading

MINIMUM = {
    "DOUBLERBAY_DATABASE": "sqlite:///desk.db",
    "DOUBLERBAY_KEYS": "/etc/doublerbay/keys.json",
}


def env(**extra):
    held = dict(MINIMUM)
    held.update(extra)
    return held


def test_a_setting_is_read_off_a_prefixed_variable():
    assert named("database") == "DOUBLERBAY_DATABASE"
    assert named("recent_days") == "DOUBLERBAY_RECENT_DAYS"


def test_a_setting_that_is_not_there_reads_as_nothing():
    assert read("host", {}) is None


def test_a_blank_setting_reads_as_nothing():
    assert read("host", {"DOUBLERBAY_HOST": "   "}) is None


def test_a_setting_is_trimmed():
    assert read("host", {"DOUBLERBAY_HOST": "  0.0.0.0  "}) == "0.0.0.0"


def test_a_required_setting_that_is_missing_is_refused_by_name():
    with pytest.raises(BadReading, match="DOUBLERBAY_DATABASE is not set"):
        required("database", {})


def test_a_required_setting_that_is_blank_is_refused():
    with pytest.raises(BadReading):
        required("database", {"DOUBLERBAY_DATABASE": ""})


def test_a_required_setting_that_is_there_comes_back():
    assert required("database", MINIMUM) == "sqlite:///desk.db"


def test_a_count_falls_back_when_it_is_not_set():
    assert whole_number("port", 8000, {}) == 8000


def test_a_count_is_read_when_it_is_set():
    assert whole_number("port", 8000, {"DOUBLERBAY_PORT": "9001"}) == 9001


def test_a_count_that_is_not_a_number_is_refused():
    with pytest.raises(BadReading, match="not a whole number"):
        whole_number("port", 8000, {"DOUBLERBAY_PORT": "soon"})


def test_a_day_that_is_not_set_reads_as_nothing():
    assert a_day("today", {}) is None


def test_a_day_that_is_set_is_read():
    assert a_day("today", {"DOUBLERBAY_TODAY": "2025-03-14"}) == date(2025, 3, 14)


def test_a_day_that_is_not_a_day_is_refused():
    with pytest.raises(BadReading, match="not a date"):
        a_day("today", {"DOUBLERBAY_TODAY": "14/03/2025"})


def test_the_minimum_environment_builds_settings():
    settings = from_environment(env())
    assert settings.database == "sqlite:///desk.db"
    assert settings.keys == Path("/etc/doublerbay/keys.json")


def test_the_defaults_are_used_when_nothing_else_is_set():
    settings = from_environment(env())
    assert settings.host == DEFAULT_HOST
    assert settings.port == DEFAULT_PORT
    assert settings.recent_days == DEFAULT_RECENT_DAYS
    assert not settings.has_a_pinned_day


def test_a_database_that_is_not_set_stops_the_service_starting():
    with pytest.raises(BadReading, match="DOUBLERBAY_DATABASE"):
        from_environment({"DOUBLERBAY_KEYS": "/etc/keys.json"})


def test_a_key_file_that_is_not_set_stops_the_service_starting():
    with pytest.raises(BadReading, match="DOUBLERBAY_KEYS"):
        from_environment({"DOUBLERBAY_DATABASE": "sqlite:///desk.db"})


def test_the_host_can_be_overridden():
    assert from_environment(env(DOUBLERBAY_HOST="0.0.0.0")).host == "0.0.0.0"


def test_the_port_can_be_overridden():
    assert from_environment(env(DOUBLERBAY_PORT="9001")).port == 9001


def test_a_pinned_day_reaches_the_settings():
    settings = from_environment(env(DOUBLERBAY_TODAY="2025-03-14"))
    assert settings.has_a_pinned_day
    assert settings.today == date(2025, 3, 14)


def test_a_port_below_the_range_is_refused():
    with pytest.raises(BadReading, match="not a port"):
        from_environment(env(DOUBLERBAY_PORT="0"))


def test_a_port_above_the_range_is_refused():
    with pytest.raises(BadReading, match="not a port"):
        from_environment(env(DOUBLERBAY_PORT="70000"))


def test_a_recent_window_of_nothing_is_refused():
    with pytest.raises(BadReading, match="at least a day"):
        from_environment(env(DOUBLERBAY_RECENT_DAYS="0"))


def test_an_in_memory_database_says_so():
    assert from_environment(env(DOUBLERBAY_DATABASE="sqlite://:memory:")).is_in_memory


def test_a_database_on_disk_says_so():
    assert not from_environment(env()).is_in_memory


def test_the_settings_read_back_as_one_line():
    settings = from_environment(env())
    assert str(settings) == "doublerbay on 127.0.0.1:8000, database on disk"


def test_every_variable_the_service_reads_can_be_listed():
    every = described()
    assert "DOUBLERBAY_DATABASE" in every
    assert "DOUBLERBAY_TODAY" in every
    assert len(every) == 6


def test_settings_can_be_built_directly():
    settings = Settings(database="sqlite:///desk.db", keys=Path("keys.json"))
    assert settings.port == DEFAULT_PORT
