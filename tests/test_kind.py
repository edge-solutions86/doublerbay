import pytest

from doublerbay.damage.kind import (
    DamageKind,
    DamageOrigin,
    check_pairing,
    is_plausible,
    plausible_origins,
)
from doublerbay.errors import BadReading


def test_a_kind_names_itself_the_way_the_form_does():
    assert str(DamageKind.LIGHTNING_BURN) == "lightning burn"
    assert str(DamageOrigin.HANDLING) == "ground handling"


def test_deformation_moves_metal_and_material_loss_removes_it():
    assert DamageKind.DENT.is_deformation
    assert DamageKind.BUCKLE.is_deformation
    assert not DamageKind.DENT.is_material_loss
    assert DamageKind.GOUGE.is_material_loss
    assert not DamageKind.GOUGE.is_deformation


def test_a_crack_is_neither_deformation_nor_material_loss():
    assert not DamageKind.CRACK.is_deformation
    assert not DamageKind.CRACK.is_material_loss


def test_through_thickness_damage_has_nothing_left_to_blend():
    assert DamageKind.HOLE.is_through_thickness
    assert DamageKind.PUNCTURE.is_through_thickness
    assert not DamageKind.GOUGE.is_through_thickness
    assert not DamageKind.CRACK.is_through_thickness


def test_the_kinds_with_no_allowable_limit_at_any_size():
    never = {kind for kind in DamageKind if kind.is_never_allowable}
    assert never == {
        DamageKind.CRACK,
        DamageKind.HOLE,
        DamageKind.PUNCTURE,
        DamageKind.BUCKLE,
    }


def test_the_kinds_that_lie_along_a_direction_need_a_bearing():
    assert DamageKind.CRACK.has_a_bearing
    assert DamageKind.SCRATCH.has_a_bearing
    assert not DamageKind.DENT.has_a_bearing
    assert not DamageKind.CORROSION.has_a_bearing


def test_a_depth_is_asked_for_wherever_metal_moved_or_went():
    assert DamageKind.DENT.needs_depth
    assert DamageKind.CORROSION.needs_depth
    assert not DamageKind.CRACK.needs_depth
    assert not DamageKind.DISBOND.needs_depth


def test_a_patch_of_damage_is_measured_as_an_area():
    assert DamageKind.CORROSION.needs_area
    assert DamageKind.DISBOND.needs_area
    assert DamageKind.LIGHTNING_BURN.needs_area
    assert not DamageKind.DENT.needs_area


def test_every_kind_is_filed_under_a_section():
    for kind in DamageKind:
        assert kind.section in (10, 20, 30, 40, 50)


def test_deformation_and_cracking_are_filed_apart():
    assert DamageKind.DENT.section != DamageKind.CRACK.section
    assert DamageKind.DENT.section == DamageKind.BUCKLE.section
    assert DamageKind.SCRATCH.section == DamageKind.GOUGE.section


def test_an_origin_that_happened_at_a_moment_can_be_looked_up_in_the_log():
    assert DamageOrigin.IMPACT.is_an_event
    assert DamageOrigin.LIGHTNING.is_an_event
    assert not DamageOrigin.FATIGUE.is_an_event


def test_a_progressive_origin_gets_worse_between_inspections():
    assert DamageOrigin.FATIGUE.is_progressive
    assert DamageOrigin.ENVIRONMENT.is_progressive
    assert not DamageOrigin.IMPACT.is_progressive


def test_every_kind_admits_an_unknown_cause():
    for kind in DamageKind:
        assert DamageOrigin.UNKNOWN in plausible_origins(kind)


def test_corrosion_comes_from_the_environment_and_not_from_a_belt_loader():
    assert is_plausible(DamageKind.CORROSION, DamageOrigin.ENVIRONMENT)
    assert not is_plausible(DamageKind.CORROSION, DamageOrigin.HANDLING)


def test_a_crack_can_be_fatigue_or_impact_but_not_weather():
    assert is_plausible(DamageKind.CRACK, DamageOrigin.FATIGUE)
    assert is_plausible(DamageKind.CRACK, DamageOrigin.IMPACT)
    assert not is_plausible(DamageKind.CRACK, DamageOrigin.ENVIRONMENT)


def test_a_lightning_burn_comes_from_lightning():
    assert plausible_origins(DamageKind.LIGHTNING_BURN) == frozenset(
        {DamageOrigin.LIGHTNING, DamageOrigin.UNKNOWN}
    )


def test_a_pairing_that_does_not_go_together_is_refused():
    check_pairing(DamageKind.DENT, DamageOrigin.IMPACT)
    check_pairing(DamageKind.DENT, DamageOrigin.UNKNOWN)
    with pytest.raises(BadReading, match="a dent does not come from lightning"):
        check_pairing(DamageKind.DENT, DamageOrigin.LIGHTNING)
