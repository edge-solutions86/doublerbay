from datetime import date

import pytest

from doublerbay.damage.kind import DamageKind
from doublerbay.errors import BadReading, NotOnFile
from doublerbay.fleet.aircraft import Aircraft
from doublerbay.fleet.model import Effectivity, Series
from doublerbay.grid.ata import Chapter, Reference
from doublerbay.ids import Registration, Serial
from doublerbay.manual.book import Book, v320_book
from doublerbay.manual.limits import LimitSet
from doublerbay.manual.table import LimitKey, LimitRow, LimitTable
from doublerbay.measure.length import Length
from doublerbay.structure.build import Region
from doublerbay.structure.panel import PanelKind
from doublerbay.v320 import manual53, manual57

DAY = date(2025, 3, 14)


def inch(text):
    return Length.of_inches(text)


def tail(serial="5127"):
    return Aircraft(
        serial=Serial(serial),
        registration=Registration("N412VD"),
        series=Series.STANDARD,
        line_number=1140,
        delivered=date(2016, 4, 12),
    )


def test_the_book_holds_both_skin_chapters():
    book = v320_book()
    assert len(book) == 2
    assert book.chapters() == frozenset({Chapter.FUSELAGE, Chapter.WINGS})
    assert str(book) == "2 chapters on the shelf"


def test_a_chapter_that_is_not_on_the_shelf_is_a_lookup_miss():
    with pytest.raises(NotOnFile, match="is not on the shelf"):
        v320_book().holding(Chapter.STABILISERS)


def test_a_chapter_shelved_twice_is_refused():
    book = v320_book()
    with pytest.raises(BadReading, match="is already on the shelf"):
        book.shelve(manual53.manual(), manual53.PAGES, manual53.allowable_damage)


def test_a_page_from_another_chapter_is_refused():
    book = Book()
    with pytest.raises(BadReading, match="is not a page of chapter 53"):
        book.shelve(manual53.manual(), manual57.PAGES, manual53.allowable_damage)


def test_the_revision_in_force_comes_off_the_day():
    book = v320_book()
    assert book.revision_on(Chapter.FUSELAGE, date(2024, 6, 1)).number == 11
    assert book.revision_on(Chapter.FUSELAGE, date(2025, 3, 14)).number == 12


def test_the_two_chapters_are_on_different_revisions_at_the_same_time():
    book = v320_book()
    assert book.revision_on(Chapter.FUSELAGE, DAY).number == 12
    assert book.revision_on(Chapter.WINGS, DAY).number == 9


def test_the_table_that_answers_is_the_one_in_force_that_day():
    book = v320_book()
    before = book.table_on(Chapter.FUSELAGE, date(2024, 6, 1))
    after = book.table_on(Chapter.FUSELAGE, DAY)
    assert before.revision == 11
    assert after.revision == 12


def test_a_limit_read_before_the_campaign_is_the_looser_one():
    book = v320_book()
    before = book.limits_for(
        Chapter.FUSELAGE,
        date(2024, 6, 1),
        tail(),
        DamageKind.DENT,
        PanelKind.PRESSURISED_SKIN,
        Region.OPEN_FIELD,
    )
    after = book.limits_for(
        Chapter.FUSELAGE,
        DAY,
        tail(),
        DamageKind.DENT,
        PanelKind.PRESSURISED_SKIN,
        Region.OPEN_FIELD,
    )
    assert before.limits.max_depth == inch("0.080")
    assert after.limits.max_depth == inch("0.060")


def test_a_day_before_the_first_revision_has_no_book_to_read():
    with pytest.raises(NotOnFile, match="had no revision in force"):
        v320_book().table_on(Chapter.FUSELAGE, date(2019, 1, 1))


def test_the_pages_in_the_book_are_the_ones_live_at_that_revision():
    book = v320_book()
    early = book.pages_on(Chapter.FUSELAGE, date(2022, 6, 1))
    later = book.pages_on(Chapter.FUSELAGE, DAY)
    assert len(early) < len(later)


def test_a_page_can_be_pulled_out_by_its_reference():
    found = v320_book().page(Reference.parse("53-10-01"), DAY)
    assert found.title.startswith("allowable damage")


def test_a_page_not_in_the_book_that_day_is_a_lookup_miss():
    with pytest.raises(NotOnFile, match="is not in the book"):
        v320_book().page(Reference.parse("53-30-02"), date(2022, 6, 1))


def blocked_table():
    return LimitTable(
        Reference.parse("53-10"),
        12,
        (
            LimitRow(
                LimitKey(DamageKind.DENT),
                LimitSet(max_depth=inch("0.060")),
                Reference.parse("53-10-01"),
                Effectivity(first_serial=5001, last_serial=5240),
            ),
            LimitRow(
                LimitKey(DamageKind.SCRATCH),
                LimitSet(max_depth=inch("0.005")),
                Reference.parse("53-20-01"),
            ),
        ),
    )


def test_a_row_written_for_a_block_of_serials_drops_out_for_a_tail_outside_it():
    inside = blocked_table().effective_for(tail("5127"))
    outside = blocked_table().effective_for(tail("5301"))
    assert len(inside) == 2
    assert len(outside) == 1


def test_a_tail_outside_the_block_gets_a_lookup_miss_and_not_the_wrong_row():
    outside = blocked_table().effective_for(tail("5301"))
    with pytest.raises(NotOnFile, match="has no row for dent"):
        outside.lookup(DamageKind.DENT, PanelKind.PRESSURISED_SKIN, Region.OPEN_FIELD)


def test_a_table_with_nothing_left_after_filtering_is_a_lookup_miss():
    only_a_block = LimitTable(
        Reference.parse("53-10"),
        12,
        (
            LimitRow(
                LimitKey(DamageKind.DENT),
                LimitSet(max_depth=inch("0.060")),
                Reference.parse("53-10-01"),
                Effectivity(first_serial=5001, last_serial=5240),
            ),
        ),
    )
    with pytest.raises(NotOnFile, match="has no rows that apply"):
        only_a_block.effective_for(tail("5301"))


def test_the_book_carries_the_cleanup_and_interaction_rules_alongside():
    book = v320_book()
    assert book.cleanup.covers(DamageKind.SCRATCH)
    assert book.interaction.counts.per_panel == 6


def test_a_holding_reads_back_what_it_is():
    printed = str(v320_book().holding(Chapter.FUSELAGE))
    assert printed.startswith("chapter 53, 5 pages")
