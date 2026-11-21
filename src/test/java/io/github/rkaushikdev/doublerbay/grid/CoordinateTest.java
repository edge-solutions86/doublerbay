package io.github.rkaushikdev.doublerbay.grid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.WrongMeasure;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.support.Types;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CoordinateTest {

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    @Test
    void aStationCarriesItsLetter() {
        assertThat(Station.ofInches("727.5")).hasToString("STA 727.500");
        assertThat(Station.ofInches("727.5").inches()).isEqualByComparingTo("727.500");
    }

    @Test
    void aWaterlineAndAStationPrintApart() {
        assertThat(Waterline.ofInches("208")).hasToString("WL 208.000");
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"727.5|727.500", "STA 727.5|727.500", "sta727.5|727.500", "-14|-14.000"})
    void aStationReadsWithOrWithoutItsLetter(String text, String inches) {
        assertThat(Station.parse(text).inches()).isEqualByComparingTo(inches);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "WL 208", "aft", "727.5 STA", "7.2.7.5"})
    void aStationRefusesWhatIsNotOne(String text) {
        assertThatThrownBy(() -> Station.parse(text))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not a station");
    }

    @Test
    void aWaterlineReadsItsOwnLetterOnly() {
        assertThat(Waterline.parse("WL 208").inches()).isEqualByComparingTo("208.000");
        assertThatThrownBy(() -> Waterline.parse("STA 208"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not a waterline");
    }

    @Test
    void aButtockLineTakesItsHandFromThePrefix() {
        assertThat(ButtockLine.parse("LBL 42").isLeft()).isTrue();
        assertThat(ButtockLine.parse("RBL 42").isLeft()).isFalse();
        assertThat(ButtockLine.parse("BL 0").isCentreline()).isTrue();
    }

    @Test
    void aSignedButtockLineReadsTheSameAsAHandedOne() {
        assertThat(ButtockLine.parse("-42")).isEqualTo(ButtockLine.parse("LBL 42"));
        assertThat(ButtockLine.parse("42")).isEqualTo(ButtockLine.parse("RBL 42"));
    }

    @Test
    void aHandedPrefixWinsOverTheSignThatWasTyped() {
        assertThat(ButtockLine.parse("LBL -42")).isEqualTo(ButtockLine.parse("LBL 42"));
        assertThat(ButtockLine.parse("RBL -42")).isEqualTo(ButtockLine.parse("RBL 42"));
    }

    @Test
    void aButtockLinePrintsItsHandBack() {
        assertThat(ButtockLine.parse("LBL 42")).hasToString("LBL 42.000");
        assertThat(ButtockLine.parse("RBL 42")).hasToString("RBL 42.000");
        assertThat(ButtockLine.parse("0")).hasToString("BL 0.000");
    }

    @Test
    void mirroringCrossesTheCentreline() {
        assertThat(ButtockLine.parse("LBL 42").mirrored()).isEqualTo(ButtockLine.parse("RBL 42"));
        assertThat(ButtockLine.parse("BL 0").mirrored()).isEqualTo(ButtockLine.parse("BL 0"));
    }

    @Test
    void twoRulersNeverCompare() {
        assertThatThrownBy(() -> Station.ofInches("208").isLessThan(Waterline.ofInches("208")))
                .isInstanceOf(WrongMeasure.class)
                .hasMessageContaining("different rulers");
        assertThatThrownBy(() -> Station.ofInches("208").gapTo(Waterline.ofInches("208")))
                .isInstanceOf(WrongMeasure.class)
                .hasMessageContaining("different rulers");
    }

    @Test
    void twoRulersAreNeverEqualEvenAtTheSameReading() {
        assertThat((Axis) Station.ofInches("208")).isNotEqualTo(Waterline.ofInches("208"));
        assertThat(new HashSet<Axis>(List.of(Station.ofInches("208"), Waterline.ofInches("208"))))
                .hasSize(2);
    }

    @Test
    void readingsOnOneRulerOrderAndMeasure() {
        Station forward = Station.ofInches("360");
        Station aft = Station.ofInches("727.5");
        assertThat(forward.isLessThan(aft)).isTrue();
        assertThat(forward.gapTo(aft)).isEqualTo(inch("367.5"));
        assertThat(aft.gapTo(forward)).isEqualTo(inch("367.5"));
    }

    @Test
    void anOffsetStaysOnTheSameRuler() {
        Axis moved = Station.ofInches("360").offset(inch("20"));
        assertThat(moved).isInstanceOf(Station.class);
        assertThat(moved).isEqualTo(Station.ofInches("380"));
    }

    @Test
    void aCoordinateHasToBeBuiltFromALength() {
        // A reading built from a bare number does not compile: the only constructor takes a length.
        Types.constructorsTakeOnly(Station.class, Length.class);
    }

    @Test
    void readingsHashAndReprByValue() {
        assertThat(new HashSet<>(List.of(Station.ofInches("360"), Station.parse("STA 360"))))
                .hasSize(1);
        assertThat(Station.ofInches("360").repr()).isEqualTo("Station.ofInches(\"360.000\")");
    }
}
