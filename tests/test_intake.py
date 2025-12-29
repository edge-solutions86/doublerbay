from datetime import date

import pytest

import sample
from doublerbay.damage.corrosion import Corrosion
from doublerbay.damage.kind import DamageKind, DamageOrigin
from doublerbay.errors import BadReading
from doublerbay.fleet.catalogue import TypeCatalogue
from doublerbay.ids import FindingId, Serial
from doublerbay.manual.book import v320_book
from doublerbay.service.intake import Intake, next_reference, take_in, within_limits
from doublerbay.store.base import build_engine, build_schema
from doublerbay.store.unit import UnitOfWork
from doublerbay.tolerance.corrosion import CorrosionLevel

DAY = date(2025, 3, 14)
LATER = date(2026, 3, 14)
MSN = Serial("5127")


@pytest.fixture
def engine():
    made = build_engine()
    build_schema(made)
    with UnitOfWork(made) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(sample.flying_log())
    return made


def corrosion(along="2.000", around="1.000", depth="0.002", exfoliating=False):
    return Corrosion(
        sample.inch(along),
        sample.inch(around),
        sample.inch(depth),
        pitted=False,
        exfoliating=exfoliating,
    )


def rust_card(sequence=1, found=DAY, geometry=None, at=None):
    return sample.card(
        sequence,
        DamageKind.CORROSION,
        geometry or corrosion(),
        at=at,
        found=found,
        origin=DamageOrigin.ENVIRONMENT,
    )


def taken(engine, finding, urgent=False):
    with UnitOfWork(engine) as unit:
        return take_in(unit, v320_book(), TypeCatalogue(), finding, urgent)


def test_a_dent_writes_the_card_and_its_disposition(engine):
    answer = taken(engine, sample.card(1))
    assert answer.reference == FindingId(2025, 1)
    assert not answer.went_on_the_programme
    with UnitOfWork(engine) as unit:
        assert unit.findings.latest(FindingId(2025, 1)).kind is DamageKind.DENT
        assert unit.dispositions.get(FindingId(2025, 1)).is_open


def test_a_dent_reads_back_as_just_raised(engine):
    assert str(taken(engine, sample.card(1))) == "raised DR-2025-000001"


def test_a_dent_never_goes_on_the_corrosion_register(engine):
    taken(engine, sample.card(1))
    with UnitOfWork(engine) as unit:
        assert unit.corrosion.on_tail(MSN) == ()


def test_a_dent_cannot_be_called_an_airworthiness_concern(engine):
    with pytest.raises(BadReading, match="only a corrosion finding"):
        taken(engine, sample.card(1), urgent=True)


def test_a_corrosion_card_goes_on_the_programme(engine):
    answer = taken(engine, rust_card())
    assert answer.went_on_the_programme
    with UnitOfWork(engine) as unit:
        assert len(unit.corrosion.on_tail(MSN)) == 1


def test_a_corrosion_card_reads_back_with_its_level(engine):
    assert "level" in str(taken(engine, rust_card()))


def test_the_first_corrosion_in_a_place_is_level_one(engine):
    assert taken(engine, rust_card()).graded.level is CorrosionLevel.ONE


def test_corrosion_back_in_the_same_place_is_level_two(engine):
    deep = corrosion(depth="0.030")
    taken(engine, rust_card(1, DAY, deep))
    second = taken(engine, rust_card(2, LATER, deep))
    assert second.graded.level is CorrosionLevel.TWO


def test_corrosion_in_a_different_place_stays_level_one(engine):
    deep = corrosion(depth="0.030")
    taken(engine, rust_card(1, DAY, deep))
    elsewhere = taken(engine, rust_card(2, LATER, deep, at=sample.cabin_point(frame=40)))
    assert elsewhere.graded.level is CorrosionLevel.ONE


def test_an_urgent_finding_is_level_three(engine):
    answer = taken(engine, rust_card(), urgent=True)
    assert answer.graded.level is CorrosionLevel.THREE
    assert answer.must_be_reported


def test_a_reportable_finding_carries_the_day_it_is_due_out(engine):
    answer = taken(engine, rust_card(), urgent=True)
    assert answer.report_by == date(2025, 3, 17)


def test_a_finding_that_is_not_reportable_has_no_report_date(engine):
    assert taken(engine, rust_card()).report_by is None


def test_a_dent_has_no_report_date(engine):
    assert taken(engine, sample.card(1)).report_by is None


def test_an_urgent_finding_lands_on_the_unreported_list(engine):
    taken(engine, rust_card(), urgent=True)
    with UnitOfWork(engine) as unit:
        assert len(unit.corrosion.unreported()) == 1


def test_the_programme_entry_is_filed_against_the_card(engine):
    taken(engine, rust_card(), urgent=True)
    with UnitOfWork(engine) as unit:
        unit.corrosion.mark_reported(FindingId(2025, 1), date(2025, 3, 15))
    with UnitOfWork(engine) as unit:
        assert unit.corrosion.unreported() == ()


def test_shallow_corrosion_inside_the_limits_reads_as_within_limits(engine):
    finding = rust_card()
    with UnitOfWork(engine) as unit:
        from doublerbay.service.intake import tail_context

        tail = tail_context(unit, TypeCatalogue(), finding)
        assert within_limits(v320_book(), tail, finding)


def test_deep_corrosion_reads_as_outside_the_limits(engine):
    finding = rust_card(geometry=corrosion(depth="0.060"))
    with UnitOfWork(engine) as unit:
        from doublerbay.service.intake import tail_context

        tail = tail_context(unit, TypeCatalogue(), finding)
        assert not within_limits(v320_book(), tail, finding)


def test_the_next_reference_follows_the_year_the_card_was_found_in(engine):
    with UnitOfWork(engine) as unit:
        assert next_reference(unit, DAY).year == 2025
        assert next_reference(unit, date(2026, 1, 1)).year == 2026


def test_the_next_reference_moves_on_after_a_card_is_raised(engine):
    taken(engine, sample.card(1))
    with UnitOfWork(engine) as unit:
        assert next_reference(unit, DAY) == FindingId(2025, 2)


def test_an_intake_with_no_grading_says_it_went_nowhere():
    assert not Intake(FindingId(2025, 1)).went_on_the_programme
