from decimal import Decimal

import pytest

from doublerbay.damage.corrosion import Corrosion, square_inches
from doublerbay.damage.strike import LightningBurn
from doublerbay.errors import BadReading
from doublerbay.grid.surface import Offset
from doublerbay.measure.length import Length
from doublerbay.measure.ratio import Taper


def inch(text):
    return Length.of_inches(text)


def rot(along="4.000", around="3.000", depth="0.006", pitted=False, exfoliating=False):
    return Corrosion(inch(along), inch(around), inch(depth), pitted, exfoliating)


def burn(along="2.000", around="2.000", pits=4, worst="0.008", on_fastener=False, entry=True):
    return LightningBurn(inch(along), inch(around), pits, inch(worst), on_fastener, entry)


def test_an_area_comes_out_in_square_inches():
    assert square_inches(inch("4.000"), inch("3.000")) == Decimal("12.00")
    assert square_inches(inch("0.500"), inch("0.500")) == Decimal("0.25")


def test_corrosion_reads_back_its_area_and_worst_depth():
    assert str(rot()) == "12.00 sq in, 0.006 deep"
    assert rot().area == Decimal("12.00")


def test_the_flags_show_up_in_the_reading():
    assert str(rot(pitted=True)).endswith(", pitted")
    assert str(rot(pitted=True, exfoliating=True)).endswith(", pitted and exfoliating")


def test_corrosion_covers_an_area():
    with pytest.raises(BadReading, match="covers an area"):
        rot(along="0")


def test_a_depth_that_runs_backwards_is_refused():
    with pytest.raises(BadReading, match="does not run backwards"):
        rot(depth="-0.001")


def test_a_stain_with_no_loss_under_it_is_surface_only():
    assert rot(depth="0").is_surface_only
    assert not rot(depth="0").reaches_below_the_surface
    assert rot().reaches_below_the_surface


def test_exfoliation_with_no_depth_is_not_exfoliation():
    with pytest.raises(BadReading, match="not a surface stain"):
        rot(depth="0", exfoliating=True)


def test_exfoliation_goes_to_a_repair_whatever_the_depth_says():
    assert rot(depth="0.001", exfoliating=True).needs_a_repair_whatever_the_depth
    assert not rot(depth="0.020").needs_a_repair_whatever_the_depth


def test_the_governing_depth_is_the_worst_pit_and_never_an_average():
    assert rot(depth="0.012", pitted=True).governing_depth == inch("0.012")


def test_the_blend_area_grows_past_the_stain_all_round():
    assert rot().blend_area(Taper(20)) == Decimal("13.74")
    assert rot().blend_area(Taper(40)) > rot().blend_area(Taper(20))


def test_the_footprint_covers_the_stain():
    centre = Offset(inch("100.000"), inch("50.000"))
    assert rot().footprint(centre).along == inch("4.000")
    assert rot().footprint(centre).contains(centre)


def test_exfoliation_is_worse_than_anything_deeper_that_is_not():
    assert rot(depth="0.002", exfoliating=True).worse_than(rot(depth="0.030"))
    assert not rot(depth="0.030").worse_than(rot(depth="0.002", exfoliating=True))


def test_two_ordinary_patches_compare_on_depth():
    assert rot(depth="0.020").worse_than(rot(depth="0.010"))


def test_a_strike_reads_back_which_end_it_is_and_what_it_left():
    assert str(burn()) == "entry, 4 pits, worst 0.008"
    assert str(burn(entry=False)).startswith("exit")
    assert str(burn(on_fastener=True)).endswith(", on a fastener")


def test_a_strike_with_no_pits_is_not_a_strike():
    with pytest.raises(BadReading, match="not a strike"):
        burn(pits=0)


def test_a_pit_has_a_depth_and_a_burn_has_an_area():
    with pytest.raises(BadReading, match="a pit has a depth"):
        burn(worst="0")
    with pytest.raises(BadReading, match="a burn covers an area"):
        burn(along="0")


def test_enough_attachment_points_make_it_a_swept_stroke():
    assert burn(pits=12).is_swept
    assert not burn(pits=11).is_swept


def test_the_pit_density_separates_a_cluster_from_a_sweep():
    assert burn(pits=4).pit_density == Decimal("1.00")
    assert burn(along="8.000", pits=4).pit_density == Decimal("0.25")


def test_a_strike_on_a_fastener_takes_the_fastener_out():
    assert burn(on_fastener=True).needs_the_fastener_out
    assert not burn().needs_the_fastener_out


def test_a_strike_on_a_fastener_is_worse_than_a_deeper_one_that_is_not():
    assert burn(worst="0.002", on_fastener=True).worse_than(burn(worst="0.030"))
    assert not burn(worst="0.030").worse_than(burn(worst="0.002", on_fastener=True))


def test_two_open_skin_strikes_compare_on_their_worst_pit():
    assert burn(worst="0.020").worse_than(burn(worst="0.010"))
