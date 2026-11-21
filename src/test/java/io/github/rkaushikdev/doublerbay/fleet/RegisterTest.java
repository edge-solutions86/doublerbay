package io.github.rkaushikdev.doublerbay.fleet;

import static io.github.rkaushikdev.doublerbay.fleet.UtilisationLog.usage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.ids.Registration;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Cycles;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RegisterTest {

    private static final Bulletin SB_53_018 = new Bulletin(53, 18);

    private static Aircraft tail() {
        return tail("5127", "N412VD", LocalDate.of(2016, 4, 12), Series.STANDARD);
    }

    private static Aircraft tail(String serial, String marks, LocalDate delivered, Series series) {
        return new Aircraft(new Serial(serial), new Registration(marks), series, 1140, delivered);
    }

    private static UtilisationLog log() {
        return log("5127");
    }

    private static UtilisationLog log(String serial) {
        return new UtilisationLog(
                new Serial(serial),
                List.of(
                        new Snapshot(LocalDate.of(2024, 1, 1), usage(12000, 21000)),
                        new Snapshot(LocalDate.of(2025, 1, 1), usage(13800, 24200))));
    }

    private static Fleet fleet() {
        Fleet register = new Fleet();
        register.enrol(tail(), log());
        register.enrol(tail("5240", "N908QA", LocalDate.of(2018, 9, 3), Series.STANDARD), log("5240"));
        register.enrol(tail("5301", "N155VD", LocalDate.of(2021, 2, 20), Series.LONG_RANGE));
        return register;
    }

    private static List<Integer> serials(Iterable<Aircraft> tails) {
        List<Integer> numbers = new ArrayList<>();
        tails.forEach(aircraft -> numbers.add(aircraft.serial().asInt()));
        return numbers;
    }

    @Test
    void theRegisterCountsWhatIsOnIt() {
        assertThat(fleet().size()).isEqualTo(3);
        assertThat(fleet()).hasToString("3 tails on the register");
        assertThat(fleet().contains(new Serial("5127"))).isTrue();
        assertThat(fleet().contains(new Serial("9999"))).isFalse();
    }

    @Test
    void theRegisterHandsBackTailsInSerialOrder() {
        assertThat(serials(fleet())).containsExactly(5127, 5240, 5301);
    }

    @Test
    void aTailEnrolledTwiceIsRefused() {
        Fleet register = fleet();
        assertThatThrownBy(() -> register.enrol(tail()))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("already on the register");
    }

    @Test
    void aLogForAnotherTailIsRefused() {
        Fleet register = new Fleet();
        assertThatThrownBy(() -> register.enrol(tail(), log("5240")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is not the log for");
    }

    @Test
    void aTailEnrolledWithoutALogOpensOneAtDelivery() {
        UtilisationLog opened = fleet().logFor(new Serial("5301"));
        assertThat(opened.openedOn()).isEqualTo(LocalDate.of(2021, 2, 20));
        assertThat(opened.latest().usage().cycles()).isEqualTo(new Cycles(0));
    }

    @Test
    void aTailOffTheRegisterIsALookupMiss() {
        assertThatThrownBy(() -> fleet().tail(new Serial("9999")))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("is not on the register");
        assertThatThrownBy(() -> fleet().logFor(new Serial("9999")))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("has no log on the register");
    }

    @Test
    void aRegistrationFindsWhoeverIsWearingIt() {
        assertThat(fleet().byRegistration(new Registration("N412VD")).serial()).isEqualTo(new Serial("5127"));
        assertThatThrownBy(() -> fleet().byRegistration(new Registration("N1")))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("is not on the register");
    }

    @Test
    void aRegistrationThatHasMovedAnswersForItsNewAeroplane() {
        Fleet register = fleet();
        register.amend(register.tail(new Serial("5127")).reRegistered(new Registration("N771VD")));
        register.amend(register.tail(new Serial("5240")).reRegistered(new Registration("N412VD")));
        assertThat(register.byRegistration(new Registration("N412VD")).serial()).isEqualTo(new Serial("5240"));
    }

    @Test
    void amendingATailThatIsNotOnTheRegisterIsRefused() {
        assertThatThrownBy(() -> new Fleet().amend(tail()))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("is not on the register");
    }

    @Test
    void aBulletinGoingOnIsAnAmendment() {
        Fleet register = fleet();
        register.amend(register.tail(new Serial("5127")).withEmbodied(SB_53_018));
        assertThat(register.tail(new Serial("5127")).hasEmbodied(SB_53_018)).isTrue();
    }

    @Test
    void onlyTheTailsDeliveredByADayAreInService() {
        assertThat(serials(fleet().inServiceOn(LocalDate.of(2017, 1, 1)))).containsExactly(5127);
    }

    @Test
    void aWithdrawnTailDropsOffTheInServiceList() {
        Fleet register = fleet();
        register.amend(register.tail(new Serial("5127")).withdrawnOn(LocalDate.of(2026, 1, 1)));
        List<Aircraft> stillFlying = register.inServiceOn(LocalDate.of(2026, 6, 1));
        assertThat(stillFlying.stream().map(Aircraft::serial).toList()).doesNotContain(new Serial("5127"));
    }

    @Test
    void effectivityPicksOutTheTailsAPageAppliesTo() {
        Effectivity block = Effectivity.allAircraft().withSerials(5200, 5400);
        assertThat(serials(fleet().coveredBy(block))).containsExactly(5240, 5301);
    }

    @Test
    void effectivityOnASeriesPicksOutThatSeries() {
        Effectivity longRange = Effectivity.allAircraft().withSeries(Set.of(Series.LONG_RANGE));
        assertThat(serials(fleet().coveredBy(longRange))).containsExactly(5301);
    }

    @Test
    void aPostedReadingLandsOnThatTailAndNoOther() {
        Fleet register = fleet();
        register.post(new Serial("5127"), new Snapshot(LocalDate.of(2025, 7, 1), usage(14700, 25800)));
        assertThat(register.logFor(new Serial("5127")).latest().usage()).isEqualTo(usage(14700, 25800));
        assertThat(register.logFor(new Serial("5240")).latest().usage()).isEqualTo(usage(13800, 24200));
    }

    @Test
    void aReadingAgainstATailThatIsNotThereIsRefused() {
        assertThatThrownBy(() -> fleet().post(new Serial("9999"), new Snapshot(LocalDate.of(2025, 7, 1), usage(1, 1))))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("has no log");
    }

    @Test
    void theForecastComesOffThatTailsOwnLog() {
        Forecast forecast = fleet().forecastFor(new Serial("5127"));
        assertThat(forecast.asOf()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(forecast.cyclesADay()).isPositive();
    }

    @Test
    void aTailThatHasNotFlownHasNoRateToForecastFrom() {
        assertThatThrownBy(() -> fleet().forecastFor(new Serial("5301")))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("one reading is not a rate");
    }

    @Test
    void theFleetTotalAddsUpOnlyWhatHadBeenDelivered() {
        Usage total = fleet().fleetUsageOn(LocalDate.of(2025, 1, 1));
        assertThat(total.cycles()).isEqualTo(new Cycles(13800 * 2));
    }

    @Test
    void theOldestTailInServiceIsTheFirstDelivered() {
        assertThat(fleet().oldestOn(LocalDate.of(2025, 1, 1)).serial()).isEqualTo(new Serial("5127"));
        assertThatThrownBy(() -> fleet().oldestOn(LocalDate.of(2000, 1, 1)))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("nothing was in service");
    }

    @Test
    void theUsageOfOneTailIsReadOffItsOwnLog() {
        assertThat(fleet().usageOn(LocalDate.of(2024, 6, 1), new Serial("5127")))
                .isEqualTo(usage(12000, 21000));
    }
}
