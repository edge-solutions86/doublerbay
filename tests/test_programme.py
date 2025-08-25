from datetime import date

import pytest

from doublerbay.errors import BadReading, NotOnFile
from doublerbay.fleet.utilisation import usage
from doublerbay.ids import PanelId, RepairId
from doublerbay.repair.scheme import RepairCategory
from doublerbay.structure.panel import PanelKind
from doublerbay.tolerance.method import InspectionMethod
from doublerbay.tolerance.programme import InspectionProgramme, InspectionTask
from doublerbay.tolerance.schedule import schedule_for

PART = PanelId(53, 10, 143, "L")
EMBODIED = date(2025, 4, 2)
AT_EMBODIMENT = usage(13800, 24200)


def repeating(method=InspectionMethod.DVI):
    return schedule_for(RepairCategory.B, PanelKind.PRESSURISED_SKIN, method)


def one_off():
    return schedule_for(RepairCategory.C, PanelKind.PRESSURISED_SKIN, InspectionMethod.GVI)


def task(sequence=1, schedule=None, last_done=None, at_last_done=None):
    return InspectionTask(
        against=RepairId(53, sequence),
        part=PART,
        schedule=schedule or repeating(),
        embodied=EMBODIED,
        at_embodiment=AT_EMBODIMENT,
        last_done=last_done,
        at_last_done=at_last_done,
    )


def test_a_task_reads_back_what_it_is_and_when_it_was_last_done():
    assert str(task()).endswith("last done never")
    done = task(last_done=date(2026, 1, 1), at_last_done=usage(15000, 26000))
    assert str(done).endswith("last done 2026-01-01")


def test_a_completed_inspection_needs_a_date_and_a_reading_together():
    with pytest.raises(BadReading, match="a date and a reading"):
        task(last_done=date(2026, 1, 1))
    with pytest.raises(BadReading, match="a date and a reading"):
        task(at_last_done=usage(15000, 26000))


def test_an_inspection_before_the_repair_went_on_is_refused():
    with pytest.raises(BadReading, match="inspected before the repair went on"):
        task(last_done=date(2024, 1, 1), at_last_done=usage(13000, 23000))


def test_the_first_look_counts_from_the_repair_going_on():
    due = task().due()
    assert due.cycles.count == 13800 + repeating().threshold.cycles.count


def test_the_first_look_is_further_out_than_the_ones_after_it():
    first = task().due()
    after = task(last_done=EMBODIED, at_last_done=AT_EMBODIMENT).due()
    assert first.cycles.count > after.cycles.count


def test_every_look_after_that_counts_from_the_last_one():
    done = task(last_done=date(2026, 1, 1), at_last_done=usage(15000, 26000))
    assert done.due().cycles.count == 15000 + repeating().repeat.cycles.count


def test_signing_a_task_off_resets_its_clock():
    before = task()
    after = before.signed_off(date(2026, 1, 1), usage(15000, 26000))
    assert after.has_been_done
    assert not before.has_been_done
    assert after.due().cycles.count == 15000 + repeating().repeat.cycles.count


def test_a_task_that_is_not_yet_at_its_threshold_is_not_due():
    assert not task().is_due(usage(14000, 24500), date(2026, 1, 1))


def test_a_task_past_its_threshold_is_due():
    beyond = 13800 + repeating().threshold.cycles.count
    assert task().is_due(usage(beyond, 90000), date(2026, 1, 1))


def test_a_replacement_limit_falls_due_on_the_calendar():
    limit = task(schedule=one_off())
    assert not limit.is_due(AT_EMBODIMENT, date(2026, 3, 1))
    assert limit.is_due(AT_EMBODIMENT, date(2026, 4, 3))


def test_a_replacement_limit_does_not_come_round_again():
    done = task(schedule=one_off()).signed_off(date(2026, 1, 1), usage(15000, 26000))
    assert not done.is_due(usage(99000, 99000), date(2099, 1, 1))
    with pytest.raises(BadReading, match="does not come round again"):
        done.due()


def test_a_replacement_limit_is_only_signed_off_once():
    done = task(schedule=one_off()).signed_off(date(2026, 1, 1), usage(15000, 26000))
    with pytest.raises(BadReading, match="only done once"):
        done.signed_off(date(2027, 1, 1), usage(16000, 27000))


def test_the_programme_counts_what_the_tail_is_carrying():
    held = InspectionProgramme()
    held.add(task(1))
    held.add(task(2))
    assert len(held) == 2
    assert str(held) == "2 repeat inspections"
    assert RepairId(53, 1) in held


def test_a_repair_only_gets_one_inspection_on_the_programme():
    held = InspectionProgramme()
    held.add(task(1))
    with pytest.raises(BadReading, match="already has an inspection"):
        held.add(task(1))


def test_a_repair_with_no_inspection_is_a_lookup_miss():
    held = InspectionProgramme()
    with pytest.raises(NotOnFile, match="has no inspection on the programme"):
        held.task_for(RepairId(53, 9))
    with pytest.raises(NotOnFile, match="has no inspection on the programme"):
        held.drop(RepairId(53, 9))


def test_signing_off_through_the_programme_keeps_the_new_state():
    held = InspectionProgramme()
    held.add(task(1))
    held.sign_off(RepairId(53, 1), date(2026, 1, 1), usage(15000, 26000))
    assert held.task_for(RepairId(53, 1)).has_been_done


def test_a_task_comes_off_when_its_repair_does():
    held = InspectionProgramme()
    held.add(task(1))
    held.drop(RepairId(53, 1))
    assert len(held) == 0


def test_the_due_list_is_the_tasks_that_have_landed():
    held = InspectionProgramme()
    held.add(task(1))
    held.add(task(2, schedule=one_off()))
    due = held.due_on(AT_EMBODIMENT, date(2026, 5, 1))
    assert [one.against.sequence for one in due] == [2]


def test_the_never_done_list_is_the_ones_nobody_has_looked_at():
    held = InspectionProgramme()
    held.add(task(1))
    held.add(task(2, last_done=date(2026, 1, 1), at_last_done=usage(15000, 26000)))
    assert [one.against.sequence for one in held.never_done()] == [1]


def test_the_programme_iterates_in_repair_number_order():
    held = InspectionProgramme()
    held.add(task(3))
    held.add(task(1))
    held.add(task(2))
    assert [one.against.sequence for one in held] == [1, 2, 3]
