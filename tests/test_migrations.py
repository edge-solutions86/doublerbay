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


def test_the_head_is_the_latest_revision_written(upgraded):
    with upgraded.connect() as connection:
        context = MigrationContext.configure(connection)
        assert context.get_current_revision() == "0002"


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
