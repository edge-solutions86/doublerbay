from datetime import date
from decimal import Decimal

from doublerbay.damage.kind import DamageKind
from doublerbay.measure.length import Length
from doublerbay.measure.ratio import Ratio
from doublerbay.structure.build import Region
from doublerbay.structure.panel import PanelKind
from doublerbay.v320.manual53 import (
    DENT_CAMPAIGN_REVISION,
    PAGES,
    allowable_damage,
    manual,
)

SKIN = Length.of_inches("0.063")


def inch(text):
    return Length.of_inches(text)


def look_up(kind, panel=PanelKind.PRESSURISED_SKIN, region=Region.OPEN_FIELD, revision=12):
    return allowable_damage(revision).lookup(kind, panel, region)


def test_the_chapter_carries_its_revision_history():
    assert manual().latest.number == 13
    assert manual().in_force_on(date(2024, 6, 1)).number == 11
    assert manual().in_force_on(date(2025, 1, 1)).number == 12


def test_the_pages_are_the_four_subjects_the_desk_reads():
    titles = {str(page.reference) for page in PAGES}
    assert titles == {"53-10-01", "53-20-01", "53-30-01", "53-30-02", "53-40-01"}


def test_the_lightning_page_arrived_a_revision_after_the_others():
    lightning = next(page for page in PAGES if str(page.reference) == "53-30-02")
    assert not lightning.is_live_at(9)
    assert lightning.is_live_at(10)


def test_the_table_is_built_once_per_revision_and_shared():
    assert allowable_damage(12) is allowable_damage(12)
    assert allowable_damage(12) is not allowable_damage(11)


def test_a_dent_on_ordinary_pressurised_skin_gets_the_pressurised_row():
    found = look_up(DamageKind.DENT)
    assert found.limits.max_depth == inch("0.060")
    assert found.limits.max_sharpness == Ratio("0.05")


def test_the_tail_cone_gets_the_loosest_dent_limits():
    loose = look_up(DamageKind.DENT, panel=PanelKind.UNPRESSURISED_SKIN)
    tight = look_up(DamageKind.DENT)
    assert loose.limits.max_depth > tight.limits.max_depth
    assert loose.limits.max_sharpness > tight.limits.max_sharpness


def test_the_crown_sits_between_the_two():
    crown = look_up(DamageKind.DENT, panel=PanelKind.CROWN_SKIN)
    assert crown.limits.max_depth == inch("0.080")


def test_the_campaign_revision_tightened_the_pressurised_dent_limits():
    before = look_up(DamageKind.DENT, revision=DENT_CAMPAIGN_REVISION - 1)
    after = look_up(DamageKind.DENT, revision=DENT_CAMPAIGN_REVISION)
    assert before.limits.max_depth == inch("0.080")
    assert after.limits.max_depth == inch("0.060")


def test_the_campaign_left_the_other_skins_alone():
    before = look_up(DamageKind.DENT, panel=PanelKind.CROWN_SKIN, revision=11)
    after = look_up(DamageKind.DENT, panel=PanelKind.CROWN_SKIN, revision=12)
    assert before.limits.max_depth == after.limits.max_depth


def test_nothing_is_allowable_on_a_fastener_row():
    for kind in (
        DamageKind.DENT,
        DamageKind.SCRATCH,
        DamageKind.GOUGE,
        DamageKind.NICK,
        DamageKind.CORROSION,
        DamageKind.LIGHTNING_BURN,
    ):
        found = look_up(kind, region=Region.FASTENER_ROW)
        assert not found.limits.allowed


def test_nothing_is_allowable_in_a_cutout_corner_or_on_a_chem_mill_step():
    for region in (Region.CUTOUT_CORNER, Region.CHEM_MILL_STEP):
        assert not look_up(DamageKind.DENT, region=region).limits.allowed


def test_a_score_is_capped_as_a_share_of_the_thickness_at_the_damage():
    found = look_up(DamageKind.SCRATCH)
    assert found.limits.depth_cap(SKIN) == Length(3)
    assert found.limits.depth_cap(inch("0.160")) == Length(8)


def test_a_gouge_is_held_tighter_than_a_scratch_on_open_skin():
    gouge = look_up(DamageKind.GOUGE, panel=PanelKind.UNPRESSURISED_SKIN)
    scratch = look_up(DamageKind.SCRATCH, panel=PanelKind.UNPRESSURISED_SKIN)
    assert gouge.limits.depth_cap(SKIN) < scratch.limits.depth_cap(SKIN)


def test_every_score_row_carries_the_blend_taper_and_the_edge_margin():
    for kind in (DamageKind.SCRATCH, DamageKind.GOUGE, DamageKind.NICK):
        found = look_up(kind)
        assert found.limits.blend_taper is not None
        assert found.limits.min_edge_margin_diameters == Decimal("2.0")


def test_corrosion_is_capped_on_area_as_well_as_depth():
    found = look_up(DamageKind.CORROSION)
    assert found.limits.max_area == Decimal("4.00")
    assert found.limits.depth_cap(SKIN) == Length(3)


def test_corrosion_gets_a_gentler_blend_than_a_scratch():
    corrosion = look_up(DamageKind.CORROSION)
    scratch = look_up(DamageKind.SCRATCH)
    assert corrosion.limits.blend_taper.satisfies(scratch.limits.blend_taper)


def test_a_lightning_burn_is_capped_absolutely_and_not_as_a_share():
    found = look_up(DamageKind.LIGHTNING_BURN)
    assert found.limits.max_depth == inch("0.010")
    assert found.limits.max_depth_fraction is None


def test_the_penetration_kinds_have_no_allowable_size_anywhere():
    for kind in (
        DamageKind.CRACK,
        DamageKind.HOLE,
        DamageKind.PUNCTURE,
        DamageKind.BUCKLE,
        DamageKind.DISBOND,
    ):
        for panel in (PanelKind.PRESSURISED_SKIN, PanelKind.UNPRESSURISED_SKIN):
            assert not look_up(kind, panel=panel).limits.allowed


def test_every_kind_the_desk_measures_has_a_row_somewhere_in_the_chapter():
    table = allowable_damage(12)
    assert table.kinds_covered() == frozenset(DamageKind)


def test_a_panel_kind_with_no_row_of_its_own_falls_to_the_catch_all():
    found = look_up(DamageKind.DENT, panel=PanelKind.FAIRING, region=Region.CUTOUT_EDGE)
    assert found.limits.max_depth == inch("0.100")
