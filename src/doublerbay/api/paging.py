"""Cutting a long list into pages, the same way on every route that needs one.

Four routes return a list that grows without bound. A tail with twenty years
of cards has four figures of them, and a client that asks for all of them gets
a response nobody renders and a query nobody wants to run twice.

Offset and limit rather than a cursor. A cursor is the better answer when rows
are being inserted under the reader, and on this service they are not: a
damage card is raised once a week per aeroplane, and the page a planner is
looking at does not move under them. An offset is also the only paging a
person can drive from a terminal without keeping state.

The page says how many there are in total. A list that only says "here are
fifty" leaves the caller counting pages by asking for them, and the count is
the number the desk actually wanted half the time anyway.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Generic, TypeVar

from pydantic import BaseModel

from doublerbay.errors import BadReading

Held = TypeVar("Held")

DEFAULT_LIMIT = 50
HIGHEST_LIMIT = 500


@dataclass(frozen=True, slots=True)
class Window:
    """How much of a list a caller asked for."""

    offset: int = 0
    limit: int = DEFAULT_LIMIT

    def __post_init__(self) -> None:
        if self.offset < 0:
            raise BadReading("a page starts at nothing or later, not before it")
        if self.limit < 1:
            raise BadReading("a page holds at least one row")
        if self.limit > HIGHEST_LIMIT:
            raise BadReading(f"a page holds at most {HIGHEST_LIMIT} rows")

    @property
    def stop(self) -> int:
        return self.offset + self.limit

    def cut(self, held: tuple[Held, ...]) -> tuple[Held, ...]:
        return held[self.offset : self.stop]

    def __str__(self) -> str:
        return f"rows {self.offset} to {self.stop}"


def window(offset: int | None = None, limit: int | None = None) -> Window:
    """Build a window from what arrived, filling in what did not."""
    return Window(
        offset=offset if offset is not None else 0,
        limit=limit if limit is not None else DEFAULT_LIMIT,
    )


class Page(BaseModel, Generic[Held]):
    """One page of a list, and enough to ask for the next."""

    total: int
    offset: int
    limit: int
    rows: list[Held]

    @property
    def has_more(self) -> bool:
        return self.offset + len(self.rows) < self.total

    @property
    def next_offset(self) -> int | None:
        return self.offset + len(self.rows) if self.has_more else None


def paged(held: tuple[Held, ...], asked: Window, shape) -> dict:
    """Cut a list to a window and shape each row, keeping the total honest.

    The total is the length before the cut. A total counted after it would be
    the page size, which reads as a fleet with fifty cards on it however many
    it really has.
    """
    return {
        "total": len(held),
        "offset": asked.offset,
        "limit": asked.limit,
        "rows": [shape(one) for one in asked.cut(held)],
    }
