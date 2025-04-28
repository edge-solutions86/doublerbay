import pytest

from doublerbay.damage.crack import Crack
from doublerbay.damage.scoring import Scoring
from doublerbay.errors import BadReading
from doublerbay.grid.surface import Offset
from doublerbay.measure.angle import Angle, Orientation
from doublerbay.measure.length import Length
from doublerbay.measure.ratio import Taper


def inch(text):
    return Length.of_inches(text)


def deg(value):
    return Angle.of_degrees(value)


def score(depth="0.010", length="2.000", width="0.060", bearing=0, sharp=False):
    return Scoring(inch(depth), inch(length), inch(width), deg(bearing), sharp)


def crack(length="0.750", bearing=0, through=True, stop_drilled=False, tips=2):
    return Crack(inch(length), deg(bearing), through, stop_drilled, tips)


def test_a_score_reads_back_its_depth_length_and_direction():
    assert str(score()) == "0.010 deep, 2.000 long, 0.0 deg"
    assert str(score(sharp=True)).endswith(", sharp")


def test_a_score_needs_all_three_measurements():
    with pytest.raises(BadReading, match="a score has a depth"):
        score(depth="0")
    with pytest.raises(BadReading, match="a score has a length"):
        score(length="0")
    with pytest.raises(BadReading, match="a score has a width"):
        score(width="0")


def test_a_score_wider_than_it_is_long_is_something_else():
    with pytest.raises(BadReading, match="wider than it is long"):
        score(length="0.050", width="0.060")


def test_a_score_along_the_body_is_the_one_hoop_stress_pulls_open():
    assert score(bearing=0).is_hoop_loaded
    assert score(bearing=10).is_hoop_loaded
    assert not score(bearing=90).is_hoop_loaded
    assert score(bearing=90).is_across_the_barrel


def test_a_score_on_the_diagonal_is_neither():
    assert score(bearing=45).orientation is Orientation.OBLIQUE
    assert not score(bearing=45).is_hoop_loaded
    assert not score(bearing=45).is_across_the_barrel


def test_the_blend_runs_out_past_both_ends():
    blended = score().blend_length(Taper(20))
    assert blended == inch("2.400")
    assert score().blend_width(Taper(20)) == inch("0.460")


def test_a_gentler_blend_takes_more_skin():
    assert score().blend_length(Taper(40)) > score().blend_length(Taper(20))


def test_the_footprint_lies_the_way_the_score_lies():
    centre = Offset(inch("100.000"), inch("50.000"))
    along_body = score(bearing=0).footprint(centre)
    around_body = score(bearing=90).footprint(centre)
    assert along_body.along == inch("2.000")
    assert around_body.around == inch("2.000")
    assert around_body.along == inch("0.060")


def test_a_crack_reads_back_its_length_and_direction():
    assert str(crack()) == "0.750 long, 0.0 deg"
    assert str(crack(through=False)).endswith(", part depth")
    assert str(crack(stop_drilled=True)).endswith(", stop drilled")


def test_a_crack_needs_a_length():
    with pytest.raises(BadReading, match="a crack has a length"):
        crack(length="0")


def test_a_crack_has_nought_one_or_two_tips():
    assert crack(tips=1).tips_found == 1
    with pytest.raises(BadReading, match="nought, one or two tips"):
        crack(tips=3)


def test_a_crack_with_no_tips_found_cannot_be_stop_drilled():
    with pytest.raises(BadReading, match="at a tip nobody found"):
        crack(stop_drilled=True, tips=0)


def test_a_crack_along_the_body_is_the_one_hoop_stress_pulls_open():
    assert crack(bearing=0).is_hoop_loaded
    assert not crack(bearing=90).is_hoop_loaded
    assert crack(bearing=90).orientation is Orientation.CIRCUMFERENTIAL


def test_a_crack_with_both_tips_found_has_a_real_length():
    assert crack().is_bounded
    assert not crack(tips=1).is_bounded
    assert "1 tips found" in str(crack(tips=1))


def test_a_crack_is_only_arrested_when_both_tips_are_drilled():
    assert crack(stop_drilled=True).is_arrested
    assert not crack(stop_drilled=True, tips=1).is_arrested
    assert not crack().is_arrested


def test_growth_is_the_difference_between_two_bounded_readings():
    assert crack(length="0.900").grown_from(crack(length="0.750")) == inch("0.150")


def test_growth_needs_both_tips_on_both_readings():
    with pytest.raises(BadReading, match="both tips found on both readings"):
        crack(length="0.900", tips=1).grown_from(crack(length="0.750"))
    with pytest.raises(BadReading, match="both tips found on both readings"):
        crack(length="0.900").grown_from(crack(length="0.750", tips=1))


def test_a_crack_does_not_get_shorter():
    with pytest.raises(BadReading, match="does not get shorter"):
        crack(length="0.600").grown_from(crack(length="0.750"))


def test_the_crack_footprint_lies_along_the_crack():
    centre = Offset(inch("100.000"), inch("50.000"))
    assert crack(bearing=0).footprint(centre).along == inch("0.750")
    assert crack(bearing=90).footprint(centre).around == inch("0.750")
