from decimal import Decimal

import pytest

from doublerbay.measure.fastener import (
    FastenerSize,
    is_stocked,
    next_size_up,
    stocked,
)
from doublerbay.measure.length import Length


def inch(text):
    return Length.of_inches(text)


def test_a_dash_number_is_thirty_seconds_of_an_inch():
    assert FastenerSize(4).diameter == inch("0.125")
    assert FastenerSize(8).diameter == inch("0.250")


def test_the_odd_sizes_round_to_the_thousandth_the_drawing_carries():
    assert FastenerSize(5).diameter == inch("0.156")
    assert FastenerSize(3).diameter == inch("0.094")


def test_the_exact_shank_is_kept_for_anything_that_accumulates():
    assert FastenerSize(5).exact_diameter == Decimal("5") / Decimal("32")


def test_a_size_prints_the_way_it_is_called_out():
    assert str(FastenerSize(5)) == "-5"


@pytest.mark.parametrize(("text", "dash"), [("-5", 5), ("5", 5), (" -12 ", 12)])
def test_the_parser_takes_the_callout_with_or_without_the_dash(text, dash):
    assert FastenerSize.parse(text).dash == dash


@pytest.mark.parametrize("text", ["", "-", "five", "-5.5", "-123"])
def test_the_parser_refuses_anything_else(text):
    with pytest.raises(ValueError):
        FastenerSize.parse(text)


def test_a_dash_zero_is_not_a_fastener():
    with pytest.raises(ValueError, match="counts thirty seconds"):
        FastenerSize(0)


def test_a_distance_in_d_turns_into_a_length():
    assert FastenerSize(8).diameters(2) == inch("0.500")
    assert FastenerSize(5).diameters("2.5") == inch("0.391")


def test_a_measured_distance_reads_back_in_d():
    assert FastenerSize(8).in_diameters(inch("0.500")) == Decimal("2.00")
    assert FastenerSize(8).in_diameters(inch("0.375")) == Decimal("1.50")


def test_the_spacing_rules_move_with_the_size():
    small = FastenerSize(4)
    large = FastenerSize(8)
    assert small.min_edge_distance == inch("0.250")
    assert large.min_edge_distance == inch("0.500")
    assert large.min_pitch == inch("1.000")
    assert large.min_row_spacing == inch("1.000")


def test_the_preferred_edge_distance_is_further_out_than_the_floor():
    size = FastenerSize(5)
    assert size.preferred_edge_distance > size.min_edge_distance


def test_the_stocked_list_climbs_and_holds_no_duplicates():
    dashes = [size.dash for size in stocked()]
    assert dashes == sorted(dashes)
    assert len(set(dashes)) == len(dashes)


def test_a_size_off_the_list_is_not_stocked():
    assert is_stocked(FastenerSize(5))
    assert not is_stocked(FastenerSize(7))


def test_a_worked_hole_goes_up_to_the_next_stocked_size():
    assert next_size_up(FastenerSize(5)) == FastenerSize(6)
    assert next_size_up(FastenerSize(6)) == FastenerSize(8)


def test_nothing_goes_up_from_the_top_of_the_list():
    with pytest.raises(ValueError, match="largest stocked fastener"):
        next_size_up(FastenerSize(12))


def test_sizes_order_and_hash_by_their_dash():
    assert FastenerSize(4) < FastenerSize(5)
    assert sorted([FastenerSize(8), FastenerSize(3)]) == [FastenerSize(3), FastenerSize(8)]
    assert len({FastenerSize(5), FastenerSize(5)}) == 1
