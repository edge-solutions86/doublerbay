from datetime import date

import pytest

import sample
from doublerbay.damage.dent import Dent
from doublerbay.damage.kind import DamageKind
from doublerbay.disposition.state import DispositionRecord, DispositionState
from doublerbay.errors import BadReading, NotOnFile, OutOfSequence
from doublerbay.fleet.model import Bulletin
from doublerbay.fleet.utilisation import Snapshot
from doublerbay.ids import FindingId, Registration, Serial
from doublerbay.store.base import build_engine, build_schema
from doublerbay.store.unit import UnitOfWork

MSN = Serial("5127")
DR = FindingId(2025, 4182)


def inch(text):
    return sample.inch(text)


def engine():
    made = build_engine()
    build_schema(made)
    return made


def stocked():
    """An engine with one tail and its log already on it."""
    made = engine()
    with UnitOfWork(made) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(sample.flying_log())
    return made


def test_a_tail_goes_in_and_comes_back_as_the_domain_object():
    made = stocked()
    with UnitOfWork(made) as unit:
        assert unit.aircraft.get(MSN) == sample.aircraft()


def test_a_tail_added_twice_is_refused():
    made = stocked()
    with pytest.raises(BadReading, match="already on the register"), UnitOfWork(made) as unit:
        unit.aircraft.add(sample.aircraft())


def test_a_tail_that_is_not_there_is_a_lookup_miss():
    made = engine()
    with pytest.raises(NotOnFile, match="is not on the register"), UnitOfWork(made) as unit:
        unit.aircraft.get(MSN)


def test_a_tail_can_be_found_by_the_marks_it_is_wearing():
    made = stocked()
    with UnitOfWork(made) as unit:
        assert unit.aircraft.by_registration(Registration("N412VD")).serial == MSN
        with pytest.raises(NotOnFile, match="is not on the register"):
            unit.aircraft.by_registration(Registration("N1"))


def test_amending_a_tail_keeps_the_new_configuration():
    made = stocked()
    with UnitOfWork(made) as unit:
        unit.aircraft.amend(unit.aircraft.get(MSN).with_embodied(Bulletin(53, 18)))
    with UnitOfWork(made) as unit:
        assert unit.aircraft.get(MSN).has_embodied(Bulletin(53, 18))


def test_amending_a_tail_that_is_not_there_is_refused():
    made = engine()
    with pytest.raises(NotOnFile, match="is not on the register"), UnitOfWork(made) as unit:
        unit.aircraft.amend(sample.aircraft())


def test_the_log_comes_back_as_a_log():
    made = stocked()
    with UnitOfWork(made) as unit:
        assert unit.aircraft.log_for(MSN) == sample.flying_log()


def test_a_tail_with_no_log_is_a_lookup_miss():
    made = engine()
    with UnitOfWork(made) as unit:
        unit.aircraft.add(sample.aircraft())
    with pytest.raises(NotOnFile, match="has no log"), UnitOfWork(made) as unit:
        unit.aircraft.log_for(MSN)


def test_a_posted_reading_lands_on_the_log():
    made = stocked()
    with UnitOfWork(made) as unit:
        unit.aircraft.post_reading(MSN, Snapshot(date(2025, 7, 1), sample.flown(14700, 25800)))
    with UnitOfWork(made) as unit:
        assert unit.aircraft.log_for(MSN).latest.usage == sample.flown(14700, 25800)


def test_a_reading_that_runs_the_counter_backwards_is_refused():
    made = stocked()
    with pytest.raises(BadReading, match="cycles fell"), UnitOfWork(made) as unit:
        unit.aircraft.post_reading(MSN, Snapshot(date(2025, 7, 1), sample.flown(1, 1)))


def test_the_in_service_list_comes_off_the_delivery_dates():
    made = stocked()
    with UnitOfWork(made) as unit:
        assert len(unit.aircraft.in_service_on(date(2020, 1, 1))) == 1
        assert unit.aircraft.in_service_on(date(2000, 1, 1)) == ()


