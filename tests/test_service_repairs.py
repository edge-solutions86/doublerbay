from datetime import date

import pytest

import sample
from doublerbay.assess.placement import place
from doublerbay.disposition.deferral import grant
from doublerbay.disposition.state import DispositionRecord, DispositionState
from doublerbay.errors import BadReading, NotOnFile
from doublerbay.fleet.utilisation import usage
from doublerbay.grid.ata import Reference
from doublerbay.grid.surface import Offset
from doublerbay.ids import FindingId, PanelId, RepairId, Serial
from doublerbay.measure.interval import Interval
from doublerbay.repair.record import RepairRecord
from doublerbay.repair.scheme import RepairCategory, select_scheme
from doublerbay.service.repairs import Embodiment, embody, remove, state_after
from doublerbay.store.base import build_engine, build_schema
from doublerbay.store.unit import UnitOfWork
from doublerbay.store.work import DeferralRow

PAGE = Reference.parse("53-40-01")
DAY = date(2025, 3, 14)
FITTED = date(2025, 4, 2)
MSN = Serial("5127")
OTHER = Serial("5240")
PART = PanelId(53, 10, 143, "L")


def inch(text):
    return sample.inch(text)


def placed():
    return place(sample.tail_context(), sample.card(at=sample.cabin_point(stringer=10)))


def record(sequence=1, centre=("468.000", "91.000"), temporary=False, embodied=FITTED):
    return RepairRecord(
        reference=RepairId(53, sequence),
        part=PART,
        against=FindingId(2025, sequence),
        scheme=select_scheme(inch("2.000"), inch("2.000"), placed(), PAGE, None, temporary),
        centre=Offset(inch(centre[0]), inch(centre[1])),
        embodied=embodied,
        at_usage=usage(13900, 24400),
    )


@pytest.fixture
def engine():
    made = build_engine()
    build_schema(made)
    with UnitOfWork(made) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(sample.flying_log())
    return made


def assessed(engine, sequence=1, serial="5127"):
    reference = FindingId(2025, sequence)
    with UnitOfWork(engine) as unit:
        unit.findings.raise_finding(sample.card(sequence, serial=serial))
        unit.dispositions.save(
            DispositionRecord.raised(reference).moved_to(
                DispositionState.ASSESSED, DAY, "a.okonkwo"
            )
        )
    return reference


def deferred(engine, sequence=1):
    assessed(engine, sequence)
    reference = FindingId(2025, sequence)
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
    return reference


def fit(engine, one=None, serial=MSN, by="s.brandt", on=None):
    with UnitOfWork(engine) as unit:
        return embody(unit, one or record(), serial, by, on)


def test_a_permanent_repair_closes_the_card(engine):
    assessed(engine)
    answer = fit(engine)
    assert answer.state is DispositionState.CLOSED
    with UnitOfWork(engine) as unit:
        assert unit.dispositions.get(FindingId(2025, 1)).state is DispositionState.CLOSED


def test_a_temporary_repair_leaves_the_card_in_work(engine):
    assessed(engine)
    answer = fit(engine, record(temporary=True))
    assert answer.state is DispositionState.IN_WORK


def test_the_state_a_repair_leaves_a_card_in_follows_its_category():
    assert state_after(record()) is DispositionState.CLOSED
    assert state_after(record(temporary=True)) is DispositionState.IN_WORK
    assert record(temporary=True).scheme.category is RepairCategory.C


def test_the_repair_itself_goes_on_file(engine):
    assessed(engine)
    fit(engine)
    with UnitOfWork(engine) as unit:
        assert unit.repairs.get(RepairId(53, 1)).part == PART


def test_embodying_lifts_the_deferral_it_answers(engine):
    deferred(engine)
    answer = fit(engine)
    assert answer.deferral_lifted
    with UnitOfWork(engine) as unit:
        assert unit.deferrals.live() == ()


