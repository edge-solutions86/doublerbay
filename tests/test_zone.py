import pytest

from doublerbay.grid.zone import MajorZone, Side, Zone, side_of_digit, zones_in


def test_a_zone_splits_into_its_three_digits():
    zone = Zone(231)
    assert zone.major is MajorZone.UPPER_FUSELAGE
    assert zone.sub == 3
    assert zone.unit == 1
    assert str(zone) == "231"


def test_the_major_zone_names_itself():
    assert MajorZone.LOWER_FUSELAGE.title == "lower fuselage"
    assert str(MajorZone.EMPENNAGE) == "300"


@pytest.mark.parametrize("number", [99, 900, 1000, 0, -100])
def test_a_number_outside_the_scheme_is_not_a_zone(number):
    with pytest.raises(ValueError, match="three digits from 100 to 899"):
        Zone(number)


@pytest.mark.parametrize("text", ["", "23", "2311", "23A", "  "])
def test_the_parser_refuses_anything_but_three_digits(text):
    with pytest.raises(ValueError, match="not a zone number"):
        Zone.parse(text)


def test_the_parser_reads_the_painted_number():
    assert Zone.parse(" 231 ") == Zone(231)


@pytest.mark.parametrize(
    ("unit", "side"),
    [(0, Side.CENTRE), (1, Side.LEFT), (2, Side.RIGHT), (7, Side.LEFT), (8, Side.RIGHT)],
)
def test_the_last_digit_carries_the_hand(unit, side):
    assert side_of_digit(unit) is side
    assert Zone(230 + unit).side is side


def test_the_mirror_crosses_to_the_other_hand():
    assert Zone(231).opposite() == Zone(232)
    assert Zone(232).opposite() == Zone(231)
    assert Zone(231).opposite().side is Side.RIGHT


def test_a_centreline_zone_mirrors_onto_itself():
    assert Zone(230).opposite() == Zone(230)
    assert Zone(230).side is Side.CENTRE


def test_two_hands_of_the_same_place_share_a_bay():
    assert Zone(231).shares_bay_with(Zone(232))
    assert not Zone(231).shares_bay_with(Zone(241))


def test_the_fuselage_is_both_halves_of_the_barrel():
    assert Zone(131).is_fuselage
    assert Zone(231).is_fuselage
    assert not Zone(531).is_fuselage


def test_a_wing_zone_is_either_wing():
    assert Zone(531).is_wing
    assert Zone(631).is_wing
    assert not Zone(331).is_wing


def test_zones_sort_and_hash_by_number():
    assert sorted([Zone(311), Zone(131), Zone(232)]) == [Zone(131), Zone(232), Zone(311)]
    assert len({Zone(231), Zone.parse("231")}) == 1
    assert Zone(231) < Zone(232)


def test_a_run_of_zones_has_to_belong_to_the_major_zone_it_is_filed_under():
    assert zones_in(MajorZone.UPPER_FUSELAGE, (231, 232, 241)) == (
        Zone(231),
        Zone(232),
        Zone(241),
    )
    with pytest.raises(ValueError, match="is not in upper fuselage"):
        zones_in(MajorZone.UPPER_FUSELAGE, (231, 531))
