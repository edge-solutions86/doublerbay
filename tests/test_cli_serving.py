import json
from datetime import date

import pytest
from click.testing import CliRunner
from fastapi.testclient import TestClient
from sqlalchemy.exc import OperationalError

from doublerbay.cli.main import cli
from doublerbay.cli.serving import application, settings_rows
from doublerbay.config import Settings
from doublerbay.errors import BadReading, NotOnFile
from doublerbay.store.base import build_schema

FOUR = [
    {"key": "reader-key-9001", "holder": "the front desk", "role": "reader"},
    {"key": "mechanic-key-9002", "holder": "s.brandt", "role": "mechanic"},
    {"key": "engineer-key-9003", "holder": "a.okonkwo", "role": "engineer"},
    {"key": "chief-key-9004", "holder": "j.mercer", "role": "chief engineer"},
]


def key_file(tmp_path, entries=None, name="keys.json"):
    path = tmp_path / name
    path.write_text(json.dumps(FOUR if entries is None else entries))
    return path


def settings(tmp_path, **rest):
    held = {
        "database": "sqlite+pysqlite:///:memory:",
        "keys": key_file(tmp_path),
    }
    held.update(rest)
    return Settings(**held)


def env(tmp_path, **extra):
    held = {
        "DOUBLERBAY_DATABASE": "sqlite+pysqlite:///:memory:",
        "DOUBLERBAY_KEYS": str(key_file(tmp_path)),
    }
    held.update(extra)
    return held


def run(command, tmp_path, monkeypatch, extra=None, args=()):
    for name, value in env(tmp_path, **(extra or {})).items():
        monkeypatch.setenv(name, value)
    return CliRunner().invoke(cli, [command, *args], obj={})


def test_the_application_builds_from_settings(tmp_path):
    api = TestClient(application(settings(tmp_path)))
    answer = api.get("/health")
    assert answer.status_code == 200
    assert answer.json()["service"] == "doublerbay"


def stocked(tmp_path, **rest):
    """A built application with its schema in place, the way a deployment runs.

    The application does not build its own schema. Migrations do that, and a
    service that quietly created tables at start up would hide a database that
    was never migrated.
    """
    built = application(settings(tmp_path, **rest))
    build_schema(built.state.deps.engine)
    return built


def test_the_built_application_uses_the_loaded_keys(tmp_path):
    api = TestClient(stocked(tmp_path))
    api.post(
        "/aircraft",
        json={
            "serial": "5127",
            "registration": "N412VD",
            "series": "200",
            "line_number": 1140,
            "delivered": "2016-04-12",
        },
        headers={"x-api-key": "chief-key-9004"},
    )
    assert api.get("/aircraft/5127").status_code == 200


def test_a_key_from_the_development_ring_no_longer_works(tmp_path):
    api = TestClient(stocked(tmp_path))
    answer = api.post(
        "/aircraft",
        json={
            "serial": "5127",
            "registration": "N412VD",
            "series": "200",
            "line_number": 1140,
            "delivered": "2016-04-12",
        },
        headers={"x-api-key": "chief-key-0001"},
    )
    assert answer.status_code == 404


def test_a_pinned_day_reaches_the_application(tmp_path):
    built = application(settings(tmp_path, today=date(2025, 3, 14)))
    assert built.state.deps.on() == date(2025, 3, 14)


def test_a_key_file_that_is_not_there_stops_the_build(tmp_path):
    with pytest.raises(NotOnFile):
        application(settings(tmp_path, keys=tmp_path / "missing.json"))


def test_the_settings_rows_name_what_was_found(tmp_path):
    rows = dict(settings_rows(settings(tmp_path)))
    assert rows["keys loaded"] == "4"
    assert rows["roles missing"] == "none"
    assert rows["stored"] == "in memory"


def test_the_settings_rows_name_a_missing_role(tmp_path):
    rows = dict(settings_rows(settings(tmp_path, keys=key_file(tmp_path, FOUR[:3], "three.json"))))
    assert "chief engineer" in rows["roles missing"]


def test_the_settings_rows_count_the_tables(tmp_path):
    rows = dict(settings_rows(settings(tmp_path)))
    assert int(rows["tables"]) > 5


def test_the_settings_command_prints_what_it_found(tmp_path, monkeypatch):
    answer = run("settings", tmp_path, monkeypatch)
    assert answer.exit_code == 0, answer.output
    assert "keys loaded" in answer.output
    assert "in memory" in answer.output


def test_the_settings_command_warns_about_a_missing_role(tmp_path, monkeypatch):
    path = key_file(tmp_path, FOUR[:3], "three.json")
    answer = run("settings", tmp_path, monkeypatch, {"DOUBLERBAY_KEYS": str(path)})
    assert "nothing on this ring can act as" in answer.output


def test_the_settings_command_refuses_a_missing_database(tmp_path, monkeypatch):
    monkeypatch.delenv("DOUBLERBAY_DATABASE", raising=False)
    monkeypatch.setenv("DOUBLERBAY_KEYS", str(key_file(tmp_path)))
    answer = CliRunner().invoke(cli, ["settings"], obj={})
    assert answer.exit_code != 0
    assert isinstance(answer.exception, BadReading)


def test_the_settings_command_takes_a_pinned_day(tmp_path, monkeypatch):
    answer = run("settings", tmp_path, monkeypatch, {"DOUBLERBAY_TODAY": "2025-03-14"})
    assert "2025-03-14" in answer.output


def test_the_variables_command_lists_every_setting(tmp_path, monkeypatch):
    answer = run("variables", tmp_path, monkeypatch)
    assert "DOUBLERBAY_DATABASE" in answer.output
    assert len(answer.output.strip().splitlines()) == 6


def test_the_serve_command_is_registered():
    answer = CliRunner().invoke(cli, ["--help"], obj={})
    assert "serve" in answer.output
    assert "settings" in answer.output


def test_the_application_does_not_build_its_own_schema(tmp_path):
    """A service started against an unmigrated database fails loudly.

    It does not come back as a tidy not found, which would read as an empty
    register and have somebody enrolling a fleet that is already on file.
    """
    api = TestClient(application(settings(tmp_path)))
    with pytest.raises(OperationalError, match="no such table"):
        api.get("/aircraft/5127", headers={"x-api-key": "reader-key-9001"})
