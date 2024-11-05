import pytest

from doublerbay.measure.gauge import (
    STOCK_LADDER,
    SheetGauge,
    gauge_for,
    identify,
    is_stocked,
    nearest,
    next_heavier,
)
from doublerbay.measure.length import Length


def inch(text):
    return Length.of_inches(text)


def test_the_ladder_climbs():
    nominals = [rung.nominal for rung in STOCK_LADDER]
    assert nominals == sorted(nominals)
    assert len(set(nominals)) == len(nominals)


def test_a_rung_names_itself_by_its_callout():
    assert str(gauge_for(inch("0.040"))) == "0.040"
    assert gauge_for(inch("0.040")).designation == "0.040"


def test_the_band_is_the_nominal_plus_and_minus_the_spread():
    rung = gauge_for(inch("0.040"))
    assert rung.floor == inch("0.036")
    assert rung.ceiling == inch("0.044")


def test_the_band_admits_its_own_edges():
    rung = gauge_for(inch("0.040"))
    assert rung.admits(inch("0.036"))
    assert rung.admits(inch("0.044"))
    assert not rung.admits(inch("0.035"))
    assert not rung.admits(inch("0.045"))


def test_a_thickness_off_the_ladder_is_not_a_gauge():
    assert not is_stocked(inch("0.047"))
    with pytest.raises(ValueError, match="not a stocked gauge"):
        gauge_for(inch("0.047"))


def test_sizing_rounds_up_to_the_next_rung():
    assert next_heavier(inch("0.047")).nominal == inch("0.050")
    assert next_heavier(inch("0.050")).nominal == inch("0.050")
    assert next_heavier(inch("0.001")).nominal == inch("0.016")


def test_nothing_heavier_than_the_top_of_the_ladder():
    with pytest.raises(ValueError, match="heavier than any stocked sheet"):
        next_heavier(inch("0.375"))


def test_nearest_can_go_either_way():
    assert nearest(inch("0.041")).nominal == inch("0.040")
    assert nearest(inch("0.048")).nominal == inch("0.050")


def test_nearest_breaks_a_tie_upwards():
    assert nearest(inch("0.045")).nominal == inch("0.050")


def test_a_reading_inside_one_band_identifies_its_callout():
    assert identify(inch("0.037")).nominal == inch("0.040")


def test_a_reading_two_bands_admit_identifies_nothing():
    overlap = SheetGauge(inch("0.040"), inch("0.010"), inch("0.010"))
    assert overlap.admits(inch("0.045"))
    assert identify(inch("0.100")).nominal == inch("0.100")


def test_a_reading_off_every_band_identifies_nothing():
    assert identify(inch("0.300")) is None
    assert identify(inch("0.001")) is None


def test_a_rung_is_frozen():
    rung = gauge_for(inch("0.040"))
    with pytest.raises(AttributeError):
        rung.nominal = inch("0.050")
