"""What the service reads out of its environment, and what it refuses to guess.

Two settings have no safe default and this module says so rather than picking
one. The database url, because defaulting to an in memory database means a
service that starts, accepts writes, and loses them at the next restart with
nothing in the log. And the key file, because defaulting to the development
ring would put four known keys on a production service.

Everything else has a default that is right for a small operator, and every
default is a constant in this file rather than a literal buried in a call, so
the whole configuration surface is readable in one screen.

Values are read once at start up into a frozen object. A setting read again
later is a setting that can change under a request, and then two halves of one
answer are computed against different configuration.
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from datetime import date
from pathlib import Path

from doublerbay.errors import BadReading

PREFIX = "DOUBLERBAY_"

# Defaults that are right for one desk with a few dozen aeroplanes.
DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 8000
DEFAULT_RECENT_DAYS = 30
LOWEST_PORT = 1
HIGHEST_PORT = 65535


def named(setting: str) -> str:
    """The environment variable one setting is read from."""
    return f"{PREFIX}{setting.upper()}"


def read(setting: str, environment: dict[str, str] | None = None) -> str | None:
    """One setting off the environment, with blanks treated as absent.

    An empty variable is how a deployment tool says "I have not set this", and
    reading it as an empty string produces a database url of nothing, which
    fails four layers down with an unrecognisable message.
    """
    held = (environment if environment is not None else dict(os.environ)).get(named(setting))
    if held is None:
        return None
    stripped = held.strip()
    return stripped or None


def required(setting: str, environment: dict[str, str] | None = None) -> str:
    """A setting with no safe default, refused by name when it is missing."""
    held = read(setting, environment)
    if held is None:
        raise BadReading(f"{named(setting)} is not set and there is no safe default for it")
    return held


def whole_number(setting: str, fallback: int, environment: dict[str, str] | None = None) -> int:
    """A count off the environment, refusing anything that is not one."""
    held = read(setting, environment)
    if held is None:
        return fallback
    try:
        return int(held)
    except ValueError:
        raise BadReading(f"{named(setting)} is {held!r}, which is not a whole number") from None


def a_day(setting: str, environment: dict[str, str] | None = None) -> date | None:
    """A pinned day off the environment, for a service under test."""
    held = read(setting, environment)
    if held is None:
        return None
    try:
        return date.fromisoformat(held)
    except ValueError:
        raise BadReading(f"{named(setting)} is {held!r}, which is not a date") from None


@dataclass(frozen=True, slots=True)
class Settings:
    """Everything the service was told at start up."""

    database: str
    keys: Path
    host: str = DEFAULT_HOST
    port: int = DEFAULT_PORT
    recent_days: int = DEFAULT_RECENT_DAYS
    today: date | None = None

    def __post_init__(self) -> None:
        if not LOWEST_PORT <= self.port <= HIGHEST_PORT:
            raise BadReading(f"{self.port} is not a port this service can listen on")
        if self.recent_days < 1:
            raise BadReading("a recent window is at least a day")

    @property
    def is_in_memory(self) -> bool:
        return ":memory:" in self.database

    @property
    def has_a_pinned_day(self) -> bool:
        return self.today is not None

    def __str__(self) -> str:
        where = "in memory" if self.is_in_memory else "on disk"
        return f"doublerbay on {self.host}:{self.port}, database {where}"


def from_environment(environment: dict[str, str] | None = None) -> Settings:
    """Read the whole configuration once, refusing what it will not guess."""
    return Settings(
        database=required("database", environment),
        keys=Path(required("keys", environment)),
        host=read("host", environment) or DEFAULT_HOST,
        port=whole_number("port", DEFAULT_PORT, environment),
        recent_days=whole_number("recent_days", DEFAULT_RECENT_DAYS, environment),
        today=a_day("today", environment),
    )


def described() -> tuple[str, ...]:
    """Every variable this service reads, for the message a bad start up prints."""
    return tuple(named(one) for one in ("database", "keys", "host", "port", "recent_days", "today"))
