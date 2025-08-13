from datetime import date

import pytest

import sample
from doublerbay.assess.placement import place
from doublerbay.errors import BadReading
from doublerbay.fleet.utilisation import usage
from doublerbay.grid.ata import Reference
from doublerbay.grid.surface import Offset
from doublerbay.ids import FindingId, PanelId, RepairId
from doublerbay.repair.record import RepairRecord, clashes
from doublerbay.repair.scheme import RepairCategory, RepairForm, select_scheme
from doublerbay.structure.footprint import DoublerForm

PAGE = Reference.parse("53-40-01")
EMBODIED = date(2025, 4, 2)


def inch(text):
    return sample.inch(text)


def at(along, around):
    return Offset(inch(along), inch(around))


def placed(point=None):
    return place(sample.tail_context(), sample.card(at=point or sample.cabin_point(stringer=10)))


def record(sequence=1, centre=("468.000", "91.000"), along="2.000", removed=None, point=None):
    return RepairRecord(
        reference=RepairId(53, sequence),
        part=PanelId(53, 10, 143, "L"),
        against=FindingId(2025, sequence),
        scheme=select_scheme(inch(along), inch("2.000"), placed(point), PAGE),
        centre=at(*centre),
        embodied=EMBODIED,
        at_usage=usage(13900, 24400),
        removed=removed,
    )


def test_a_repair_reads_back_where_it_is_and_what_it_committed_to():
    assert str(record()) == "RP-53-0001 on 5310-143L, category A, fitted"
    assert str(record(removed=date(2026, 1, 1))).endswith("removed")


def test_a_repair_removed_before_it_went_on_is_refused():
    with pytest.raises(BadReading, match="removed before it went on"):
        record(removed=date(2024, 1, 1))


def test_a_fitted_repair_is_fitted_until_it_is_taken_off():
    assert record().is_fitted
    assert not record(removed=date(2026, 1, 1)).is_fitted


def test_the_footprint_is_the_patch_and_not_the_damage():
    made = record(along="2.000")
    assert made.footprint.along == made.scheme.doubler.along
    assert made.footprint.along > inch("2.000")


def test_a_category_a_repair_carries_no_inspection():
    assert record().category is RepairCategory.A
    assert not record().needs_inspection


def test_a_wing_repair_carries_an_inspection_while_it_is_fitted():
    wing = record(point=sample.lower_wing_point())
    assert wing.category is RepairCategory.B
    assert wing.needs_inspection


def test_a_removed_repair_needs_no_more_inspecting():
    wing = record(point=sample.lower_wing_point(), removed=date(2026, 1, 1))
    assert wing.category is RepairCategory.B
    assert not wing.needs_inspection


def test_the_record_hands_itself_to_the_structure_as_an_installed_doubler():
    fitted = record().as_installed()
    assert fitted.part == "RP-53-0001"
    assert fitted.form is DoublerForm.EXTERNAL
    assert fitted.gauge == record().scheme.doubler.gauge


def test_a_flush_repair_hands_itself_over_as_a_flush_doubler():
    crown = record(point=sample.cabin_point(stringer=2))
    assert crown.scheme.form is RepairForm.FLUSH
    assert crown.as_installed().form is DoublerForm.FLUSH


def test_the_clearance_asked_for_comes_off_the_fastener_size():
    made = record()
    assert made.required_clearance == made.scheme.doubler.size.diameters("4.0")


def test_two_repairs_far_apart_clear_each_other():
    first = record(1, centre=("400.000", "91.000"))
    second = record(2, centre=("500.000", "91.000"))
    assert first.clears(second)
    assert first.clearance_to(second) > first.required_clearance


def test_two_repairs_on_top_of_each_other_do_not_clear():
    first = record(1, centre=("468.000", "91.000"))
    second = record(2, centre=("469.000", "91.000"))
    assert not first.clears(second)
    assert first.clearance_to(second).mils == 0


def test_the_clearance_is_measured_between_the_patches_and_not_the_damages():
    first = record(1, centre=("468.000", "91.000"), along="2.000")
    second = record(2, centre=("474.000", "91.000"), along="2.000")
    assert first.clearance_to(second) < inch("4.000")


def test_the_larger_of_the_two_clearances_governs():
    first = record(1, centre=("400.000", "91.000"))
    second = record(2, centre=("410.000", "91.000"))
    needed = max(first.required_clearance, second.required_clearance)
    assert first.clears(second) == (first.clearance_to(second) >= needed)


def test_a_clash_sweep_finds_the_pairs_that_are_too_close():
    close = (record(1, centre=("468.000", "91.000")), record(2, centre=("469.000", "91.000")))
    assert len(clashes(close)) == 1


def test_a_clash_sweep_ignores_repairs_that_have_been_taken_off():
    mixed = (
        record(1, centre=("468.000", "91.000")),
        record(2, centre=("469.000", "91.000"), removed=date(2026, 1, 1)),
    )
    assert clashes(mixed) == ()


def test_a_clash_sweep_over_repairs_that_clear_finds_nothing():
    apart = (record(1, centre=("400.000", "91.000")), record(2, centre=("500.000", "91.000")))
    assert clashes(apart) == ()


def test_taking_a_repair_off_leaves_the_earlier_record_alone():
    before = record()
    after = before.removed_on(date(2026, 1, 1))
    assert after.removed == date(2026, 1, 1)
    assert before.removed is None
