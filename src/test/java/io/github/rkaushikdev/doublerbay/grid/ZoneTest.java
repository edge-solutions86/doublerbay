package io.github.rkaushikdev.doublerbay.grid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ZoneTest {

    @Test
    void aZoneSplitsIntoItsThreeDigits() {
        Zone zone = new Zone(231);
        assertThat(zone.major()).isSameAs(MajorZone.UPPER_FUSELAGE);
        assertThat(zone.sub()).isEqualTo(3);
        assertThat(zone.unit()).isEqualTo(1);
        assertThat(zone).hasToString("231");
    }

    @Test
    void theMajorZoneNamesItself() {
        assertThat(MajorZone.LOWER_FUSELAGE.title()).isEqualTo("lower fuselage");
        assertThat(MajorZone.EMPENNAGE).hasToString("300");
    }

    @ParameterizedTest
    @ValueSource(ints = {99, 900, 1000, 0, -100})
    void aNumberOutsideTheSchemeIsNotAZone(int number) {
        assertThatThrownBy(() -> new Zone(number))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("three digits from 100 to 899");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "23", "2311", "23A", "  "})
    void theParserRefusesAnythingButThreeDigits(String text) {
        assertThatThrownBy(() -> Zone.parse(text))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not a zone number");
    }

    @Test
    void theParserReadsThePaintedNumber() {
        assertThat(Zone.parse(" 231 ")).isEqualTo(new Zone(231));
    }

    @ParameterizedTest
    @CsvSource({"0, CENTRE", "1, LEFT", "2, RIGHT", "7, LEFT", "8, RIGHT"})
    void theLastDigitCarriesTheHand(int unit, Side side) {
        assertThat(Zone.sideOfDigit(unit)).isSameAs(side);
        assertThat(new Zone(230 + unit).side()).isSameAs(side);
    }

    @Test
    void theMirrorCrossesToTheOtherHand() {
        assertThat(new Zone(231).opposite()).isEqualTo(new Zone(232));
        assertThat(new Zone(232).opposite()).isEqualTo(new Zone(231));
        assertThat(new Zone(231).opposite().side()).isSameAs(Side.RIGHT);
    }

    @Test
    void aCentrelineZoneMirrorsOntoItself() {
        assertThat(new Zone(230).opposite()).isEqualTo(new Zone(230));
        assertThat(new Zone(230).side()).isSameAs(Side.CENTRE);
    }

    @Test
    void twoHandsOfTheSamePlaceShareABay() {
        assertThat(new Zone(231).sharesBayWith(new Zone(232))).isTrue();
        assertThat(new Zone(231).sharesBayWith(new Zone(241))).isFalse();
    }

    @Test
    void theFuselageIsBothHalvesOfTheBarrel() {
        assertThat(new Zone(131).isFuselage()).isTrue();
        assertThat(new Zone(231).isFuselage()).isTrue();
        assertThat(new Zone(531).isFuselage()).isFalse();
    }

    @Test
    void aWingZoneIsEitherWing() {
        assertThat(new Zone(531).isWing()).isTrue();
        assertThat(new Zone(631).isWing()).isTrue();
        assertThat(new Zone(331).isWing()).isFalse();
    }

    @Test
    void zonesSortAndHashByNumber() {
        List<Zone> sorted = new ArrayList<>(List.of(new Zone(311), new Zone(131), new Zone(232)));
        sorted.sort(null);
        assertThat(sorted).containsExactly(new Zone(131), new Zone(232), new Zone(311));
        assertThat(new HashSet<>(List.of(new Zone(231), Zone.parse("231")))).hasSize(1);
        assertThat(new Zone(231).isLessThan(new Zone(232))).isTrue();
    }

    @Test
    void aRunOfZonesHasToBelongToTheMajorZoneItIsFiledUnder() {
        assertThat(Zone.zonesIn(MajorZone.UPPER_FUSELAGE, List.of(231, 232, 241)))
                .containsExactly(new Zone(231), new Zone(232), new Zone(241));
        assertThatThrownBy(() -> Zone.zonesIn(MajorZone.UPPER_FUSELAGE, List.of(231, 531)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is not in upper fuselage");
    }
}
