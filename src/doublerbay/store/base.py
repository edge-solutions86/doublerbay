"""The declarative floor, and the naming convention every constraint is built under.

Constraints get named explicitly because an unnamed one cannot be dropped in a
migration on every backend, and a schema you can only build forwards is a
schema nobody can roll back. The convention is the usual one and it is set here
once so no table has to remember it.

The session factory is a function rather than a module level object on purpose.
A module level engine is created at import time, which means importing anything
from this package opens a file or a socket, and that turns every unit test into
an integration test.
"""

from __future__ import annotations

from collections.abc import Iterator
from contextlib import contextmanager
from datetime import UTC, datetime

from sqlalchemy import Engine, MetaData, create_engine
from sqlalchemy.orm import DeclarativeBase, Mapped, Session, mapped_column, sessionmaker
from sqlalchemy.pool import StaticPool

NAMING = {
    "ix": "ix_%(column_0_label)s",
    "uq": "uq_%(table_name)s_%(column_0_name)s",
    "ck": "ck_%(table_name)s_%(constraint_name)s",
    "fk": "fk_%(table_name)s_%(column_0_name)s_%(referred_table_name)s",
    "pk": "pk_%(table_name)s",
}

IN_MEMORY = "sqlite+pysqlite:///:memory:"


class Base(DeclarativeBase):
    """Every table in the schema hangs off this."""

    metadata = MetaData(naming_convention=NAMING)


class Recorded:
    """When a row was written, which every table carries for the audit trail."""

    recorded_at: Mapped[datetime] = mapped_column(default=lambda: datetime.now(UTC))


def build_engine(url: str = IN_MEMORY, echo: bool = False) -> Engine:
    """Open an engine. Called explicitly so importing this package opens nothing.

    An in memory database lives inside its connection, so the default pool
    hands a second caller an empty one. Anything running the service in a
    thread, which every test client does, then finds no tables at all. One
    shared connection is the only thing that behaves like a database there.
    """
    if url == IN_MEMORY:
        return create_engine(
            url,
            echo=echo,
            future=True,
            poolclass=StaticPool,
            connect_args={"check_same_thread": False},
        )
    return create_engine(url, echo=echo, future=True)


def load_models() -> None:
    """Import the table modules so the metadata knows what it is describing.

    A table registers itself on the metadata when its module is imported and
    not before, so a caller that has only imported this module gets an empty
    schema built without a word of complaint. Every entry point that touches
    the metadata calls this first.
    """
    from doublerbay.store import tables, work  # noqa: F401


def build_schema(engine: Engine) -> None:
    """Create every table, for tests and for a first run against an empty file."""
    load_models()
    Base.metadata.create_all(engine)


def drop_schema(engine: Engine) -> None:
    load_models()
    Base.metadata.drop_all(engine)


def session_factory(engine: Engine) -> sessionmaker[Session]:
    return sessionmaker(bind=engine, expire_on_commit=False, future=True)


@contextmanager
def opened(engine: Engine) -> Iterator[Session]:
    """A session that commits on the way out and rolls back if anything raised."""
    session = session_factory(engine)()
    try:
        yield session
        session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()


def table_names() -> tuple[str, ...]:
    """Every table the schema defines, in the order they would be created."""
    load_models()
    return tuple(table.name for table in Base.metadata.sorted_tables)
