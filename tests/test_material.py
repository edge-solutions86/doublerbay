import pytest

from doublerbay.errors import BadReading, NotOnFile
from doublerbay.measure.length import Length
from doublerbay.structure.material import (
    CATALOGUE,
    Family,
    Material,
    Temper,
    is_carried,
    material,
)


def inch(text):
    return Length.of_inches(text)


def test_a_callout_reads_back_the_way_the_drawing_writes_it():
    assert str(material("2024-T3 CLAD")) == "2024-T3 CLAD"
    assert str(material("7075-T6")) == "7075-T6"


def test_a_callout_is_looked_up_case_insensitively():
    assert material(" 2024-t3 clad ") == material("2024-T3 CLAD")
    assert is_carried("2024-t3 clad")
    assert not is_carried("6061-T6")


def test_a_material_off_the_catalogue_is_a_lookup_miss():
    with pytest.raises(NotOnFile, match="not a material this desk carries"):
        material("6061-T6")


def test_the_catalogue_keys_are_the_callouts():
    for callout, entry in CATALOGUE.items():
        assert str(entry) == callout


def test_the_two_thousand_series_is_the_damage_tolerant_one():
    assert material("2024-T3 CLAD").is_damage_tolerant
    assert not material("7075-T6").is_damage_tolerant
    assert Family.TWO_THOUSAND.is_damage_tolerant
    assert str(Family.SEVEN_THOUSAND) == "7xxx"


def test_a_series_the_desk_does_not_carry_is_refused_when_asked():
    odd = Material("6061", Temper.T6, False, 45, 40)
    with pytest.raises(BadReading, match="not a series this desk carries"):
        odd.family


def test_an_alloy_designation_is_four_digits():
    with pytest.raises(BadReading, match="not an alloy designation"):
        Material("20244", Temper.T3, True, 64, 47)
    with pytest.raises(BadReading, match="not an alloy designation"):
        Material("T3", Temper.T3, True, 64, 47)


def test_a_material_cannot_yield_above_its_ultimate():
    with pytest.raises(BadReading, match="yields above its ultimate"):
        Material("2024", Temper.T3, True, 47, 64)


def test_bare_sheet_carries_no_cladding():
    bare = material("7075-T6")
    assert bare.clad_thickness(inch("0.040")) == Length(0)
    assert bare.bare_below(inch("0.040")) == inch("0.040")
    assert bare.is_through_clad(inch("0.040"), Length(1))


def test_thin_clad_sheet_is_five_percent_a_side():
    clad = material("2024-T3 CLAD")
    assert clad.clad_thickness(inch("0.040")) == Length(2)
    assert clad.bare_below(inch("0.040")) == Length(36)


def test_heavy_clad_sheet_steps_down_to_two_and_a_half_percent():
    clad = material("2024-T3 CLAD")
    assert clad.clad_thickness(inch("0.100")) == Length(2)
    assert clad.clad_thickness(inch("0.062")) == Length(3)


def test_the_step_belongs_to_the_thin_side():
    clad = material("2024-T3 CLAD")
    on_the_step = clad.clad_thickness(inch("0.062"))
    just_over = clad.clad_thickness(inch("0.063"))
    assert on_the_step == Length(3)
    assert just_over == Length(2)


def test_a_blend_inside_the_cladding_has_not_reached_the_alloy():
    clad = material("2024-T3 CLAD")
    assert not clad.is_through_clad(inch("0.040"), Length(2))
    assert clad.is_through_clad(inch("0.040"), Length(3))


def test_the_annealed_temper_names_itself():
    assert Temper.ANNEALED.is_annealed
    assert not Temper.T3.is_annealed
    assert str(Temper.ANNEALED) == "O"
    assert str(material("2024-O CLAD")) == "2024-O CLAD"


def test_a_material_is_frozen_and_hashes_by_value():
    assert len({material("2024-T3 CLAD"), material("2024-T3 CLAD")}) == 1
    with pytest.raises(AttributeError):
        material("2024-T3 CLAD").clad = False
