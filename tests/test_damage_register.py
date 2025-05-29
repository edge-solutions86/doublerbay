from datetime import date

import pytest

import sample
from doublerbay.damage.crack import Crack
from doublerbay.damage.dent import Dent
from doublerbay.damage.finding import Finding
from doublerbay.damage.kind import DamageKind, DamageOrigin
from doublerbay.damage.register import DamageRegister
from doublerbay.errors import BadReading, NotOnFile
from doublerbay.fleet.check import DiscoverySource
from doublerbay.fleet.utilisation import usage
from doublerbay.ids import FindingId, Serial
from doublerbay.measure.angle import Angle

MSN = Serial("5127")
OTHER = Serial("5240")


def inch(text):
    return sample.inch(text)


def dent(depth="0.030"):
    return Dent(inch(depth), inch("3.000"), inch("2.000"))


def card(sequence=1, serial=MSN, kind=DamageKind.DENT, geometry=None, found=date(2025, 3, 14)):
    return Finding(
        reference=FindingId(2025, sequence),
        serial=serial,
        kind=kind,
        geometry=geometry if geometry is not None else dent(),
        point=sample.point(),
        found_on=found,
        source=DiscoverySource.SCHEDULED_CHECK,
        origin=DamageOrigin.IMPACT if kind is DamageKind.DENT else DamageOrigin.FATIGUE,
        at_usage=usage(13900, 24400),
    )


def register(*cards):
    held = DamageRegister()
    for one in cards or (card(),):
        held.raise_finding(one)
    return held


def test_a_register_counts_its_cards_and_how_many_are_open():
    assert str(register()) == "1 cards, 1 open"
    assert len(register()) == 1
    assert FindingId(2025, 1) in register()


def test_a_card_raised_twice_is_refused():
    held = register()
    with pytest.raises(BadReading, match="has already been raised"):
        held.raise_finding(card())


def test_a_card_raised_at_a_later_revision_is_refused():
    later = card().re_measured(dent("0.040"), date(2025, 6, 1), usage(14200, 24900))
    with pytest.raises(BadReading, match="is raised at revision one"):
        DamageRegister().raise_finding(later)


def test_a_card_that_is_not_there_is_a_lookup_miss():
    with pytest.raises(NotOnFile, match="is not a finding on this register"):
        register().latest(FindingId(2025, 99))


def test_a_re_measurement_makes_a_revision_and_the_earlier_one_stays():
    held = register()
    held.re_measure(FindingId(2025, 1), dent("0.040"), date(2025, 6, 1), usage(14200, 24900))
    assert held.latest(FindingId(2025, 1)).geometry.depth == inch("0.040")
    assert len(held.history(FindingId(2025, 1))) == 2
    assert held.history(FindingId(2025, 1))[0].geometry.depth == inch("0.030")


def test_the_history_comes_back_oldest_first():
    held = register()
    held.re_measure(FindingId(2025, 1), dent("0.040"), date(2025, 6, 1), usage(14200, 24900))
    held.re_measure(FindingId(2025, 1), dent("0.050"), date(2025, 9, 1), usage(14600, 25400))
    assert [one.revision for one in held.history(FindingId(2025, 1))] == [1, 2, 3]


def test_a_card_stays_one_card_however_often_it_is_re_measured():
    held = register()
    held.re_measure(FindingId(2025, 1), dent("0.040"), date(2025, 6, 1), usage(14200, 24900))
    assert len(held) == 1


def test_superseding_closes_a_card_and_names_what_replaced_it():
    held = register(card(1), card(2))
    closed = held.supersede(FindingId(2025, 1), FindingId(2025, 2))
    assert not closed.is_open
    assert held.latest(FindingId(2025, 1)).superseded_by == FindingId(2025, 2)
    assert str(held) == "2 cards, 1 open"


def test_superseding_by_a_card_that_is_not_there_is_refused():
    with pytest.raises(NotOnFile, match="is not a finding on this register"):
        register().supersede(FindingId(2025, 1), FindingId(2025, 99))


def test_a_superseded_card_is_not_superseded_again():
    held = register(card(1), card(2))
    held.supersede(FindingId(2025, 1), FindingId(2025, 2))
    with pytest.raises(BadReading, match="has already been superseded"):
        held.supersede(FindingId(2025, 1), FindingId(2025, 2))


def test_a_superseded_card_is_not_re_measured():
    held = register(card(1), card(2))
    held.supersede(FindingId(2025, 1), FindingId(2025, 2))
    with pytest.raises(BadReading, match="is not re measured"):
        held.re_measure(FindingId(2025, 1), dent("0.040"), date(2025, 6, 1), usage(1, 1))


def test_the_cards_on_one_tail_are_the_cards_on_that_tail():
    held = register(card(1), card(2, serial=OTHER))
    assert [one.reference.sequence for one in held.on_tail(MSN)] == [1]
    assert [one.reference.sequence for one in held.on_tail(OTHER)] == [2]


def test_the_open_list_drops_what_has_been_superseded():
    held = register(card(1), card(2))
    held.supersede(FindingId(2025, 1), FindingId(2025, 2))
    assert [one.reference.sequence for one in held.open_on(MSN)] == [2]


def test_cards_can_be_pulled_out_by_kind():
    cracked = card(2, kind=DamageKind.CRACK, geometry=Crack(inch("0.750"), Angle.of_degrees(0)))
    held = register(card(1), cracked)
    assert [one.reference.sequence for one in held.of_kind(DamageKind.CRACK)] == [2]
    assert [one.reference.sequence for one in held.of_kind(DamageKind.DENT)] == [1]


def test_cards_can_be_pulled_out_by_the_window_they_were_raised_in():
    held = register(card(1, found=date(2025, 1, 5)), card(2, found=date(2025, 6, 20)))
    found = held.found_between(date(2025, 1, 1), date(2025, 3, 1))
    assert [one.reference.sequence for one in found] == [1]


def test_a_window_that_runs_backwards_is_refused():
    with pytest.raises(BadReading, match="is before"):
        register().found_between(date(2025, 6, 1), date(2025, 1, 1))


def test_the_window_is_taken_on_when_the_card_was_raised_and_not_re_measured():
    held = register(card(1, found=date(2025, 1, 5)))
    held.re_measure(FindingId(2025, 1), dent("0.040"), date(2025, 9, 1), usage(14600, 25400))
    assert len(held.found_between(date(2025, 1, 1), date(2025, 3, 1))) == 1
    assert held.found_between(date(2025, 8, 1), date(2025, 10, 1)) == ()


def test_the_next_number_carries_on_from_the_highest_used_that_year():
    held = register(card(1), card(7))
    assert held.next_reference(2025) == FindingId(2025, 8)
    assert held.next_reference(2026) == FindingId(2026, 1)


def test_iterating_gives_the_latest_revision_of_every_card():
    held = register(card(1), card(2))
    held.re_measure(FindingId(2025, 1), dent("0.040"), date(2025, 6, 1), usage(14200, 24900))
    depths = [one.geometry.depth for one in held]
    assert inch("0.040") in depths
    assert inch("0.030") in depths
