from datetime import date

import pytest

from doublerbay.errors import BadReading, NotOnFile
from doublerbay.fleet.utilisation import usage
from doublerbay.measure.interval import Interval
from doublerbay.measure.length import Length
from doublerbay.measure.usage import Cycles
from doublerbay.repair.scheme import RepairCategory
from doublerbay.structure.panel import PanelKind
from doublerbay.tolerance.method import InspectionMethod
from doublerbay.tolerance.schedule import (
    GrowthBasis,
    InspectionSchedule,
    basis_for,
    schedule_for,
)

FLOWN = usage(13800, 24200)
EMBODIED = date(2025, 4, 2)


def inch(text):
    return Length.of_inches(text)


def test_a_growth_basis_reads_back_its_two_numbers():
    basis = basis_for(PanelKind.PRESSURISED_SKIN)
    assert str(basis) == "critical 6.000, 0.100 per 1000 FC"


def test_a_basis_needs_a_critical_length_and_a_growth_rate():
    with pytest.raises(BadReading, match="a critical crack length is a length"):
        GrowthBasis(inch("0"), inch("0.100"))
    with pytest.raises(BadReading, match="does not grow needs no inspection"):
        GrowthBasis(inch("6.000"), inch("0"))


def test_the_growth_life_is_the_run_divided_by_the_rate():
    basis = GrowthBasis(inch("6.000"), inch("0.100"))
    assert basis.growth_cycles(inch("1.000")) == Cycles(50000)


def test_a_crack_already_at_critical_has_no_growth_life_left():
    basis = GrowthBasis(inch("6.000"), inch("0.100"))
    with pytest.raises(BadReading, match="already at or past the critical"):
        basis.growth_cycles(inch("6.000"))


def test_the_lower_wing_grows_cracks_faster_than_the_barrel():
    wing = basis_for(PanelKind.WING_LOWER)
    barrel = basis_for(PanelKind.PRESSURISED_SKIN)
    assert wing.growth_per_thousand > barrel.growth_per_thousand
    assert wing.critical < barrel.critical


def test_a_panel_kind_with_no_growth_data_is_a_lookup_miss():
    with pytest.raises(NotOnFile, match="no crack growth data on file"):
        basis_for(PanelKind.FAIRING)


def test_a_permanent_repair_carries_no_schedule_at_all():
    assert schedule_for(RepairCategory.A, PanelKind.PRESSURISED_SKIN, InspectionMethod.DVI) is None


def test_an_inspected_repair_gets_a_threshold_and_a_repeat():
    made = schedule_for(RepairCategory.B, PanelKind.PRESSURISED_SKIN, InspectionMethod.DVI)
    assert made.is_repeating
    assert made.threshold.cycles.count > made.repeat.cycles.count


def test_the_repeat_is_half_the_growth_life_from_what_the_method_finds():
    made = schedule_for(RepairCategory.B, PanelKind.PRESSURISED_SKIN, InspectionMethod.DVI)
    growth = basis_for(PanelKind.PRESSURISED_SKIN).growth_cycles(InspectionMethod.DVI.finds_down_to)
    assert made.repeat.cycles.count == growth.count // 2


def test_a_more_sensitive_method_buys_a_longer_interval():
    visual = schedule_for(RepairCategory.B, PanelKind.PRESSURISED_SKIN, InspectionMethod.DVI)
    eddy = schedule_for(RepairCategory.B, PanelKind.PRESSURISED_SKIN, InspectionMethod.HFEC)
    assert eddy.repeat.cycles.count > visual.repeat.cycles.count


def test_the_fast_growing_surface_gets_the_shorter_interval():
    wing = schedule_for(RepairCategory.B, PanelKind.WING_LOWER, InspectionMethod.HFEC)
    barrel = schedule_for(RepairCategory.B, PanelKind.PRESSURISED_SKIN, InspectionMethod.HFEC)
    assert wing.repeat.cycles.count < barrel.repeat.cycles.count


def test_a_temporary_repair_gets_a_replacement_limit_and_no_repeat():
    made = schedule_for(RepairCategory.C, PanelKind.PRESSURISED_SKIN, InspectionMethod.GVI)
    assert not made.is_repeating
    assert made.threshold.months == 12


def test_a_method_that_cannot_find_it_before_it_is_critical_is_refused():
    tight = GrowthBasis(inch("1.000"), inch("0.100"))
    with pytest.raises(BadReading, match="already at or past the critical"):
        tight.growth_cycles(InspectionMethod.GVI.finds_down_to)


def test_a_walk_round_on_the_fast_surface_buys_far_less_interval():
    visual = schedule_for(RepairCategory.B, PanelKind.WING_LOWER, InspectionMethod.GVI)
    eddy = schedule_for(RepairCategory.B, PanelKind.WING_LOWER, InspectionMethod.HFEC)
    assert visual.repeat.cycles.count < eddy.repeat.cycles.count
    assert eddy.repeat.cycles.count - visual.repeat.cycles.count > 6000


def test_the_first_look_is_the_threshold_from_when_it_went_on():
    made = schedule_for(RepairCategory.B, PanelKind.PRESSURISED_SKIN, InspectionMethod.DVI)
    due = made.first_due(FLOWN, EMBODIED)
    assert due.cycles.count == 13800 + made.threshold.cycles.count


def test_each_look_after_that_is_the_repeat_from_the_last_one():
    made = schedule_for(RepairCategory.B, PanelKind.PRESSURISED_SKIN, InspectionMethod.DVI)
    due = made.next_due(FLOWN, date(2026, 1, 1))
    assert due.cycles.count == 13800 + made.repeat.cycles.count


def test_a_replacement_limit_has_no_next_look():
    made = schedule_for(RepairCategory.C, PanelKind.PRESSURISED_SKIN, InspectionMethod.GVI)
    with pytest.raises(BadReading, match="it is a replacement limit"):
        made.next_due(FLOWN, date(2026, 1, 1))


def test_a_schedule_reads_back_as_a_programme_line():
    repeating = schedule_for(RepairCategory.B, PanelKind.PRESSURISED_SKIN, InspectionMethod.DVI)
    assert str(repeating).startswith("detailed visual at ")
    assert "then every" in str(repeating)


def test_a_replacement_limit_reads_back_as_one():
    once = InspectionSchedule(InspectionMethod.GVI, Interval(months=12), None)
    assert str(once) == "general visual once by 12 MO"
