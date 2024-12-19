import pytest

from doublerbay.grid.lattice import Bay, Frame, Stringer
from doublerbay.grid.zone import Side


def test_a_stringer_carries_a_number_and_a_hand():
    stringer = Stringer(14, Side.LEFT)
    assert str(stringer) == "S-14L"
    assert stringer.side is Side.LEFT


def test_the_crown_stringer_has_no_hand():
    assert str(Stringer(0)) == "S-0"
    assert Stringer(0).is_crown
    with pytest.raises(ValueError, match="crown stringer has no hand"):
        Stringer(0, Side.LEFT)


def test_a_stringer_off_the_crown_has_to_say_which_hand():
    with pytest.raises(ValueError, match="has to say which hand"):
        Stringer(14)


def test_a_stringer_number_off_the_barrel_is_refused():
    with pytest.raises(ValueError, match="not on this barrel"):
        Stringer(60, Side.LEFT)
    with pytest.raises(ValueError, match="not on this barrel"):
        Stringer(-1, Side.LEFT)


@pytest.mark.parametrize(
    ("text", "expected"),
    [
        ("S-14L", Stringer(14, Side.LEFT)),
        ("S14R", Stringer(14, Side.RIGHT)),
        ("s-4l", Stringer(4, Side.LEFT)),
        (" S-0 ", Stringer(0)),
    ],
)
def test_the_stringer_parser_reads_the_written_form(text, expected):
    assert Stringer.parse(text) == expected


@pytest.mark.parametrize("text", ["", "14L", "S-", "SL", "S-100L"])
def test_the_stringer_parser_refuses_anything_else(text):
    with pytest.raises(ValueError):
        Stringer.parse(text)


def test_a_handed_stringer_written_without_a_hand_is_refused():
    with pytest.raises(ValueError, match="does not say which hand"):
        Stringer.parse("S-14")


def test_the_lower_number_is_the_higher_stringer():
    assert Stringer(4, Side.LEFT).is_above(Stringer(22, Side.LEFT))
    assert not Stringer(22, Side.LEFT).is_above(Stringer(4, Side.LEFT))


def test_mirroring_swaps_the_hand_and_leaves_the_crown_alone():
    assert Stringer(14, Side.LEFT).mirrored() == Stringer(14, Side.RIGHT)
    assert Stringer(0).mirrored() == Stringer(0)


def test_stringers_span_within_a_hand_and_not_across():
    assert Stringer(4, Side.LEFT).spans_to(Stringer(9, Side.LEFT)) == 5
    assert Stringer(0).spans_to(Stringer(4, Side.LEFT)) == 4
    with pytest.raises(ValueError, match="opposite hands do not span"):
        Stringer(4, Side.LEFT).spans_to(Stringer(4, Side.RIGHT))


def test_a_frame_reads_and_prints_its_number():
    assert Frame.parse("FR42") == Frame(42)
    assert Frame.parse("FS 42") == Frame(42)
    assert str(Frame(42)) == "FR42"


@pytest.mark.parametrize("text", ["", "42", "FRAME 42", "FR", "FR1234"])
def test_the_frame_parser_refuses_anything_else(text):
    with pytest.raises(ValueError, match="not a frame"):
        Frame.parse(text)


def test_frames_start_at_one():
    with pytest.raises(ValueError, match="start at one"):
        Frame(0)


def test_frames_order_and_step_aft():
    assert Frame(41) < Frame(42)
    assert Frame(41).next_aft() == Frame(42)
    assert Frame(41).spans_to(Frame(45)) == 4


def bay(forward=41, aft=42, upper=(14, Side.LEFT), lower=(15, Side.LEFT)):
    return Bay(Frame(forward), Frame(aft), Stringer(*upper), Stringer(*lower))


def test_a_bay_is_bounded_by_two_frames_and_two_stringers():
    assert str(bay()) == "FR41-FR42 / S-14L-S-15L"
    assert bay().side is Side.LEFT
    assert bay().is_single


def test_a_bay_that_spans_more_than_one_pitch_is_not_a_single_bay():
    wide = bay(aft=44, lower=(18, Side.LEFT))
    assert wide.frame_pitches == 3
    assert wide.stringer_pitches == 4
    assert not wide.is_single


def test_a_bay_has_to_be_given_its_bounds_the_right_way_round():
    with pytest.raises(ValueError, match="is not forward of"):
        bay(forward=42, aft=41)
    with pytest.raises(ValueError, match="is not above"):
        bay(upper=(15, Side.LEFT), lower=(14, Side.LEFT))


def test_a_bay_does_not_straddle_the_crown():
    with pytest.raises(ValueError, match="does not straddle the crown"):
        Bay(Frame(41), Frame(42), Stringer(2, Side.LEFT), Stringer(3, Side.RIGHT))


def test_a_bay_may_hang_off_the_crown_stringer():
    crown_bay = Bay(Frame(41), Frame(42), Stringer(0), Stringer(1, Side.LEFT))
    assert crown_bay.side is Side.LEFT
    assert crown_bay.is_single
