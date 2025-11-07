from datetime import date

import pytest

from doublerbay.disposition.deferral import grant
from doublerbay.errors import BadReading, NotOnFile
from doublerbay.ids import FindingId
from doublerbay.measure.interval import Interval
from doublerbay.store.base import build_engine, build_schema
from doublerbay.store.decisions import apply_to_row, deferral_to_row, row_to_deferral
from doublerbay.store.unit import UnitOfWork
from doublerbay.store.work import DeferralRow
from doublerbay.tolerance.method import InspectionMethod
from sample import DAY, aircraft, card, flown, flying_log

REFERENCE = FindingId(2025, 41)
OTHER = FindingId(2025, 42)


@pytest.fixture
def engine():
    made = build_engine()
    build_schema(made)
    with UnitOfWork(made) as unit:
        unit.aircraft.add(aircraft())
        unit.aircraft.open_log(flying_log())
    return made


def deferral(against=REFERENCE, on=DAY, by="j.mercer", **rest):
    return grant(against, on, flown(13800, 24200), by, **rest)


def test_a_deferral_goes_in_and_comes_back_the_same(engine):
    with UnitOfWork(engine) as unit:
        unit.deferrals.grant(deferral())
    with UnitOfWork(engine) as unit:
        held = unit.deferrals.get(REFERENCE)
    assert held.against == REFERENCE
    assert held.by == "j.mercer"
    assert held.limit.cycles is not None


def test_the_four_clocks_survive_the_round_trip(engine):
    limit = Interval.parse("300 FC / 400 FH / 90 DY")
    with UnitOfWork(engine) as unit:
        unit.deferrals.grant(deferral(engineer_set=limit))
    with UnitOfWork(engine) as unit:
        held = unit.deferrals.get(REFERENCE)
    assert held.limit.cycles.count == 300
    assert held.limit.days == 90
    assert held.limit.months is None


def test_the_method_survives_the_round_trip(engine):
    with UnitOfWork(engine) as unit:
        unit.deferrals.grant(deferral(method=InspectionMethod.HFEC))
    with UnitOfWork(engine) as unit:
        assert unit.deferrals.get(REFERENCE).re_inspect_with is InspectionMethod.HFEC


def test_a_second_live_deferral_against_one_card_is_refused(engine):
    with UnitOfWork(engine) as unit:
        unit.deferrals.grant(deferral())
        with pytest.raises(BadReading, match="already deferred"):
            unit.deferrals.grant(deferral())


def test_a_card_that_is_not_deferred_is_not_on_file(engine):
    with UnitOfWork(engine) as unit, pytest.raises(NotOnFile, match="not deferred"):
        unit.deferrals.get(OTHER)


def test_lifting_takes_it_off_the_live_list(engine):
    with UnitOfWork(engine) as unit:
        unit.deferrals.grant(deferral())
    with UnitOfWork(engine) as unit:
        unit.deferrals.lift(REFERENCE, date(2025, 6, 1))
    with UnitOfWork(engine) as unit:
        assert unit.deferrals.live() == ()


def test_a_lifted_deferral_keeps_its_row(engine):
    with UnitOfWork(engine) as unit:
        unit.deferrals.grant(deferral())
    with UnitOfWork(engine) as unit:
        unit.deferrals.lift(REFERENCE, date(2025, 6, 1))
    with UnitOfWork(engine) as unit:
        row = unit.session.get(DeferralRow, REFERENCE)
        assert row is not None
        assert row.lifted == date(2025, 6, 1)


def test_a_deferral_cannot_be_lifted_before_it_was_granted(engine):
    with UnitOfWork(engine) as unit:
        unit.deferrals.grant(deferral())
        with pytest.raises(BadReading, match="cannot be lifted before"):
            unit.deferrals.lift(REFERENCE, date(2024, 1, 1))


def test_lifting_one_that_was_never_granted_is_refused(engine):
    with UnitOfWork(engine) as unit, pytest.raises(NotOnFile):
        unit.deferrals.lift(OTHER, DAY)


def test_a_card_can_be_deferred_again_after_the_first_was_lifted(engine):
    with UnitOfWork(engine) as unit:
        unit.deferrals.grant(deferral())
        unit.deferrals.lift(REFERENCE, date(2025, 6, 1))
    with UnitOfWork(engine) as unit:
        unit.deferrals.grant(deferral(on=date(2025, 6, 2)))
    with UnitOfWork(engine) as unit:
        assert unit.deferrals.get(REFERENCE).granted == date(2025, 6, 2)


