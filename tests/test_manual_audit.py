from doublerbay.complaints import Complaint, Severity, blocking, is_clear, sorted_complaints
from doublerbay.damage.kind import DamageKind
from doublerbay.grid.ata import Reference
from doublerbay.manual.audit import audit_book, audit_table
from doublerbay.manual.book import v320_book
from doublerbay.manual.limits import LimitSet
from doublerbay.manual.table import LimitKey, LimitRow, LimitTable
from doublerbay.measure.length import Length
from doublerbay.measure.ratio import Ratio
from doublerbay.v320 import manual53, manual57

PAGE = Reference.parse("53-10")
SUBJECT = Reference.parse("53-10-01")


def inch(text):
    return Length.of_inches(text)


def row(kind=DamageKind.DENT, panel=None, region=None, **limits):
    return LimitRow(LimitKey(kind, panel, region), LimitSet(**limits), SUBJECT)


def every_kind(**limits):
    return [row(kind=kind, **limits) for kind in DamageKind]


def table(*rows):
    return LimitTable(PAGE, 12, tuple(rows))


def test_a_complaint_reads_back_the_same_way_wherever_it_came_from():
    complaint = Complaint(Severity.BLOCKING, "53-10", "no row anywhere for a dent")
    assert str(complaint) == "[blocking] 53-10: no row anywhere for a dent"


def test_the_helpers_split_blocking_from_advisory():
    complaints = (
        Complaint(Severity.ADVISORY, "a", "one"),
        Complaint(Severity.BLOCKING, "b", "two"),
    )
    assert len(blocking(complaints)) == 1
    assert not is_clear(complaints)
    assert is_clear(complaints[:1])


def test_complaints_sort_blocking_first_and_then_by_subject():
    ordered = sorted_complaints(
        (
            Complaint(Severity.ADVISORY, "z", "one"),
            Complaint(Severity.BLOCKING, "b", "two"),
            Complaint(Severity.ADVISORY, "a", "three"),
        )
    )
    assert [one.about for one in ordered] == ["b", "a", "z"]


def test_a_kind_with_no_row_anywhere_is_blocking():
    thin = table(*[row(kind=kind, max_depth=inch("0.010")) for kind in list(DamageKind)[:3]])
    found = audit_table(thin)
    missing = [one for one in found if "no row anywhere" in one.says]
    assert len(missing) == len(DamageKind) - 3
    assert all(one.severity is Severity.BLOCKING for one in missing)


def test_a_row_that_allows_damage_and_caps_nothing_is_blocking():
    found = audit_table(table(*every_kind()))
    open_rows = [one for one in found if "caps nothing" in one.says]
    assert open_rows
    assert all(one.severity is Severity.BLOCKING for one in open_rows)


def test_a_depth_cap_that_leaves_less_than_the_residual_floor_is_blocking():
    crossed = table(
        *every_kind(max_depth_fraction=Ratio("0.20"), min_residual_fraction=Ratio("0.90"))
    )
    found = audit_table(crossed)
    contradictions = [one for one in found if "and asks for" in one.says]
    assert contradictions
    assert all(one.severity is Severity.BLOCKING for one in contradictions)


def test_a_cap_and_a_floor_that_agree_raise_nothing():
    agreeing = table(
        *every_kind(max_depth_fraction=Ratio("0.05"), min_residual_fraction=Ratio("0.90"))
    )
    found = audit_table(agreeing)
    assert not [one for one in found if "and asks for" in one.says]


def test_two_rows_that_answer_equally_show_up_in_the_sweep():
    tied = table(
        *every_kind(max_depth=inch("0.010")),
        row(panel=None, region=None, max_depth=inch("0.005")),
    )
    found = audit_table(tied)
    clashes = [one for one in found if "rows that answer" in one.says]
    assert clashes
    assert all(one.severity is Severity.BLOCKING for one in clashes)


def test_a_row_citing_a_page_that_is_not_in_the_book_is_advisory():
    found = audit_table(table(*every_kind(max_depth=inch("0.010"))), pages=())
    orphans = [one for one in found if "not in the book" in one.says]
    assert len(orphans) == 1
    assert orphans[0].severity is Severity.ADVISORY


def test_a_row_citing_a_live_page_raises_nothing():
    found = audit_table(table(*every_kind(max_depth=inch("0.010"))), pages=manual53.PAGES)
    assert not [one for one in found if "not in the book" in one.says]


def test_the_transcribed_fuselage_chapter_has_nothing_blocking_in_it():
    found = audit_table(manual53.allowable_damage(12), manual53.PAGES)
    assert is_clear(found)


def test_the_transcribed_wing_chapter_has_nothing_blocking_in_it():
    found = audit_table(manual57.allowable_damage(10), manual57.PAGES)
    assert is_clear(found)


def test_the_whole_book_walks_clean():
    assert is_clear(audit_book(v320_book()))


def test_the_book_can_be_walked_at_an_earlier_revision():
    found = audit_book(v320_book(), {"53": 11, "57": 9})
    assert is_clear(found)


def test_the_sweep_reports_the_combinations_no_row_answers_for():
    thin = table(*every_kind(max_depth=inch("0.010")))
    found = audit_table(thin)
    assert not [one for one in found if "no row answers for" in one.says]
