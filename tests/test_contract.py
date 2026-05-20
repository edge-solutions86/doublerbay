import json

from click.testing import CliRunner

from conftest import client_for, new_engine
from doublerbay.api.contract import (
    UNGUARDED,
    WRITING,
    Endpoint,
    as_document,
    endpoints,
    tags,
    undocumented,
    unguarded_writes,
    writes,
)
from doublerbay.cli.main import cli


def app():
    return client_for(new_engine()).app


def test_the_surface_lists_its_routes():
    assert len(endpoints(app())) > 40


def test_every_route_carries_a_path_and_a_method():
    for one in endpoints(app()):
        assert one.path.startswith("/")
        assert one.method


def test_the_routes_come_back_in_path_order():
    paths = [one.path for one in endpoints(app())]
    assert paths == sorted(paths)


def test_a_post_is_a_write():
    assert Endpoint("/x", "POST", "x", "x", True, ()).writes


def test_a_get_is_not_a_write():
    assert not Endpoint("/x", "GET", "x", "x", True, ()).writes


def test_the_writing_methods_are_the_ones_that_change_something():
    assert {"POST", "PUT", "PATCH", "DELETE"} == WRITING


def test_the_surface_has_writes_on_it():
    assert len(writes(app())) > 10


def test_no_write_on_this_service_is_unguarded():
    open_writes = unguarded_writes(app())
    assert open_writes == (), f"unguarded: {[str(one) for one in open_writes]}"


def test_every_route_is_documented():
    assert undocumented(app()) == ()


def test_the_health_route_is_deliberately_open():
    assert "/health" in UNGUARDED


def test_an_open_route_reads_back_as_open():
    assert str(Endpoint("/health", "GET", "health", "x", False, ())) == "GET /health (open)"


def test_a_guarded_route_reads_back_as_guarded():
    assert "(guarded)" in str(Endpoint("/x", "POST", "x", "x", True, ()))


def test_a_route_with_no_summary_is_undocumented():
    assert not Endpoint("/x", "GET", "x", "   ", True, ()).is_documented


def test_a_write_on_the_open_list_needs_no_guard():
    assert not Endpoint("/health", "POST", "x", "x", False, ()).needs_a_guard


def test_a_write_off_the_list_needs_a_guard():
    assert Endpoint("/deferrals", "POST", "x", "x", False, ()).needs_a_guard


def test_the_surface_groups_its_routes_under_tags():
    every = tags(app())
    assert "findings" in every
    assert "reports" in every
    assert len(every) > 8


def test_the_contract_is_json_that_parses():
    assert json.loads(as_document(app()))["info"]["title"] == "doublerbay"


def test_the_contract_carries_every_path():
    document = json.loads(as_document(app()))
    assert "/deferrals" in document["paths"]
    assert "/reports/due" in document["paths"]


def test_the_contract_is_stable_between_two_builds():
    assert as_document(app()) == as_document(app())


def test_the_routes_command_lists_them():
    answer = CliRunner().invoke(cli, ["routes"], obj={})
    assert answer.exit_code == 0, answer.output
    assert "/deferrals" in answer.output
    assert "guarded" in answer.output


def test_the_routes_command_can_show_only_writes():
    answer = CliRunner().invoke(cli, ["routes", "--writes-only"], obj={})
    assert "POST" in answer.output
    assert "GET " not in answer.output


def test_the_routes_command_reports_no_unguarded_writes():
    answer = CliRunner().invoke(cli, ["routes"], obj={})
    assert "nothing is guarding" not in answer.output


def test_the_contract_command_prints_the_document():
    answer = CliRunner().invoke(cli, ["contract"], obj={})
    assert json.loads(answer.output)["openapi"].startswith("3.")


def test_the_contract_command_writes_a_file(tmp_path):
    path = tmp_path / "openapi.json"
    answer = CliRunner().invoke(cli, ["contract", "--to", str(path)], obj={})
    assert answer.exit_code == 0
    assert "wrote" in answer.output
    assert json.loads(path.read_text())["info"]["title"] == "doublerbay"
