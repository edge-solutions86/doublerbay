import pytest

from doublerbay.errors import BadReading
from doublerbay.measure.fastener import FastenerSize
from doublerbay.measure.length import Length
from doublerbay.repair.doubler import (
    DoublerPlan,
    carried_load,
    fasteners_each_side,
    shear_allowable,
    size_doubler,
)
from doublerbay.structure.material import material

SKIN = Length.of_inches("0.040")
CLAD = material("2024-T3 CLAD")


def inch(text):
    return Length.of_inches(text)


def plan(along="2.000", around="2.000", skin="0.040", dash=5):
    return size_doubler(inch(along), inch(around), inch(skin), CLAD, FastenerSize(dash))


def test_the_shear_allowable_comes_off_the_standard_practices():
    assert shear_allowable(FastenerSize(5)) == 596
    assert shear_allowable(FastenerSize(8)) > shear_allowable(FastenerSize(5))


def test_a_fastener_with_no_allowable_on_file_is_refused():
    with pytest.raises(BadReading, match="no shear allowable on file"):
        shear_allowable(FastenerSize(7))


def test_the_carried_load_is_the_strip_area_times_the_ultimate():
    assert carried_load(inch("0.040"), inch("2.000"), CLAD) == 5120


def test_a_thicker_skin_over_the_same_width_carries_more():
    thin = carried_load(inch("0.040"), inch("2.000"), CLAD)
    thick = carried_load(inch("0.080"), inch("2.000"), CLAD)
    assert thick == thin * 2


def test_a_stronger_alloy_over_the_same_strip_carries_more():
    tough = carried_load(inch("0.040"), inch("2.000"), material("2024-T3 CLAD"))
    strong = carried_load(inch("0.040"), inch("2.000"), material("7075-T6"))
    assert strong > tough


def test_a_strip_with_no_size_carries_nothing_and_is_refused():
    with pytest.raises(BadReading, match="a thickness and a width"):
        carried_load(inch("0"), inch("2.000"), CLAD)


def test_the_fastener_count_is_the_load_over_what_one_will_take():
    assert fasteners_each_side(inch("0.040"), inch("2.000"), CLAD, FastenerSize(5)) == 9


def test_a_bigger_fastener_needs_fewer_of_them():
    small = fasteners_each_side(inch("0.040"), inch("2.000"), CLAD, FastenerSize(4))
    large = fasteners_each_side(inch("0.040"), inch("2.000"), CLAD, FastenerSize(8))
    assert large < small


def test_even_a_trivial_load_takes_one_fastener():
    assert fasteners_each_side(inch("0.001"), inch("0.100"), CLAD, FastenerSize(8)) == 1


def test_the_doubler_goes_on_one_gauge_heavier_than_the_skin():
    assert plan(skin="0.040").gauge == inch("0.050")
    assert plan(skin="0.063").gauge == inch("0.071")


def test_the_doubler_reaches_past_the_hole_by_its_skirt():
    made = plan(along="2.000", around="2.000")
    assert made.along == inch("2.000") + made.skirt * 2
    assert made.around == inch("2.000") + made.skirt * 2


def test_the_skirt_grows_with_every_extra_row():
    one_row = DoublerPlan(
        inch("0.050"),
        CLAD,
        inch("3"),
        inch("3"),
        FastenerSize(5),
        1,
        4,
        inch("0.625"),
        inch("0.625"),
        inch("0.391"),
    )
    two_rows = DoublerPlan(
        inch("0.050"),
        CLAD,
        inch("3"),
        inch("3"),
        FastenerSize(5),
        2,
        4,
        inch("0.625"),
        inch("0.625"),
        inch("0.391"),
    )
    assert two_rows.skirt > one_row.skirt


def test_the_spacings_come_off_the_fastener_size():
    made = plan(dash=5)
    assert made.pitch == FastenerSize(5).min_pitch
    assert made.row_spacing == FastenerSize(5).min_row_spacing
    assert made.edge_distance == FastenerSize(5).preferred_edge_distance


def test_a_bigger_fastener_spreads_the_pattern_out():
    small = plan(dash=4)
    large = plan(dash=8)
    assert large.pitch > small.pitch
    assert large.edge_distance > small.edge_distance


def test_the_hole_gets_enough_fasteners_to_carry_its_load():
    made = plan(along="2.000", around="2.000", skin="0.040", dash=5)
    needed = fasteners_each_side(inch("0.040"), inch("2.000"), CLAD, FastenerSize(5))
    assert made.rows_each_side * made.per_row >= needed


def test_a_heavier_skin_takes_more_rows_over_the_same_hole():
    thin = plan(skin="0.040")
    thick = plan(skin="0.190")
    assert thick.rows_each_side > thin.rows_each_side


def test_the_fastener_count_is_the_pattern_on_four_sides():
    made = plan()
    assert made.fastener_count == made.rows_each_side * made.per_row * 4


def test_a_row_of_one_fastener_is_not_a_joint():
    with pytest.raises(BadReading, match="is a hinge, not a joint"):
        DoublerPlan(
            inch("0.050"),
            CLAD,
            inch("3"),
            inch("3"),
            FastenerSize(5),
            1,
            1,
            inch("0.625"),
            inch("0.625"),
            inch("0.391"),
        )


def test_a_doubler_with_no_rows_is_refused():
    with pytest.raises(BadReading, match="at least one row"):
        DoublerPlan(
            inch("0.050"),
            CLAD,
            inch("3"),
            inch("3"),
            FastenerSize(5),
            0,
            4,
            inch("0.625"),
            inch("0.625"),
            inch("0.391"),
        )


def test_a_hole_with_no_size_is_refused():
    with pytest.raises(BadReading, match="covers a hole with a size"):
        plan(along="0")


def test_the_plan_reads_back_as_a_parts_line():
    printed = str(plan())
    assert printed.startswith("0.050 2024-T3 CLAD")
    assert "off -5" in printed
