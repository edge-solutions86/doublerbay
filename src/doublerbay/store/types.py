"""Column types that store what the domain means rather than what a float would.

Everything measured in this package is an integer count of thousandths of an
inch, and it stays that way in the database. A column of REAL holding 0.040
comes back as 0.04000000000000000083 on some builds and compares unequal to the
limit it is being tested against, which is the one failure mode this whole
design exists to avoid.

The identifiers go in as the text people read, because a damage report number
in a database that reads DR-2025-004182 can be grepped out of a log, matched
against a paper card and pasted into an email. A surrogate integer saves four
bytes and costs an hour every time somebody has to trace one.
"""

from __future__ import annotations

from typing import Any

from sqlalchemy import Dialect, Integer, String, TypeDecorator

from doublerbay.ids import FindingId, PanelId, Registration, RepairId, Serial
from doublerbay.measure.length import Length
from doublerbay.measure.usage import Cycles, Hours


class MilsColumn(TypeDecorator[Length]):
    """A length, stored as a whole number of thousandths of an inch."""

    impl = Integer
    cache_ok = True

    def process_bind_param(self, value: Length | None, dialect: Dialect) -> int | None:
        return None if value is None else value.mils

    def process_result_value(self, value: int | None, dialect: Dialect) -> Length | None:
        return None if value is None else Length(value)


class CyclesColumn(TypeDecorator[Cycles]):
    """A count of pressurisations."""

    impl = Integer
    cache_ok = True

    def process_bind_param(self, value: Cycles | None, dialect: Dialect) -> int | None:
        return None if value is None else value.count

    def process_result_value(self, value: int | None, dialect: Dialect) -> Cycles | None:
        return None if value is None else Cycles(value)


class HoursColumn(TypeDecorator[Hours]):
    """Time under load, stored as whole minutes because the log book is."""

    impl = Integer
    cache_ok = True

    def process_bind_param(self, value: Hours | None, dialect: Dialect) -> int | None:
        return None if value is None else value.minutes

    def process_result_value(self, value: int | None, dialect: Dialect) -> Hours | None:
        return None if value is None else Hours(value)


class _TextId(TypeDecorator[Any]):
    """An identifier stored the way it is printed, so it can be read in a log."""

    impl = String
    cache_ok = True
    default_length = 32

    def __init__(self, length: int | None = None) -> None:
        """Take a length so a generated migration can hand one back.

        Alembic renders a column as its type called with the arguments it was
        built with, so a type that will not accept its own length cannot be
        put in a migration file.
        """
        super().__init__(length or self.default_length)

    def parse(self, text: str) -> Any:  # pragma: no cover - overridden
        raise NotImplementedError

    def process_bind_param(self, value: Any, dialect: Dialect) -> str | None:
        return None if value is None else str(value)

    def process_result_value(self, value: str | None, dialect: Dialect) -> Any:
        return None if value is None else self.parse(value)


class SerialColumn(_TextId):
    """A manufacturer serial number, stored without the MSN prefix it prints with."""

    cache_ok = True
    default_length = 8

    def process_bind_param(self, value: Serial | None, dialect: Dialect) -> str | None:
        return None if value is None else value.number

    def parse(self, text: str) -> Serial:
        return Serial(text)


class RegistrationColumn(_TextId):
    cache_ok = True
    default_length = 8

    def parse(self, text: str) -> Registration:
        return Registration(text)


class FindingIdColumn(_TextId):
    cache_ok = True
    default_length = 16

    def parse(self, text: str) -> FindingId:
        return FindingId.parse(text)


class RepairIdColumn(_TextId):
    cache_ok = True
    default_length = 16

    def parse(self, text: str) -> RepairId:
        return RepairId.parse(text)


class PanelIdColumn(_TextId):
    cache_ok = True
    default_length = 16

    def parse(self, text: str) -> PanelId:
        return PanelId.parse(text)
