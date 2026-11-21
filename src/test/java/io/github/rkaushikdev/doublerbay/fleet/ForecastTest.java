package io.github.rkaushikdev.doublerbay.fleet;

import static io.github.rkaushikdev.doublerbay.fleet.UtilisationLog.usage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Cycles;
import io.github.rkaushikdev.doublerbay.measure.DueAt;
import io.github.rkaushikdev.doublerbay.measure.Hours;
import io.github.rkaushikdev.doublerbay.measure.Interval;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ForecastTest {

    private static final Serial MSN = new Serial("5127");
    private static final LocalDate AS_OF = LocalDate.of(2025, 1, 1);

    private static UtilisationLog log() {
        return new UtilisationLog(
                MSN,
                List.of(
                        new Snapshot(LocalDate.of(2024, 1, 1), usage(12000, 21000)),
                        new Snapshot(AS_OF, usage(13800, 24200))));
    }

    private static Forecast forecast() {
        return forecast("5", "9", 7300);
    }

    private static Forecast forecast(String cyclesADay, String hoursADay) {
        return forecast(cyclesADay, hoursADay, 7300);
    }

    private static Forecast forecast(int horizonDays) {
        return forecast("5", "9", horizonDays);
    }

    private static Forecast forecast(String cyclesADay, String hoursADay, int horizonDays) {
        return new Forecast(log(), new BigDecimal(cyclesADay), new BigDecimal(hoursADay), horizonDays);
    }

    private static DueAt cyclesDue(int count) {
        return DueAt.of(new Cycles(count), null, null);
    }

    @Test
    void aForecastReadsBackTheRateItIsRunningAt() {
        assertThat(forecast()).hasToString("MSN 5127: 5 FC and 9 FH a day");
        assertThat(forecast().asOf()).isEqualTo(AS_OF);
        assertThat(forecast().flown()).isEqualTo(usage(13800, 24200));
    }

    @Test
    void aRateTakenOffTheLogMatchesTheLog() {
        Forecast taken = Forecast.fromLog(log());
        assertThat(taken.cyclesADay()).isEqualByComparingTo("4.918");
        assertThat(taken.hoursADay()).isEqualByComparingTo("8.743");
    }

    @Test
    void aNegativeRateIsRefused() {
        assertThatThrownBy(() -> forecast("-1", "9"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("does not run backwards");
    }

    @Test
    void aHorizonOfNothingIsRefused() {
        assertThatThrownBy(() -> forecast(0)).isInstanceOf(BadReading.class).hasMessageContaining("at least a day");
    }

    @Test
    void theProjectionRunsTheCountersForwardAtTheRate() {
        Usage ahead = forecast().projected(LocalDate.of(2025, 1, 31));
        assertThat(ahead.cycles()).isEqualTo(new Cycles(13800 + 5 * 30));
        assertThat(ahead.hours()).isEqualTo(Hours.ofHours(24200 + 9 * 30));
    }

    @Test
    void aProjectionBackwardsReadsTheLogInsteadOfForecasting() {
        assertThat(forecast().projected(LocalDate.of(2024, 6, 1))).isEqualTo(usage(12000, 21000));
        assertThat(forecast().projected(AS_OF)).isEqualTo(usage(13800, 24200));
    }

    @Test
    void aCycleLimitLandsWhenTheCyclesGetThere() {
        assertThat(forecast().whenDue(cyclesDue(14100))).contains(LocalDate.of(2025, 3, 2));
    }

    @Test
    void anHourLimitLandsWhenTheHoursGetThere() {
        DueAt due = DueAt.of(null, Hours.ofHours(24470), null);
        assertThat(forecast().whenDue(due)).contains(LocalDate.of(2025, 1, 31));
    }

    @Test
    void aCalendarLimitLandsOnItsOwnDate() {
        DueAt due = DueAt.of(null, null, LocalDate.of(2025, 3, 1));
        assertThat(forecast().whenDue(due)).contains(LocalDate.of(2025, 3, 1));
    }

    @Test
    void theEarliestOfTheThreeClocksWins() {
        DueAt due = Interval.parse("3000 FC / 300 FH / 24 MO").dueFrom(usage(13800, 24200), AS_OF);
        assertThat(forecast().whenDue(due)).contains(LocalDate.of(2025, 2, 4));
    }

    @Test
    void aLimitAlreadyReachedLandsToday() {
        assertThat(forecast().whenDue(cyclesDue(13000))).contains(AS_OF);
    }

    @Test
    void aTailThatIsNotFlyingNeverReachesACycleLimit() {
        Forecast grounded = forecast("0", "0");
        assertThat(grounded.whenDue(cyclesDue(14100))).isEmpty();
    }

    @Test
    void aGroundedTailStillReachesACalendarLimit() {
        Forecast grounded = forecast("0", "0");
        assertThat(grounded.whenDue(DueAt.of(null, null, LocalDate.of(2026, 1, 1))))
                .contains(LocalDate.of(2026, 1, 1));
    }

    @Test
    void aLimitPastTheHorizonIsNotForecast() {
        Forecast nearSighted = forecast(30);
        assertThat(nearSighted.whenDue(cyclesDue(14100))).isEmpty();
        assertThat(nearSighted.whenDue(cyclesDue(13900))).contains(LocalDate.of(2025, 1, 21));
    }

    @Test
    void aDueWithNoClockOnItNeverLands() {
        assertThat(forecast().whenDue(DueAt.none())).isEmpty();
        assertThat(forecast().daysUntil(DueAt.none())).isEmpty();
    }

    @Test
    void theDaysLeftCountsFromTodayAndNotFromTheReading() {
        DueAt due = cyclesDue(14100);
        assertThat(forecast().daysUntil(due)).contains(60);
        assertThat(forecast().daysUntil(due, LocalDate.of(2025, 2, 1))).contains(29);
    }

    @Test
    void aLimitAlreadyPastHasNoDaysLeft() {
        assertThat(forecast().daysUntil(cyclesDue(13000), LocalDate.of(2025, 6, 1)))
                .contains(0);
    }

    @Test
    void forecastingFromBeforeTheLastReadingIsRefused() {
        assertThatThrownBy(() -> forecast().whenDue(cyclesDue(14100), LocalDate.of(2024, 6, 1)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("before the last reading");
    }
}
