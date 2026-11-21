package io.github.rkaushikdev.doublerbay.fleet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.ids.Registration;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.support.Types;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AircraftTest {

    private static final Bulletin SB_53_018 = new Bulletin(53, 18);
    private static final LocalDate DELIVERED = LocalDate.of(2016, 4, 12);

    private static Aircraft tail() {
        return tail("5127", Series.STANDARD, 1140, null);
    }

    private static Aircraft tail(String serial) {
        return tail(serial, Series.STANDARD, 1140, null);
    }

    private static Aircraft tail(Series series) {
        return tail("5127", series, 1140, null);
    }

    private static Aircraft tail(String serial, Series series, int lineNumber, LocalDate withdrawn) {
        return Aircraft.of(
                new Serial(serial), new Registration("N412VD"), series, lineNumber, DELIVERED, Set.of(), withdrawn);
    }

    @Test
    void aTailReadsAsItsMarksSerialAndSeries() {
        assertThat(tail()).hasToString("N412VD (MSN 5127, V320-200)");
        assertThat(tail().tail()).isEqualTo("N412VD");
    }

    @Test
    void aLineNumberStartsAtOne() {
        assertThatThrownBy(() -> tail("5127", Series.STANDARD, 0, null))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("line numbers start at one");
    }

    @Test
    void aTailCannotBeWithdrawnBeforeItArrives() {
        assertThatThrownBy(() -> tail("5127", Series.STANDARD, 1140, LocalDate.of(2015, 1, 1)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("withdrawn before it was delivered");
    }

    @Test
    void aTailIsInServiceFromDelivery() {
        Aircraft aircraft = tail();
        assertThat(aircraft.inServiceOn(LocalDate.of(2016, 4, 11))).isFalse();
        assertThat(aircraft.inServiceOn(LocalDate.of(2016, 4, 12))).isTrue();
        assertThat(aircraft.inServiceOn(LocalDate.of(2030, 1, 1))).isTrue();
    }

    @Test
    void aWithdrawnTailStopsBeingInServiceOnTheDayItGoes() {
        Aircraft aircraft = tail().withdrawnOn(LocalDate.of(2026, 6, 30));
        assertThat(aircraft.inServiceOn(LocalDate.of(2026, 6, 29))).isTrue();
        assertThat(aircraft.inServiceOn(LocalDate.of(2026, 6, 30))).isFalse();
    }

    @Test
    void theAgeIsMeasuredFromDelivery() {
        assertThat(tail().ageYears(LocalDate.of(2016, 4, 12))).isEqualByComparingTo("0.00");
        assertThat(tail().ageYears(LocalDate.of(2026, 4, 12))).isEqualByComparingTo("10.00");
    }

    @Test
    void anAgeBeforeDeliveryIsRefused() {
        assertThatThrownBy(() -> tail().ageYears(LocalDate.of(2015, 1, 1)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is before it was delivered");
    }

    @Test
    void aBulletinGoesOnByMakingANewRecord() {
        Aircraft before = tail();
        Aircraft after = before.withEmbodied(SB_53_018);
        assertThat(before.hasEmbodied(SB_53_018)).isFalse();
        assertThat(after.hasEmbodied(SB_53_018)).isTrue();
        assertThat(after.serial()).isEqualTo(before.serial());
    }

    @Test
    void embodyingABulletinTwiceChangesNothing() {
        Aircraft once = tail().withEmbodied(SB_53_018);
        assertThat(once.withEmbodied(SB_53_018)).isSameAs(once);
    }

    @Test
    void newMarksMakeANewRecordOnTheSameAirframe() {
        Aircraft before = tail();
        Aircraft after = before.reRegistered(new Registration("N908QA"));
        assertThat(after.registration()).isEqualTo(new Registration("N908QA"));
        assertThat(after.serial()).isEqualTo(before.serial());
        assertThat(before.registration()).isEqualTo(new Registration("N412VD"));
    }

    @Test
    void reRegisteringToTheSameMarksChangesNothing() {
        Aircraft aircraft = tail();
        assertThat(aircraft.reRegistered(new Registration("N412VD"))).isSameAs(aircraft);
    }

    @Test
    void theSerialIsTheKeyAndTheMarksAreNot() {
        Aircraft one = tail();
        Aircraft sameMetal = one.reRegistered(new Registration("N908QA"));
        Aircraft otherMetal = tail("5240");
        assertThat(one.serial()).isEqualTo(sameMetal.serial());
        assertThat(one.serial()).isNotEqualTo(otherMetal.serial());
    }

    @Test
    void aPageWrittenForARangeAnswersForATailInsideIt() {
        Aircraft aircraft = tail();
        Effectivity block = Effectivity.allAircraft().withSerials(5001, 5240);
        assertThat(aircraft.coveredBy(block)).isTrue();
        assertThat(tail("5300").coveredBy(block)).isFalse();
    }

    @Test
    void aPageWrittenAfterABulletinNeedsTheBulletinOn() {
        Effectivity afterMod = Effectivity.allAircraft().withRequires(SB_53_018);
        assertThat(tail().coveredBy(afterMod)).isFalse();
        assertThat(tail().withEmbodied(SB_53_018).coveredBy(afterMod)).isTrue();
    }

    @Test
    void aPageForAnotherSeriesDoesNotAnswer() {
        Effectivity onlyShort = Effectivity.allAircraft().withSeries(Set.of(Series.SHORT));
        assertThat(tail().coveredBy(onlyShort)).isFalse();
        assertThat(tail(Series.SHORT).coveredBy(onlyShort)).isTrue();
    }

    @Test
    void aTailIsFrozenAndHashesByValue() {
        assertThat(new HashSet<>(List.of(tail(), tail()))).hasSize(1);
        // Reassigning the marks on a built record does not compile: every field is final.
        Types.isFrozen(Aircraft.class);
    }
}
