from datetime import date
from decimal import Decimal

import pytest

import sample
from doublerbay.assess.placement import place
from doublerbay.errors import BadReading
from doublerbay.fleet.utilisation import usage
from doublerbay.grid.ata import Reference
from doublerbay.grid.surface import Offset
from doublerbay.ids import FindingId, PanelId, RepairId, Serial
from doublerbay.repair.record import RepairRecord
from doublerbay.repair.scheme import select_scheme
from doublerbay.report.audit import (
    CROWDED_PANEL,
    TEMPORARY_MONTHS,
    audit,
    months_between,
    panel_loads,
    temporaries,
)

PAGE = Reference.parse("53-40-01")
FITTED = date(2025, 4, 2)
LATER = date(2026, 11, 2)
MSN = Serial("5127")
PART = PanelId(53, 10, 143, "L")
ELSEWHERE = PanelId(53, 10, 210, "R")


def inch(text):
    return sample.inch(text)


def placed():
    return place(sample.tail_context(), sample.card(at=sample.cabin_point(stringer=10)))


def repair(sequence=1, part=PART, centre=("468.000", "91.000"), temporary=False, on=FITTED):
    return RepairRecord(
        reference=RepairId(53, sequence),
        part=part,
        against=FindingId(2025, sequence),
        scheme=select_scheme(inch("2.000"), inch("2.000"), placed(), PAGE, None, temporary),
        centre=Offset(inch(centre[0]), inch(centre[1])),
        embodied=on,
        at_usage=usage(13900, 24400),
    )


def test_a_tail_with_no_repairs_reads_as_clean():
    built = audit(MSN, (), FITTED)
    assert built.total_repairs == 0
    assert built.is_clean
    assert str(built) == "MSN 5127: no repairs on file"


def test_one_repair_lands_on_its_panel():
    built = audit(MSN, (repair(),), FITTED)
    assert built.total_repairs == 1
    assert built.load_on(PART) == 1
    assert built.load_on(ELSEWHERE) == 0


def test_panels_come_back_busiest_first():
    fitted = (
        repair(1, PART, ("468.000", "91.000")),
        repair(2, PART, ("620.000", "91.000")),
        repair(3, ELSEWHERE, ("700.000", "10.000")),
    )
    loads = panel_loads(fitted)
    assert loads[0].part == PART
    assert loads[0].repairs == 2


def test_two_panels_with_the_same_load_sort_by_part():
    fitted = (repair(1, ELSEWHERE), repair(2, PART))
    assert [one.part for one in panel_loads(fitted)] == [PART, ELSEWHERE]


def test_a_panel_below_the_threshold_is_not_crowded():
    built = audit(MSN, (repair(1, PART, ("468.000", "91.000")),), FITTED)
    assert built.crowded == ()


def test_a_panel_at_the_threshold_is_crowded():
    fitted = tuple(
        repair(step + 1, PART, (f"{468 + step * 200}.000", "91.000"))
        for step in range(CROWDED_PANEL)
    )
    built = audit(MSN, fitted, FITTED)
    assert len(built.crowded) == 1
    assert not built.is_clean


def test_a_panel_load_reads_back_with_its_temporaries():
    load = panel_loads((repair(1, PART, temporary=True),))[0]
    assert str(load) == "5310-143L: 1 repairs, 1 temporary"


def test_a_panel_with_no_temporaries_says_nothing_about_them():
    load = panel_loads((repair(1, PART),))[0]
    assert str(load) == "5310-143L: 1 repairs"


def test_a_permanent_repair_is_not_a_temporary():
    assert temporaries((repair(1, PART),), FITTED) == ()


def test_a_temporary_repair_is_listed_with_its_age():
    found = temporaries((repair(1, PART, temporary=True),), LATER)
    assert len(found) == 1
    assert found[0].months_fitted > 17


def test_a_temporary_repair_inside_its_life_is_not_overdue():
    built = audit(MSN, (repair(1, PART, temporary=True),), date(2025, 6, 1))
    assert built.overdue == ()
    assert built.is_clean


def test_a_temporary_repair_past_its_life_is_overdue():
    built = audit(MSN, (repair(1, PART, temporary=True),), LATER)
    assert len(built.overdue) == 1
    assert not built.is_clean


def test_the_temporary_life_is_the_one_the_book_gives_it():
    assert TEMPORARY_MONTHS == 18


def test_temporaries_come_back_longest_fitted_first():
    fitted = (
        repair(1, PART, ("468.000", "91.000"), temporary=True, on=date(2025, 4, 2)),
        repair(2, PART, ("620.000", "91.000"), temporary=True, on=date(2024, 4, 2)),
    )
    assert [one.reference.sequence for one in temporaries(fitted, LATER)] == [2, 1]


def test_an_overdue_temporary_reads_back_with_its_age():
    found = temporaries((repair(1, PART, temporary=True),), LATER)[0]
    assert str(found).startswith("RP-53-0001 on 5310-143L,")
    assert "months fitted" in str(found)


def test_months_between_counts_to_a_tenth():
    assert months_between(date(2025, 1, 1), date(2025, 2, 1)) == Decimal("1.0")


def test_the_same_day_is_no_months():
    assert months_between(FITTED, FITTED) == Decimal("0.0")


def test_a_day_before_the_repair_went_on_is_refused():
    with pytest.raises(BadReading, match="negative time"):
        months_between(FITTED, date(2024, 1, 1))


def test_two_repairs_too_close_are_reported_as_a_clash():
    fitted = (
        repair(1, PART, ("468.000", "91.000")),
        repair(2, PART, ("469.000", "91.000")),
    )
    built = audit(MSN, fitted, FITTED)
    assert built.clashing == ((RepairId(53, 1), RepairId(53, 2)),)
    assert not built.is_clean


def test_two_repairs_far_apart_do_not_clash():
    fitted = (
        repair(1, PART, ("468.000", "91.000")),
        repair(2, PART, ("700.000", "91.000")),
    )
    assert audit(MSN, fitted, FITTED).clashing == ()


def test_the_audit_line_names_what_it_flagged():
    fitted = (
        repair(1, PART, ("468.000", "91.000"), temporary=True, on=date(2024, 1, 1)),
        repair(2, PART, ("469.000", "91.000")),
    )
    line = str(audit(MSN, fitted, LATER))
    assert "temporary repairs overdue" in line
    assert "clashes" in line


def test_a_tail_with_repairs_and_nothing_wrong_says_so():
    built = audit(MSN, (repair(1, PART),), FITTED)
    assert "nothing flagged" in str(built)
