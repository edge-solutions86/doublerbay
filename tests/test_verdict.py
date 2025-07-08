import pytest

from doublerbay.assess.verdict import Disposition, Reason, Verdict, worst_of
from doublerbay.errors import BadReading
from doublerbay.grid.ata import Reference

PAGE = Reference.parse("53-10-01")


def reason(rule="depth", detail="0.030 against a cap of 0.060", passed=True):
    return Reason(rule, detail, passed)


def verdict(disposition=Disposition.ALLOWABLE, reasons=None, page=PAGE, revision=12):
    return Verdict(disposition, reasons or (reason(),), page, revision)


def test_the_dispositions_run_worst_last():
    assert Disposition.OUT_OF_LIMITS.worse_than(Disposition.REPAIRABLE)
    assert Disposition.REPAIRABLE.worse_than(Disposition.REWORKABLE)
    assert Disposition.REWORKABLE.worse_than(Disposition.ALLOWABLE)
    assert not Disposition.ALLOWABLE.worse_than(Disposition.ALLOWABLE)


def test_only_the_allowable_answer_leaves_the_aeroplane_alone():
    assert not Disposition.ALLOWABLE.needs_work
    for disposition in (
        Disposition.REWORKABLE,
        Disposition.REPAIRABLE,
        Disposition.OUT_OF_LIMITS,
    ):
        assert disposition.needs_work


def test_only_the_last_answer_goes_to_the_manufacturer():
    assert Disposition.OUT_OF_LIMITS.needs_the_manufacturer
    assert not Disposition.REPAIRABLE.needs_the_manufacturer


def test_a_reason_reads_back_whether_it_passed():
    assert str(reason()).startswith("ok  depth:")
    assert str(reason(passed=False)).startswith("no  depth:")


def test_a_verdict_carries_the_rules_it_walked():
    with pytest.raises(BadReading, match="carries the rules it walked"):
        Verdict(Disposition.ALLOWABLE, ())


def test_a_verdict_reads_back_its_page_and_why():
    assert str(verdict()) == "allowable as it stands per 53-10-01 rev 12: every limit met"


def test_a_verdict_with_no_page_leaves_the_citation_off():
    assert str(verdict(page=None)).startswith("allowable as it stands:")


def test_the_failures_and_the_passes_come_apart():
    walked = verdict(
        reasons=(reason(passed=True), reason(rule="edge margin", passed=False)),
    )
    assert len(walked.failures()) == 1
    assert len(walked.passes()) == 1
    assert walked.failures()[0].rule == "edge margin"


def test_the_reason_line_names_what_stopped_it():
    walked = verdict(
        disposition=Disposition.REPAIRABLE,
        reasons=(reason(rule="edge margin", detail="0.100 against 0.312", passed=False),),
    )
    assert walked.why() == "0.100 against 0.312"


def test_a_verdict_that_passed_everything_says_so():
    assert verdict().why() == "every limit met"
    assert verdict().is_allowable
    assert not verdict().needs_work


def test_a_reason_can_be_added_without_touching_the_original():
    before = verdict()
    after = before.with_reason(reason(rule="sharpness"))
    assert len(after.reasons) == 2
    assert len(before.reasons) == 1
    assert after.reference == before.reference


def test_a_verdict_can_be_raised_to_a_worse_answer():
    raised = verdict().raised_to(Disposition.REPAIRABLE)
    assert raised.disposition is Disposition.REPAIRABLE
    assert raised.reasons == verdict().reasons


def test_a_verdict_is_never_talked_down():
    repairable = verdict(disposition=Disposition.REPAIRABLE)
    with pytest.raises(BadReading, match="not talked down"):
        repairable.raised_to(Disposition.ALLOWABLE)


def test_raising_to_the_same_answer_is_allowed():
    assert verdict().raised_to(Disposition.ALLOWABLE).disposition is Disposition.ALLOWABLE


def test_the_worst_of_several_is_the_one_that_stands():
    worst = worst_of(
        (
            verdict(disposition=Disposition.ALLOWABLE),
            verdict(disposition=Disposition.REPAIRABLE),
            verdict(disposition=Disposition.REWORKABLE),
        )
    )
    assert worst.disposition is Disposition.REPAIRABLE


def test_the_worst_of_several_carries_every_trail():
    worst = worst_of(
        (
            verdict(reasons=(reason(rule="depth"),)),
            verdict(
                disposition=Disposition.REPAIRABLE,
                reasons=(reason(rule="edge margin", passed=False),),
            ),
        )
    )
    assert {one.rule for one in worst.reasons} == {"depth", "edge margin"}


def test_the_worst_of_nothing_is_refused():
    with pytest.raises(BadReading, match="no worst of nothing"):
        worst_of(())


def test_the_worst_of_one_is_that_one():
    only = verdict(disposition=Disposition.REWORKABLE)
    assert worst_of((only,)).disposition is Disposition.REWORKABLE
