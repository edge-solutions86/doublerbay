from datetime import date

import pytest

from doublerbay.disposition.state import (
    MOVES,
    DispositionRecord,
    DispositionState,
    Transition,
)
from doublerbay.errors import BadReading, OutOfSequence
from doublerbay.ids import FindingId

DR = FindingId(2025, 4182)
DAY = date(2025, 3, 14)


def raised():
    return DispositionRecord.raised(DR)


def assessed(on=DAY):
    return raised().moved_to(DispositionState.ASSESSED, on, "structures")


def test_a_card_starts_raised_with_nothing_behind_it():
    assert raised().state is DispositionState.RAISED
    assert raised().moves_made == 0
    assert str(raised()) == "DR-2025-004182 raised, 0 moves"


def test_only_superseded_is_terminal():
    for state in DispositionState:
        assert state.is_terminal == (state is DispositionState.SUPERSEDED)
    assert MOVES[DispositionState.SUPERSEDED] == frozenset()


def test_closed_and_superseded_are_the_two_the_desk_no_longer_owns():
    assert not DispositionState.CLOSED.is_open
    assert not DispositionState.SUPERSEDED.is_open
    for state in (
        DispositionState.RAISED,
        DispositionState.ASSESSED,
        DispositionState.DEFERRED,
        DispositionState.HELD,
        DispositionState.IN_WORK,
    ):
        assert state.is_open


def test_the_states_where_the_aeroplane_is_flying_with_the_damage():
    assert DispositionState.DEFERRED.is_flying_with_it
    assert DispositionState.HELD.is_flying_with_it
    assert not DispositionState.IN_WORK.is_flying_with_it
    assert not DispositionState.CLOSED.is_flying_with_it


def test_a_card_cannot_skip_the_assessment_on_its_way_to_closed():
    assert not DispositionState.RAISED.can_move_to(DispositionState.CLOSED)
    with pytest.raises(OutOfSequence, match="cannot go from raised to closed"):
        raised().moved_to(DispositionState.CLOSED, DAY, "structures")


def test_an_assessed_card_can_go_four_ways():
    for state in (
        DispositionState.DEFERRED,
        DispositionState.HELD,
        DispositionState.IN_WORK,
        DispositionState.CLOSED,
    ):
        assert assessed().moved_to(state, DAY, "structures").state is state


def test_a_deferred_card_comes_back_to_assessed_after_a_re_measurement():
    deferred = assessed().moved_to(DispositionState.DEFERRED, DAY, "structures")
    again = deferred.moved_to(DispositionState.ASSESSED, date(2025, 6, 1), "structures")
    assert again.state is DispositionState.ASSESSED


def test_a_card_in_work_does_not_go_back_to_deferred():
    in_work = assessed().moved_to(DispositionState.IN_WORK, DAY, "hangar")
    with pytest.raises(OutOfSequence, match="cannot go from in work to deferred"):
        in_work.moved_to(DispositionState.DEFERRED, DAY, "structures")


def test_a_closed_card_can_be_reopened_by_assessing_it_again():
    closed = assessed().moved_to(DispositionState.CLOSED, DAY, "structures")
    assert (
        closed.moved_to(DispositionState.ASSESSED, date(2026, 1, 1), "structures").state
        is DispositionState.ASSESSED
    )


def test_a_superseded_card_goes_nowhere():
    gone = raised().moved_to(DispositionState.SUPERSEDED, DAY, "structures")
    for state in DispositionState:
        with pytest.raises(OutOfSequence):
            gone.moved_to(state, DAY, "structures")


def test_every_move_is_written_into_the_history():
    walked = assessed().moved_to(DispositionState.IN_WORK, date(2025, 4, 1), "hangar")
    assert walked.moves_made == 2
    assert walked.history[0].now is DispositionState.ASSESSED
    assert walked.history[1].by == "hangar"


def test_a_move_dated_before_the_one_before_it_is_refused():
    walked = assessed(on=date(2025, 4, 1))
    with pytest.raises(BadReading, match="dated before the one before it"):
        walked.moved_to(DispositionState.IN_WORK, date(2025, 1, 1), "hangar")


def test_a_move_needs_somebody_to_have_made_it():
    with pytest.raises(BadReading, match="made by somebody"):
        Transition(DispositionState.RAISED, DispositionState.ASSESSED, DAY, "  ")


def test_a_move_reads_back_who_made_it_and_why():
    move = Transition(
        DispositionState.ASSESSED, DispositionState.DEFERRED, DAY, "structures", "awaiting parts"
    )
    assert str(move) == "2025-03-14 assessed to deferred by structures (awaiting parts)"


def test_a_card_remembers_every_state_it_has_been_in():
    walked = assessed().moved_to(DispositionState.DEFERRED, date(2025, 4, 1), "structures")
    assert walked.has_been(DispositionState.RAISED)
    assert walked.has_been(DispositionState.ASSESSED)
    assert walked.has_been(DispositionState.DEFERRED)
    assert not walked.has_been(DispositionState.CLOSED)


def test_a_card_remembers_when_it_last_entered_a_state():
    walked = assessed(on=date(2025, 3, 14)).moved_to(
        DispositionState.DEFERRED, date(2025, 4, 1), "structures"
    )
    assert walked.entered(DispositionState.DEFERRED) == date(2025, 4, 1)
    assert walked.entered(DispositionState.CLOSED) is None


def test_re_entering_a_state_updates_when_it_last_happened():
    walked = assessed(on=date(2025, 3, 14))
    walked = walked.moved_to(DispositionState.DEFERRED, date(2025, 4, 1), "structures")
    walked = walked.moved_to(DispositionState.ASSESSED, date(2025, 6, 1), "structures")
    walked = walked.moved_to(DispositionState.DEFERRED, date(2025, 7, 1), "structures")
    assert walked.entered(DispositionState.DEFERRED) == date(2025, 7, 1)


def test_moving_a_card_leaves_the_earlier_record_alone():
    before = assessed()
    before.moved_to(DispositionState.IN_WORK, DAY, "hangar")
    assert before.state is DispositionState.ASSESSED
    assert before.moves_made == 1
