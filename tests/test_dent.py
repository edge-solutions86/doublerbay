from decimal import Decimal

import pytest

from doublerbay.damage.dent import Dent
from doublerbay.errors import BadReading
from doublerbay.grid.surface import Offset
from doublerbay.measure.length import Length


def inch(text):
    return Length.of_inches(text)


def dent(depth="0.030", along="3.000", around="2.000", sharp=False, cracked=False):
    return Dent(inch(depth), inch(along), inch(around), sharp, cracked)


def test_a_dent_reads_back_the_three_numbers():
    assert str(dent()) == "0.030 deep, 3.000 by 2.000"


def test_the_two_judgements_show_up_in_the_reading():
    assert str(dent(sharp=True)).endswith(", sharp")
    assert str(dent(cracked=True)).endswith(", cracked")
    assert str(dent(sharp=True, cracked=True)).endswith(", cracked")


def test_a_dent_needs_a_depth_and_both_diameters():
    with pytest.raises(BadReading, match="a dent has a depth"):
        dent(depth="0")
    with pytest.raises(BadReading, match="a diameter both ways"):
        dent(along="0")
    with pytest.raises(BadReading, match="a diameter both ways"):
        dent(around="0")


def test_a_dent_deeper_than_it_is_wide_is_not_a_dent():
    with pytest.raises(BadReading, match="is a puncture, not a dent"):
        dent(depth="2.000", along="3.000", around="2.000")


def test_the_wide_and_narrow_diameters_come_out_either_way_round():
    assert dent(along="3.000", around="2.000").wide == inch("3.000")
    assert dent(along="3.000", around="2.000").narrow == inch("2.000")
    assert dent(along="2.000", around="3.000").wide == inch("3.000")
    assert dent(along="2.000", around="3.000").narrow == inch("2.000")


def test_the_sharpness_is_taken_on_the_narrow_diameter():
    assert dent().sharpness.value == Decimal("0.0150")


def test_taking_the_wide_diameter_would_have_cleared_it_and_does_not():
    narrow_graded = dent(depth="0.030", along="12.000", around="1.000").sharpness
    wide_graded = dent(depth="0.030", along="1.000", around="1.000").sharpness
    assert narrow_graded == wide_graded
    assert narrow_graded.value == Decimal("0.0300")


def test_a_dent_with_a_clean_bottom_and_no_crack_is_smooth():
    assert dent().is_smooth
    assert not dent(sharp=True).is_smooth
    assert not dent(cracked=True).is_smooth


def test_a_dent_twice_as_long_as_it_is_wide_is_a_trough():
    assert dent(along="4.000", around="2.000").is_elongated
    assert not dent(along="3.900", around="2.000").is_elongated
    assert dent(along="2.000", around="4.000").is_elongated


def test_the_footprint_covers_the_skin_the_dent_is_on():
    centre = Offset(inch("100.000"), inch("50.000"))
    covered = dent().footprint(centre)
    assert covered.along == inch("3.000")
    assert covered.around == inch("2.000")
    assert covered.corner_radius == inch("1.000")
    assert covered.contains(centre)


def test_two_dents_compare_on_depth():
    assert dent(depth="0.040").deeper_than(dent(depth="0.030"))
    assert not dent(depth="0.030").deeper_than(dent(depth="0.040"))


def test_a_dent_is_frozen_and_hashes_by_value():
    assert len({dent(), dent()}) == 1
    with pytest.raises(AttributeError):
        dent().depth = inch("0.100")