def test_a_card_that_was_never_deferred_lifts_nothing(engine):
    assessed(engine)
    assert not fit(engine).deferral_lifted


def test_the_deferral_is_lifted_on_the_day_the_repair_went_on(engine):
    deferred(engine)
    fit(engine)
    with UnitOfWork(engine) as unit:
        assert unit.session.get(DeferralRow, FindingId(2025, 1)).lifted == FITTED


def test_a_repair_must_be_embodied_by_somebody(engine):
    assessed(engine)
    with pytest.raises(BadReading, match="embodied by somebody"):
        fit(engine, by="   ")


def test_a_repair_cannot_be_signed_off_before_it_was_fitted(engine):
    assessed(engine)
    with pytest.raises(BadReading, match="before it was fitted"):
        fit(engine, on=date(2024, 1, 1))


def test_a_repair_against_another_tail_s_card_is_refused(engine):
    assessed(engine)
    with pytest.raises(BadReading, match="is not on MSN 5240"):
        fit(engine, serial=OTHER)


def test_a_repair_against_a_card_that_is_not_on_file_is_refused(engine):
    with UnitOfWork(engine) as unit, pytest.raises(NotOnFile):
        embody(unit, record(), MSN, "s.brandt")


def test_a_repair_number_used_twice_is_refused(engine):
    assessed(engine)
    fit(engine)
    with pytest.raises(BadReading, match="already on file"):
        fit(engine)


def test_a_clean_fit_reports_no_clashes(engine):
    assessed(engine)
    answer = fit(engine)
    assert answer.is_clean
    assert answer.clashes == ()


def test_a_repair_too_close_to_an_older_one_is_reported(engine):
    assessed(engine, 1)
    assessed(engine, 2)
    fit(engine, record(1, ("468.000", "91.000")))
    answer = fit(engine, record(2, ("469.000", "91.000")))
    assert not answer.is_clean
    assert answer.clashes[0] == (RepairId(53, 1), RepairId(53, 2))


def test_a_repair_far_from_the_older_one_is_clean(engine):
    assessed(engine, 1)
    assessed(engine, 2)
    fit(engine, record(1, ("468.000", "91.000")))
    assert fit(engine, record(2, ("620.000", "91.000"))).is_clean


def test_a_clash_does_not_stop_the_repair_going_on(engine):
    assessed(engine, 1)
    assessed(engine, 2)
    fit(engine, record(1, ("468.000", "91.000")))
    fit(engine, record(2, ("469.000", "91.000")))
    with UnitOfWork(engine) as unit:
        assert len(unit.repairs.on_tail(MSN)) == 2


def test_an_embodiment_reads_back_as_one_line(engine):
    assessed(engine)
    assert str(fit(engine)) == "RP-53-0001 fitted, card closed"


def test_an_embodiment_with_clashes_says_how_many():
    made = Embodiment(
        RepairId(53, 2), DispositionState.CLOSED, False, ((RepairId(53, 1), RepairId(53, 2)),)
    )
    assert "1 clashes" in str(made)


def test_removing_a_repair_takes_it_off_the_fitted_list(engine):
    assessed(engine)
    fit(engine)
    with UnitOfWork(engine) as unit:
        remove(unit, RepairId(53, 1), date(2026, 1, 5))
    with UnitOfWork(engine) as unit:
        assert unit.repairs.on_tail(MSN) == ()


def test_removing_a_repair_does_not_reopen_the_card(engine):
    assessed(engine)
    fit(engine)
    with UnitOfWork(engine) as unit:
        remove(unit, RepairId(53, 1), date(2026, 1, 5))
    with UnitOfWork(engine) as unit:
        assert unit.dispositions.get(FindingId(2025, 1)).state is DispositionState.CLOSED


def test_removing_one_that_is_not_on_file_is_refused(engine):
    with UnitOfWork(engine) as unit, pytest.raises(NotOnFile):
        remove(unit, RepairId(53, 9), DAY)
