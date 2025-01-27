from decimal import Decimal

import pytest

from doublerbay.errors import BadReading
from doublerbay.grid.lattice import Frame, Stringer
from doublerbay.grid.zone import Side
from doublerbay.measure.fastener import FastenerSize
from doublerbay.measure.length import Length
from doublerbay.structure.joint import FastenerRow, Joint, JointKind


def inch(text):
    return Length.of_inches(text)


def row(dash=5, pitch="0.750", offset="0.500", countersunk=False):
    return FastenerRow(FastenerSize(dash), inch(pitch), inch(offset), countersunk)


def lap(rows=None):
    return Joint(
        JointKind.LAP,
        Stringer(14, Side.LEFT),
        rows or (row(offset="0.500"), row(offset="1.375"), row(offset="2.250")),
    )


def test_a_row_reads_as_its_size_and_pitch():
    assert str(row()) == "-5 at 0.750"
    assert str(row(countersunk=True)) == "-5 at 0.750 csk"


def test_a_row_pitch_reads_back_in_diameters():
    assert row().pitch_in_diameters == Decimal("4.80")


def test_a_pitch_inside_the_minimum_is_refused():
    with pytest.raises(BadReading, match="closer than the"):
        row(pitch="0.500")


def test_a_row_with_no_pitch_is_refused():
    with pytest.raises(BadReading, match="a row has a pitch"):
        FastenerRow(FastenerSize(5), inch("0"), inch("0.500"))


def test_a_run_carries_both_its_end_fasteners():
    assert row().fastener_count(inch("0")) == 1
    assert row().fastener_count(inch("0.750")) == 2
    assert row().fastener_count(inch("7.500")) == 11


def test_a_part_pitch_at_the_end_of_a_run_does_not_buy_a_fastener():
    assert row().fastener_count(inch("1.400")) == 2


def test_a_run_the_wrong_way_is_refused():
    with pytest.raises(BadReading, match="does not go backwards"):
        row().fastener_count(inch("-1.000"))


def test_the_run_and_the_count_invert_each_other():
    assert row().run_for(11) == inch("7.500")
    assert row().fastener_count(row().run_for(11)) == 11


def test_a_row_holds_at_least_one_fastener():
    with pytest.raises(BadReading, match="at least one fastener"):
        row().run_for(0)


def test_a_lap_joint_runs_along_a_stringer_and_a_butt_joint_around_a_frame():
    assert lap().is_longitudinal
    butt = Joint(JointKind.BUTT, Frame(41), (row(),))
    assert not butt.is_longitudinal


def test_a_lap_joint_on_a_frame_is_refused():
    with pytest.raises(BadReading, match="runs along a stringer"):
        Joint(JointKind.LAP, Frame(41), (row(),))


def test_a_butt_joint_on_a_stringer_is_refused():
    with pytest.raises(BadReading, match="runs around a frame"):
        Joint(JointKind.BUTT, Stringer(14, Side.LEFT), (row(),))


def test_lap_and_splice_are_the_fatigue_critical_kinds():
    assert JointKind.LAP.is_fatigue_critical
    assert JointKind.SPLICE.is_fatigue_critical
    assert not JointKind.BUTT.is_fatigue_critical
    assert not JointKind.BONDED.is_fatigue_critical
    assert lap().is_fatigue_critical


def test_a_bonded_joint_carries_no_rows_and_is_allowed_none():
    bonded = Joint(JointKind.BONDED, Stringer(14, Side.LEFT), ())
    assert bonded.row_count == 0
    assert bonded.width == Length(0)


def test_a_riveted_joint_with_no_rows_is_refused():
    with pytest.raises(BadReading, match="has fastener rows"):
        Joint(JointKind.LAP, Stringer(14, Side.LEFT), ())


def test_rows_are_listed_from_the_member_outwards():
    with pytest.raises(BadReading, match="from the member outwards"):
        lap((row(offset="2.250"), row(offset="0.500")))


def test_two_rows_on_the_same_line_are_refused():
    with pytest.raises(BadReading, match="same line"):
        lap((row(offset="0.500"), row(offset="0.500")))


def test_rows_closer_than_the_minimum_spacing_are_refused():
    with pytest.raises(BadReading, match="inside the"):
        lap((row(offset="0.500"), row(offset="0.900")))


def test_the_row_spacing_minimum_comes_from_the_larger_of_the_two_sizes():
    mixed = lap((row(dash=4, offset="0.500"), row(dash=8, pitch="1.000", offset="1.500")))
    assert mixed.row_count == 2
    with pytest.raises(BadReading, match="inside the"):
        lap((row(dash=4, offset="0.500"), row(dash=8, pitch="1.000", offset="1.499")))


def test_the_joint_band_runs_out_to_the_last_row():
    assert lap().width == inch("2.250")
    assert lap().is_within(inch("0"))
    assert lap().is_within(inch("2.250"))
    assert not lap().is_within(inch("2.251"))
    assert not lap().is_within(inch("-0.001"))


def test_the_outermost_row_is_the_one_that_cracks_first():
    assert lap().critical_row.offset == inch("2.250")


def test_a_joint_with_no_rows_has_no_critical_row():
    bonded = Joint(JointKind.BONDED, Stringer(14, Side.LEFT), ())
    with pytest.raises(BadReading, match="no rows to be critical"):
        bonded.critical_row
    with pytest.raises(BadReading, match="no rows to measure to"):
        bonded.row_nearest(inch("1.000"))


def test_a_damage_is_measured_to_whichever_row_is_nearest():
    assert lap().row_nearest(inch("0.600")).offset == inch("0.500")
    assert lap().row_nearest(inch("1.300")).offset == inch("1.375")
    assert lap().distance_to_nearest_row(inch("1.300")) == inch("0.075")


def test_a_damage_outside_the_band_still_measures_to_the_last_row():
    assert lap().distance_to_nearest_row(inch("4.000")) == inch("1.750")


def test_a_joint_reads_back_what_it_is_and_where():
    assert str(lap()) == "lap joint on S-14L, 3 rows"
