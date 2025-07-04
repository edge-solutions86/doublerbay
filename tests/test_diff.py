from decimal import Decimal

from doublerbay.damage.kind import DamageKind
from doublerbay.grid.ata import Reference
from doublerbay.manual.diff import Direction, diff_tables
from doublerbay.manual.limits import LimitSet
from doublerbay.manual.table import LimitKey, LimitRow, LimitTable
from doublerbay.measure.length import Length
from doublerbay.measure.ratio import Ratio
from doublerbay.structure.panel import PanelKind
from doublerbay.v320.manual53 import allowable_damage

PAGE = Reference.parse("53-10")


def inch(text):
    return Length.of_inches(text)


def row(kind=DamageKind.DENT, panel=None, **limits):
    return LimitRow(LimitKey(kind, panel), LimitSet(**limits), PAGE)


def table(revision, *rows):
    return LimitTable(PAGE, revision, rows)


def test_two_identical_tables_diff_to_nothing():
    before = table(11, row(max_depth=inch("0.080")))
    after = table(12, row(max_depth=inch("0.080")))
    found = diff_tables(before, after)
    assert found.is_empty
    assert str(found).endswith("no change")


def test_a_smaller_depth_cap_is_a_tightening():
    found = diff_tables(
        table(11, row(max_depth=inch("0.080"))), table(12, row(max_depth=inch("0.060")))
    )
    assert len(found.tightenings()) == 1
    assert found.loosenings() == ()
    assert found.changed[0].field == "max_depth"


def test_a_larger_depth_cap_is_a_loosening():
    found = diff_tables(
        table(11, row(max_depth=inch("0.060"))), table(12, row(max_depth=inch("0.080")))
    )
    assert len(found.loosenings()) == 1
    assert found.tightenings() == ()


def test_a_larger_residual_floor_is_a_tightening_and_not_a_loosening():
    found = diff_tables(
        table(11, row(min_residual_fraction=Ratio("0.90"))),
        table(12, row(min_residual_fraction=Ratio("0.95"))),
    )
    assert len(found.tightenings()) == 1
    assert found.changed[0].field == "min_residual_fraction"


def test_a_smaller_residual_floor_is_a_loosening():
    found = diff_tables(
        table(11, row(min_residual_fraction=Ratio("0.95"))),
        table(12, row(min_residual_fraction=Ratio("0.90"))),
    )
    assert len(found.loosenings()) == 1


def test_a_limit_appearing_where_there_was_none_is_a_tightening():
    found = diff_tables(table(11, row()), table(12, row(max_area=Decimal("4.00"))))
    assert found.changed[0].direction is Direction.TIGHTER


def test_a_limit_disappearing_is_a_loosening():
    found = diff_tables(table(11, row(max_area=Decimal("4.00"))), table(12, row()))
    assert found.changed[0].direction is Direction.LOOSER


def test_a_row_that_stops_allowing_anything_is_a_tightening():
    found = diff_tables(
        table(11, row(max_depth=inch("0.060"))),
        table(12, LimitRow(LimitKey(DamageKind.DENT), LimitSet.nothing_allowable(), PAGE)),
    )
    assert found.changed[0].field == "allowed"
    assert found.changed[0].direction is Direction.TIGHTER


def test_a_row_that_starts_allowing_something_is_a_loosening():
    found = diff_tables(
        table(12, LimitRow(LimitKey(DamageKind.DENT), LimitSet.nothing_allowable(), PAGE)),
        table(13, row(max_depth=inch("0.060"))),
    )
    assert found.changed[0].direction is Direction.LOOSER


def test_a_row_that_stops_allowing_anything_reports_only_that():
    found = diff_tables(
        table(11, row(max_depth=inch("0.060"), max_area=Decimal("4.00"))),
        table(12, LimitRow(LimitKey(DamageKind.DENT), LimitSet.nothing_allowable(), PAGE)),
    )
    assert len(found.changed) == 1


def test_a_new_row_is_reported_as_added():
    found = diff_tables(table(11, row()), table(12, row(), row(kind=DamageKind.SCRATCH)))
    assert len(found.added) == 1
    assert found.removed == ()


def test_a_withdrawn_row_is_reported_as_removed():
    found = diff_tables(table(11, row(), row(kind=DamageKind.SCRATCH)), table(12, row()))
    assert len(found.removed) == 1
    assert found.added == ()


def test_the_review_list_is_everything_that_did_not_get_looser():
    found = diff_tables(
        table(
            11, row(max_depth=inch("0.080")), row(kind=DamageKind.SCRATCH, max_depth=inch("0.005"))
        ),
        table(
            12, row(max_depth=inch("0.060")), row(kind=DamageKind.SCRATCH, max_depth=inch("0.008"))
        ),
    )
    assert len(found.needs_review()) == 1
    assert Direction.LOOSER.needs_review is False
    assert Direction.TIGHTER.needs_review is True


def test_a_diff_says_whether_it_touches_a_given_row():
    key = LimitKey(DamageKind.DENT)
    found = diff_tables(
        table(11, row(max_depth=inch("0.080"))), table(12, row(max_depth=inch("0.060")))
    )
    assert found.touches(key)
    assert not found.touches(LimitKey(DamageKind.CRACK))


def test_a_diff_reads_back_a_summary_of_what_moved():
    found = diff_tables(
        table(11, row(max_depth=inch("0.080"))), table(12, row(max_depth=inch("0.060")))
    )
    assert str(found) == "53-10 rev 11 to 12: 0 added, 0 removed, 1 tightened, 0 loosened"


def test_the_real_campaign_revision_shows_up_as_a_tightening():
    found = diff_tables(allowable_damage(11), allowable_damage(12))
    tightened = found.tightenings()
    assert tightened
    assert all(change.key.panel_kind is PanelKind.PRESSURISED_SKIN for change in tightened)
    assert found.loosenings() == ()


def test_a_revision_that_changed_nothing_diffs_to_nothing():
    assert diff_tables(allowable_damage(12), allowable_damage(13)).is_empty


def test_a_change_reads_back_the_field_and_the_direction():
    found = diff_tables(
        table(11, row(max_depth=inch("0.080"))), table(12, row(max_depth=inch("0.060")))
    )
    printed = str(found.changed[0])
    assert "max_depth" in printed
    assert "(tighter)" in printed
