import pytest

from doublerbay.errors import BadReading
from doublerbay.grid.lattice import Bay, Frame, Stringer
from doublerbay.grid.zone import Side
from doublerbay.measure.length import Length
from doublerbay.measure.ratio import Taper
from doublerbay.structure.thickness import Pad, ThicknessMap


def inch(text):
    return Length.of_inches(text)


def bay(forward=41):
    return Bay(Frame(forward), Frame(forward + 1), Stringer(12, Side.LEFT), Stringer(13, Side.LEFT))


def milled(land="0.063", pocket="0.040", width="1.000", step=None):
    return ThicknessMap(inch(land), inch(pocket), inch(width), step or Taper(20))


def test_a_milled_panel_reads_as_its_two_gauges():
    assert str(milled()) == "0.063 land / 0.040 pocket"
    assert not milled().is_flat


def test_an_unmilled_panel_is_one_gauge_everywhere():
    flat = ThicknessMap.flat(inch("0.050"))
    assert flat.is_flat
    assert str(flat) == "flat 0.050"
    assert flat.at(inch("0")) == inch("0.050")
    assert flat.at(inch("10")) == inch("0.050")


def test_the_step_run_comes_from_the_depth_and_the_taper():
    assert milled().step_depth == inch("0.023")
    assert milled().step_run == inch("0.460")
    assert milled().pocket_starts_at == inch("1.460")


def test_a_gentler_step_runs_out_further():
    assert milled(step=Taper(40)).step_run == inch("0.920")


def test_the_land_holds_full_gauge_out_to_its_own_width():
    panel = milled()
    assert panel.at(inch("0")) == inch("0.063")
    assert panel.at(inch("1.000")) == inch("0.063")


def test_the_pocket_floor_starts_where_the_taper_ends():
    panel = milled()
    assert panel.at(inch("1.460")) == inch("0.040")
    assert panel.at(inch("3.000")) == inch("0.040")


def test_the_taper_falls_between_the_two():
    panel = milled()
    on_taper = panel.at(inch("1.230"))
    assert inch("0.040") < on_taper < inch("0.063")
    assert on_taper == Length(51)


def test_the_step_is_where_the_manual_will_not_let_you_blend():
    panel = milled()
    assert not panel.on_the_step(inch("1.000"))
    assert panel.on_the_step(inch("1.001"))
    assert panel.on_the_step(inch("1.459"))
    assert not panel.on_the_step(inch("1.460"))


def test_a_flat_panel_has_no_step_to_be_on():
    assert not ThicknessMap.flat(inch("0.050")).on_the_step(inch("2.000"))


def test_a_margin_measured_the_wrong_way_is_refused():
    with pytest.raises(BadReading, match="measured in from a member"):
        milled().at(inch("-0.100"))


def test_a_pocket_thicker_than_its_land_is_refused():
    with pytest.raises(BadReading, match="is thicker than land"):
        ThicknessMap(inch("0.040"), inch("0.063"), inch("1.000"))


def test_a_map_with_no_thickness_is_refused():
    with pytest.raises(BadReading, match="a land has a thickness"):
        ThicknessMap(inch("0"), inch("0"), inch("1.000"))
    with pytest.raises(BadReading, match="a pocket has a thickness"):
        ThicknessMap(inch("0.063"), inch("0"), inch("1.000"))


def test_a_pad_overrides_the_mill_across_its_whole_bay():
    panel = milled().with_pad(Pad("fitting pad", bay(), inch("0.100")))
    assert panel.at(inch("3.000"), bay()) == inch("0.100")
    assert panel.at(inch("0"), bay()) == inch("0.100")


def test_a_pad_on_another_bay_changes_nothing_here():
    panel = milled().with_pad(Pad("fitting pad", bay(50), inch("0.100")))
    assert panel.at(inch("3.000"), bay()) == inch("0.040")
    assert panel.pad_on(bay()) is None
    assert panel.pad_on(None) is None


def test_a_pad_thinner_than_the_land_is_not_a_pad():
    with pytest.raises(BadReading, match="is thinner than the land"):
        milled().with_pad(Pad("fitting pad", bay(), inch("0.050")))


def test_a_pad_needs_a_name_and_a_thickness():
    with pytest.raises(BadReading, match="a pad has a name"):
        Pad("  ", bay(), inch("0.100"))
    with pytest.raises(BadReading, match="a pad has a thickness"):
        Pad("fitting pad", bay(), inch("0"))


def test_a_pad_reads_back_where_it_sits():
    assert str(Pad("fitting pad", bay(), inch("0.100"))).startswith("fitting pad 0.100 at FR41")


def test_adding_a_pad_leaves_the_original_map_alone():
    panel = milled()
    panel.with_pad(Pad("fitting pad", bay(), inch("0.100")))
    assert panel.pads == ()
