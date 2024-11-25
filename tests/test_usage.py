from decimal import Decimal

import pytest

from doublerbay.measure.usage import Cycles, Hours, Usage


def test_cycles_are_a_whole_count():
    assert Cycles(1200).count == 1200
    assert str(Cycles(1200)) == "1200 FC"
    with pytest.raises(TypeError):
        Cycles(1200.5)  # type: ignore[arg-type]


def test_no_counter_runs_backwards():
    with pytest.raises(ValueError, match="negative cycle"):
        Cycles(-1)
    with pytest.raises(ValueError, match="negative hour"):
        Hours(-1)


def test_hours_are_held_as_minutes():
    assert Hours.of_hours("1.5").minutes == 90
    assert Hours(90).hours == Decimal("1.5")
    assert str(Hours(90)) == "1.5 FH"


def test_an_hour_finer_than_a_minute_is_refused():
    with pytest.raises(ValueError, match="finer than a minute"):
        Hours.of_hours("1.001")


def test_counters_add_and_subtract_within_their_own_kind():
    assert Cycles(10) + Cycles(5) == Cycles(15)
    assert Hours(120) - Hours(30) == Hours(90)


def test_a_counter_will_not_mix_with_the_other_one():
    with pytest.raises(TypeError):
        Cycles(10) + Hours(10)  # type: ignore[operator]
    with pytest.raises(TypeError):
        Cycles(10) < Hours(10)  # type: ignore[operator]


def test_counters_order_and_hash_within_their_kind():
    assert Cycles(10) < Cycles(20)
    assert Hours(60) >= Hours(60)
    assert len({Cycles(10), Cycles(10)}) == 1
    assert len({Hours(60), Hours(60)}) == 1


def test_a_usage_carries_both_counters():
    usage = Usage(Cycles(1200), Hours.of_hours(2400))
    assert str(usage) == "1200 FC / 2400.0 FH"


def test_usages_add_and_subtract_together():
    a = Usage(Cycles(100), Hours.of_hours(200))
    b = Usage(Cycles(20), Hours.of_hours(50))
    assert (a + b) == Usage(Cycles(120), Hours.of_hours(250))
    assert (a - b) == Usage(Cycles(80), Hours.of_hours(150))


def test_a_short_haul_tail_and_a_long_haul_tail_do_not_order():
    short_haul = Usage(Cycles(9000), Hours.of_hours(11000))
    long_haul = Usage(Cycles(3000), Hours.of_hours(18000))
    assert not short_haul.reaches_both(long_haul)
    assert not long_haul.reaches_both(short_haul)
    assert short_haul.reaches_either(long_haul)
    assert long_haul.reaches_either(short_haul)


def test_a_limit_falls_due_on_whichever_counter_gets_there_first():
    limit = Usage(Cycles(3000), Hours.of_hours(6000))
    flown = Usage(Cycles(3000), Hours.of_hours(4100))
    assert flown.reaches_either(limit)
    assert not flown.reaches_both(limit)


def test_neither_counter_there_yet_is_not_due():
    limit = Usage(Cycles(3000), Hours.of_hours(6000))
    flown = Usage(Cycles(2999), Hours.of_hours(5999))
    assert not flown.reaches_either(limit)


def test_the_average_leg_separates_the_two_operations():
    short_haul = Usage(Cycles(9000), Hours.of_hours(11000))
    long_haul = Usage(Cycles(3000), Hours.of_hours(18000))
    assert short_haul.average_leg == Decimal("1.22")
    assert long_haul.average_leg == Decimal("6.00")


def test_a_tail_that_has_not_flown_has_no_average_leg():
    with pytest.raises(ZeroDivisionError):
        Usage.zero().average_leg


def test_zero_is_zero_on_both_counters():
    assert Usage.zero() == Usage(Cycles(0), Hours(0))
