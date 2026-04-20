"""The migrations and the models have to describe the same schema.

The check is the one that matters and it is easy to skip: build the database
from the migrations, then ask alembic what would still need doing to make it
match the models. Anything it finds is a model change somebody made without
writing the migration, and it will not show up until a deployment.
"""

from pathlib import Path

import pytest
from alembic import command
from alembic.autogenerate import compare_metadata
from alembic.config import Config
from alembic.migration import MigrationContext
from sqlalchemy import inspect

from doublerbay.store.base import Base, build_engine

ROOT = Path(__file__).resolve().parents[1]


def alembic_config(connection):
    config = Config(str(ROOT / "alembic.ini"))
    config.set_main_option("script_location", str(ROOT / "migrations"))
    config.attributes["connection"] = connection
    return config


@pytest.fixture
def upgraded():
    engine = build_engine()
    with engine.begin() as connection:
        command.upgrade(alembic_config(connection), "head")
    return engine


def test_the_migrations_build_every_table_the_models_define(upgraded):
    built = set(inspect(upgraded).get_table_names())
    expected = set(Base.metadata.tables) | {"alembic_version"}
    assert expected <= built


def test_nothing_is_left_over_once_the_migrations_have_run(upgraded):
    with upgraded.connect() as connection:
        context = MigrationContext.configure(connection)
        differences = compare_metadata(context, Base.metadata)
    assert differences == []


def test_the_schema_comes_apart_again():
    engine = build_engine()
    with engine.begin() as connection:
        config = alembic_config(connection)
        command.upgrade(config, "head")
        command.downgrade(config, "base")
    left = set(inspect(engine).get_table_names())
    assert left <= {"alembic_version"}


def revisions_on_disk():
    """Every revision file, by the number it starts with.

    Derived rather than written down. An assertion that names the head has to
    be edited by every migration, and the one it catches is the one where
    somebody forgot to edit it.
    """
    here = ROOT / "migrations" / "versions"
    return sorted(one.name.split("_")[0] for one in here.glob("[0-9]*.py"))


def test_the_head_is_the_latest_revision_written(upgraded):
    with upgraded.connect() as connection:
        context = MigrationContext.configure(connection)
        assert context.get_current_revision() == revisions_on_disk()[-1]


def test_every_revision_file_is_numbered_in_sequence():
    every = revisions_on_disk()
    assert every == [f"{step:04d}" for step in range(1, len(every) + 1)]


def test_the_conditions_column_arrives_with_its_own_revision(upgraded):
    columns = {one["name"] for one in inspect(upgraded).get_columns("engineering_order")}
    assert "conditions" in columns


def test_every_constraint_carries_a_name_from_the_convention(upgraded):
    inspector = inspect(upgraded)
    for table in Base.metadata.tables:
        for key in inspector.get_foreign_keys(table):
            assert key["name"]
        for index in inspector.get_indexes(table):
            assert index["name"]


def test_the_report_indexes_are_all_built(upgraded):
    """The composite indexes the reports run against, by name."""
    inspector = inspect(upgraded)
    built = {
        index["name"] for table in Base.metadata.tables for index in inspector.get_indexes(table)
    }
    assert "ix_repair_fitted" in built
    assert "ix_deferral_live" in built
    assert "ix_corrosion_here_before" in built


def test_an_index_covers_the_columns_its_query_filters_on(upgraded):
    columns = {
        index["name"]: index["column_names"] for index in inspect(upgraded).get_indexes("repair")
    }
    assert columns["ix_repair_fitted"] == ["serial", "part", "removed"]


def test_the_corrosion_index_is_ordered_for_the_history_check(upgraded):
    columns = {
        index["name"]: index["column_names"]
        for index in inspect(upgraded).get_indexes("corrosion_entry")
    }
    assert columns["ix_corrosion_here_before"] == ["serial", "part", "found_on"]
