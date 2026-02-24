from datetime import date

import pytest

import sample
from doublerbay.assess.verdict import Disposition
from doublerbay.damage.dent import Dent
from doublerbay.disposition.deferral import grant
from doublerbay.disposition.state import DispositionRecord, DispositionState
from doublerbay.errors import BadReading, NotOnFile
from doublerbay.fleet.catalogue import TypeCatalogue
from doublerbay.ids import FindingId
from doublerbay.manual.book import v320_book
from doublerbay.measure.interval import Interval
from doublerbay.service.measuring import (
    ReMeasured,
    grew_between,
    history_of,
    re_measure,
)
from doublerbay.store.base import build_engine, build_schema
from doublerbay.store.unit import UnitOfWork

DAY = date(2025, 3, 14)
AGAIN = date(2025, 9, 1)
AGAINST = FindingId(2025, 1)


def dent(depth="0.020", along="3.000", around="2.000"):
    return Dent(sample.inch(depth), sample.inch(along), sample.inch(around))


@pytest.fixture
def engine():
    made = build_engine()
    build_schema(made)
    with UnitOfWork(made) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(sample.flying_log())
    return made


def raised(engine, sequence=1, geometry=None):
    reference = FindingId(2025, sequence)
    with UnitOfWork(engine) as unit:
        unit.findings.raise_finding(
            sample.card(sequence, geometry=geometry or dent(), at=sample.cabin_point(stringer=10))
        )
        unit.dispositions.save(
            DispositionRecord.raised(reference).moved_to(
                DispositionState.ASSESSED, DAY, "a.okonkwo"
            )
        )
    return reference


def defer(engine, reference):
    with UnitOfWork(engine) as unit:
        unit.deferrals.grant(
            grant(
                reference,
                DAY,
                sample.flown(13800, 24200),
                "j.mercer",
                engineer_set=Interval.parse("500 FC"),
            )
        )
        held = unit.dispositions.get(reference)
        unit.dispositions.save(held.moved_to(DispositionState.DEFERRED, DAY, "j.mercer"))


def measured(engine, geometry, reference=AGAINST, on=AGAIN):
    with UnitOfWork(engine) as unit:
        return re_measure(unit, v320_book(), TypeCatalogue(), reference, geometry, on)


def test_a_re_measurement_adds_a_revision(engine):
    raised(engine)
    answer = measured(engine, dent("0.025"))
    assert answer.revision == 2
    with UnitOfWork(engine) as unit:
        assert len(history_of(unit, AGAINST)) == 2


def test_the_first_reading_is_kept(engine):
    raised(engine)
    measured(engine, dent("0.025"))
    with UnitOfWork(engine) as unit:
        first = history_of(unit, AGAINST)[0]
    assert first.geometry.depth == sample.inch("0.020")


def test_the_latest_reading_is_the_new_one(engine):
    raised(engine)
    measured(engine, dent("0.025"))
    with UnitOfWork(engine) as unit:
        assert unit.findings.latest(AGAINST).geometry.depth == sample.inch("0.025")


def test_the_day_it_was_looked_at_is_recorded(engine):
    raised(engine)
    measured(engine, dent("0.025"))
    with UnitOfWork(engine) as unit:
        assert unit.findings.latest(AGAINST).found_on == AGAIN


def test_a_damage_that_has_not_moved_does_not_get_worse(engine):
    raised(engine)
    answer = measured(engine, dent("0.020"))
    assert not answer.got_worse
    assert answer.was is answer.now


def test_a_damage_that_has_grown_past_the_limit_gets_worse(engine):
    raised(engine)
    answer = measured(engine, dent("0.400"))
    assert answer.got_worse
    assert answer.now.rank > answer.was.rank


def test_a_card_measured_before_it_was_found_is_refused(engine):
    raised(engine)
    with pytest.raises(BadReading, match="before it was found"):
        measured(engine, dent("0.025"), on=date(2024, 1, 1))


def test_a_card_that_is_not_on_file_cannot_be_measured(engine):
    with pytest.raises(NotOnFile):
        measured(engine, dent("0.025"), reference=FindingId(2025, 9))


def test_a_superseded_card_points_at_the_one_that_carries_it(engine):
    raised(engine, 1)
    raised(engine, 2)
    with UnitOfWork(engine) as unit:
        unit.findings.supersede(AGAINST, FindingId(2025, 2))
    with pytest.raises(BadReading, match="measure DR-2025-000002 instead"):
        measured(engine, dent("0.025"))


def test_a_card_with_no_deferral_is_not_still_deferred(engine):
    raised(engine)
    assert not measured(engine, dent("0.025")).still_deferred


def test_a_deferred_card_says_it_is_still_deferred(engine):
    reference = raised(engine)
    defer(engine, reference)
    assert measured(engine, dent("0.025")).still_deferred


def test_a_deferred_card_that_grew_has_outgrown_its_deferral(engine):
    reference = raised(engine)
    defer(engine, reference)
    answer = measured(engine, dent("0.400"))
    assert answer.outgrew_its_deferral


def test_a_deferred_card_that_did_not_grow_has_not_outgrown_it(engine):
    reference = raised(engine)
    defer(engine, reference)
    assert not measured(engine, dent("0.020")).outgrew_its_deferral


def test_a_card_that_grew_but_was_never_deferred_has_outgrown_nothing(engine):
    raised(engine)
    assert not measured(engine, dent("0.400")).outgrew_its_deferral


def test_a_re_measurement_reads_back_with_its_revision(engine):
    raised(engine)
    answer = measured(engine, dent("0.020"))
    assert str(answer).startswith("DR-2025-000001 revision 2, ")


def test_a_worse_answer_names_both_dispositions(engine):
    raised(engine)
    answer = measured(engine, dent("0.400"))
    assert " to " in str(answer)


def test_a_card_never_measured_again_has_not_grown(engine):
    raised(engine)
    with UnitOfWork(engine) as unit:
        assert not grew_between(unit, AGAINST)


def test_a_card_measured_the_same_has_not_grown(engine):
    raised(engine)
    measured(engine, dent("0.020"))
    with UnitOfWork(engine) as unit:
        assert not grew_between(unit, AGAINST)


def test_a_card_measured_differently_has_grown(engine):
    raised(engine)
    measured(engine, dent("0.025"))
    with UnitOfWork(engine) as unit:
        assert grew_between(unit, AGAINST)


def test_the_counters_come_off_the_log_when_none_are_given(engine):
    raised(engine)
    measured(engine, dent("0.025"))
    with UnitOfWork(engine) as unit:
        assert unit.findings.latest(AGAINST).at_usage.cycles.count > 0


def test_the_counters_can_be_handed_in(engine):
    raised(engine)
    with UnitOfWork(engine) as unit:
        re_measure(
            unit,
            v320_book(),
            TypeCatalogue(),
            AGAINST,
            dent("0.025"),
            AGAIN,
            sample.flown(14500, 25500),
        )
    with UnitOfWork(engine) as unit:
        assert unit.findings.latest(AGAINST).at_usage.cycles.count == 14500


def test_a_re_measured_record_carries_its_two_answers():
    made = ReMeasured(
        finding=sample.card(1),
        was=Disposition.ALLOWABLE,
        now=Disposition.REPAIRABLE,
        still_deferred=False,
    )
    assert made.got_worse
    assert not made.outgrew_its_deferral
