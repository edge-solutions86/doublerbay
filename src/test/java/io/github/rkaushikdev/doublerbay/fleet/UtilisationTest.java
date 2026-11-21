package io.github.rkaushikdev.doublerbay.fleet;

import static io.github.rkaushikdev.doublerbay.fleet.UtilisationLog.usage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Cycles;
import io.github.rkaushikdev.doublerbay.measure.Hours;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class UtilisationTest {

    private static final Serial MSN = new Serial("5127");

    private record Reading(LocalDate day, int cycles, int hours) {}

    private static Reading reading(int year, int month, int day, int cycles, int hours) {
        return new Reading(LocalDate.of(year, month, day), cycles, hours);
    }

    private static UtilisationLog log() {
        return log(
                reading(2024, 1, 1, 12000, 21000),
                reading(2024, 7, 1, 12900, 22600),
                reading(2025, 1, 1, 13800, 24200));
    }

    private static UtilisationLog log(Reading... readings) {
        List<Snapshot> snapshots = new ArrayList<>();
        for (Reading one : readings) {
            snapshots.add(new Snapshot(one.day(), usage(one.cycles(), one.hours())));
        }
        return new UtilisationLog(MSN, snapshots);
    }

    @Test
    void aReadingReadsBackAsADateAndBothCounters() {
        assertThat(new Snapshot(LocalDate.of(2025, 1, 1), usage(13800, 24200)))
                .hasToString("2025-01-01 13800 FC / 24200.0 FH");
    }

    @Test
    void aLogReadsBackItsLatestReading() {
        assertThat(log()).hasToString("MSN 5127: 2025-01-01 13800 FC / 24200.0 FH");
        assertThat(log().latest().usage()).isEqualTo(usage(13800, 24200));
        assertThat(log().openedOn()).isEqualTo(LocalDate.of(2024, 1, 1));
    }

    @Test
    void aNewAirframeOpensAtNothing() {
        UtilisationLog fresh = UtilisationLog.opened(MSN, LocalDate.of(2025, 6, 1));
        assertThat(fresh.latest().usage()).isEqualTo(Usage.zero());
        assertThat(fresh.openedOn()).isEqualTo(LocalDate.of(2025, 6, 1));
    }

    @Test
    void anEmptyLogIsRefused() {
        assertThatThrownBy(() -> new UtilisationLog(MSN, List.of()))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("at least one reading");
    }

    @Test
    void readingsHaveToBePostedOldestFirst() {
        List<Snapshot> reversed = new ArrayList<>(log().snapshots());
        Collections.reverse(reversed);
        assertThatThrownBy(() -> new UtilisationLog(MSN, reversed))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("posted oldest first");
    }

    @Test
    void twoReadingsOnOneDayAreRefused() {
        assertThatThrownBy(() -> log(reading(2024, 1, 1, 12000, 21000), reading(2024, 1, 1, 12100, 21200)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("same day");
    }

    @Test
    void aCounterThatFallsIsRefused() {
        assertThatThrownBy(() -> log(reading(2024, 1, 1, 12000, 21000), reading(2024, 7, 1, 11900, 22600)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("cycles fell");
        assertThatThrownBy(() -> log(reading(2024, 1, 1, 12000, 21000), reading(2024, 7, 1, 12900, 20000)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("hours fell");
    }

    @Test
    void aReadingIsTakenAtTheLastPostingOnOrBeforeTheDay() {
        assertThat(log().at(LocalDate.of(2024, 7, 1))).isEqualTo(usage(12900, 22600));
        assertThat(log().at(LocalDate.of(2024, 8, 15))).isEqualTo(usage(12900, 22600));
        assertThat(log().at(LocalDate.of(2025, 6, 1))).isEqualTo(usage(13800, 24200));
    }

    @Test
    void aDayBeforeTheLogOpensHasNothingPosted() {
        assertThatThrownBy(() -> log().at(LocalDate.of(2023, 12, 31)))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("nothing posted on or before");
    }

    @Test
    void whatWasFlownAcrossAWindowIsTheDifferenceOfTwoPostings() {
        Usage flown = log().flownBetween(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1));
        assertThat(flown.cycles()).isEqualTo(new Cycles(1800));
        assertThat(flown.hours()).isEqualTo(Hours.ofHours(3200));
    }

    @Test
    void aWindowThatRunsBackwardsIsRefused() {
        assertThatThrownBy(() -> log().flownBetween(LocalDate.of(2025, 1, 1), LocalDate.of(2024, 1, 1)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is before");
    }

    @Test
    void aWindowInsideOnePostingFlewNothing() {
        assertThat(log().flownBetween(LocalDate.of(2024, 7, 2), LocalDate.of(2024, 8, 15)))
                .isEqualTo(Usage.zero());
    }

    @Test
    void postingAReadingLeavesTheOldLogAlone() {
        UtilisationLog before = log();
        UtilisationLog after = before.posted(new Snapshot(LocalDate.of(2025, 7, 1), usage(14700, 25800)));
        assertThat(after.latest().usage()).isEqualTo(usage(14700, 25800));
        assertThat(before.latest().usage()).isEqualTo(usage(13800, 24200));
    }

    @Test
    void postingAReadingThatFallsIsRefusedAtThePost() {
        assertThatThrownBy(() -> log().posted(new Snapshot(LocalDate.of(2025, 7, 1), usage(13000, 25800))))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("cycles fell");
    }

    @Test
    void theDailyRateComesOffTheRecentStretchOfTheLog() {
        DailyRate rate = log().dailyRate();
        assertThat(rate.cyclesADay()).isEqualByComparingTo("4.891");
        assertThat(rate.hoursADay()).isEqualByComparingTo("8.696");
    }

    @Test
    void aWindowWithNoReadingInsideItFallsBackToTheWholeLog() {
        assertThat(log().dailyRate(100).cyclesADay()).isEqualByComparingTo("4.918");
    }

    @Test
    void oneReadingIsNotARate() {
        UtilisationLog fresh = UtilisationLog.opened(MSN, LocalDate.of(2025, 6, 1));
        assertThatThrownBy(fresh::dailyRate)
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("one reading is not a rate");
    }

    @Test
    void aWindowThatCatchesOnlyTheLastReadingFallsBackToTheWholeLog() {
        assertThat(log().dailyRate(0).cyclesADay()).isEqualByComparingTo("4.918");
    }
}
