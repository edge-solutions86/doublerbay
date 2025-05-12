from decimal import Decimal

import pytest

from doublerbay.damage.opening import Buckle, Disbond, Opening
from doublerbay.errors import BadReading
from doublerbay.grid.surface import Offset
from doublerbay.measure.angle import Angle, Orientation
from doublerbay.measure.length import Length


def inch(text):
    return Length.of_inches(text)


def deg(value):
    return Angle.of_degrees(value)


def hole(along="0.500", around="0.500", torn=False, was_fastener=False):
    return Opening(inch(along), inch(around), torn, was_fastener)


def buckle(length="6.000", height="0.120", bearing=90, permanent=True):
    return Buckle(inch(length), inch(height), deg(bearing), permanent)


def disbond(along="3.000", around="2.000", tapped=True, edge=False):
    return Disbond(inch(along), inch(around), tapped, edge)


def test_an_opening_reads_back_both_ways_across():
    assert str(hole()) == "0.500 by 0.500"
    assert str(hole(torn=True)).endswith(", torn")
    assert "worked fastener hole" in str(hole(was_fastener=True))


def test_an_opening_has_a_size_both_ways():
    with pytest.raises(BadReading, match="a size both ways"):
        hole(along="0")


def test_a_round_opening_measures_the_same_both_ways():
    assert hole().is_round
    assert not hole(along="0.750").is_round
    assert hole(along="0.750").wide == inch("0.750")
    assert hole(along="0.750").narrow == inch("0.500")


def test_a_torn_edge_gets_cut_back_before_a_repair_goes_on():
    assert hole(torn=True).needs_trimming_before_repair
    assert not hole().needs_trimming_before_repair


def test_trimming_grows_the_opening_and_leaves_it_clean():
    trimmed = hole(torn=True).trimmed(inch("0.125"))
    assert trimmed.along == inch("0.750")
    assert trimmed.around == inch("0.750")
    assert not trimmed.torn_edges


def test_trimming_carries_the_fastener_hole_flag_through():
    assert hole(was_fastener=True).trimmed(inch("0.125")).was_a_fastener_hole


def test_trimming_backwards_is_refused():
    with pytest.raises(BadReading, match="does not make an opening smaller"):
        hole().trimmed(inch("-0.010"))


def test_the_opening_footprint_covers_the_hole():
    centre = Offset(inch("100.000"), inch("50.000"))
    assert hole().footprint(centre).contains(centre)
    assert hole().footprint(centre).along == inch("0.500")


def test_a_buckle_reads_back_its_length_and_how_far_out_it_is():
    assert str(buckle()) == "6.000 long, 0.120 out of plane"
    assert str(buckle(permanent=False)).endswith(", not confirmed permanent")


def test_a_buckle_has_a_length_and_a_height():
    with pytest.raises(BadReading, match="a buckle has a length"):
        buckle(length="0")
    with pytest.raises(BadReading, match="a height out of plane"):
        buckle(height="0")


def test_a_buckle_round_the_barrel_lies_across_the_load_that_made_it():
    assert buckle(bearing=90).is_across_the_load
    assert not buckle(bearing=0).is_across_the_load
    assert buckle(bearing=0).orientation is Orientation.LONGITUDINAL


def test_a_buckle_nobody_has_confirmed_may_still_be_oil_canning():
    assert buckle(permanent=False).is_oil_canning
    assert not buckle().is_oil_canning


def test_the_buckle_footprint_lies_the_way_the_buckle_lies():
    centre = Offset(inch("100.000"), inch("50.000"))
    assert buckle(bearing=90).footprint(centre).around == inch("6.000")
    assert buckle(bearing=0).footprint(centre).along == inch("6.000")


def test_a_shallow_buckle_still_gets_a_workable_footprint_across_it():
    centre = Offset(inch("100.000"), inch("50.000"))
    assert buckle(height="0.020", bearing=0).footprint(centre).around == inch("0.500")


def test_a_disbond_reads_back_its_area():
    assert str(disbond()) == "6.00 sq in"
    assert disbond().area == Decimal("6.00")


def test_a_disbond_covers_an_area():
    with pytest.raises(BadReading, match="covers an area"):
        disbond(along="0")


def test_a_disbond_nobody_has_tapped_out_has_no_known_extent():
    assert disbond().is_bounded
    assert not disbond(tapped=False).is_bounded
    assert str(disbond(tapped=False)).endswith(", extent not tapped out")


def test_a_disbond_running_to_a_free_edge_can_spread():
    assert disbond(edge=True).can_spread
    assert not disbond().can_spread
    assert "runs to an edge" in str(disbond(edge=True))