def test_a_card_goes_in_and_comes_back_as_the_domain_object():
    made = stocked()
    with UnitOfWork(made) as unit:
        unit.findings.raise_finding(sample.card())
    with UnitOfWork(made) as unit:
        assert unit.findings.latest(FindingId(2025, 1)) == sample.card()


def test_a_card_raised_twice_is_refused():
    made = stocked()
    with UnitOfWork(made) as unit:
        unit.findings.raise_finding(sample.card())
    with pytest.raises(BadReading, match="already been raised"), UnitOfWork(made) as unit:
        unit.findings.raise_finding(sample.card())


def test_a_card_raised_at_a_later_revision_is_refused():
    made = stocked()
    later = sample.card().re_measured(
        Dent(inch("0.040"), inch("3.000"), inch("2.000")), date(2025, 6, 1), sample.flown(1, 1)
    )
    with pytest.raises(BadReading, match="raised at revision one"), UnitOfWork(made) as unit:
        unit.findings.raise_finding(later)


def test_a_later_revision_lands_on_the_same_card():
    made = stocked()
    with UnitOfWork(made) as unit:
        unit.findings.raise_finding(sample.card())
    with UnitOfWork(made) as unit:
        unit.findings.record_revision(
            unit.findings.latest(FindingId(2025, 1)).re_measured(
                Dent(inch("0.040"), inch("3.000"), inch("2.000")),
                date(2025, 6, 1),
                sample.flown(14200, 24900),
            )
        )
    with UnitOfWork(made) as unit:
        assert unit.findings.latest(FindingId(2025, 1)).revision == 2
        assert len(unit.findings.history(FindingId(2025, 1))) == 2


def test_a_revision_of_a_card_that_is_not_on_file_is_refused():
    made = stocked()
    with pytest.raises(NotOnFile, match="not a finding on this register"), UnitOfWork(made) as u:
        u.findings.record_revision(sample.card())


def test_a_revision_that_is_not_later_is_refused():
    made = stocked()
    with UnitOfWork(made) as unit:
        unit.findings.raise_finding(sample.card())
    with pytest.raises(BadReading, match="already at revision 1"), UnitOfWork(made) as unit:
        unit.findings.record_revision(sample.card())


def test_the_cards_on_a_tail_come_back_in_number_order():
    made = stocked()
    with UnitOfWork(made) as unit:
        for sequence in (3, 1, 2):
            unit.findings.raise_finding(sample.card(sequence=sequence))
    with UnitOfWork(made) as unit:
        found = unit.findings.on_tail(MSN)
    assert [one.reference.sequence for one in found] == [1, 2, 3]


def test_the_open_list_drops_what_has_been_superseded():
    made = stocked()
    with UnitOfWork(made) as unit:
        unit.findings.raise_finding(sample.card(sequence=1))
        unit.findings.raise_finding(sample.card(sequence=2))
    with UnitOfWork(made) as unit:
        unit.findings.supersede(FindingId(2025, 1), FindingId(2025, 2))
    with UnitOfWork(made) as unit:
        assert [one.reference.sequence for one in unit.findings.open_on(MSN)] == [2]


def test_superseding_does_not_make_a_revision_because_nothing_was_re_measured():
    made = stocked()
    with UnitOfWork(made) as unit:
        unit.findings.raise_finding(sample.card(sequence=1))
        unit.findings.raise_finding(sample.card(sequence=2))
    with UnitOfWork(made) as unit:
        unit.findings.supersede(FindingId(2025, 1), FindingId(2025, 2))
    with UnitOfWork(made) as unit:
        assert len(unit.findings.history(FindingId(2025, 1))) == 1
        assert unit.findings.latest(FindingId(2025, 1)).revision == 1


def test_superseding_a_card_that_is_not_on_file_is_refused():
    made = stocked()
    with pytest.raises(NotOnFile, match="not a finding on this register"), UnitOfWork(made) as u:
        u.findings.supersede(FindingId(2025, 1), FindingId(2025, 2))


