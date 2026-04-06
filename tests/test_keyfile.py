import json

import pytest

from doublerbay.api.keyfile import (
    missing_roles,
    ring_from_file,
    ring_from_text,
    role_named,
    roles_on,
)
from doublerbay.api.security import Action, KeyRing, Role, check
from doublerbay.errors import BadReading, NotOnFile

FOUR = [
    {"key": "reader-key-9001", "holder": "the front desk", "role": "reader"},
    {"key": "mechanic-key-9002", "holder": "s.brandt", "role": "mechanic"},
    {"key": "engineer-key-9003", "holder": "a.okonkwo", "role": "engineer"},
    {"key": "chief-key-9004", "holder": "j.mercer", "role": "chief engineer"},
]


def written(tmp_path, entries=None, name="keys.json"):
    path = tmp_path / name
    path.write_text(json.dumps(FOUR if entries is None else entries))
    return path


def test_a_role_is_read_off_its_written_name():
    assert role_named("chief engineer") is Role.CHIEF
    assert role_named("reader") is Role.READER


def test_a_role_is_also_read_off_its_short_name():
    assert role_named("CHIEF") is Role.CHIEF
    assert role_named(" mechanic ") is Role.MECHANIC


def test_a_role_that_is_not_one_names_the_ones_that_are():
    with pytest.raises(BadReading, match="chief engineer"):
        role_named("wizard")


def test_a_file_of_four_keys_loads():
    ring = ring_from_text(json.dumps(FOUR))
    assert len(ring) == 4


def test_a_loaded_key_authorises_what_its_role_allows():
    ring = ring_from_text(json.dumps(FOUR))
    holder = check(ring, "chief-key-9004", Action.DEFER)
    assert holder.holder == "j.mercer"


def test_a_loaded_key_refuses_what_its_role_does_not_allow():
    ring = ring_from_text(json.dumps(FOUR))
    with pytest.raises(Exception, match="may not grant a deferral"):
        check(ring, "mechanic-key-9002", Action.DEFER)


def test_a_file_that_is_not_json_is_refused():
    with pytest.raises(BadReading, match="not readable json"):
        ring_from_text("{not json")


def test_a_file_that_is_not_a_list_is_refused():
    with pytest.raises(BadReading, match="a list of keys"):
        ring_from_text(json.dumps({"key": "x"}))


def test_a_file_with_no_keys_is_refused():
    with pytest.raises(BadReading, match="would refuse every request"):
        ring_from_text("[]")


def test_an_entry_that_is_not_an_object_names_its_position():
    with pytest.raises(BadReading, match="entry 2 is a str"):
        ring_from_text(json.dumps([FOUR[0], "chief-key-9004"]))


def test_an_entry_missing_a_field_names_the_field():
    entry = {"key": "chief-key-9004", "role": "chief engineer"}
    with pytest.raises(BadReading, match="entry 1 is missing holder"):
        ring_from_text(json.dumps([entry]))


def test_an_entry_missing_two_fields_names_both():
    with pytest.raises(BadReading, match="missing holder, role"):
        ring_from_text(json.dumps([{"key": "chief-key-9004"}]))


def test_a_key_that_is_too_short_is_refused():
    entry = {"key": "abc", "holder": "j.mercer", "role": "chief engineer"}
    with pytest.raises(BadReading, match="at least eight characters"):
        ring_from_text(json.dumps([entry]))


def test_a_key_with_no_holder_is_refused():
    entry = {"key": "chief-key-9004", "holder": "   ", "role": "chief engineer"}
    with pytest.raises(BadReading, match="belongs to somebody"):
        ring_from_text(json.dumps([entry]))


def test_the_same_key_twice_is_refused():
    with pytest.raises(BadReading, match="already been issued"):
        ring_from_text(json.dumps([FOUR[0], FOUR[0]]))


def test_a_file_is_read_off_disk(tmp_path):
    assert len(ring_from_file(written(tmp_path))) == 4


def test_a_file_that_is_not_there_is_refused(tmp_path):
    with pytest.raises(NotOnFile, match="not a key file"):
        ring_from_file(tmp_path / "missing.json")


def test_a_directory_is_not_a_key_file(tmp_path):
    with pytest.raises(NotOnFile):
        ring_from_file(tmp_path)


def test_a_full_ring_is_missing_no_roles(tmp_path):
    ring = ring_from_file(written(tmp_path))
    assert missing_roles(ring) == ()
    assert len(roles_on(ring)) == 4


def test_a_ring_with_no_chief_says_which_role_is_missing():
    ring = ring_from_text(json.dumps(FOUR[:3]))
    assert missing_roles(ring) == (Role.CHIEF,)


def test_a_ring_with_only_readers_is_missing_three_roles():
    ring = ring_from_text(json.dumps(FOUR[:1]))
    assert len(missing_roles(ring)) == 3


def test_an_empty_ring_has_no_roles_on_it():
    assert roles_on(KeyRing()) == ()
