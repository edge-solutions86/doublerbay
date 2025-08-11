import pytest

import sample
from doublerbay.assess.placement import place
from doublerbay.errors import BadReading
from doublerbay.grid.ata import Reference
from doublerbay.measure.fastener import FastenerSize
from doublerbay.repair.doubler import size_doubler
from doublerbay.repair.scheme import (
    RepairCategory,
    RepairForm,
    RepairScheme,
    category_for,
    form_for,
    needs_sealing,
    select_scheme,
)
from doublerbay.structure.material import material

PAGE = Reference.parse("53-40-01")


def inch(text):
    return sample.inch(text)


def placed(at=None):
    return place(sample.tail_context(), sample.card(at=at or sample.cabin_point(stringer=10)))


def scheme(at=None, temporary=False, along="2.000", around="2.000"):
    return select_scheme(inch(along), inch(around), placed(at), PAGE, FastenerSize(5), temporary)


def test_the_categories_say_what_they_commit_the_operator_to():
    assert not RepairCategory.A.needs_inspection
    assert RepairCategory.B.needs_inspection
    assert RepairCategory.C.needs_inspection
    assert RepairCategory.A.is_permanent
    assert RepairCategory.B.is_permanent
    assert not RepairCategory.C.is_permanent
    assert str(RepairCategory.B) == "category B"


def test_only_an_external_patch_stands_in_the_airflow():
    assert RepairForm.EXTERNAL.is_in_the_airflow
    assert not RepairForm.FLUSH.is_in_the_airflow
    assert not RepairForm.INTERNAL.is_in_the_airflow


def test_ordinary_barrel_skin_takes_an_external_patch():
    assert form_for(placed()) is RepairForm.EXTERNAL


def test_the_crown_and_the_wing_will_not_have_one_standing_proud():
    crown = placed(at=sample.cabin_point(stringer=2))
    wing = place(sample.tail_context(), sample.card(at=sample.lower_wing_point()))
    assert form_for(crown) is RepairForm.FLUSH
    assert form_for(wing) is RepairForm.FLUSH


def test_a_flush_repair_carries_a_filler_to_make_up_the_skin_line():
    crown = scheme(at=sample.cabin_point(stringer=2))
    assert crown.form is RepairForm.FLUSH
    assert crown.filler is not None


def test_an_external_repair_carries_no_filler():
    assert scheme().filler is None


def test_a_flush_repair_with_no_filler_is_refused():
    with pytest.raises(BadReading, match="needs a filler"):
        RepairScheme(
            RepairForm.FLUSH,
            RepairCategory.A,
            size_doubler(
                inch("2"), inch("2"), inch("0.040"), material("2024-T3 CLAD"), FastenerSize(5)
            ),
            True,
            PAGE,
            None,
        )


def test_a_filler_with_no_thickness_is_refused():
    with pytest.raises(BadReading, match="a filler has a thickness"):
        RepairScheme(
            RepairForm.EXTERNAL,
            RepairCategory.A,
            size_doubler(
                inch("2"), inch("2"), inch("0.040"), material("2024-T3 CLAD"), FastenerSize(5)
            ),
            True,
            PAGE,
            inch("0"),
        )


def test_ordinary_open_skin_is_a_permanent_repair():
    assert category_for(placed()) is RepairCategory.A
    assert not scheme().needs_inspection


def test_the_tension_surface_of_a_wing_is_always_inspected():
    wing = place(sample.tail_context(), sample.card(at=sample.lower_wing_point()))
    assert category_for(wing) is RepairCategory.B


def test_a_repair_on_a_fastener_row_is_always_inspected():
    on_a_row = placed(at=sample.cabin_point(stringer=8, down="0.500"))
    assert category_for(on_a_row) is RepairCategory.B


def test_a_stopgap_is_temporary_whatever_it_is_on():
    assert category_for(placed(), temporary=True) is RepairCategory.C
    assert scheme(temporary=True).category is RepairCategory.C


def test_a_repair_through_the_pressure_boundary_is_sealed():
    assert needs_sealing(placed())
    assert scheme().sealed


def test_a_repair_on_the_tail_cone_is_not_sealed():
    tail_cone = placed(at=sample.cabin_point(frame=50, stringer=10))
    assert not needs_sealing(tail_cone)
    assert not scheme(at=sample.cabin_point(frame=50, stringer=10)).sealed


def test_the_scheme_carries_the_doubler_it_sized():
    made = scheme(along="2.000", around="2.000")
    assert made.doubler.along > inch("2.000")
    assert made.fastener_count == made.doubler.fastener_count


def test_a_bigger_hole_takes_more_fasteners():
    small = scheme(along="1.000", around="1.000")
    large = scheme(along="6.000", around="6.000")
    assert large.fastener_count > small.fastener_count


def test_the_scheme_reads_back_as_a_repair_line():
    printed = str(scheme())
    assert printed.startswith("external doubler, category A")
    assert printed.endswith("sealed")


def test_a_wing_repair_reads_back_flush_and_inspected():
    wing = select_scheme(
        inch("2.000"),
        inch("2.000"),
        place(sample.tail_context(), sample.card(at=sample.lower_wing_point())),
        Reference.parse("57-40-01"),
    )
    printed = str(wing)
    assert "flush doubler" in printed
    assert "category B" in printed
