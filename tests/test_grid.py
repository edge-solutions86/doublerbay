import pytest

from doublerbay.errors import BadReading, NotOnFile
from doublerbay.grid.coordinate import Station
from doublerbay.grid.lattice import Frame, Stringer
from doublerbay.grid.location import FuselagePoint
from doublerbay.grid.surface import Offset
from doublerbay.grid.zone import Side
from doublerbay.measure.length import Length
from doublerbay.structure.grid import FrameStation, FuselageGrid, Margin, StringerLine


def inch(text):
    return Length.of_inches(text)


def grid():
    return FuselageGrid(
        frames=tuple(
            FrameStation(Frame(number), Station.of_inches(str(600 + (number - 40) * 20)))
            for number in range(40, 46)
        ),
        stringers=tuple(
            StringerLine(
                Stringer(number, Side.LEFT) if number else Stringer(0), inch(str(number * 8))
            )
            for number in range(0, 6)
        ),
    )


def point(frame=41, aft="12.500", stringer=2, down="3.000"):
    return FuselagePoint(Frame(frame), inch(aft), Stringer(stringer, Side.LEFT), inch(down))


def test_a_grid_holds_both_ladders_in_order():
    assert grid().station_of(Frame(41)) == Station.of_inches("620")
    assert grid().around_of(Stringer(2, Side.LEFT)) == inch("16.000")


def test_a_member_off_the_grid_is_a_lookup_miss():
    with pytest.raises(NotOnFile, match="is not on this grid"):
        grid().station_of(Frame(90))
    with pytest.raises(NotOnFile, match="is not on this grid"):
        grid().around_of(Stringer(40, Side.LEFT))


def test_the_pitch_is_the_gap_to_the_next_member_aft_and_below():
    assert grid().frame_pitch_aft_of(Frame(41)) == inch("20.000")
    assert grid().stringer_pitch_below(Stringer(2, Side.LEFT)) == inch("8.000")


def test_the_last_member_has_nothing_past_it():
    with pytest.raises(NotOnFile, match="no frame aft of it"):
        grid().frame_pitch_aft_of(Frame(45))
    with pytest.raises(NotOnFile, match="no stringer below it"):
        grid().stringer_pitch_below(Stringer(5, Side.LEFT))


def test_a_written_location_resolves_to_barrel_coordinates():
    assert grid().absolute(point()) == Offset(inch("632.500"), inch("19.000"))


def test_a_grid_needs_two_of_each_to_bound_anything():
    with pytest.raises(BadReading, match="at least two frames"):
        FuselageGrid((FrameStation(Frame(41), Station.of_inches("620")),), grid().stringers)
    with pytest.raises(BadReading, match="at least two stringers"):
        FuselageGrid(grid().frames, (StringerLine(Stringer(0), inch("0")),))


def test_frames_have_to_be_listed_forward_to_aft():
    reversed_frames = tuple(reversed(grid().frames))
    with pytest.raises(BadReading, match="forward to aft"):
        FuselageGrid(reversed_frames, grid().stringers)


def test_two_frames_on_the_same_station_are_refused():
    frames = (
        FrameStation(Frame(41), Station.of_inches("620")),
        FrameStation(Frame(42), Station.of_inches("620")),
    )
    with pytest.raises(BadReading, match="no two on the same station"):
        FuselageGrid(frames, grid().stringers)


def test_stringers_have_to_climb_from_the_crown():
    stringers = (
        StringerLine(Stringer(0), inch("8.000")),
        StringerLine(Stringer(1, Side.LEFT), inch("4.000")),
    )
    with pytest.raises(BadReading, match="no two together"):
        FuselageGrid(grid().frames, stringers)


def test_a_stringer_above_the_crown_is_refused():
    with pytest.raises(BadReading, match="cannot be above the crown"):
        StringerLine(Stringer(0), inch("-1.000"))


def test_a_point_falls_in_the_bay_that_brackets_it():
    assert str(grid().bay_of(point())) == "FR41-FR42 / S-2L-S-3L"


def test_a_point_measured_off_the_wrong_frame_still_lands_in_the_right_bay():
    far_back = point(frame=40, aft="32.500")
    assert grid().bay_of(far_back) == grid().bay_of(point())


def test_a_point_on_a_member_belongs_to_the_bay_aft_and_below_it():
    on_frame = point(aft="0", down="0")
    assert str(grid().bay_of(on_frame)) == "FR41-FR42 / S-2L-S-3L"


def test_a_point_off_the_end_of_the_grid_is_refused():
    with pytest.raises(BadReading, match="is off the frames"):
        grid().bay_of(point(frame=45, aft="30.000"))
    with pytest.raises(BadReading, match="is off the stringers"):
        grid().bay_of(point(stringer=5, down="10.000"))


def test_the_margin_is_the_nearest_of_the_four_bounding_members():
    margin = grid().margin_of(point())
    assert margin.member == Stringer(2, Side.LEFT)
    assert margin.distance == inch("3.000")
    assert str(margin) == "3.000 off S-2L"


def test_the_margin_can_be_to_a_frame_instead():
    margin = grid().margin_of(point(aft="2.000", down="4.000"))
    assert margin.member == Frame(41)
    assert margin.distance == inch("2.000")


def test_the_margin_can_be_to_the_far_side_of_the_bay():
    margin = grid().margin_of(point(aft="19.000", down="4.000"))
    assert margin.member == Frame(42)
    assert margin.distance == inch("1.000")

    below = grid().margin_of(point(aft="10.000", down="7.500"))
    assert below.member == Stringer(3, Side.LEFT)
    assert below.distance == inch("0.500")


def test_two_cards_for_the_same_dent_normalise_to_the_same_record():
    written_off_41 = point()
    written_off_40 = point(frame=40, aft="32.500")
    assert grid().normalised(written_off_41) == grid().normalised(written_off_40)
    assert written_off_41 != written_off_40


def test_a_normalised_point_is_measured_off_the_members_that_bound_it():
    normalised = grid().normalised(point(frame=40, aft="32.500"))
    assert normalised.frame == Frame(41)
    assert normalised.aft_of_frame == inch("12.500")
    assert normalised.stringer == Stringer(2, Side.LEFT)
    assert normalised.below_stringer == inch("3.000")


def test_normalising_something_already_normal_changes_nothing():
    assert grid().normalised(point()) == point()


def test_a_margin_reads_back_what_it_is_off():
    assert str(Margin(Frame(41), inch("2.000"))) == "2.000 off FR41"


def test_a_frame_and_stringer_line_read_back_where_they_are():
    assert str(FrameStation(Frame(41), Station.of_inches("620"))) == "FR41 at STA 620.000"
    assert str(StringerLine(Stringer(2, Side.LEFT), inch("16"))) == "S-2L at 16.000 round"