def test_cards_can_be_pulled_out_by_kind():
    made = stocked()
    with UnitOfWork(made) as unit:
        unit.findings.raise_finding(sample.card())
    with UnitOfWork(made) as unit:
        assert len(unit.findings.of_kind(DamageKind.DENT)) == 1
        assert unit.findings.of_kind(DamageKind.CRACK) == ()


def test_the_next_number_carries_on_from_the_highest_used():
    made = stocked()
    with UnitOfWork(made) as unit:
        unit.findings.raise_finding(sample.card(sequence=7))
    with UnitOfWork(made) as unit:
        assert unit.findings.next_reference(2025) == FindingId(2025, 8)
        assert unit.findings.next_reference(2026) == FindingId(2026, 1)


def test_a_disposition_saves_and_comes_back_with_its_history():
    made = stocked()
    record = DispositionRecord.raised(DR).moved_to(
        DispositionState.ASSESSED, date(2025, 3, 14), "structures"
    )
    with UnitOfWork(made) as unit:
        unit.dispositions.save(record)
    with UnitOfWork(made) as unit:
        assert unit.dispositions.get(DR) == record


def test_saving_a_disposition_again_replaces_what_was_there():
    made = stocked()
    first = DispositionRecord.raised(DR).moved_to(
        DispositionState.ASSESSED, date(2025, 3, 14), "structures"
    )
    with UnitOfWork(made) as unit:
        unit.dispositions.save(first)
    with UnitOfWork(made) as unit:
        unit.dispositions.save(first.moved_to(DispositionState.IN_WORK, date(2025, 4, 1), "hangar"))
    with UnitOfWork(made) as unit:
        held = unit.dispositions.get(DR)
    assert held.state is DispositionState.IN_WORK
    assert held.moves_made == 2


def test_dispositions_can_be_pulled_out_by_state():
    made = stocked()
    with UnitOfWork(made) as unit:
        unit.dispositions.save(DispositionRecord.raised(DR))
        unit.dispositions.save(DispositionRecord.raised(FindingId(2025, 4183)))
    with UnitOfWork(made) as unit:
        assert len(unit.dispositions.in_state("raised")) == 2
        assert unit.dispositions.in_state("closed") == ()
        assert len(unit.dispositions.all()) == 2


def test_a_disposition_that_is_not_on_file_is_a_lookup_miss():
    made = stocked()
    with pytest.raises(NotOnFile, match="has no disposition on file"), UnitOfWork(made) as unit:
        unit.dispositions.get(DR)


def test_nothing_is_written_when_the_block_raises():
    made = stocked()
    with pytest.raises(RuntimeError), UnitOfWork(made) as unit:
        unit.findings.raise_finding(sample.card())
        unit.dispositions.save(DispositionRecord.raised(FindingId(2025, 1)))
        raise RuntimeError("something went wrong")
    with UnitOfWork(made) as unit:
        assert unit.findings.on_tail(MSN) == ()
        assert unit.dispositions.all() == ()


def test_a_unit_of_work_outside_its_block_refuses_to_do_anything():
    unit = UnitOfWork(stocked())
    with pytest.raises(OutOfSequence, match="not open, use it in a with block"):
        unit.findings.latest(DR)


def test_a_flush_makes_a_write_visible_inside_the_same_block():
    made = stocked()
    with UnitOfWork(made) as unit:
        unit.findings.raise_finding(sample.card())
        unit.flush()
        assert unit.findings.latest(FindingId(2025, 1)).reference == FindingId(2025, 1)


def test_a_rollback_inside_the_block_undoes_what_came_before_it():
    made = stocked()
    with UnitOfWork(made) as unit:
        unit.findings.raise_finding(sample.card())
        unit.rollback()
    with UnitOfWork(made) as unit:
        assert unit.findings.on_tail(MSN) == ()
