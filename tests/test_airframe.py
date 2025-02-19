import pytest

import sample
from doublerbay.errors import BadReading, NotOnFile
from doublerbay.grid.zone import Zone
from doublerbay.ids import PanelId
from doublerbay.structure.airframe import Airframe
from doublerbay.structure.build import Region


def inch(text):
    return sample.inch(text)


def test_an_airframe_reads_as_its_model_and_how_much_is_on_it():
    assert str(sample.airframe()) == "V320-200, 3 panels"
    assert sample.airframe().bay_count == 18


def test_an_airframe_needs_a_model_and_some_panels():
    with pytest.raises(BadReading, match="an airframe has a model"):
        Airframe("  ", sample.grid(), (sample.forward_upper(),))
    with pytest.raises(BadReading, match="has panels on it"):
        Airframe("V320-200", sample.grid(), ())


def test_two_panels_with_the_same_part_number_are_refused():
    with pytest.raises(BadReading, match="same part number"):
        Airframe("V320-200", sample.grid(), (sample.forward_upper(), sample.forward_upper()))


def test_a_panel_is_found_by_its_part_number():
    build = sample.airframe().build_for(PanelId(53, 10, 142, "L"))
    assert build.panel.part == PanelId(53, 10, 142, "L")


def test_a_part_number_not_on_the_type_is_a_lookup_miss():
    with pytest.raises(NotOnFile, match="is not a panel on the V320-200"):
        sample.airframe().build_for(PanelId(53, 10, 999, "L"))


def test_a_written_point_lands_on_the_panel_that_covers_its_bay():
    build = sample.airframe().panel_at(sample.point())
    assert build.panel.part == PanelId(53, 10, 142, "L")


def test_a_point_further_aft_lands_on_the_next_panel_back():
    build = sample.airframe().panel_at(sample.point(frame=43, aft="5.000"))
    assert build.panel.part == PanelId(53, 10, 143, "L")


def test_a_point_further_down_lands_on_the_lower_panel():
    build = sample.airframe().panel_at(sample.point(stringer=5))
    assert build.panel.part == PanelId(53, 10, 152, "L")


def test_a_point_on_no_panel_is_a_lookup_miss():
    with pytest.raises(NotOnFile, match="covers"):
        sample.airframe().panel_at(sample.point(frame=46))


def test_resolving_gives_the_bay_the_panel_the_spot_and_the_margins_at_once():
    resolved = sample.airframe().resolve(sample.point())
    assert str(resolved.bay) == "FR41-FR42 / S-2L-S-3L"
    assert resolved.part == PanelId(53, 10, 142, "L")
    assert resolved.spot.region is Region.OPEN_FIELD
    assert resolved.margin.distance == inch("4.000")
    assert resolved.margins.margin == inch("10.000")


def test_the_member_margin_and_the_governing_edge_are_two_different_numbers():
    resolved = sample.airframe().resolve(sample.point())
    assert resolved.margin.distance == inch("4.000")
    assert resolved.margins.governing.to == "FR41"


def test_the_spot_and_the_margin_are_taken_off_the_same_resolved_point():
    resolved = sample.airframe().resolve(sample.point())
    assert resolved.spot.thickness == inch("0.040")
    assert str(resolved.margin.member) == "S-2L"


def test_a_point_on_the_lap_joint_resolves_onto_its_rows():
    resolved = sample.airframe().resolve(sample.point(stringer=4, down="0.500"))
    assert resolved.spot.region is Region.FASTENER_ROW
    assert "lap joint" in resolved.spot.feature


def test_a_point_by_the_window_resolves_into_its_keep_out():
    resolved = sample.airframe().resolve(sample.point(frame=42, aft="11.000", down="4.000"))
    assert resolved.spot.region is Region.CUTOUT_EDGE
    assert resolved.spot.feature == "W12L"


def test_a_point_over_the_old_doubler_resolves_under_it():
    resolved = sample.airframe().resolve(sample.point(stringer=5, down="4.000"))
    assert resolved.spot.region is Region.UNDER_DOUBLER
    assert resolved.spot.feature == "RP-53-0007-1"


def test_a_resolved_point_reads_back_as_part_bay_and_spot():
    resolved = sample.airframe().resolve(sample.point())
    assert str(resolved).startswith("5310-142L FR41-FR42 / S-2L-S-3L, in open field")


def test_the_zones_are_what_the_panels_say_they_are():
    assert sample.airframe().zones == frozenset({Zone(231), Zone(131)})
    assert len(sample.airframe().in_zone(Zone(231))) == 2
    assert len(sample.airframe().in_zone(Zone(131))) == 1
    assert sample.airframe().in_zone(Zone(331)) == ()
