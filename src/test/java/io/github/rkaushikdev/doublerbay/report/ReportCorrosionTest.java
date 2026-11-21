package io.github.rkaushikdev.doublerbay.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.tolerance.CorrosionEntry;
import io.github.rkaushikdev.doublerbay.tolerance.CorrosionLevel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportCorrosionTest {

    private static final Serial MSN = new Serial("5127");
    private static final PanelId PART = new PanelId(53, 10, 143, "L");
    private static final PanelId ELSEWHERE = new PanelId(53, 10, 210, "R");
    private static final LocalDate SINCE = LocalDate.of(2020, 1, 1);
    private static final LocalDate UNTIL = LocalDate.of(2025, 12, 31);

    private static CorrosionEntry entry(PanelId part, CorrosionLevel level, LocalDate found) {
        return new CorrosionEntry(part, level, found);
    }

    private static CorrosionEntry entry(PanelId part) {
        return entry(part, CorrosionLevel.ONE, LocalDate.of(2023, 6, 1));
    }

    private static CorrosionEntry entry(CorrosionLevel level) {
        return entry(PART, level, LocalDate.of(2023, 6, 1));
    }

    private static CorrosionEntry entry(CorrosionLevel level, PanelId part) {
        return entry(part, level, LocalDate.of(2023, 6, 1));
    }

    private static CorrosionEntry entry(LocalDate found) {
        return entry(PART, CorrosionLevel.ONE, found);
    }

    private static CorrosionEntry entry(PanelId part, LocalDate found) {
        return entry(part, CorrosionLevel.ONE, found);
    }

    private static CorrosionEntry entry() {
        return entry(PART);
    }

    private static ProgrammeReport report(CorrosionEntry... entries) {
        return ProgrammeReport.of(MSN, List.of(entries), SINCE, UNTIL);
    }

    private static List<CorrosionEntry> fiveGradings() {
        return List.of(
                entry(LocalDate.of(2021, 1, 1)),
                entry(LocalDate.of(2022, 1, 1)),
                entry(LocalDate.of(2023, 1, 1)),
                entry(ELSEWHERE, LocalDate.of(2021, 1, 1)),
                entry(ELSEWHERE, LocalDate.of(2022, 1, 1)));
    }

    @Test
    void aTailWithNothingGradedIsCoping() {
        ProgrammeReport built = report();
        assertThat(built.total()).isZero();
        assertThat(built.isCoping()).isTrue();
        assertThat(built.escalationRate()).isEqualByComparingTo("0.000");
    }

    @Test
    void levelOnesAloneMeanTheProgrammeIsCoping() {
        ProgrammeReport built = report(entry(), entry(ELSEWHERE));
        assertThat(built.total()).isEqualTo(2);
        assertThat(built.isCoping()).isTrue();
    }

    @Test
    void oneLevelTwoMeansItIsNotCoping() {
        ProgrammeReport built = report(entry(), entry(CorrosionLevel.TWO, ELSEWHERE));
        assertThat(built.isCoping()).isFalse();
        assertThat(built.aboveLevelOne()).isEqualTo(1);
    }

    @Test
    void aLevelThreeCountsAsAboveLevelOne() {
        ProgrammeReport built = report(entry(CorrosionLevel.THREE));
        assertThat(built.aboveLevelOne()).isEqualTo(1);
    }

    @Test
    void theLevelsComeBackInOrder() {
        ProgrammeReport built = report(entry(CorrosionLevel.THREE), entry(CorrosionLevel.ONE, ELSEWHERE));
        assertThat(built.byLevel().stream().map(row -> row.what().value())).containsExactly(1, 3);
    }

    @Test
    void aLevelCanBeCountedOnItsOwn() {
        ProgrammeReport built = report(entry(), entry(ELSEWHERE));
        assertThat(built.countAt(CorrosionLevel.ONE)).isEqualTo(2);
        assertThat(built.countAt(CorrosionLevel.TWO)).isZero();
    }

    @Test
    void theEscalationRateIsTheShareAboveLevelOne() {
        ProgrammeReport built =
                report(entry(), entry(ELSEWHERE), entry(CorrosionLevel.TWO, new PanelId(53, 10, 300, "L")));
        assertThat(built.escalationRate()).isEqualByComparingTo("0.333");
    }

    @Test
    void aFleetWithEverythingEscalatedReadsAsOne() {
        assertThat(report(entry(CorrosionLevel.TWO)).escalationRate()).isEqualByComparingTo("1.000");
    }

    @Test
    void theDenominatorIsSaidOutLoud() {
        assertThat(report().denominator()).contains("not panels inspected");
    }

    @Test
    void aFindingBeforeTheWindowIsLeftOut() {
        ProgrammeReport built = report(entry(LocalDate.of(2019, 1, 1)), entry(ELSEWHERE));
        assertThat(built.total()).isEqualTo(1);
    }

    @Test
    void aFindingAfterTheWindowIsLeftOut() {
        ProgrammeReport built = report(entry(LocalDate.of(2026, 6, 1)), entry(ELSEWHERE));
        assertThat(built.total()).isEqualTo(1);
    }

    @Test
    void aFindingOnTheWindowEdgeIsInsideIt() {
        assertThat(report(entry(SINCE)).total()).isEqualTo(1);
        assertThat(report(entry(UNTIL)).total()).isEqualTo(1);
    }

    @Test
    void aWindowThatEndsBeforeItStartsIsRefused() {
        assertThatThrownBy(() -> ProgrammeReport.of(MSN, List.of(), UNTIL, SINCE))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("ends after it starts");
    }

    @Test
    void aPanelGradedOnceIsNotARepeat() {
        assertThat(ProgrammeReport.repeatsIn(List.of(entry()))).isEmpty();
    }

    @Test
    void aPanelGradedTwiceIsARepeat() {
        List<RepeatLocation> found =
                ProgrammeReport.repeatsIn(List.of(entry(LocalDate.of(2021, 1, 1)), entry(LocalDate.of(2023, 1, 1))));
        assertThat(found).hasSize(1);
        assertThat(found.get(0).times()).isEqualTo(2);
    }

    @Test
    void aRepeatCarriesTheFirstAndLatestGrading() {
        RepeatLocation found = ProgrammeReport.repeatsIn(
                        List.of(entry(LocalDate.of(2023, 1, 1)), entry(LocalDate.of(2021, 1, 1))))
                .get(0);
        assertThat(found.first()).isEqualTo(LocalDate.of(2021, 1, 1));
        assertThat(found.latest()).isEqualTo(LocalDate.of(2023, 1, 1));
    }

    @Test
    void twoRepeatsSortByHowOftenFirst() {
        assertThat(ProgrammeReport.repeatsIn(fiveGradings()).stream().map(RepeatLocation::part))
                .containsExactly(PART, ELSEWHERE);
    }

    @Test
    void aRepeatBelowTheThresholdIsNotPersistent() {
        RepeatLocation found = ProgrammeReport.repeatsIn(
                        List.of(entry(LocalDate.of(2021, 1, 1)), entry(LocalDate.of(2023, 1, 1))))
                .get(0);
        assertThat(found.isPersistent()).isFalse();
    }

    @Test
    void aRepeatAtTheThresholdIsPersistent() {
        List<CorrosionEntry> entries = new ArrayList<>();
        for (int step = 0; step < ProgrammeReport.REPEAT_THRESHOLD; step++) {
            entries.add(entry(LocalDate.of(2020 + step, 1, 1)));
        }
        assertThat(ProgrammeReport.repeatsIn(entries).get(0).isPersistent()).isTrue();
    }

    @Test
    void thePersistentOnesArePickedOutOfTheReport() {
        ProgrammeReport built = report(fiveGradings().toArray(CorrosionEntry[]::new));
        assertThat(built.repeats()).hasSize(2);
        assertThat(built.persistent().stream().map(RepeatLocation::part)).containsExactly(PART);
    }

    @Test
    void aRepeatReadsBackWithTheSpanInYears() {
        RepeatLocation found = new RepeatLocation(PART, 3, LocalDate.of(2021, 1, 1), LocalDate.of(2023, 1, 1));
        assertThat(found.yearsBetween()).isEqualByComparingTo("2.000");
        assertThat(found).hasToString("5310-143L graded 3 times over 2.000 years");
    }

    @Test
    void aRepeatInsideOneYearReadsAsPartOfAYear() {
        RepeatLocation found = new RepeatLocation(PART, 2, LocalDate.of(2023, 1, 1), LocalDate.of(2023, 7, 1));
        assertThat(found.yearsBetween()).isLessThan(BigDecimal.ONE);
    }

    @Test
    void theReportReadsBackAsOneLine() {
        assertThat(report(entry())).hasToString("MSN 5127: 1 graded, coping");
        assertThat(report(entry(CorrosionLevel.TWO)).toString()).contains("1 above level one");
    }
}
