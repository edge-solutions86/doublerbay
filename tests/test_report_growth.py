from dataclasses import replace
from decimal import Decimal

import pytest

import sample
from doublerbay.damage.crack import Crack
from doublerbay.damage.kind import DamageKind, DamageOrigin
from doublerbay.errors import BadReading
from doublerbay.measure.angle import Angle
from doublerbay.measure.length import Length
from doublerbay.report.growth import (
    PER_CYCLES,
    SHORTEST_SPAN,
    Growth,
    Step,
    growth_of,
    measured_length,
)


def crack(length="0.400"):
    return Crack(sample.inch(length), Angle(0))


def card(length="0.400", cycles=13800, revision=1):
    made = sample.card(
        1,
        DamageKind.CRACK,
        crack(length),
        origin=DamageOrigin.FATIGUE,
    )
    return replace(made, revision=revision, at_usage=sample.flown(cycles, cycles * 2))


def test_a_crack_is_measured_on_its_length():
    assert measured_length(card("0.400")) == sample.inch("0.400")


def test_a_dent_is_not_tracked_for_growth():
    with pytest.raises(BadReading, match="not tracked for growth"):
        measured_length(sample.card(1))


def test_a_card_with_no_revisions_has_no_growth():
    with pytest.raises(BadReading, match="no revisions"):
        growth_of(())


def test_one_reading_gives_no_steps():
    built = growth_of((card(),))
    assert built.steps == ()
    assert built.readings == 1
    assert not built.has_grown


def test_one_reading_reads_back_as_no_growth_measured():
    assert str(growth_of((card(),))) == "DR-2025-000001: one reading, no growth measured"


def test_two_readings_give_one_step():
    built = growth_of((card("0.400", 13800, 1), card("0.500", 14800, 2)))
    assert len(built.steps) == 1
    assert built.has_grown


def test_the_total_is_the_first_to_the_last():
    built = growth_of((card("0.400", 13800, 1), card("0.500", 14800, 2)))
    assert built.total == sample.inch("0.100")


def test_a_crack_that_has_not_moved_has_not_grown():
    built = growth_of((card("0.400", 13800, 1), card("0.400", 14800, 2)))
    assert not built.has_grown
    assert built.total == Length(0)


def test_the_revisions_are_ordered_before_they_are_read():
    built = growth_of((card("0.500", 14800, 2), card("0.400", 13800, 1)))
    assert built.total == sample.inch("0.100")


def test_two_readings_are_not_a_trend():
    assert not growth_of((card("0.400", 13800, 1), card("0.500", 14800, 2))).is_a_trend


def test_three_readings_are_a_trend():
    built = growth_of((card("0.400", 13800, 1), card("0.450", 14300, 2), card("0.500", 14800, 3)))
    assert built.is_a_trend
    assert len(built.steps) == 2


def test_a_step_reads_back_with_both_readings():
    step = Step(sample.inch("0.400"), sample.inch("0.500"), 1000)
    assert str(step) == "0.400 to 0.500 over 1000 FC"


def test_a_step_over_a_thousand_cycles_gives_a_rate():
    step = Step(sample.inch("0.400"), sample.inch("0.500"), 1000)
    assert step.rate == Decimal("0.1000")


def test_a_step_over_two_thousand_cycles_halves_the_rate():
    step = Step(sample.inch("0.400"), sample.inch("0.500"), 2000)
    assert step.rate == Decimal("0.0500")


def test_the_rate_is_quoted_per_thousand_cycles():
    assert PER_CYCLES == 1000


def test_a_step_shorter_than_the_floor_has_no_rate():
    step = Step(sample.inch("0.400"), sample.inch("0.500"), SHORTEST_SPAN - 1)
    assert not step.is_measurable
    with pytest.raises(BadReading, match="too short a span"):
        step.rate


def test_a_step_at_the_floor_is_measurable():
    assert Step(sample.inch("0.400"), sample.inch("0.500"), SHORTEST_SPAN).is_measurable


def test_a_step_that_runs_backwards_in_cycles_is_refused():
    with pytest.raises(BadReading, match="does not run backwards"):
        Step(sample.inch("0.400"), sample.inch("0.500"), -1)


def test_a_crack_that_shrank_reads_as_negative_growth():
    step = Step(sample.inch("0.500"), sample.inch("0.400"), 1000)
    assert step.grew_by.mils < 0
    assert step.rate < 0


def test_the_worst_rate_is_the_fastest_stretch():
    built = growth_of((card("0.400", 13800, 1), card("0.450", 14300, 2), card("0.600", 14800, 3)))
    assert built.worst_rate == Decimal("0.3000")


def test_a_card_with_no_measurable_step_has_no_worst_rate():
    built = growth_of((card("0.400", 13800, 1), card("0.500", 13820, 2)))
    assert built.measurable == ()
    assert built.worst_rate is None


def test_a_card_with_no_measurable_rate_says_so():
    built = growth_of((card("0.400", 13800, 1), card("0.500", 13820, 2)))
    assert "no measurable rate" in str(built)


def test_a_card_with_a_rate_names_it():
    built = growth_of((card("0.400", 13800, 1), card("0.500", 14800, 2)))
    assert "worst 0.1000 in per 1000 FC" in str(built)


def test_a_growth_with_no_steps_totals_nothing():
    assert Growth(sample.card(1).reference, 1, ()).total == Length(0)
