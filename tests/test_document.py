from datetime import date

import pytest

from doublerbay.errors import BadReading, NotOnFile
from doublerbay.fleet.model import Effectivity
from doublerbay.grid.ata import Chapter, Reference
from doublerbay.manual.document import Manual, Page, Revision


def revision(number=12, issued=date(2024, 9, 1), effective=date(2024, 12, 1)):
    return Revision(number, issued, effective)


def manual(*revisions):
    if not revisions:
        revisions = (
            Revision(10, date(2023, 3, 1), date(2023, 6, 1)),
            Revision(11, date(2023, 12, 1), date(2024, 3, 1)),
            Revision(12, date(2024, 9, 1), date(2024, 12, 1)),
        )
    return Manual(Chapter.FUSELAGE, revisions)


def page(reference="53-10-03", revision_number=10, withdrawn=None):
    return Page(
        Reference.parse(reference),
        "allowable damage, fuselage skin",
        revision_number,
        Effectivity.all_aircraft(),
        withdrawn,
    )


def test_a_revision_reads_back_when_it_started_to_govern():
    assert str(revision()) == "rev 12 of 2024-12-01"
    assert revision().lead_days == 91


def test_a_revision_that_takes_effect_before_it_was_issued_is_refused():
    with pytest.raises(BadReading, match="takes effect before it was issued"):
        Revision(12, date(2024, 12, 1), date(2024, 9, 1))


def test_revisions_are_numbered_from_one():
    with pytest.raises(BadReading, match="numbered from one"):
        Revision(0, date(2024, 9, 1), date(2024, 12, 1))


def test_a_revision_is_not_in_force_before_its_effective_date():
    assert not revision().in_force_on(date(2024, 11, 30))
    assert revision().in_force_on(date(2024, 12, 1))
    assert revision().in_force_on(date(2026, 1, 1))


def test_a_chapter_reads_back_its_latest_revision():
    assert str(manual()) == "chapter 53, rev 12 of 2024-12-01"
    assert manual().latest.number == 12
    assert manual().first.number == 10


def test_a_chapter_needs_at_least_one_revision():
    with pytest.raises(BadReading, match="at least one revision"):
        Manual(Chapter.FUSELAGE, ())


def test_revisions_have_to_be_listed_in_order():
    with pytest.raises(BadReading, match="in order with no repeats"):
        manual(
            Revision(12, date(2024, 9, 1), date(2024, 12, 1)),
            Revision(11, date(2023, 12, 1), date(2024, 3, 1)),
        )


def test_two_revisions_taking_effect_on_one_day_are_refused():
    with pytest.raises(BadReading, match="no two on one day"):
        manual(
            Revision(11, date(2023, 12, 1), date(2024, 3, 1)),
            Revision(12, date(2024, 1, 1), date(2024, 3, 1)),
        )


def test_the_revision_in_force_is_the_last_one_to_have_started():
    assert manual().in_force_on(date(2024, 6, 1)).number == 11
    assert manual().in_force_on(date(2024, 12, 1)).number == 12
    assert manual().in_force_on(date(2026, 1, 1)).number == 12


def test_the_day_before_a_revision_starts_the_previous_one_still_governs():
    assert manual().in_force_on(date(2024, 11, 30)).number == 11


def test_a_day_before_the_first_revision_has_nothing_in_force():
    with pytest.raises(NotOnFile, match="had no revision in force"):
        manual().in_force_on(date(2020, 1, 1))


def test_a_revision_can_be_asked_for_by_number():
    assert manual().numbered(11).effective == date(2024, 3, 1)
    with pytest.raises(NotOnFile, match="has no revision 99"):
        manual().numbered(99)


def test_issuing_a_revision_leaves_the_earlier_chapter_alone():
    before = manual()
    after = before.issued(Revision(13, date(2025, 6, 1), date(2025, 9, 1)))
    assert after.latest.number == 13
    assert before.latest.number == 12


def test_a_page_reads_back_its_reference_and_title():
    assert str(page()) == "53-10-03 allowable damage, fuselage skin"


def test_a_page_needs_a_title_and_a_revision():
    with pytest.raises(BadReading, match="has a title"):
        Page(Reference.parse("53-10-03"), "  ", 10)
    with pytest.raises(BadReading, match="is issued at a revision"):
        Page(Reference.parse("53-10-03"), "allowable damage", 0)


def test_a_page_is_not_in_the_book_before_it_was_issued():
    assert not page(revision_number=11).is_live_at(10)
    assert page(revision_number=11).is_live_at(11)
    assert page(revision_number=11).is_live_at(12)


def test_a_withdrawn_page_leaves_the_book_at_the_revision_that_withdrew_it():
    withdrawn = page(revision_number=10, withdrawn=12)
    assert withdrawn.is_live_at(11)
    assert not withdrawn.is_live_at(12)
    assert not withdrawn.is_live_at(13)


def test_a_page_withdrawn_before_it_was_issued_is_refused():
    with pytest.raises(BadReading, match="withdrawn before it was issued"):
        page(revision_number=12, withdrawn=12)
