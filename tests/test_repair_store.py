from datetime import date

import pytest

import sample
from doublerbay.assess.placement import place
from doublerbay.errors import BadReading, NotOnFile
from doublerbay.fleet.utilisation import usage
from doublerbay.grid.ata import Reference
from doublerbay.grid.surface import Offset
from doublerbay.ids import FindingId, PanelId, RepairId, Serial
from doublerbay.measure.fastener import FastenerSize
from doublerbay.repair.record import RepairRecord
from doublerbay.repair.scheme import RepairCategory, RepairForm, select_scheme
from doublerbay.store.base import build_engine, build_schema
from doublerbay.store.decisions import repair_to_row, row_to_repair
from doublerbay.store.unit import UnitOfWork

PAGE = Reference.parse("53-40-01")
EMBODIED = date(2025, 4, 2)
PART = PanelId(53, 10, 143, "L")
MSN = Serial("5127")


def inch(text):
    return sample.inch(text)


def placed(point=None):
    return place(sample.tail_context(), sample.card(at=point or sample.cabin_point(stringer=10)))


def record(sequence=1, centre=("468.000", "91.000"), along="2.000", removed=None, size=None):
    return RepairRecord(
        reference=RepairId(53, sequence),
        part=PART,
        against=FindingId(2025, sequence),
        scheme=select_scheme(inch(along), inch("2.000"), placed(), PAGE, size),
        centre=Offset(inch(centre[0]), inch(centre[1])),
        embodied=EMBODIED,
        at_usage=usage(13900, 24400),
        removed=removed,
    )


@pytest.fixture
def engine():
    made = build_engine()
    build_schema(made)
    with UnitOfWork(made) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(sample.flying_log())
    return made


def embodied(engine, *records, serial=MSN):
    with UnitOfWork(engine) as unit:
        for one in records:
            unit.repairs.embody(one, serial)


def test_a_repair_goes_in_and_comes_back_the_same(engine):
    embodied(engine, record())
    with UnitOfWork(engine) as unit:
        held = unit.repairs.get(RepairId(53, 1))
    assert held.part == PART
    assert held.against == FindingId(2025, 1)
    assert held.embodied == EMBODIED


def test_the_whole_drawing_survives_the_round_trip(engine):
    original = record(size=FastenerSize(6))
    embodied(engine, original)
    with UnitOfWork(engine) as unit:
        held = unit.repairs.get(RepairId(53, 1))
    assert held.scheme.doubler == original.scheme.doubler
    assert held.scheme.doubler.size.dash == 6


def test_the_material_comes_back_off_the_catalogue_rather_than_the_row(engine):
    original = record()
    embodied(engine, original)
    with UnitOfWork(engine) as unit:
        held = unit.repairs.get(RepairId(53, 1))
    assert held.scheme.doubler.material is original.scheme.doubler.material


def test_the_form_and_category_survive(engine):
    embodied(engine, record())
    with UnitOfWork(engine) as unit:
        held = unit.repairs.get(RepairId(53, 1))
    assert isinstance(held.scheme.form, RepairForm)
    assert isinstance(held.scheme.category, RepairCategory)


def test_the_page_it_was_drawn_against_survives(engine):
    embodied(engine, record())
    with UnitOfWork(engine) as unit:
        assert unit.repairs.get(RepairId(53, 1)).scheme.reference == PAGE


def test_the_centre_survives_as_two_components(engine):
    embodied(engine, record(centre=("468.000", "-91.000")))
    with UnitOfWork(engine) as unit:
        centre = unit.repairs.get(RepairId(53, 1)).centre
    assert centre.along == inch("468.000")
    assert centre.around == inch("-91.000")


def test_a_repair_number_used_twice_is_refused(engine):
    embodied(engine, record())
    with UnitOfWork(engine) as unit, pytest.raises(BadReading, match="already on file"):
        unit.repairs.embody(record(), MSN)


def test_a_repair_that_is_not_on_file_is_refused(engine):
    with UnitOfWork(engine) as unit, pytest.raises(NotOnFile, match="not a repair on file"):
        unit.repairs.get(RepairId(53, 99))


def test_removing_takes_it_off_the_fitted_list(engine):
    embodied(engine, record())
    with UnitOfWork(engine) as unit:
        unit.repairs.remove(RepairId(53, 1), date(2026, 1, 5))
    with UnitOfWork(engine) as unit:
        assert unit.repairs.on_tail(MSN) == ()
        assert len(unit.repairs.on_tail(MSN, fitted_only=False)) == 1


