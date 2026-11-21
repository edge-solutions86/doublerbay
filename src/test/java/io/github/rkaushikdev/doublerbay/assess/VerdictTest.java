package io.github.rkaushikdev.doublerbay.assess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Reference;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class VerdictTest {

    private static final Reference PAGE = Reference.parse("53-10-01");

    private static Reason reason(String rule, String detail, boolean passed) {
        return new Reason(rule, detail, passed);
    }

    private static Reason reason(String rule, boolean passed) {
        return reason(rule, "0.030 against a cap of 0.060", passed);
    }

    private static Reason reason(boolean passed) {
        return reason("depth", passed);
    }

    private static Reason reason(String rule) {
        return reason(rule, true);
    }

    private static Reason reason() {
        return reason(true);
    }

    private static Verdict verdict(Disposition disposition, List<Reason> reasons, Reference page, Integer revision) {
        return Verdict.of(
                disposition, reasons == null || reasons.isEmpty() ? List.of(reason()) : reasons, page, revision);
    }

    private static Verdict verdict(Disposition disposition, List<Reason> reasons) {
        return verdict(disposition, reasons, PAGE, 12);
    }

    private static Verdict verdict(List<Reason> reasons) {
        return verdict(Disposition.ALLOWABLE, reasons);
    }

    private static Verdict verdict(Disposition disposition) {
        return verdict(disposition, null);
    }

    private static Verdict verdict() {
        return verdict(Disposition.ALLOWABLE);
    }

    @Test
    void theDispositionsRunWorstLast() {
        assertThat(Disposition.OUT_OF_LIMITS.worseThan(Disposition.REPAIRABLE)).isTrue();
        assertThat(Disposition.REPAIRABLE.worseThan(Disposition.REWORKABLE)).isTrue();
        assertThat(Disposition.REWORKABLE.worseThan(Disposition.ALLOWABLE)).isTrue();
        assertThat(Disposition.ALLOWABLE.worseThan(Disposition.ALLOWABLE)).isFalse();
    }

    @Test
    void onlyTheAllowableAnswerLeavesTheAeroplaneAlone() {
        assertThat(Disposition.ALLOWABLE.needsWork()).isFalse();
        for (Disposition disposition :
                List.of(Disposition.REWORKABLE, Disposition.REPAIRABLE, Disposition.OUT_OF_LIMITS)) {
            assertThat(disposition.needsWork()).isTrue();
        }
    }

    @Test
    void onlyTheLastAnswerGoesToTheManufacturer() {
        assertThat(Disposition.OUT_OF_LIMITS.needsTheManufacturer()).isTrue();
        assertThat(Disposition.REPAIRABLE.needsTheManufacturer()).isFalse();
    }

    @Test
    void aReasonReadsBackWhetherItPassed() {
        assertThat(reason().toString()).startsWith("ok  depth:");
        assertThat(reason(false).toString()).startsWith("no  depth:");
    }

    @Test
    void aVerdictCarriesTheRulesItWalked() {
        assertThatThrownBy(() -> new Verdict(Disposition.ALLOWABLE, List.of()))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("carries the rules it walked");
    }

    @Test
    void aVerdictReadsBackItsPageAndWhy() {
        assertThat(verdict()).hasToString("allowable as it stands per 53-10-01 rev 12: every limit met");
    }

    @Test
    void aVerdictWithNoPageLeavesTheCitationOff() {
        assertThat(verdict(Disposition.ALLOWABLE, null, null, 12).toString()).startsWith("allowable as it stands:");
    }

    @Test
    void theFailuresAndThePassesComeApart() {
        Verdict walked = verdict(List.of(reason(true), reason("edge margin", false)));
        assertThat(walked.failures()).hasSize(1);
        assertThat(walked.passes()).hasSize(1);
        assertThat(walked.failures().get(0).rule()).isEqualTo("edge margin");
    }

    @Test
    void theReasonLineNamesWhatStoppedIt() {
        Verdict walked = verdict(Disposition.REPAIRABLE, List.of(reason("edge margin", "0.100 against 0.312", false)));
        assertThat(walked.why()).isEqualTo("0.100 against 0.312");
    }

    @Test
    void aVerdictThatPassedEverythingSaysSo() {
        assertThat(verdict().why()).isEqualTo("every limit met");
        assertThat(verdict().isAllowable()).isTrue();
        assertThat(verdict().needsWork()).isFalse();
    }

    @Test
    void aReasonCanBeAddedWithoutTouchingTheOriginal() {
        Verdict before = verdict();
        Verdict after = before.withReason(reason("sharpness"));
        assertThat(after.reasons()).hasSize(2);
        assertThat(before.reasons()).hasSize(1);
        assertThat(after.reference()).isEqualTo(before.reference());
    }

    @Test
    void aVerdictCanBeRaisedToAWorseAnswer() {
        Verdict raised = verdict().raisedTo(Disposition.REPAIRABLE);
        assertThat(raised.disposition()).isSameAs(Disposition.REPAIRABLE);
        assertThat(raised.reasons()).isEqualTo(verdict().reasons());
    }

    @Test
    void aVerdictIsNeverTalkedDown() {
        Verdict repairable = verdict(Disposition.REPAIRABLE);
        assertThatThrownBy(() -> repairable.raisedTo(Disposition.ALLOWABLE))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not talked down");
    }

    @Test
    void raisingToTheSameAnswerIsAllowed() {
        assertThat(verdict().raisedTo(Disposition.ALLOWABLE).disposition()).isSameAs(Disposition.ALLOWABLE);
    }

    @Test
    void theWorstOfSeveralIsTheOneThatStands() {
        Verdict worst = Verdict.worstOf(List.of(
                verdict(Disposition.ALLOWABLE), verdict(Disposition.REPAIRABLE), verdict(Disposition.REWORKABLE)));
        assertThat(worst.disposition()).isSameAs(Disposition.REPAIRABLE);
    }

    @Test
    void theWorstOfSeveralCarriesEveryTrail() {
        Verdict worst = Verdict.worstOf(List.of(
                verdict(List.of(reason("depth"))),
                verdict(Disposition.REPAIRABLE, List.of(reason("edge margin", false)))));
        Set<String> rules = worst.reasons().stream().map(Reason::rule).collect(Collectors.toSet());
        assertThat(rules).isEqualTo(Set.of("depth", "edge margin"));
    }

    @Test
    void theWorstOfNothingIsRefused() {
        assertThatThrownBy(() -> Verdict.worstOf(List.of()))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("no worst of nothing");
    }

    @Test
    void theWorstOfOneIsThatOne() {
        Verdict only = verdict(Disposition.REWORKABLE);
        assertThat(Verdict.worstOf(List.of(only)).disposition()).isSameAs(Disposition.REWORKABLE);
    }
}
