from decimal import Decimal

import pytest

from doublerbay.damage.kind import DamageKind
from doublerbay.errors import BadReading, NotOnFile
from doublerbay.manual.cleanup import NO_BLEND_REGIONS, CleanupRule, CleanupRules, standard_rules
from doublerbay.measure.length import Length
from doublerbay.measure.ratio import Taper
from doublerbay.structure.build import Region


def inch(text):
    return Length.of_inches(text)


def rule(taper=None, blendable=True, radius="0.250", fraction="0.25"):
    return CleanupRule(
        taper or Taper(20),
        blendable,
        inch(radius) if radius else None,
        Decimal(fraction) if fraction else None,
    )


def test_a_rule_reads_back_how_the_blend_runs():
    assert str(rule()) == "blend at 20:1, up to 25.00% of the panel"
    assert str(rule(blendable=False, radius=None, fraction=None)) == "not blendable"


def test_a_kind_that_cannot_be_blended_carries_no_radius():
    with pytest.raises(BadReading, match="carries no blend radius"):
        rule(blendable=False)


def test_a_blend_fraction_is_a_part_of_the_panel():
    with pytest.raises(BadReading, match="is a part of the panel"):
        rule(fraction="1.5")
    with pytest.raises(BadReading, match="is a part of the panel"):
        rule(fraction="0")


def test_the_runout_comes_off_the_taper_and_the_depth():
    assert rule().runout(inch("0.010")) == inch("0.200")
    assert rule(taper=Taper(30)).runout(inch("0.010")) == inch("0.300")


def test_a_depth_that_runs_backwards_is_refused():
    with pytest.raises(BadReading, match="does not run backwards"):
        rule().runout(inch("-0.001"))


def test_the_blend_covers_the_damage_plus_a_runout_at_each_end():
    assert rule().blend_extent(inch("2.000"), inch("0.010")) == inch("2.400")


def test_a_blend_is_refused_on_a_chem_mill_step_at_any_depth():
    assert not rule().allowed_in(Region.CHEM_MILL_STEP)
    assert Region.CHEM_MILL_STEP in NO_BLEND_REGIONS


def test_a_blend_is_refused_on_a_fastener_row_and_under_a_doubler():
    assert not rule().allowed_in(Region.FASTENER_ROW)
    assert not rule().allowed_in(Region.UNDER_DOUBLER)


def test_a_blend_is_allowed_in_the_open_field_and_on_a_land():
    assert rule().allowed_in(Region.OPEN_FIELD)
    assert rule().allowed_in(Region.LAND)


def test_a_kind_that_cannot_be_blended_is_not_blendable_anywhere():
    never = rule(blendable=False, radius=None, fraction=None)
    assert not never.allowed_in(Region.OPEN_FIELD)


def test_a_blend_inside_the_panel_share_is_allowed_and_past_it_is_not():
    assert rule().within_panel_share(inch("5.000"), inch("20.000"))
    assert rule().within_panel_share(inch("5.000"), inch("20.001"))
    assert not rule().within_panel_share(inch("6.000"), inch("20.000"))


def test_a_rule_with_no_share_limit_never_takes_too_much():
    open_rule = rule(fraction=None)
    assert open_rule.within_panel_share(inch("19.000"), inch("20.000"))


def test_a_panel_with_no_extent_cannot_be_shared_into():
    with pytest.raises(BadReading, match="a panel extent is a length"):
        rule().within_panel_share(inch("1.000"), inch("0"))


def test_the_standard_rules_cover_every_kind_the_desk_measures():
    rules = standard_rules()
    for kind in DamageKind:
        assert rules.covers(kind)
    assert rules.kinds() == frozenset(DamageKind)


def test_the_kinds_with_no_allowable_size_are_also_not_blendable():
    rules = standard_rules()
    for kind in DamageKind:
        if kind.is_never_allowable:
            assert not rules.for_kind(kind).blendable


def test_a_dent_is_not_blended_out_because_it_has_lost_no_metal():
    assert not standard_rules().for_kind(DamageKind.DENT).blendable


def test_the_material_loss_kinds_are_the_ones_that_get_blended():
    rules = standard_rules()
    for kind in (DamageKind.SCRATCH, DamageKind.GOUGE, DamageKind.NICK, DamageKind.CORROSION):
        assert rules.for_kind(kind).blendable


def test_corrosion_gets_the_gentler_taper_and_the_larger_share():
    rules = standard_rules()
    corrosion = rules.for_kind(DamageKind.CORROSION)
    scratch = rules.for_kind(DamageKind.SCRATCH)
    assert corrosion.taper.satisfies(scratch.taper)
    assert corrosion.max_blend_fraction > scratch.max_blend_fraction


def test_a_nick_is_held_to_the_smallest_share_of_the_panel():
    rules = standard_rules()
    assert rules.for_kind(DamageKind.NICK).max_blend_fraction == Decimal("0.10")


def test_a_kind_the_book_has_no_rule_for_is_a_lookup_miss():
    thin = CleanupRules({DamageKind.DENT: rule(blendable=False, radius=None, fraction=None)})
    assert not thin.covers(DamageKind.SCRATCH)
    with pytest.raises(NotOnFile, match="no cleanup rule for a scratch"):
        thin.for_kind(DamageKind.SCRATCH)


def test_the_rules_read_back_how_many_kinds_they_cover():
    assert str(standard_rules()) == "cleanup rules for 11 kinds"