def test_a_removed_repair_keeps_the_day_it_came_off(engine):
    embodied(engine, record())
    with UnitOfWork(engine) as unit:
        unit.repairs.remove(RepairId(53, 1), date(2026, 1, 5))
    with UnitOfWork(engine) as unit:
        assert unit.repairs.get(RepairId(53, 1)).removed == date(2026, 1, 5)


def test_removing_one_twice_is_refused(engine):
    embodied(engine, record())
    with UnitOfWork(engine) as unit:
        unit.repairs.remove(RepairId(53, 1), date(2026, 1, 5))
        with pytest.raises(BadReading, match="already removed"):
            unit.repairs.remove(RepairId(53, 1), date(2026, 2, 5))


def test_a_repair_cannot_come_off_before_it_went_on(engine):
    embodied(engine, record())
    with UnitOfWork(engine) as unit, pytest.raises(BadReading, match="before it went on"):
        unit.repairs.remove(RepairId(53, 1), date(2024, 1, 1))


def test_removing_one_that_is_not_on_file_is_refused(engine):
    with UnitOfWork(engine) as unit, pytest.raises(NotOnFile):
        unit.repairs.remove(RepairId(53, 99), EMBODIED)


def test_the_repairs_on_a_tail_come_back_oldest_first(engine):
    embodied(engine, record(1), record(2))
    with UnitOfWork(engine) as unit:
        assert [one.reference.sequence for one in unit.repairs.on_tail(MSN)] == [1, 2]


def test_another_tail_s_repairs_are_not_on_this_one(engine):
    with UnitOfWork(engine) as unit:
        unit.aircraft.add(sample.aircraft("5240"))
        unit.aircraft.open_log(sample.flying_log("5240"))
    embodied(engine, record(1))
    embodied(engine, record(2), serial=Serial("5240"))
    with UnitOfWork(engine) as unit:
        assert len(unit.repairs.on_tail(MSN)) == 1
        assert len(unit.repairs.on_tail(Serial("5240"))) == 1


def test_a_panel_is_asked_for_on_one_tail_at_a_time(engine):
    with UnitOfWork(engine) as unit:
        unit.aircraft.add(sample.aircraft("5240"))
        unit.aircraft.open_log(sample.flying_log("5240"))
    embodied(engine, record(1))
    embodied(engine, record(2), serial=Serial("5240"))
    with UnitOfWork(engine) as unit:
        assert len(unit.repairs.on_panel(MSN, PART)) == 1


def test_the_repairs_against_one_card_come_back_together(engine):
    embodied(engine, record(1), record(2))
    with UnitOfWork(engine) as unit:
        assert len(unit.repairs.against(FindingId(2025, 1))) == 1


def test_two_repairs_too_close_together_come_back_as_a_clash(engine):
    embodied(engine, record(1, ("468.000", "91.000")), record(2, ("469.000", "91.000")))
    with UnitOfWork(engine) as unit:
        clashing = unit.repairs.clashing_on(MSN, PART)
    assert len(clashing) == 1


def test_two_repairs_far_apart_do_not_clash(engine):
    embodied(engine, record(1, ("468.000", "91.000")), record(2, ("620.000", "91.000")))
    with UnitOfWork(engine) as unit:
        assert unit.repairs.clashing_on(MSN, PART) == ()


def test_a_removed_repair_does_not_clash_with_the_one_that_replaced_it(engine):
    embodied(engine, record(1, ("468.000", "91.000")), record(2, ("469.000", "91.000")))
    with UnitOfWork(engine) as unit:
        unit.repairs.remove(RepairId(53, 1), date(2026, 1, 5))
    with UnitOfWork(engine) as unit:
        assert unit.repairs.clashing_on(MSN, PART) == ()


def test_the_ones_carrying_a_repeat_inspection_are_listed(engine):
    embodied(engine, record())
    with UnitOfWork(engine) as unit:
        carried = unit.repairs.needing_inspection(MSN)
        every = unit.repairs.on_tail(MSN)
    assert all(one.needs_inspection for one in carried)
    assert len(carried) <= len(every)


def test_a_row_built_from_a_record_carries_the_tail_it_was_fitted_to():
    row = repair_to_row(record(), MSN)
    assert row.serial == MSN
    assert row.part == PART
    assert row_to_repair(row).reference == RepairId(53, 1)
