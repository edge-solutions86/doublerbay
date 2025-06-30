"""The whole book on the shelf, and reading a limit out of it as of a date and a tail.

Three things have to line up before a limit is the right limit. The chapter has
to be the one the structure belongs to, the revision has to be the one that was
in force on the day, and the row has to apply to this aeroplane. Every one of
those is a place a desk can be wrong without noticing, and the third is the
quiet one: a row written for serials 5001 through 5240 looks like an answer on
a tail that is 5301, and nothing about it says otherwise.

So the book takes the day and the aeroplane and does all three at once. There
is no way to get a row out of it without saying which tail and when.
"""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from datetime import date

from doublerbay.damage.kind import DamageKind
from doublerbay.errors import BadReading, NotOnFile
from doublerbay.fleet.aircraft import Aircraft
from doublerbay.grid.ata import Chapter, Reference
from doublerbay.manual.cleanup import CleanupRules, standard_rules
from doublerbay.manual.document import Manual, Page, Revision
from doublerbay.manual.interaction import DEFAULT_RULES, InteractionRules
from doublerbay.manual.table import LimitRow, LimitTable
from doublerbay.structure.build import Region
from doublerbay.structure.panel import PanelKind

TableBuilder = Callable[[int], LimitTable]


@dataclass(frozen=True, slots=True)
class Holding:
    """One chapter as the shelf holds it: its history, its pages and its table."""

    manual: Manual
    pages: tuple[Page, ...]
    build_table: TableBuilder

    @property
    def chapter(self) -> Chapter:
        return self.manual.chapter

    def table_at(self, revision: int) -> LimitTable:
        return self.build_table(revision)

    def pages_at(self, revision: int) -> tuple[Page, ...]:
        return tuple(page for page in self.pages if page.is_live_at(revision))

    def __str__(self) -> str:
        return f"chapter {self.chapter}, {len(self.pages)} pages, {self.manual.latest}"


class Book:
    """Every chapter the desk holds, read as of a date and a tail."""

    def __init__(
        self,
        cleanup: CleanupRules | None = None,
        interaction: InteractionRules = DEFAULT_RULES,
    ) -> None:
        self._holdings: dict[Chapter, Holding] = {}
        self.cleanup = cleanup if cleanup is not None else standard_rules()
        self.interaction = interaction

    def shelve(self, manual: Manual, pages: tuple[Page, ...], build_table: TableBuilder) -> None:
        if manual.chapter in self._holdings:
            raise BadReading(f"chapter {manual.chapter} is already on the shelf")
        for page in pages:
            if page.reference.chapter is not manual.chapter:
                raise BadReading(f"{page.reference} is not a page of chapter {manual.chapter}")
        self._holdings[manual.chapter] = Holding(manual, pages, build_table)

    def holding(self, chapter: Chapter) -> Holding:
        try:
            return self._holdings[chapter]
        except KeyError:
            raise NotOnFile(f"chapter {chapter} is not on the shelf") from None

    def chapters(self) -> frozenset[Chapter]:
        return frozenset(self._holdings)

    def revision_on(self, chapter: Chapter, day: date) -> Revision:
        return self.holding(chapter).manual.in_force_on(day)

    def table_on(self, chapter: Chapter, day: date) -> LimitTable:
        revision = self.revision_on(chapter, day)
        return self.holding(chapter).table_at(revision.number)

    def pages_on(self, chapter: Chapter, day: date) -> tuple[Page, ...]:
        revision = self.revision_on(chapter, day)
        return self.holding(chapter).pages_at(revision.number)

    def page(self, reference: Reference, day: date) -> Page:
        for page in self.pages_on(reference.chapter, day):
            if page.reference == reference:
                return page
        raise NotOnFile(f"{reference} is not in the book on {day.isoformat()}")

    def limits_for(
        self,
        chapter: Chapter,
        day: date,
        aircraft: Aircraft,
        damage: DamageKind,
        panel: PanelKind,
        region: Region,
    ) -> LimitRow:
        """The row that answers, for this chapter on this day for this tail."""
        table = self.table_on(chapter, day).effective_for(aircraft)
        return table.lookup(damage, panel, region)

    def __len__(self) -> int:
        return len(self._holdings)

    def __str__(self) -> str:
        return f"{len(self._holdings)} chapters on the shelf"


def v320_book() -> Book:
    """The V320 book, with both skin chapters on the shelf."""
    from doublerbay.v320 import manual53, manual57

    book = Book()
    book.shelve(manual53.manual(), manual53.PAGES, manual53.allowable_damage)
    book.shelve(manual57.manual(), manual57.PAGES, manual57.allowable_damage)
    return book
