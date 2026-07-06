"""The gates, and the assertion that the workflow actually runs all of them.

This is the file that keeps continuous integration honest. Every other test in
the suite says something about an aeroplane. This one says that the checks
this repository claims to run are the checks a runner will actually run, which
is the claim everything else rests on.
"""

from pathlib import Path

import pytest
from click.testing import CliRunner

from doublerbay.cli.main import cli
from doublerbay.errors import BadReading
from doublerbay.tooling.gates import (
    GATES,
    Gate,
    failed,
    gates_named_in,
    named,
    names,
    workflow_path,
)

ROOT = Path(__file__).resolve().parents[1]


def workflow() -> str:
    return workflow_path(ROOT).read_text(encoding="utf-8")


def test_the_workflow_is_where_this_repository_says_it_is():
    assert workflow_path(ROOT).exists()


def test_the_workflow_runs_every_gate_this_repository_names():
    missing = set(names()) - gates_named_in(workflow())
    assert not missing, f"the workflow does not run: {sorted(missing)}"


def test_the_workflow_names_no_gate_this_repository_does_not_have():
    assert gates_named_in(workflow()) <= set(names())


def test_the_workflow_installs_the_development_extras():
    assert 'pip install -e ".[dev]"' in workflow()


def test_the_workflow_runs_on_more_than_one_python():
    text = workflow()
    assert '"3.11"' in text
    assert '"3.12"' in text


def test_the_workflow_runs_the_python_versions_this_project_supports():
    supported = (ROOT / "pyproject.toml").read_text(encoding="utf-8")
    assert 'requires-python = ">=3.11"' in supported
    assert '"3.11"' in workflow()


def test_the_workflow_checks_the_route_guards():
    assert "doublerbay routes" in workflow()


def test_the_workflow_keeps_the_contract():
    assert "doublerbay contract" in workflow()


def test_the_workflow_runs_the_migrations_both_ways():
    text = workflow()
    assert "alembic upgrade head" in text
    assert "alembic downgrade base" in text


def test_every_gate_has_a_command():
    for gate in GATES:
        assert gate.command


def test_every_gate_says_why_it_is_worth_the_seconds():
    for gate in GATES:
        assert len(gate.why) > 20


def test_the_gate_names_are_all_different():
    assert len(set(names())) == len(GATES)


def test_a_gate_reads_back_as_its_command():
    made = Gate("lint", ("ruff", "check", "."), "x" * 30)
    assert str(made) == "lint: ruff check ."


def test_a_gate_with_no_name_is_refused():
    with pytest.raises(BadReading, match="has a name"):
        Gate("  ", ("ruff",), "x" * 30)


def test_a_gate_with_no_command_is_refused():
    with pytest.raises(BadReading, match="no command to run"):
        Gate("lint", (), "x" * 30)


def test_a_gate_can_be_found_by_name():
    assert named("tests").command == ("pytest",)


def test_a_gate_that_is_not_one_names_the_ones_that_are():
    with pytest.raises(BadReading, match="lint, format"):
        named("vibes")


def test_nothing_failed_when_everything_passed():
    assert failed(tuple((one, 0) for one in GATES)) == ()


def test_a_failure_is_reported_by_gate():
    results = tuple((one, 1 if one.name == "types" else 0) for one in GATES)
    assert [one.name for one in failed(results)] == ["types"]


def test_the_command_lists_the_gates():
    answer = CliRunner().invoke(cli, ["gates"], obj={})
    assert answer.exit_code == 0, answer.output
    for name in names():
        assert name in answer.output


def test_the_command_prints_the_command_each_gate_runs():
    answer = CliRunner().invoke(cli, ["gates"], obj={})
    assert "ruff check ." in answer.output
    assert "alembic upgrade head" in answer.output


def test_the_command_prints_why_each_gate_exists():
    answer = CliRunner().invoke(cli, ["gates"], obj={})
    assert "deploy time" in answer.output
