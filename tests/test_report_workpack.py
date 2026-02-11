from datetime import date

import pytest

import sample
from doublerbay.disposition.deferral import grant
from doublerbay.disposition.state import DispositionRecord, DispositionState
from doublerbay.errors import BadReading
from doublerbay.fleet.utilisation import Snapshot, UtilisationLog
from doublerbay.ids import FindingId, RepairId, Serial
from doublerbay.measure.interval import Interval
from doublerbay.report.workpack import (
    PackLine,
    build,
    references_on,
    repairs_touched,
)
from doublerbay.service.orders import raise_order
from doublerbay.store.base import build_engine, build_schema
from doublerbay.store.unit import UnitOfWork

DAY = date(2025, 3, 14)
OPENS = date(2025, 4, 1)
CLOSES = date(2025, 4, 8)
MSN = Serial("5127")
QUESTION = "dent in the chem mill land at FR15, past the allowable, what may we do"


def log_with_two_readings():
    opened = UtilisationLog.opened(MSN, date(2016, 4, 12))
    opened = opened.posted(Snapshot(date(2024, 3, 14), sample.flown(12000, 21000)))
    return opened.posted(Snapshot(date(2025, 3, 1), sample.flown(13800, 24200)))


@pytest.fixture
def engine():
    made = build_engine()
    build_schema(made)
    with UnitOfWork(made) as unit:
        unit.aircraft.add(sample.aircraft())
        unit.aircraft.open_log(log_with_two_readings())
    return made


def assessed(engine, sequence=1):
    reference = FindingId(2025, sequence)
    with UnitOfWork(engine) as unit:
        unit.findings.raise_finding(sample.card(sequence, at=sample.cabin_point(stringer=10)))
        unit.dispositions.save(
            DispositionRecord.raised(reference).moved_to(
                DispositionState.ASSESSED, DAY, "a.okonkwo"
            )
        )
    return reference


def defer(engine, reference, limit="500 FC", on=DAY, at=(13800, 24200)):
    with UnitOfWork(engine) as unit:
        unit.deferrals.grant(
            grant(reference, on, sample.flown(*at), "j.mercer", engineer_set=Interval.parse(limit))
        )
        held = unit.dispositions.get(reference)
        unit.dispositions.save(held.moved_to(DispositionState.DEFERRED, on, "j.mercer"))


def order(engine, reference, on=DAY):
    with UnitOfWork(engine) as unit:
        raise_order(unit, reference, QUESTION, on, "a.okonkwo")


def pack(engine, opens=OPENS, closes=CLOSES, horizon=None):
    with UnitOfWork(engine) as unit:
        return build(unit, MSN, opens, closes, horizon)


def test_a_tail_with_nothing_owed_has_an_empty_pack(engine):
    built = pack(engine)
    assert built.is_empty
    assert str(built) == "MSN 5127: nothing for this check"


def test_a_check_that_closes_before_it_opens_is_refused(engine):
    with pytest.raises(BadReading, match="closes after it opens"):
        pack(engine, CLOSES, OPENS)


def test_the_pack_counts_its_own_days(engine):
    assert pack(engine).days == 8


def test_a_one_day_check_is_one_day(engine):
    assert pack(engine, OPENS, OPENS).days == 1


def test_a_deferral_falling_due_inside_the_check_is_on_the_pack(engine):
    defer(engine, assessed(engine), "1 FC")
    built = pack(engine)
    assert len(built.deferrals) == 1
    assert built.deferrals[0].what == "deferral"


def test_a_deferral_far_out_is_not_on_the_pack(engine):
    defer(engine, assessed(engine), "5000 FC")
    assert pack(engine).deferrals == ()


def test_a_horizon_can_widen_the_pack_past_the_check(engine):
    defer(engine, assessed(engine), "500 FC")
    assert pack(engine).deferrals == ()
    assert len(pack(engine, horizon=400).deferrals) == 1


def test_a_deferral_already_over_is_marked_over(engine):
    defer(engine, assessed(engine), "1 FC", at=(100, 200))
    built = pack(engine)
    assert built.deferrals[0].overrun
    assert len(built.overrun) == 1


def test_an_order_out_lands_under_waiting(engine):
    order(engine, assessed(engine))
    built = pack(engine)
    assert len(built.waiting) == 1
    assert built.waiting[0].what == "waiting"
    assert "no answer yet" in built.waiting[0].detail


def test_an_order_is_not_something_the_check_can_clear(engine):
    order(engine, assessed(engine))
    built = pack(engine)
    assert built.due_lines == ()
    assert built.total == 1


def test_another_tail_s_order_is_not_on_this_pack(engine):
    with UnitOfWork(engine) as unit:
        unit.aircraft.add(sample.aircraft("5240"))
        unit.aircraft.open_log(sample.flying_log("5240"))
        unit.findings.raise_finding(sample.card(2, serial="5240"))
        unit.dispositions.save(
            DispositionRecord.raised(FindingId(2025, 2)).moved_to(
                DispositionState.ASSESSED, DAY, "a.okonkwo"
            )
        )
    order(engine, FindingId(2025, 2))
    assert pack(engine).waiting == ()


def test_the_pack_reads_back_with_a_count_and_a_span(engine):
    defer(engine, assessed(engine), "1 FC")
    assert "1 items over 8 days" in str(pack(engine))


def test_a_pack_with_an_overrun_says_so(engine):
    defer(engine, assessed(engine), "1 FC", at=(100, 200))
    assert "already over" in str(pack(engine))


def test_every_reference_on_the_pack_can_be_listed(engine):
    defer(engine, assessed(engine, 1), "1 FC")
    order(engine, assessed(engine, 2))
    assert set(references_on(pack(engine))) == {"DR-2025-000001", "DR-2025-000002"}


def test_a_pack_with_no_inspections_touches_no_repairs(engine):
    defer(engine, assessed(engine), "1 FC")
    assert repairs_touched(pack(engine)) == ()


def test_a_pack_line_reads_back_as_one_line():
    line = PackLine("deferral", "DR-2025-000001", "detailed visual, 3 days on calendar")
    assert str(line) == "deferral DR-2025-000001: detailed visual, 3 days on calendar"


def test_an_overrun_line_is_marked_at_the_front():
    line = PackLine("deferral", "DR-2025-000001", "over", overrun=True)
    assert str(line).startswith("OVER ")


def test_a_repair_id_on_an_inspection_line_parses_back():
    assert RepairId.parse("RP-53-0001") == RepairId(53, 1)
