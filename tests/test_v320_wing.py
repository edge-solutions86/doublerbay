import pytest

from doublerbay.errors import NotOnFile
from doublerbay.grid.location import WingPoint
from doublerbay.grid.wing import Rib, Spar, Surface, WingStation
from doublerbay.grid.zone import Side
from doublerbay.measure.length import Length
from doublerbay.structure.panel import PanelKind
from doublerbay.v320.wing import (
    RIB_COUNT,
    chord_at_rib,
    grid,
    material_of,
    station_of_rib,
    wing_box,
    zone_of,
)


def inch(text):
    return Length.of_inches(text)


def point(rib=1, outboard="10.000", spar=Spar.FRONT, aft="10.000", surface=Surface.LOWER):
    return WingPoint(Rib(rib, Side.LEFT), inch(outboard), spar, inch(aft), surface)


def test_the_root_rib_is_at_the_side_of_body():
    assert station_of_rib(1) == WingStation.of_inches("70")
    assert station_of_rib(2) == WingStation.of_inches("92")
    assert station_of_rib(RIB_COUNT) == WingStation.of_inches("620")


def test_the_chord_tapers_from_root_to_tip():
    assert chord_at_rib(1) == inch("240.000")
    assert chord_at_rib(RIB_COUNT) == inch("78.000")
    assert chord_at_rib(1) > chord_at_rib(13) > chord_at_rib(RIB_COUNT)


def test_the_taper_is_a_straight_line():
    drops = [chord_at_rib(n).mils - chord_at_rib(n + 1).mils for n in range(1, RIB_COUNT)]
    assert max(drops) - min(drops) <= 1


def test_the_upper_surface_is_the_strong_alloy_and_the_lower_is_the_tough_one():
    assert material_of(Surface.UPPER).alloy == "7075"
    assert material_of(Surface.LOWER).alloy == "2024"
    assert material_of(Surface.LOWER).is_damage_tolerant
    assert not material_of(Surface.UPPER).is_damage_tolerant


def test_the_zones_separate_hand_and_surface():
    assert zone_of(Surface.LOWER, Side.LEFT, 0) != zone_of(Surface.UPPER, Side.LEFT, 0)
    assert zone_of(Surface.LOWER, Side.LEFT, 0) != zone_of(Surface.LOWER, Side.RIGHT, 0)
    assert zone_of(Surface.LOWER, Side.LEFT, 0).is_wing


def test_the_grid_carries_every_rib_on_its_own_wing():
    left = grid(Side.LEFT)
    assert len(left.ribs) == RIB_COUNT
    assert left.station_of(Rib(5, Side.LEFT)) == WingStation.of_inches("158")
    with pytest.raises(NotOnFile, match="is not on this wing grid"):
        left.station_of(Rib(5, Side.RIGHT))


def test_the_spars_sit_at_their_fraction_of_the_local_chord():
    left = grid(Side.LEFT)
    assert left.spar_at(Spar.FRONT, inch("70.000")) == inch("36.000")
    assert left.spar_at(Spar.REAR, inch("70.000")) == inch("156.000")
    assert left.spar_at(Spar.FRONT, inch("620.000")) == inch("11.700")


def test_the_box_is_panelled_on_both_surfaces_and_both_wings():
    box = wing_box()
    assert len(box.panels) == 28
    assert box.surfaces() == frozenset({Surface.LOWER, Surface.UPPER})
    left = [b for b in box.panels if b.panel.side is Side.LEFT]
    assert len(left) == 14


def test_every_wing_panel_number_is_its_own():
    parts = [build.panel.part for build in wing_box().panels]
    assert len(set(parts)) == len(parts)


def test_the_box_is_shared_rather_than_rebuilt():
    assert wing_box() is wing_box()


def test_a_root_point_lands_on_the_heaviest_plate():
    build = wing_box().panel_at(point())
    assert build.panel.nominal == inch("0.250")
    assert build.panel.kind is PanelKind.WING_LOWER


def test_an_outboard_point_lands_on_thinner_plate():
    build = wing_box().panel_at(point(rib=22))
    assert build.panel.nominal == inch("0.100")


def test_the_two_surfaces_are_different_panels_at_the_same_place():
    lower = wing_box().panel_at(point())
    upper = wing_box().panel_at(point(surface=Surface.UPPER))
    assert lower.panel.part != upper.panel.part
    assert lower.panel.surface is Surface.LOWER
    assert upper.panel.surface is Surface.UPPER


def test_a_wing_panel_is_machined_with_a_pocket():
    build = wing_box().panel_at(point())
    assert not build.thickness.is_flat
    assert build.thickness.pocket == inch("0.150")
    assert build.thickness.land == inch("0.250")


def test_the_margin_at_the_root_is_measured_off_the_nearest_member():
    margin = grid(Side.LEFT).margin_of(point())
    assert margin.member in (Rib(1, Side.LEFT), Spar.FRONT)
    assert margin.distance == inch("10.000")


def test_a_point_past_the_tip_rib_is_off_the_wing():
    with pytest.raises(ValueError, match="is off the ribs"):
        grid(Side.LEFT).bay_of(point(rib=RIB_COUNT, outboard="10.000"))
