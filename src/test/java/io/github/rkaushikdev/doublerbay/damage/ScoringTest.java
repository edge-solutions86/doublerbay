package io.github.rkaushikdev.doublerbay.damage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.measure.Angle;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.measure.Orientation;
import io.github.rkaushikdev.doublerbay.measure.Taper;
import io.github.rkaushikdev.doublerbay.structure.Footprint;
import org.junit.jupiter.api.Test;

class ScoringTest {

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    private static Angle deg(int value) {
        return Angle.ofDegrees(value);
    }

    private static Scoring score(String depth, String length, String width, int bearing, boolean sharp) {
        return new Scoring(inch(depth), inch(length), inch(width), deg(bearing), sharp);
    }

    private static Scoring score(String depth, String length, String width) {
        return score(depth, length, width, 0, false);
    }

    private static Scoring score(int bearing) {
        return score("0.010", "2.000", "0.060", bearing, false);
    }

    private static Scoring score() {
        return score(0);
    }

    private static Crack crack(String length, int bearing, boolean through, boolean stopDrilled, int tips) {
        return new Crack(inch(length), deg(bearing), through, stopDrilled, tips);
    }

    private static Crack crack(boolean stopDrilled, int tips) {
        return crack("0.750", 0, true, stopDrilled, tips);
    }

    private static Crack crack(String length, int tips) {
        return crack(length, 0, true, false, tips);
    }

    private static Crack crack(String length) {
        return crack(length, 2);
    }

    private static Crack crack(int bearing) {
        return crack("0.750", bearing, true, false, 2);
    }

    private static Crack crack() {
        return crack(0);
    }

    @Test
    void aScoreReadsBackItsDepthLengthAndDirection() {
        assertThat(score()).hasToString("0.010 deep, 2.000 long, 0.0 deg");
        assertThat(score("0.010", "2.000", "0.060", 0, true).toString()).endsWith(", sharp");
    }

    @Test
    void aScoreNeedsAllThreeMeasurements() {
        assertThatThrownBy(() -> score("0", "2.000", "0.060"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("a score has a depth");
        assertThatThrownBy(() -> score("0.010", "0", "0.060"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("a score has a length");
        assertThatThrownBy(() -> score("0.010", "2.000", "0"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("a score has a width");
    }

    @Test
    void aScoreWiderThanItIsLongIsSomethingElse() {
        assertThatThrownBy(() -> score("0.010", "0.050", "0.060"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("wider than it is long");
    }

    @Test
    void aScoreAlongTheBodyIsTheOneHoopStressPullsOpen() {
        assertThat(score(0).isHoopLoaded()).isTrue();
        assertThat(score(10).isHoopLoaded()).isTrue();
        assertThat(score(90).isHoopLoaded()).isFalse();
        assertThat(score(90).isAcrossTheBarrel()).isTrue();
    }

    @Test
    void aScoreOnTheDiagonalIsNeither() {
        assertThat(score(45).orientation()).isSameAs(Orientation.OBLIQUE);
        assertThat(score(45).isHoopLoaded()).isFalse();
        assertThat(score(45).isAcrossTheBarrel()).isFalse();
    }

    @Test
    void theBlendRunsOutPastBothEnds() {
        Length blended = score().blendLength(new Taper(20));
        assertThat(blended).isEqualTo(inch("2.400"));
        assertThat(score().blendWidth(new Taper(20))).isEqualTo(inch("0.460"));
    }

    @Test
    void aGentlerBlendTakesMoreSkin() {
        assertThat(score().blendLength(new Taper(40))).isGreaterThan(score().blendLength(new Taper(20)));
    }

    @Test
    void theFootprintLiesTheWayTheScoreLies() {
        Offset centre = new Offset(inch("100.000"), inch("50.000"));
        Footprint alongBody = score(0).footprint(centre);
        Footprint aroundBody = score(90).footprint(centre);
        assertThat(alongBody.along()).isEqualTo(inch("2.000"));
        assertThat(aroundBody.around()).isEqualTo(inch("2.000"));
        assertThat(aroundBody.along()).isEqualTo(inch("0.060"));
    }

    @Test
    void aCrackReadsBackItsLengthAndDirection() {
        assertThat(crack()).hasToString("0.750 long, 0.0 deg");
        assertThat(crack("0.750", 0, false, false, 2).toString()).endsWith(", part depth");
        assertThat(crack(true, 2).toString()).endsWith(", stop drilled");
    }

    @Test
    void aCrackNeedsALength() {
        assertThatThrownBy(() -> crack("0"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("a crack has a length");
    }

    @Test
    void aCrackHasNoughtOneOrTwoTips() {
        assertThat(crack(false, 1).tipsFound()).isEqualTo(1);
        assertThatThrownBy(() -> crack(false, 3))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("nought, one or two tips");
    }

    @Test
    void aCrackWithNoTipsFoundCannotBeStopDrilled() {
        assertThatThrownBy(() -> crack(true, 0))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("at a tip nobody found");
    }

    @Test
    void aCrackAlongTheBodyIsTheOneHoopStressPullsOpen() {
        assertThat(crack(0).isHoopLoaded()).isTrue();
        assertThat(crack(90).isHoopLoaded()).isFalse();
        assertThat(crack(90).orientation()).isSameAs(Orientation.CIRCUMFERENTIAL);
    }

    @Test
    void aCrackWithBothTipsFoundHasARealLength() {
        assertThat(crack().isBounded()).isTrue();
        assertThat(crack(false, 1).isBounded()).isFalse();
        assertThat(crack(false, 1).toString()).contains("1 tips found");
    }

    @Test
    void aCrackIsOnlyArrestedWhenBothTipsAreDrilled() {
        assertThat(crack(true, 2).isArrested()).isTrue();
        assertThat(crack(true, 1).isArrested()).isFalse();
        assertThat(crack().isArrested()).isFalse();
    }

    @Test
    void growthIsTheDifferenceBetweenTwoBoundedReadings() {
        assertThat(crack("0.900").grownFrom(crack("0.750"))).isEqualTo(inch("0.150"));
    }

    @Test
    void growthNeedsBothTipsOnBothReadings() {
        assertThatThrownBy(() -> crack("0.900", 1).grownFrom(crack("0.750")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("both tips found on both readings");
        assertThatThrownBy(() -> crack("0.900").grownFrom(crack("0.750", 1)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("both tips found on both readings");
    }

    @Test
    void aCrackDoesNotGetShorter() {
        assertThatThrownBy(() -> crack("0.600").grownFrom(crack("0.750")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("does not get shorter");
    }

    @Test
    void theCrackFootprintLiesAlongTheCrack() {
        Offset centre = new Offset(inch("100.000"), inch("50.000"));
        assertThat(crack(0).footprint(centre).along()).isEqualTo(inch("0.750"));
        assertThat(crack(90).footprint(centre).around()).isEqualTo(inch("0.750"));
    }
}
