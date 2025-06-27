from datetime import date
from decimal import Decimal

from doublerbay.damage.kind import DamageKind
from doublerbay.measure.length import Length
from doublerbay.structure.build import Region
from doublerbay.structure.panel import PanelKind
from doublerbay.v320 import manual53
from doublerbay.v320.manual57 import PAGES, allowable_damage, manual

PLATE = Length.of_inches("0.160")


def inch(text):
    return Length.of_inches(text)


def look_up(kind, panel=PanelKind.WING_LOWER, region=Region.OPEN_FIELD, revision=10):
    return allowable_damage(revision).lookup(kind, panel, region)


def test_the_chapter_carries_its_own_revision_history():
    assert manual().latest.number == 10
    assert manual().in_force_on(date(2024, 8, 1)).number == 9
    assert manual().latest.number != manual53.manual().latest.number


def test_the_wing_pages_are_the_four_subjects():
    assert {str(page.reference) for page in PAGES} == {
        "57-10-01",
        "57-20-01",
        "57-30-01",
        "57-40-01",
    }


def test_a_dent_is_never_allowable_on_the_lower_surface():
    assert not look_up(DamageKind.DENT, panel=PanelKind.WING_LOWER).limits.allowed


def test_a_dent_on_the_upper_surface_has_a_limit():
    upper = look_up(DamageKind.DENT, panel=PanelKind.WING_UPPER)
    assert upper.limits.allowed
    assert upper.limits.max_depth == inch("0.040")


def test_the_tension_surface_gets_half_of_what_the_compression_surface_gets():
    lower = look_up(DamageKind.SCRATCH, panel=PanelKind.WING_LOWER)
    upper = look_up(DamageKind.SCRATCH, panel=PanelKind.WING_UPPER)
    assert lower.limits.depth_cap(PLATE) * 2 == upper.limits.depth_cap(PLATE)


def test_the_absolute_cap_bites_on_the_heavy_plate_inboard():
    upper = look_up(DamageKind.SCRATCH, panel=PanelKind.WING_UPPER)
    root = upper.limits.depth_cap(inch("0.250"))
    tip = upper.limits.depth_cap(inch("0.100"))
    assert root == inch("0.010")
    assert tip == Length(5)


def test_the_wing_is_tighter_than_the_fuselage_on_the_same_damage():
    wing = look_up(DamageKind.SCRATCH, panel=PanelKind.WING_LOWER)
    barrel = manual53.allowable_damage(12).lookup(
        DamageKind.SCRATCH, PanelKind.PRESSURISED_SKIN, Region.OPEN_FIELD
    )
    assert wing.limits.depth_cap(PLATE) < barrel.limits.depth_cap(PLATE)


def test_the_wing_asks_for_more_edge_margin_than_the_barrel():
    wing = look_up(DamageKind.SCRATCH, panel=PanelKind.WING_UPPER)
    barrel = manual53.allowable_damage(12).lookup(
        DamageKind.SCRATCH, PanelKind.PRESSURISED_SKIN, Region.OPEN_FIELD
    )
    assert wing.limits.min_edge_margin_diameters > barrel.limits.min_edge_margin_diameters


def test_the_wing_blend_is_gentler_than_the_barrel_blend():
    wing = look_up(DamageKind.SCRATCH, panel=PanelKind.WING_UPPER)
    barrel = manual53.allowable_damage(12).lookup(
        DamageKind.SCRATCH, PanelKind.PRESSURISED_SKIN, Region.OPEN_FIELD
    )
    assert wing.limits.blend_taper.satisfies(barrel.limits.blend_taper)


def test_corrosion_on_the_lower_surface_is_capped_on_a_smaller_area():
    lower = look_up(DamageKind.CORROSION, panel=PanelKind.WING_LOWER)
    upper = look_up(DamageKind.CORROSION, panel=PanelKind.WING_UPPER)
    assert lower.limits.max_area == Decimal("1.50")
    assert upper.limits.max_area == Decimal("3.00")


def test_nothing_is_allowable_on_a_row_or_a_corner_on_the_wing_either():
    for region in (Region.FASTENER_ROW, Region.CUTOUT_CORNER, Region.CHEM_MILL_STEP):
        for kind in (DamageKind.SCRATCH, DamageKind.CORROSION, DamageKind.DENT):
            assert not look_up(kind, region=region).limits.allowed


def test_the_penetration_kinds_have_no_allowable_size_on_the_wing():
    for kind in (
        DamageKind.CRACK,
        DamageKind.HOLE,
        DamageKind.PUNCTURE,
        DamageKind.BUCKLE,
        DamageKind.DISBOND,
    ):
        assert not look_up(kind).limits.allowed


def test_every_kind_the_desk_measures_has_a_row_in_the_wing_chapter():
    assert allowable_damage(10).kinds_covered() == frozenset(DamageKind)


def test_a_panel_kind_off_the_wing_falls_to_the_catch_all_row():
    found = look_up(DamageKind.SCRATCH, panel=PanelKind.FAIRING)
    assert found.limits.max_depth == inch("0.005")


def test_the_wing_lightning_limit_is_tighter_than_the_barrel_one():
    wing = look_up(DamageKind.LIGHTNING_BURN)
    barrel = manual53.allowable_damage(12).lookup(
        DamageKind.LIGHTNING_BURN, PanelKind.PRESSURISED_SKIN, Region.OPEN_FIELD
    )
    assert wing.limits.max_depth < barrel.limits.max_depth
    assert wing.limits.max_area < barrel.limits.max_area


def test_the_table_is_built_once_per_revision_and_shared():
    assert allowable_damage(10) is allowable_damage(10)
