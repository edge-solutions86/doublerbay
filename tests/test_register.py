from datetime import date

import pytest

from doublerbay.errors import BadReading, NotOnFile
from doublerbay.fleet.aircraft import Aircraft
from doublerbay.fleet.model import Bulletin, Effectivity, Series
from doublerbay.fleet.register import Fleet
from doublerbay.fleet.utilisation import Snapshot, UtilisationLog, usage
from doublerbay.ids import Registration, Serial
from doublerbay.measure.usage import Cycles

SB_53_018 = Bulletin(53, 18)


def tail(serial="5127", marks="N412VD", delivered=date(2016, 4, 12), series=Series.STANDARD):
    return Aircraft(
        serial=Serial(serial),
        registration=Registration(marks),
        series=series,
        line_number=1140,
        delivered=delivered,
        embodied=frozenset(),
    )


def log(serial="5127"):
    return UtilisationLog(
        Serial(serial),
        (
            Snapshot(date(2024, 1, 1), usage(12000, 21000)),
            Snapshot(date(2025, 1, 1), usage(13800, 24200)),
        ),
    )


def fleet():
    register = Fleet()
    register.enrol(tail(), log())
    register.enrol(tail(serial="5240", marks="N908QA", delivered=date(2018, 9, 3)), log("5240"))
    register.enrol(
        tail(serial="5301", marks="N155VD", delivered=date(2021, 2, 20), series=Series.LONG_RANGE)
    )
    return register


def test_the_register_counts_what_is_on_it():
    assert len(fleet()) == 3
    assert str(fleet()) == "3 tails on the register"
    assert Serial("5127") in fleet()
    assert Serial("9999") not in fleet()


def test_the_register_hands_back_tails_in_serial_order():
    assert [aircraft.serial.as_int for aircraft in fleet()] == [5127, 5240, 5301]


def test_a_tail_enrolled_twice_is_refused():
    register = fleet()
    with pytest.raises(BadReading, match="already on the register"):
        register.enrol(tail())


def test_a_log_for_another_tail_is_refused():
    register = Fleet()
    with pytest.raises(BadReading, match="is not the log for"):
        register.enrol(tail(), log("5240"))


def test_a_tail_enrolled_without_a_log_opens_one_at_delivery():
    opened = fleet().log_for(Serial("5301"))
    assert opened.opened_on == date(2021, 2, 20)
    assert opened.latest.usage.cycles == Cycles(0)


def test_a_tail_off_the_register_is_a_lookup_miss():
    with pytest.raises(NotOnFile, match="is not on the register"):
        fleet().tail(Serial("9999"))
    with pytest.raises(NotOnFile, match="has no log on the register"):
        fleet().log_for(Serial("9999"))


def test_a_registration_finds_whoever_is_wearing_it():
    assert fleet().by_registration(Registration("N412VD")).serial == Serial("5127")
    with pytest.raises(NotOnFile, match="is not on the register"):
        fleet().by_registration(Registration("N1"))


def test_a_registration_that_has_moved_answers_for_its_new_aeroplane():
    register = fleet()
    register.amend(register.tail(Serial("5127")).re_registered(Registration("N771VD")))
    register.amend(register.tail(Serial("5240")).re_registered(Registration("N412VD")))
    assert register.by_registration(Registration("N412VD")).serial == Serial("5240")


def test_amending_a_tail_that_is_not_on_the_register_is_refused():
    with pytest.raises(NotOnFile, match="is not on the register"):
        Fleet().amend(tail())


def test_a_bulletin_going_on_is_an_amendment():
    register = fleet()
    register.amend(register.tail(Serial("5127")).with_embodied(SB_53_018))
    assert register.tail(Serial("5127")).has_embodied(SB_53_018)


def test_only_the_tails_delivered_by_a_day_are_in_service():
    on_that_day = fleet().in_service_on(date(2017, 1, 1))
    assert [aircraft.serial.as_int for aircraft in on_that_day] == [5127]


def test_a_withdrawn_tail_drops_off_the_in_service_list():
    register = fleet()
    register.amend(register.tail(Serial("5127")).withdrawn_on(date(2026, 1, 1)))
    still_flying = register.in_service_on(date(2026, 6, 1))
    assert Serial("5127") not in [aircraft.serial for aircraft in still_flying]


def test_effectivity_picks_out_the_tails_a_page_applies_to():
    block = Effectivity(first_serial=5200, last_serial=5400)
    assert [a.serial.as_int for a in fleet().covered_by(block)] == [5240, 5301]


def test_effectivity_on_a_series_picks_out_that_series():
    long_range = Effectivity(series=frozenset({Series.LONG_RANGE}))
    assert [a.serial.as_int for a in fleet().covered_by(long_range)] == [5301]


def test_a_posted_reading_lands_on_that_tail_and_no_other():
    register = fleet()
    register.post(Serial("5127"), Snapshot(date(2025, 7, 1), usage(14700, 25800)))
    assert register.log_for(Serial("5127")).latest.usage == usage(14700, 25800)
    assert register.log_for(Serial("5240")).latest.usage == usage(13800, 24200)


def test_a_reading_against_a_tail_that_is_not_there_is_refused():
    with pytest.raises(NotOnFile, match="has no log"):
        fleet().post(Serial("9999"), Snapshot(date(2025, 7, 1), usage(1, 1)))


def test_the_forecast_comes_off_that_tails_own_log():
    forecast = fleet().forecast_for(Serial("5127"))
    assert forecast.as_of == date(2025, 1, 1)
    assert forecast.cycles_a_day > 0


def test_a_tail_that_has_not_flown_has_no_rate_to_forecast_from():
    with pytest.raises(NotOnFile, match="one reading is not a rate"):
        fleet().forecast_for(Serial("5301"))


def test_the_fleet_total_adds_up_only_what_had_been_delivered():
    total = fleet().fleet_usage_on(date(2025, 1, 1))
    assert total.cycles == Cycles(13800 * 2)


def test_the_oldest_tail_in_service_is_the_first_delivered():
    assert fleet().oldest_on(date(2025, 1, 1)).serial == Serial("5127")
    with pytest.raises(NotOnFile, match="nothing was in service"):
        fleet().oldest_on(date(2000, 1, 1))


def test_the_usage_of_one_tail_is_read_off_its_own_log():
    assert fleet().usage_on(date(2024, 6, 1), Serial("5127")) == usage(12000, 21000)
