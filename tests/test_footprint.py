from datetime import date

import pytest

from doublerbay.errors import BadReading
from doublerbay.grid.surface import Offset
from doublerbay.measure.length import Length
from doublerbay.structure.footprint import DoublerForm, Footprint, InstalledDoubler
from doublerbay.structure.material import material


def inch(text):
    return Length.of_inches(text)


def at(along, around):
    return Offset(inch(along), inch(around))


def patch(along="6.000", around="4.000", radius="1.000", centre=("10.000", "10.000")):
    return Footprint(at(*centre), inch(along), inch(around), inch(radius))


def test_a_footprint_reads_as_its_size_and_place():
    assert str(patch()) == "6.000 by 4.000 at 10.000 along, 10.000 around"
    assert patch().area_bound == inch("6.000")


def test_a_footprint_needs_a_size():
    with pytest.raises(BadReading, match="a footprint has a size"):
        patch(along="0")


def test_a_corner_radius_past_the_middle_is_refused():
    with pytest.raises(BadReading, match="past the middle is not a rectangle"):
        patch(radius="2.001")
    with pytest.raises(BadReading, match="does not run backwards"):
        patch(radius="-0.001")


def test_a_square_footprint_has_no_corner_radius():
    square = Footprint.square(at("0", "0"), inch("6.000"), inch("4.000"))
    assert square.corner_radius == Length(0)
    assert square.contains(at("3.000", "2.000"))
    assert not square.contains(at("3.001", "2.000"))


def test_a_point_inside_the_patch_is_contained():
    assert patch().contains(at("10.000", "10.000"))
    assert patch().contains(at("13.000", "10.000"))
    assert not patch().contains(at("13.001", "10.000"))


def test_a_point_off_a_corner_measures_round_the_radius():
    assert patch().distance_to(at("13.000", "12.000")) == Length(414)
    assert patch().distance_to(at("10.000", "10.000")) == Length(0)


def test_growing_a_patch_pushes_every_edge_out():
    grown = patch().grown(inch("0.500"))
    assert grown.along == inch("7.000")
    assert grown.around == inch("5.000")
    assert grown.corner_radius == inch("1.500")
    assert grown.centre == patch().centre


def test_a_patch_does_not_grow_inwards():
    with pytest.raises(BadReading, match="does not grow inwards"):
        patch().grown(inch("-0.100"))


def test_two_patches_far_apart_do_not_overlap():
    near = patch()
    far = patch(centre=("30.000", "10.000"))
    assert not near.overlaps(far)
    assert near.gap_along(far) == inch("14.000")
    assert near.gap_around(far) == Length(0)


def test_two_patches_sharing_skin_overlap():
    near = patch()
    on_top = patch(centre=("12.000", "10.000"))
    assert near.overlaps(on_top)
    assert near.clearance_to(on_top) == Length(0)


def test_patches_edge_to_edge_are_touching_and_not_clear():
    near = patch()
    beside = patch(centre=("16.000", "10.000"))
    assert near.overlaps(beside)
    assert near.clearance_to(beside) == Length(0)


def test_the_clearance_between_two_patches_is_the_shortest_gap():
    near = patch()
    beside = patch(centre=("20.000", "10.000"))
    assert near.clearance_to(beside) == inch("4.000")
    assert near.clears(beside, inch("4.000"))
    assert not near.clears(beside, inch("4.001"))


def test_a_diagonal_gap_takes_both_components():
    near = patch()
    off_corner = patch(centre=("19.000", "17.000"))
    assert near.gap_along(off_corner) == inch("3.000")
    assert near.gap_around(off_corner) == inch("3.000")
    assert near.clearance_to(off_corner) == Length(4243)


def test_a_patch_beside_but_overlapping_on_one_axis_gaps_on_the_other_only():
    near = patch()
    beside = patch(centre=("20.000", "11.000"))
    assert near.gap_around(beside) == Length(0)
    assert near.clearance_to(beside) == inch("4.000")


def test_the_forms_say_what_they_do_to_the_airflow():
    assert DoublerForm.EXTERNAL.is_in_airflow
    assert not DoublerForm.INTERNAL.is_in_airflow
    assert DoublerForm.FLUSH.is_flush
    assert str(DoublerForm.EXTERNAL) == "external"


def doubler(part="RP-53-0142-1", gauge="0.050"):
    return InstalledDoubler(
        part=part,
        form=DoublerForm.EXTERNAL,
        footprint=patch(),
        material=material("2024-T3 CLAD"),
        gauge=inch(gauge),
        fitted=date(2023, 6, 14),
    )


def test_an_installed_doubler_reads_as_its_part_form_and_gauge():
    assert str(doubler()) == "RP-53-0142-1 external 0.050"


def test_an_installed_doubler_needs_a_part_number_and_a_thickness():
    with pytest.raises(BadReading, match="has a part number"):
        doubler(part="  ")
    with pytest.raises(BadReading, match="a doubler has a thickness"):
        doubler(gauge="0")
