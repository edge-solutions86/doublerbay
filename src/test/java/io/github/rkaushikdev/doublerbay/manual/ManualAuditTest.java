package io.github.rkaushikdev.doublerbay.manual;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.doublerbay.complaints.Complaint;
import io.github.rkaushikdev.doublerbay.complaints.Severity;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.grid.Reference;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.measure.Ratio;
import io.github.rkaushikdev.doublerbay.v320.Manual53;
import io.github.rkaushikdev.doublerbay.v320.Manual57;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ManualAuditTest {

    private static final Reference PAGE = Reference.parse("53-10");
    private static final Reference SUBJECT = Reference.parse("53-10-01");

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    private static LimitRow row(DamageKind kind, LimitSet limits) {
        return new LimitRow(new LimitKey(kind), limits, SUBJECT);
    }

    private static List<LimitRow> everyKind(LimitSet limits) {
        return Arrays.stream(DamageKind.values()).map(kind -> row(kind, limits)).toList();
    }

    private static LimitTable table(List<LimitRow> rows) {
        return new LimitTable(PAGE, 12, rows);
    }

    private static List<Complaint> saying(List<Complaint> found, String words) {
        return found.stream().filter(one -> one.says().contains(words)).toList();
    }

    @Test
    void aComplaintReadsBackTheSameWayWhereverItCameFrom() {
        Complaint complaint = new Complaint(Severity.BLOCKING, "53-10", "no row anywhere for a dent");
        assertThat(complaint).hasToString("[blocking] 53-10: no row anywhere for a dent");
    }

    @Test
    void theHelpersSplitBlockingFromAdvisory() {
        List<Complaint> complaints =
                List.of(new Complaint(Severity.ADVISORY, "a", "one"), new Complaint(Severity.BLOCKING, "b", "two"));
        assertThat(Complaint.blocking(complaints)).hasSize(1);
        assertThat(Complaint.isClear(complaints)).isFalse();
        assertThat(Complaint.isClear(complaints.subList(0, 1))).isTrue();
    }

    @Test
    void complaintsSortBlockingFirstAndThenBySubject() {
        List<Complaint> ordered = Complaint.sorted(List.of(
                new Complaint(Severity.ADVISORY, "z", "one"),
                new Complaint(Severity.BLOCKING, "b", "two"),
                new Complaint(Severity.ADVISORY, "a", "three")));
        assertThat(ordered.stream().map(Complaint::about).toList()).containsExactly("b", "a", "z");
    }

    @Test
    void aKindWithNoRowAnywhereIsBlocking() {
        List<LimitRow> rows = new ArrayList<>();
        for (DamageKind kind : Arrays.copyOf(DamageKind.values(), 3)) {
            rows.add(row(kind, LimitSet.allowable().withMaxDepth(inch("0.010"))));
        }
        List<Complaint> found = ManualAudits.auditTable(table(rows));
        List<Complaint> missing = saying(found, "no row anywhere");
        assertThat(missing).hasSize(DamageKind.values().length - 3);
        assertThat(missing).allMatch(one -> one.severity() == Severity.BLOCKING);
    }

    @Test
    void aRowThatAllowsDamageAndCapsNothingIsBlocking() {
        List<Complaint> found = ManualAudits.auditTable(table(everyKind(LimitSet.allowable())));
        List<Complaint> openRows = saying(found, "caps nothing");
        assertThat(openRows).isNotEmpty();
        assertThat(openRows).allMatch(one -> one.severity() == Severity.BLOCKING);
    }

    @Test
    void aDepthCapThatLeavesLessThanTheResidualFloorIsBlocking() {
        LimitTable crossed = table(everyKind(LimitSet.allowable()
                .withMaxDepthFraction(new Ratio("0.20"))
                .withMinResidualFraction(new Ratio("0.90"))));
        List<Complaint> found = ManualAudits.auditTable(crossed);
        List<Complaint> contradictions = saying(found, "and asks for");
        assertThat(contradictions).isNotEmpty();
        assertThat(contradictions).allMatch(one -> one.severity() == Severity.BLOCKING);
    }

    @Test
    void aCapAndAFloorThatAgreeRaiseNothing() {
        LimitTable agreeing = table(everyKind(LimitSet.allowable()
                .withMaxDepthFraction(new Ratio("0.05"))
                .withMinResidualFraction(new Ratio("0.90"))));
        List<Complaint> found = ManualAudits.auditTable(agreeing);
        assertThat(saying(found, "and asks for")).isEmpty();
    }

    @Test
    void twoRowsThatAnswerEquallyShowUpInTheSweep() {
        List<LimitRow> rows = new ArrayList<>(everyKind(LimitSet.allowable().withMaxDepth(inch("0.010"))));
        rows.add(row(DamageKind.DENT, LimitSet.allowable().withMaxDepth(inch("0.005"))));
        List<Complaint> found = ManualAudits.auditTable(table(rows));
        List<Complaint> clashes = saying(found, "rows that answer");
        assertThat(clashes).isNotEmpty();
        assertThat(clashes).allMatch(one -> one.severity() == Severity.BLOCKING);
    }

    @Test
    void aRowCitingAPageThatIsNotInTheBookIsAdvisory() {
        List<Complaint> found =
                ManualAudits.auditTable(table(everyKind(LimitSet.allowable().withMaxDepth(inch("0.010")))), List.of());
        List<Complaint> orphans = saying(found, "not in the book");
        assertThat(orphans).hasSize(1);
        assertThat(orphans.get(0).severity()).isSameAs(Severity.ADVISORY);
    }

    @Test
    void aRowCitingALivePageRaisesNothing() {
        List<Complaint> found =
                ManualAudits.auditTable(table(everyKind(LimitSet.allowable().withMaxDepth(inch("0.010")))), Manual53.PAGES);
        assertThat(saying(found, "not in the book")).isEmpty();
    }

    @Test
    void theTranscribedFuselageChapterHasNothingBlockingInIt() {
        List<Complaint> found = ManualAudits.auditTable(Manual53.allowableDamage(12), Manual53.PAGES);
        assertThat(Complaint.isClear(found)).isTrue();
    }

    @Test
    void theTranscribedWingChapterHasNothingBlockingInIt() {
        List<Complaint> found = ManualAudits.auditTable(Manual57.allowableDamage(10), Manual57.PAGES);
        assertThat(Complaint.isClear(found)).isTrue();
    }

    @Test
    void theWholeBookWalksClean() {
        assertThat(Complaint.isClear(ManualAudits.auditBook(Book.v320()))).isTrue();
    }

    @Test
    void theBookCanBeWalkedAtAnEarlierRevision() {
        List<Complaint> found = ManualAudits.auditBook(Book.v320(), Map.of("53", 11, "57", 9));
        assertThat(Complaint.isClear(found)).isTrue();
    }

    @Test
    void theSweepReportsTheCombinationsNoRowAnswersFor() {
        LimitTable thin = table(everyKind(LimitSet.allowable().withMaxDepth(inch("0.010"))));
        List<Complaint> found = ManualAudits.auditTable(thin);
        assertThat(saying(found, "no row answers for")).isEmpty();
    }
}
