import pytest

from doublerbay.grid.ata import Chapter
from doublerbay.grid.lattice import Frame, Stringer
from doublerbay.grid.location import FuselagePoint, WingPoint, chapter_of, same_side
from doublerbay.grid.wing import Rib, Spar, Surface
from doublerbay.grid.zone import Side
from doublerbay.measure.length import Length


def inch(text):
    return Length.of_inches(text)


def barrel(aft="12.5", down="3.0", stringer=(14, Side.LEFT), frame=41):
    return FuselagePoint(Frame(frame), inch(aft), Stringer(*stringer), inch(down))


def panel(outboard="8.0", aft="14.0", surface=Surface.LOWER, rib=(4, Side.LEFT)):
    return WingPoint(Rib(*rib), inch(outboard), Spar.FRONT, inch(aft), surface)


def test_a_barrel_point_reads_back_the_way_it_was_written():
    assert str(barrel()) == "FR41 12.500 aft, S-14L 3.000 down"


def test_an_offset_the_other_way_says_so():
    assert str(barrel(aft="-6.0", down="-2.0")) == "FR41 6.000 fwd, S-14L 2.000 up"


def test_a_point_sitting_on_a_member_says_on():
    assert str(barrel(aft="0", down="0")) == "FR41 on, S-14L on"
    assert barrel(aft="0", down="0").is_on_a_frame
    assert barrel(aft="0", down="0").is_on_a_stringer


def test_a_point_off_a_member_is_not_on_it():
    assert not barrel().is_on_a_frame
    assert not barrel().is_on_a_stringer


def test_a_barrel_point_takes_its_hand_from_the_stringer():
    assert barrel().side is Side.LEFT
    assert barrel(stringer=(14, Side.RIGHT)).side is Side.RIGHT


def test_a_barrel_point_belongs_to_the_fuselage_chapter():
    assert barrel().chapter is Chapter.FUSELAGE
    assert chapter_of(barrel()) is Chapter.FUSELAGE


def test_an_offset_past_every_bay_on_the_aircraft_is_a_bad_reading():
    with pytest.raises(ValueError, match="aft of frame is further than any bay"):
        barrel(aft="61.0")
    with pytest.raises(ValueError, match="below stringer is further than any bay"):
        barrel(down="-61.0")


def test_mirroring_a_barrel_point_keeps_the_offsets():
    mirrored = barrel().mirrored()
    assert mirrored.stringer == Stringer(14, Side.RIGHT)
    assert mirrored.aft_of_frame == inch("12.5")
    assert mirrored.below_stringer == inch("3.0")


def test_a_wing_point_reads_back_with_its_surface():
    assert str(panel()) == "RIB4L 8.000 outbd, front spar 14.000 aft, lower"


def test_a_wing_point_belongs_to_the_wing_chapter():
    assert panel().chapter is Chapter.WINGS
    assert chapter_of(panel()) is Chapter.WINGS


def test_a_wing_point_takes_its_hand_from_the_rib():
    assert panel().side is Side.LEFT
    assert panel(rib=(4, Side.RIGHT)).side is Side.RIGHT


def test_the_lower_surface_carries_its_criticality_onto_the_point():
    assert panel(surface=Surface.LOWER).is_tension_critical
    assert not panel(surface=Surface.UPPER).is_tension_critical


def test_a_point_forward_of_the_spar_it_was_measured_from_is_refused():
    with pytest.raises(ValueError, match="not forward of it"):
        panel(aft="-1.0")


def test_a_wing_offset_past_every_bay_is_a_bad_reading():
    with pytest.raises(ValueError, match="outboard of rib is further"):
        panel(outboard="61.0")
    with pytest.raises(ValueError, match="aft of spar is further"):
        panel(aft="61.0")


def test_mirroring_a_wing_point_crosses_to_the_other_wing():
    assert panel().mirrored().rib == Rib(4, Side.RIGHT)
    assert panel().mirrored().surface is Surface.LOWER


def test_two_points_on_the_same_hand_agree_across_the_two_kinds():
    assert same_side(barrel(), panel())
    assert not same_side(barrel(), panel(rib=(4, Side.RIGHT)))


def test_points_are_frozen_and_hash_by_value():
    assert len({barrel(), barrel()}) == 1
    with pytest.raises(AttributeError):
        barrel().aft_of_frame = inch("1.0")
