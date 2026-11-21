package io.github.rkaushikdev.doublerbay.fleet;

import static io.github.rkaushikdev.doublerbay.fleet.UtilisationLog.usage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.DueAt;
import io.github.rkaushikdev.doublerbay.measure.Interval;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CheckTest {

    private static final Serial MSN = new Serial("5127");

    private static CheckProgramme programme() {
        return programme(Map.of());
    }

    private static CheckProgramme programme(Map<CheckKind, Interval> extra) {
        Map<CheckKind, Interval> intervals = new LinkedHashMap<>();
        intervals.put(CheckKind.LINE, Interval.parse("750 FH / 60 DY"));
        intervals.put(CheckKind.HANGAR, Interval.parse("6000 FH / 24 MO"));
        intervals.put(CheckKind.HEAVY, Interval.parse("25000 FH / 72 MO"));
        intervals.putAll(extra);
        return new CheckProgramme(intervals);
    }

    private static CheckEvent event() {
        return event(CheckKind.HANGAR);
    }

    private static CheckEvent event(CheckKind kind) {
        return event(kind, LocalDate.of(2025, 1, 6), LocalDate.of(2025, 1, 20));
    }

    private static CheckEvent event(CheckKind kind, LocalDate started, LocalDate finished) {
        return new CheckEvent(MSN, kind, started, finished, usage(13800, 24200));
    }

    @Test
    void theChecksOpenTheAeroplaneUpByDegrees() {
        assertThat(CheckKind.HEAVY.opensMoreThan(CheckKind.HANGAR)).isTrue();
        assertThat(CheckKind.HANGAR.opensMoreThan(CheckKind.LINE)).isTrue();
        assertThat(CheckKind.LINE.opensMoreThan(CheckKind.WALKAROUND)).isTrue();
        assertThat(CheckKind.WALKAROUND.opensMoreThan(CheckKind.TRANSIT)).isFalse();
    }

    @Test
    void onlyTheOpenedUpChecksWouldFindFineDamage() {
        assertThat(CheckKind.HANGAR.canFindFineDamage()).isTrue();
        assertThat(CheckKind.NDT_SWEEP.canFindFineDamage()).isTrue();
        assertThat(CheckKind.LINE.canFindFineDamage()).isFalse();
        assertThat(CheckKind.WALKAROUND.canFindFineDamage()).isFalse();
    }

    @Test
    void everythingButTheWalkaroundIsScheduled() {
        assertThat(CheckKind.LINE.isScheduled()).isTrue();
        assertThat(CheckKind.WALKAROUND.isScheduled()).isFalse();
        assertThat(CheckKind.HANGAR).hasToString("hangar check");
        assertThat(CheckKind.fromValue("hangar check")).isSameAs(CheckKind.HANGAR);
    }

    @Test
    void theSourcesSplitIntoPeopleAndInstruments() {
        assertThat(DiscoverySource.CREW_REPORT.isReportedByAPerson()).isTrue();
        assertThat(DiscoverySource.WALKAROUND.isReportedByAPerson()).isTrue();
        assertThat(DiscoverySource.NDT.isReportedByAPerson()).isFalse();
        assertThat(DiscoverySource.SCHEDULED_CHECK.isReportedByAPerson()).isFalse();
    }

    @Test
    void someSourcesComeWithSomethingThatHappened() {
        assertThat(DiscoverySource.LIGHTNING_REPORT.carriesAnEvent()).isTrue();
        assertThat(DiscoverySource.GROUND_HANDLING.carriesAnEvent()).isTrue();
        assertThat(DiscoverySource.SCHEDULED_CHECK.carriesAnEvent()).isFalse();
        assertThat(DiscoverySource.NDT).hasToString("non destructive test");
        assertThat(DiscoverySource.fromValue("non destructive test")).isSameAs(DiscoverySource.NDT);
    }

    @Test
    void aCheckReadsBackTheTailKindAndDay() {
        assertThat(event()).hasToString("MSN 5127 hangar check 2025-01-06");
    }

    @Test
    void aCheckCountsItsOwnDaysInclusively() {
        assertThat(event().days()).isEqualTo(15);
        assertThat(event(CheckKind.HANGAR, LocalDate.of(2025, 1, 6), LocalDate.of(2025, 1, 6))
                        .days())
                .isEqualTo(1);
    }

    @Test
    void aCheckThatFinishesBeforeItStartsIsRefused() {
        assertThatThrownBy(() -> event(CheckKind.HANGAR, LocalDate.of(2025, 1, 20), LocalDate.of(2025, 1, 6)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("does not finish before it starts");
    }

    @Test
    void aDayInsideTheCheckIsCoveredByIt() {
        assertThat(event().covers(LocalDate.of(2025, 1, 6))).isTrue();
        assertThat(event().covers(LocalDate.of(2025, 1, 14))).isTrue();
        assertThat(event().covers(LocalDate.of(2025, 1, 20))).isTrue();
        assertThat(event().covers(LocalDate.of(2025, 1, 21))).isFalse();
    }

    @Test
    void aProgrammeHasToSetTheThreeScheduledChecks() {
        Map<CheckKind, Interval> two = new LinkedHashMap<>();
        two.put(CheckKind.LINE, Interval.parse("750 FH"));
        two.put(CheckKind.HANGAR, Interval.parse("6000 FH"));
        assertThatThrownBy(() -> new CheckProgramme(two))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("sets an interval for the heavy check");
    }

    @Test
    void anIntervalComesBackForAKindThatIsOnTheProgramme() {
        assertThat(programme().intervalFor(CheckKind.HANGAR).months()).contains(24);
        assertThatThrownBy(() -> programme().intervalFor(CheckKind.WALKAROUND))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("has no walkaround on it");
    }

    @Test
    void theNextCheckOfAKindRunsFromTheEndOfTheLastOne() {
        DueAt due = programme().nextAfter(event());
        assertThat(due.hours().orElseThrow().hours()).isEqualByComparingTo(BigDecimal.valueOf(24200 + 6000));
        assertThat(due.on()).contains(LocalDate.of(2027, 1, 20));
    }

    @Test
    void aLineCheckComesRoundFarSoonerThanAHangarCheck() {
        DueAt line = programme().nextAfter(event(CheckKind.LINE));
        DueAt hangar = programme().nextAfter(event(CheckKind.HANGAR));
        assertThat(line.on().orElseThrow()).isBefore(hangar.on().orElseThrow());
    }

    @Test
    void theDeepestCheckAlreadyDueIsTheOneAFindingRidesOn() {
        DueAt due = programme().nextAfter(event());
        assertThat(programme().deepestBy(due, usage(13800, 24200), LocalDate.of(2025, 1, 20)))
                .isEmpty();
        assertThat(programme().deepestBy(due, usage(13800, 31000), LocalDate.of(2025, 1, 20)))
                .containsSame(CheckKind.HEAVY);
    }

    @Test
    void aProgrammeReadsBackShallowestFirst() {
        String printed = programme().toString();
        assertThat(printed.indexOf("line check")).isLessThan(printed.indexOf("hangar check"));
        assertThat(printed.indexOf("hangar check")).isLessThan(printed.indexOf("heavy check"));
    }

    @Test
    void anExtraKindOnTheProgrammeIsCarried() {
        CheckProgramme withSweep = programme(Map.of(CheckKind.NDT_SWEEP, Interval.parse("48 MO")));
        assertThat(withSweep.intervalFor(CheckKind.NDT_SWEEP).months()).contains(48);
    }
}
