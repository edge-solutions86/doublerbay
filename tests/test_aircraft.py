from datetime import date
from decimal import Decimal

import pytest

from doublerbay.errors import BadReading
from doublerbay.fleet.aircraft import Aircraft
from doublerbay.fleet.model import Bulletin, Effectivity, Series
from doublerbay.ids import Registration, Serial

SB_53_018 = Bulletin(53, 18)


def tail(
    serial="5127",
    marks="N412VD",
    series=Series.STANDARD,
    line_number=1140,
    delivered=date(2016, 4, 12),
    embodied=(),
    withdrawn=None,
):
    return Aircraft(
        serial=Serial(serial),
        registration=Registration(marks),
        series=series,
        line_number=line_number,
        delivered=delivered,
        embodied=frozenset(embodied),
        withdrawn=withdrawn,
    )


def test_a_tail_reads_as_its_marks_serial_and_series():
    assert str(tail()) == "N412VD (MSN 5127, V320-200)"
    assert tail().tail == "N412VD"


def test_a_line_number_starts_at_one():
    with pytest.raises(BadReading, match="line numbers start at one"):
        tail(line_number=0)


def test_a_tail_cannot_be_withdrawn_before_it_arrives():
    with pytest.raises(BadReading, match="withdrawn before it was delivered"):
        tail(withdrawn=date(2015, 1, 1))


def test_a_tail_is_in_service_from_delivery():
    aircraft = tail()
    assert not aircraft.in_service_on(date(2016, 4, 11))
    assert aircraft.in_service_on(date(2016, 4, 12))
    assert aircraft.in_service_on(date(2030, 1, 1))


def test_a_withdrawn_tail_stops_being_in_service_on_the_day_it_goes():
    aircraft = tail().withdrawn_on(date(2026, 6, 30))
    assert aircraft.in_service_on(date(2026, 6, 29))
    assert not aircraft.in_service_on(date(2026, 6, 30))


def test_the_age_is_measured_from_delivery():
    assert tail().age_years(date(2016, 4, 12)) == Decimal("0.00")
    assert tail().age_years(date(2026, 4, 12)) == Decimal("10.00")


def test_an_age_before_delivery_is_refused():
    with pytest.raises(BadReading, match="is before it was delivered"):
        tail().age_years(date(2015, 1, 1))


def test_a_bulletin_goes_on_by_making_a_new_record():
    before = tail()
    after = before.with_embodied(SB_53_018)
    assert not before.has_embodied(SB_53_018)
    assert after.has_embodied(SB_53_018)
    assert after.serial == before.serial


def test_embodying_a_bulletin_twice_changes_nothing():
    once = tail().with_embodied(SB_53_018)
    assert once.with_embodied(SB_53_018) is once


def test_new_marks_make_a_new_record_on_the_same_airframe():
    before = tail()
    after = before.re_registered(Registration("N908QA"))
    assert after.registration == Registration("N908QA")
    assert after.serial == before.serial
    assert before.registration == Registration("N412VD")


def test_re_registering_to_the_same_marks_changes_nothing():
    aircraft = tail()
    assert aircraft.re_registered(Registration("N412VD")) is aircraft


def test_the_serial_is_the_key_and_the_marks_are_not():
    one = tail()
    same_metal = one.re_registered(Registration("N908QA"))
    other_metal = tail(serial="5240")
    assert one.serial == same_metal.serial
    assert one.serial != other_metal.serial


def test_a_page_written_for_a_range_answers_for_a_tail_inside_it():
    aircraft = tail()
    block = Effectivity(first_serial=5001, last_serial=5240)
    assert aircraft.covered_by(block)
    assert not tail(serial="5300").covered_by(block)


def test_a_page_written_after_a_bulletin_needs_the_bulletin_on():
    after_mod = Effectivity(requires=SB_53_018)
    assert not tail().covered_by(after_mod)
    assert tail().with_embodied(SB_53_018).covered_by(after_mod)


def test_a_page_for_another_series_does_not_answer():
    only_short = Effectivity(series=frozenset({Series.SHORT}))
    assert not tail().covered_by(only_short)
    assert tail(series=Series.SHORT).covered_by(only_short)


def test_a_tail_is_frozen_and_hashes_by_value():
    assert len({tail(), tail()}) == 1
    with pytest.raises(AttributeError):
        tail().registration = Registration("N908QA")
