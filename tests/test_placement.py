import pytest

import sample
from doublerbay.assess.placement import place
from doublerbay.errors import BadReading
from doublerbay.grid.ata import Chapter
from doublerbay.measure.fastener import FastenerSize
from doublerbay.structure.build import Region
from doublerbay.structure.panel import PanelKind


def inch(text):
    return sample.inch(text)


def placed(**point):
    """Place a card at a cabin point, clear of the window line unless asked."""
    return place(sample.tail_context(), sample.card(at=sample.cabin_point(**point)))


def test_a_cabin_card_places_onto_pressurised_skin():
    found = place(sample.tail_context(), sample.card())
    assert found.panel_kind is PanelKind.PRESSURISED_SKIN
    assert found.chapter is Chapter.FUSELAGE
    assert not found.is_on_the_wing


def test_the_thickness_is_read_at_the_damage_and_not_off_the_drawing():
    on_the_land = placed(stringer=10, down="0.000")
    in_the_bay = placed(stringer=10, down="4.000")
    assert on_the_land.thickness > in_the_bay.thickness


def test_the_region_comes_off_the_same_resolved_point_as_the_thickness():
    on_the_land = placed(stringer=10, down="0.000")
    assert on_the_land.region is Region.LAND
    in_the_bay = placed(stringer=10, down="4.000")
    assert in_the_bay.region in (Region.OPEN_FIELD, Region.CHEM_MILL_STEP)


def test_a_card_on_a_lap_joint_places_onto_its_rows_and_names_the_fastener():
    on_the_joint = sample.cabin_point(stringer=8, down="0.500")
    found = place(sample.tail_context(), sample.card(at=on_the_joint))
    assert found.region is Region.FASTENER_ROW
    assert found.nearest_fastener == FastenerSize(5)


def test_a_card_away_from_a_joint_names_no_fastener():
    found = placed(stringer=10, down="4.000")
    assert found.nearest_fastener is None


def test_a_card_by_a_window_places_into_its_keep_out():
    by_the_window = sample.cabin_point(frame=20, aft="16.000", stringer=7, down="0.000")
    found = place(sample.tail_context(), sample.card(at=by_the_window))
    assert found.region in (Region.CUTOUT_EDGE, Region.CUTOUT_CORNER)


def test_the_tail_cone_places_onto_unpressurised_skin():
    found = place(sample.tail_context(), sample.card(at=sample.cabin_point(frame=50)))
    assert found.panel_kind is PanelKind.UNPRESSURISED_SKIN


def test_a_wing_card_places_onto_the_lower_surface():
    found = place(sample.tail_context(), sample.card(at=sample.lower_wing_point()))
    assert found.panel_kind is PanelKind.WING_LOWER
    assert found.chapter is Chapter.WINGS
    assert found.is_on_the_wing


def test_a_wing_card_reads_the_plate_gauge_at_the_damage():
    root = place(sample.tail_context(), sample.card(at=sample.lower_wing_point(rib=2)))
    outboard = place(sample.tail_context(), sample.card(at=sample.lower_wing_point(rib=22)))
    assert root.thickness > outboard.thickness


def test_a_wing_card_on_the_land_reads_the_full_plate():
    on_land = place(
        sample.tail_context(), sample.card(at=sample.lower_wing_point(outboard="1.000"))
    )
    assert on_land.region is Region.LAND
    assert on_land.thickness == inch("0.250")


def test_a_wing_card_names_no_fastener_because_the_box_carries_no_joints():
    found = place(sample.tail_context(), sample.card(at=sample.lower_wing_point()))
    assert found.nearest_fastener is None


def test_the_panel_extent_is_the_longer_side_of_the_panel():
    found = place(sample.tail_context(), sample.card())
    assert found.panel_extent.mils > 0
    assert found.panel_extent >= inch("30.000")


def test_the_material_comes_off_the_panel_the_damage_is_on():
    barrel = place(sample.tail_context(), sample.card())
    wing = place(sample.tail_context(), sample.card(at=sample.lower_wing_point()))
    assert barrel.material.clad
    assert wing.material.alloy == "2024"


def test_a_card_raised_on_another_tail_is_refused():
    with pytest.raises(BadReading, match="is not a card on"):
        place(sample.tail_context(), sample.card(serial="5240"))


def test_the_placement_reads_back_the_part_region_and_thickness():
    printed = str(place(sample.tail_context(), sample.card()))
    assert printed.startswith("5310-")
    assert "thick" in printed


def test_the_edge_margin_is_the_governing_one_and_not_the_member_margin():
    found = placed(stringer=10, down="0.000")
    assert found.edge_margin.mils >= 0
