import pytest

from doublerbay.damage.kind import DamageKind
from doublerbay.errors import BadReading, NotOnFile
from doublerbay.grid.ata import Reference
from doublerbay.manual.limits import LimitSet
from doublerbay.manual.table import LimitKey, LimitRow, LimitTable
from doublerbay.measure.length import Length
from doublerbay.structure.build import Region
from doublerbay.structure.panel import PanelKind

PAGE = Reference.parse("53-10-03")


def inch(text):
    return Length.of_inches(text)


def row(damage=DamageKind.DENT, panel=None, region=None, depth="0.010"):
    return LimitRow(
        LimitKey(damage, panel, region),
        LimitSet(max_depth=inch(depth)),
        PAGE,
    )


def table(*rows):
    if not rows:
        rows = (
            row(depth="0.010"),
            row(panel=PanelKind.PRESSURISED_SKIN, depth="0.008"),
            row(panel=PanelKind.PRESSURISED_SKIN, region=Region.FASTENER_ROW, depth="0.004"),
        )
    return LimitTable(PAGE, 12, rows)


def test_a_key_reads_back_what_it_is_keyed_on():
    assert str(LimitKey(DamageKind.DENT)) == "dent on any skin anywhere"
    keyed = LimitKey(DamageKind.DENT, PanelKind.PRESSURISED_SKIN, Region.FASTENER_ROW)
    assert str(keyed) == "dent on pressurised skin on a fastener row"


def test_specificity_counts_how_much_of_the_key_is_pinned_down():
    assert LimitKey(DamageKind.DENT).specificity == 1
    assert LimitKey(DamageKind.DENT, PanelKind.PRESSURISED_SKIN).specificity == 2
    assert LimitKey(DamageKind.DENT, None, Region.LAND).specificity == 2
    assert LimitKey(DamageKind.DENT, PanelKind.PRESSURISED_SKIN, Region.LAND).specificity == 3


def test_a_blank_field_matches_anything_in_that_field():
    open_key = LimitKey(DamageKind.DENT)
    assert open_key.matches(DamageKind.DENT, PanelKind.PRESSURISED_SKIN, Region.LAND)
    assert open_key.matches(DamageKind.DENT, PanelKind.WING_LOWER, Region.OPEN_FIELD)
    assert not open_key.matches(DamageKind.CRACK, PanelKind.PRESSURISED_SKIN, Region.LAND)


def test_a_pinned_field_only_matches_itself():
    keyed = LimitKey(DamageKind.DENT, PanelKind.PRESSURISED_SKIN)
    assert keyed.matches(DamageKind.DENT, PanelKind.PRESSURISED_SKIN, Region.LAND)
    assert not keyed.matches(DamageKind.DENT, PanelKind.BELLY_SKIN, Region.LAND)


def test_a_table_reads_back_its_page_and_size():
    assert str(table()) == "53-10-03 rev 12, 3 rows"
    assert len(table()) == 3


def test_a_table_needs_rows_and_a_revision():
    with pytest.raises(BadReading, match="has rows in it"):
        LimitTable(PAGE, 12, ())
    with pytest.raises(BadReading, match="is issued at a revision"):
        LimitTable(PAGE, 0, (row(),))


def test_the_most_specific_matching_row_answers():
    found = table().lookup(DamageKind.DENT, PanelKind.PRESSURISED_SKIN, Region.FASTENER_ROW)
    assert found.limits.max_depth == inch("0.004")


def test_a_less_specific_row_answers_when_nothing_tighter_matches():
    found = table().lookup(DamageKind.DENT, PanelKind.PRESSURISED_SKIN, Region.OPEN_FIELD)
    assert found.limits.max_depth == inch("0.008")


def test_the_catch_all_row_answers_when_nothing_else_does():
    found = table().lookup(DamageKind.DENT, PanelKind.WING_LOWER, Region.OPEN_FIELD)
    assert found.limits.max_depth == inch("0.010")


def test_the_candidates_come_back_most_specific_first():
    found = table().candidates(DamageKind.DENT, PanelKind.PRESSURISED_SKIN, Region.FASTENER_ROW)
    assert [one.key.specificity for one in found] == [3, 2, 1]


def test_a_damage_kind_with_no_row_is_a_lookup_miss():
    with pytest.raises(NotOnFile, match="has no row for crack"):
        table().lookup(DamageKind.CRACK, PanelKind.PRESSURISED_SKIN, Region.OPEN_FIELD)


def test_two_rows_that_answer_equally_are_a_contradiction_and_not_a_tie():
    contradictory = table(
        row(panel=PanelKind.PRESSURISED_SKIN, depth="0.008"),
        row(region=Region.FASTENER_ROW, depth="0.004"),
    )
    with pytest.raises(BadReading, match="has 2 rows that answer"):
        contradictory.lookup(DamageKind.DENT, PanelKind.PRESSURISED_SKIN, Region.FASTENER_ROW)


def test_a_contradiction_only_bites_where_both_rows_actually_match():
    contradictory = table(
        row(panel=PanelKind.PRESSURISED_SKIN, depth="0.008"),
        row(region=Region.FASTENER_ROW, depth="0.004"),
    )
    found = contradictory.lookup(DamageKind.DENT, PanelKind.PRESSURISED_SKIN, Region.OPEN_FIELD)
    assert found.limits.max_depth == inch("0.008")


def test_a_table_says_whether_it_could_answer_at_all():
    assert table().has_a_row_for(DamageKind.DENT, PanelKind.WING_LOWER, Region.LAND)
    assert not table().has_a_row_for(DamageKind.CRACK, PanelKind.WING_LOWER, Region.LAND)


def test_a_table_lists_the_kinds_it_covers():
    covering = table(row(damage=DamageKind.DENT), row(damage=DamageKind.SCRATCH))
    assert covering.kinds_covered() == frozenset({DamageKind.DENT, DamageKind.SCRATCH})


def test_adding_a_row_leaves_the_earlier_table_alone():
    before = table()
    after = before.with_row(row(damage=DamageKind.SCRATCH))
    assert len(after) == 4
    assert len(before) == 3


def test_a_row_reads_back_its_page_key_and_limits():
    printed = str(row(panel=PanelKind.PRESSURISED_SKIN))
    assert printed.startswith("53-10-03 dent on pressurised skin anywhere")
    assert "depth 0.010" in printed
