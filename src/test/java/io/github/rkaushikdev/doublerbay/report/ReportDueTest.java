package io.github.rkaushikdev.doublerbay.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.disposition.Deferral;
import io.github.rkaushikdev.doublerbay.fleet.Forecast;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Cycles;
import io.github.rkaushikdev.doublerbay.measure.DueAt;
import io.github.rkaushikdev.doublerbay.measure.Hours;
import io.github.rkaushikdev.doublerbay.measure.Interval;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import io.github.rkaushikdev.doublerbay.support.Tails;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionMethod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportDueTest {

    private static final LocalDate DAY = LocalDate.of(2025, 3, 14);
    private static final Serial MSN = new Serial("5127");
    private static final Usage FLOWN = Tails.flown(13800, 24200);

    private static Forecast rate() {
        return rate("4", "7");
    }

    private static Forecast rate(String cycles) {
        return rate(cycles, "7");
    }

    private static Forecast rate(String cycles, String hours) {
        return new Forecast(Tails.flyingLog(), new BigDecimal(cycles), new BigDecimal(hours));
    }

    private static DueAt cyclesDue(int count) {
        return DueAt.of(new Cycles(count), null, null);
    }

    private static Deferral deferral() {
        return deferral(41, "500 FC", InspectionMethod.DVI);
    }

    private static Deferral deferral(int sequence, String limit) {
        return deferral(sequence, limit, InspectionMethod.DVI);
    }

    private static Deferral deferral(int sequence, String limit, InspectionMethod method) {
        return Deferral.grant(
                new FindingId(2025, sequence), DAY, FLOWN, "j.mercer", method, Interval.parse(limit), null, "");
    }

    private static Deferral over() {
        return Deferral.grant(
                new FindingId(2025, 44),
                LocalDate.of(2024, 1, 1),
                Tails.flown(100, 200),
                "j.mercer",
                InspectionMethod.DVI,
                Interval.parse("50 FC"),
                null,
                "");
    }

    private static List<Integer> sequences(List<DueItem> items) {
        return items.stream().map(one -> one.against().sequence()).toList();
    }

    @Test
    void aCyclesLimitBecomesDaysAtTheRate() {
        Margin margin = DueList.daysTo(cyclesDue(13840), FLOWN, rate("4"), DAY);
        assertThat(margin.clock()).isSameAs(Clock.CYCLES);
        assertThat(margin.days()).isEqualTo(10);
    }

    @Test
    void anHoursLimitBecomesDaysAtTheRate() {
        DueAt due = DueAt.of(null, Hours.ofHours("24270"), null);
        assertThat(DueList.daysTo(due, FLOWN, rate("4", "7"), DAY).days()).isEqualTo(10);
    }

    @Test
    void aCalendarLimitIsAlreadyInDays() {
        Margin margin = DueList.daysTo(DueAt.of(null, null, LocalDate.of(2025, 3, 24)), FLOWN, rate(), DAY);
        assertThat(margin).isEqualTo(new Margin(10, Clock.CALENDAR));
    }

    @Test
    void theTightestOfTheThreeIsTheOneReported() {
        DueAt due = DueAt.of(new Cycles(13840), Hours.ofHours("24500"), LocalDate.of(2026, 1, 1));
        assertThat(DueList.daysTo(due, FLOWN, rate(), DAY).clock()).isSameAs(Clock.CYCLES);
    }

    @Test
    void aLimitAlreadyPassedComesBackNegative() {
        Margin margin = DueList.daysTo(cyclesDue(13780), FLOWN, rate("4"), DAY);
        assertThat(margin.days()).isEqualTo(-5);
        assertThat(margin.isOverrun()).isTrue();
    }

    @Test
    void anOverrunRoundsAwayFromTheAeroplane() {
        // Ten cycles over at three a day is four days over, not three.
        Margin margin = DueList.daysTo(cyclesDue(13790), FLOWN, rate("3"), DAY);
        assertThat(margin.days()).isEqualTo(-4);
    }

    @Test
    void aMarginLeftRoundsTowardsTheAeroplane() {
        assertThat(DueList.daysTo(cyclesDue(13810), FLOWN, rate("3"), DAY).days())
                .isEqualTo(3);
    }

    @Test
    void aLimitLandingExactlyOnTodayIsDueNow() {
        Margin margin = DueList.daysTo(cyclesDue(13800), FLOWN, rate("4"), DAY);
        assertThat(margin.days()).isZero();
        assertThat(margin.isDueNow()).isTrue();
        assertThat(margin.isOverrun()).isFalse();
    }

    @Test
    void aTailThatIsNotFlyingNeverReachesACyclesLimit() {
        assertThat(DueList.daysTo(cyclesDue(14000), FLOWN, rate("0"), DAY).days())
                .isEqualTo(DueList.HORIZON_DAYS);
    }

    @Test
    void aTailThatIsNotFlyingIsStillOverALimitItPassed() {
        assertThat(DueList.daysTo(cyclesDue(13000), FLOWN, rate("0"), DAY).days())
                .isZero();
    }

    @Test
    void aMarginBeyondTheHorizonIsCutAtTheHorizon() {
        assertThat(DueList.daysTo(cyclesDue(999999), FLOWN, rate("1"), DAY).days())
                .isEqualTo(DueList.HORIZON_DAYS);
    }

    @Test
    void aDuePointWithNoClocksCannotBeCountedDown() {
        assertThatThrownBy(() -> DueList.daysTo(DueAt.none(), FLOWN, rate(), DAY))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("no clocks on it");
    }

    @Test
    void aMarginReadsBackAsDaysAndAClock() {
        assertThat(new Margin(10, Clock.CYCLES)).hasToString("10 days on flight cycles");
        assertThat(new Margin(-3, Clock.CALENDAR)).hasToString("3 days over on calendar");
    }

    @Test
    void aDeferralBecomesALineNamingTheMethod() {
        DueItem item = DueList.deferralDue(deferral(41, "500 FC", InspectionMethod.HFEC), MSN, FLOWN, rate(), DAY);
        assertThat(item.what()).contains("high frequency eddy current");
        assertThat(item.against()).isEqualTo(new FindingId(2025, 41));
    }

    @Test
    void aDueLineReadsBackWithTheTailAndTheMargin() {
        DueItem item = DueList.deferralDue(deferral(41, "500 FC"), MSN, FLOWN, rate("4"), DAY);
        assertThat(item.toString()).startsWith("MSN 5127 DR-2025-000041 deferral");
        assertThat(item.toString()).contains("125 days");
    }

    @Test
    void theListPutsTheWorstAtTheTop() {
        List<DueItem> items = DueList.of(
                List.of(deferral(41, "500 FC"), deferral(42, "50 FC"), deferral(43, "200 FC")),
                MSN,
                FLOWN,
                rate("4"),
                DAY);
        assertThat(sequences(items)).containsExactly(42, 43, 41);
    }

    @Test
    void twoLinesWithTheSameMarginSortByReference() {
        List<DueItem> items =
                DueList.of(List.of(deferral(43, "50 FC"), deferral(42, "50 FC")), MSN, FLOWN, rate("4"), DAY);
        assertThat(sequences(items)).containsExactly(42, 43);
    }

    @Test
    void aHorizonCutsTheListOff() {
        List<DueItem> items =
                DueList.of(List.of(deferral(41, "500 FC"), deferral(42, "50 FC")), MSN, FLOWN, rate("4"), DAY, 30);
        assertThat(sequences(items)).containsExactly(42);
    }

    @Test
    void aHorizonStillCarriesTheOnesAlreadyOver() {
        List<DueItem> items = DueList.of(List.of(over()), MSN, FLOWN, rate("4"), DAY, 1);
        assertThat(items).hasSize(1);
    }

    @Test
    void aHorizonThatRunsBackwardsIsRefused() {
        assertThatThrownBy(() -> DueList.of(List.of(deferral()), MSN, FLOWN, rate(), DAY, -1))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("count of days ahead");
    }

    @Test
    void anEmptyListIsAnEmptyList() {
        assertThat(DueList.of(List.of(), MSN, FLOWN, rate(), DAY)).isEmpty();
    }

    @Test
    void theGroundingListIsOnlyWhatIsAlreadyOver() {
        List<DueItem> items = DueList.of(List.of(deferral(41, "500 FC"), over()), MSN, FLOWN, rate("4"), DAY);
        assertThat(sequences(DueList.overrun(items))).containsExactly(44);
    }

    @Test
    void nothingOverIsAnEmptyGroundingList() {
        List<DueItem> items = DueList.of(List.of(deferral(41, "500 FC")), MSN, FLOWN, rate("4"), DAY);
        assertThat(DueList.overrun(items)).isEmpty();
    }
}