def test_tightening_keeps_the_day_it_was_granted(engine):
    with UnitOfWork(engine) as unit:
        unit.deferrals.grant(deferral())
    with UnitOfWork(engine) as unit:
        held = unit.deferrals.get(REFERENCE)
        unit.deferrals.tighten(held.tightened_to(Interval.parse("50 FC"), "a.okonkwo", "wing root"))
    with UnitOfWork(engine) as unit:
        after = unit.deferrals.get(REFERENCE)
    assert after.granted == DAY
    assert after.limit.cycles.count == 50
    assert after.by == "a.okonkwo"
    assert after.reason == "wing root"


def test_tightening_a_card_that_is_not_deferred_is_refused(engine):
    with UnitOfWork(engine) as unit, pytest.raises(NotOnFile, match="not deferred"):
        unit.deferrals.tighten(deferral(against=OTHER))


def test_a_row_is_not_amended_with_another_card_s_deferral():
    row = deferral_to_row(deferral())
    with pytest.raises(BadReading, match="another card"):
        apply_to_row(row, deferral(against=OTHER))


def test_the_live_list_comes_back_in_the_order_granted(engine):
    with UnitOfWork(engine) as unit:
        unit.deferrals.grant(deferral(against=OTHER, on=date(2025, 1, 4)))
        unit.deferrals.grant(deferral(on=DAY))
    with UnitOfWork(engine) as unit:
        assert [one.against for one in unit.deferrals.live()] == [OTHER, REFERENCE]


def test_the_ones_against_a_named_set_come_back_in_the_order_asked_for(engine):
    with UnitOfWork(engine) as unit:
        unit.deferrals.grant(deferral(against=OTHER, on=date(2025, 1, 4)))
        unit.deferrals.grant(deferral(on=DAY))
    with UnitOfWork(engine) as unit:
        asked = unit.deferrals.against((REFERENCE, OTHER))
    assert [one.against for one in asked] == [REFERENCE, OTHER]


def test_a_card_in_the_set_that_is_not_deferred_is_left_out(engine):
    with UnitOfWork(engine) as unit:
        unit.deferrals.grant(deferral())
    with UnitOfWork(engine) as unit:
        assert len(unit.deferrals.against((REFERENCE, OTHER))) == 1


def test_a_window_picks_up_what_was_granted_inside_it(engine):
    with UnitOfWork(engine) as unit:
        unit.deferrals.grant(deferral(against=OTHER, on=date(2025, 1, 4)))
        unit.deferrals.grant(deferral(on=DAY))
    with UnitOfWork(engine) as unit:
        window = unit.deferrals.granted_between(date(2025, 2, 1), date(2025, 12, 31))
    assert [one.against for one in window] == [REFERENCE]


def test_a_window_picks_up_a_lifted_one_too(engine):
    with UnitOfWork(engine) as unit:
        unit.deferrals.grant(deferral())
        unit.deferrals.lift(REFERENCE, date(2025, 6, 1))
    with UnitOfWork(engine) as unit:
        assert len(unit.deferrals.granted_between(DAY, date(2025, 12, 31))) == 1


def test_a_window_that_ends_before_it_starts_is_refused(engine):
    with UnitOfWork(engine) as unit, pytest.raises(BadReading, match="ends after it starts"):
        unit.deferrals.granted_between(date(2025, 12, 31), DAY)


def test_a_stored_row_with_no_clocks_left_on_it_is_refused_by_name():
    row = DeferralRow(
        against=REFERENCE,
        granted=DAY,
        at_cycles=flown(13800, 24200).cycles,
        at_hours=flown(13800, 24200).hours,
        limit_cycles=None,
        limit_hours=None,
        limit_months=None,
        limit_days=None,
        method="detailed visual",
        granted_by="j.mercer",
        reason="",
    )
    with pytest.raises(BadReading, match="has no limit on it"):
        row_to_deferral(row)


def test_a_deferral_survives_beside_the_card_it_is_against(engine):
    with UnitOfWork(engine) as unit:
        raised = card(41)
        unit.findings.raise_finding(raised)
        unit.deferrals.grant(deferral(against=raised.reference))
    with UnitOfWork(engine) as unit:
        assert unit.deferrals.get(REFERENCE).against == REFERENCE
