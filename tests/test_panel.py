import pytest

from doublerbay.errors import BadReading
from doublerbay.grid.ata import Chapter
from doublerbay.grid.lattice import Bay, Frame, Stringer
from doublerbay.grid.wing import Rib, Spar, Surface
from doublerbay.grid.zone import Side, Zone
from doublerbay.ids import PanelId
from doublerbay.measure.length import Length
from doublerbay.structure.material import material
from doublerbay.structure.panel import FuselagePanel, PanelKind, WingPanel


def inch(text):
    return Length.of_inches(text)


def skin(
    forward=41,
    aft=44,
    upper=(12, Side.LEFT),
    lower=(15, Side.LEFT),
    kind=PanelKind.PRESSURISED_SKIN,
    nominal="0.040",
):
    return FuselagePanel(
        part=PanelId(53, 10, 142, "L"),
        forward=Frame(forward),
        aft=Frame(aft),
        upper=Stringer(*upper),
        lower=Stringer(*lower),
        zone=Zone(231),
        kind=kind,
        material=material("2024-T3 CLAD"),
        nominal=inch(nominal),
    )


def wing(inboard=4, outboard=7, surface=Surface.LOWER, kind=PanelKind.WING_LOWER):
    return WingPanel(
        part=PanelId(57, 10, 22, "L"),
        inboard=Rib(inboard, Side.LEFT),
        outboard=Rib(outboard, Side.LEFT),
        forward=Spar.FRONT,
        aft=Spar.REAR,
        surface=surface,
        zone=Zone(531),
        kind=kind,
        material=material("2024-T351"),
        nominal=inch("0.160"),
    )


def test_a_panel_names_itself_by_part_and_kind():
    assert str(skin()) == "5310-142L (pressurised skin)"
    assert skin().chapter is Chapter.FUSELAGE
    assert skin().side is Side.LEFT


def test_a_panel_covers_a_grid_of_bays():
    assert skin().frame_pitches == 3
    assert skin().stringer_pitches == 3
    assert skin().bay_count == 9
    assert len(list(skin().bays())) == 9


def test_every_bay_it_yields_is_a_single_bay_inside_it():
    panel = skin()
    for bay in panel.bays():
        assert bay.is_single
        assert panel.covers(bay)


def test_the_bays_come_out_forward_to_aft_and_crown_down():
    first, second = list(skin().bays())[:2]
    assert str(first) == "FR41-FR42 / S-12L-S-13L"
    assert str(second) == "FR41-FR42 / S-13L-S-14L"


def test_a_panel_hanging_off_the_crown_yields_the_crown_stringer():
    panel = skin(upper=(0, Side.CENTRE), lower=(2, Side.LEFT))
    assert list(panel.bays())[0].upper == Stringer(0)


def test_a_panel_spans_its_own_boundaries_and_nothing_past_them():
    panel = skin()
    assert panel.spans_frame(Frame(41))
    assert panel.spans_frame(Frame(44))
    assert not panel.spans_frame(Frame(45))
    assert panel.spans_stringer(Stringer(12, Side.LEFT))
    assert not panel.spans_stringer(Stringer(16, Side.LEFT))


def test_a_panel_does_not_span_the_other_hand():
    assert not skin().spans_stringer(Stringer(13, Side.RIGHT))


def test_a_bay_off_the_panel_is_not_covered():
    outside = Bay(Frame(45), Frame(46), Stringer(12, Side.LEFT), Stringer(13, Side.LEFT))
    assert not skin().covers(outside)


def test_a_panel_needs_its_bounds_the_right_way_round():
    with pytest.raises(BadReading, match="is not forward of"):
        skin(forward=44, aft=41)
    with pytest.raises(BadReading, match="is not above"):
        skin(upper=(15, Side.LEFT), lower=(12, Side.LEFT))


def test_a_panel_does_not_straddle_the_crown():
    with pytest.raises(BadReading, match="does not straddle the crown"):
        skin(upper=(2, Side.LEFT), lower=(4, Side.RIGHT))


def test_a_panel_has_a_thickness():
    with pytest.raises(BadReading, match="a panel has a thickness"):
        skin(nominal="0")


def test_a_wing_kind_is_not_a_fuselage_panel():
    with pytest.raises(BadReading, match="is not a fuselage panel"):
        skin(kind=PanelKind.WING_LOWER)


def test_the_pressurised_kinds_know_they_are():
    assert PanelKind.PRESSURISED_SKIN.is_pressurised
    assert PanelKind.BELLY_SKIN.is_pressurised
    assert not PanelKind.WING_LOWER.is_pressurised
    assert not PanelKind.FAIRING.is_pressurised


def test_a_fairing_is_not_primary_structure():
    assert PanelKind.PRESSURISED_SKIN.is_primary
    assert not PanelKind.FAIRING.is_primary


def test_a_wing_panel_covers_a_run_of_rib_bays():
    assert wing().rib_pitches == 3
    assert wing().bay_count == 3
    bays = list(wing().bays())
    assert len(bays) == 3
    assert all(bay.is_tension_critical for bay in bays)


def test_a_wing_panel_names_its_surface_in_its_kind():
    with pytest.raises(BadReading, match="is not on the upper surface"):
        wing(surface=Surface.UPPER, kind=PanelKind.WING_LOWER)


def test_a_fairing_sits_on_either_surface():
    assert wing(kind=PanelKind.FAIRING).kind is PanelKind.FAIRING


def test_a_wing_panel_spans_its_own_ribs_on_its_own_wing():
    panel = wing()
    assert panel.spans_rib(Rib(4, Side.LEFT))
    assert panel.spans_rib(Rib(7, Side.LEFT))
    assert not panel.spans_rib(Rib(8, Side.LEFT))
    assert not panel.spans_rib(Rib(5, Side.RIGHT))


def test_a_wing_panel_needs_its_bounds_the_right_way_round():
    with pytest.raises(BadReading, match="is not inboard of"):
        wing(inboard=7, outboard=4)
