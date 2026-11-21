package io.github.rkaushikdev.doublerbay.measure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.support.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AngleTest {

    private static Angle deg(String value) {
        return Angle.ofDegrees(value);
    }

    private static Angle deg(long value) {
        return Angle.ofDegrees(value);
    }

    @Test
    void anAngleKeepsItsTenth() {
        assertThat(deg("12.5").tenths()).isEqualTo(125);
        assertThat(deg("12.5").degrees()).isEqualByComparingTo("12.5");
        assertThat(deg("12.5")).hasToString("12.5 deg");
    }

    @Test
    void finerThanATenthIsRefused() {
        assertThatThrownBy(() -> deg("12.55"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("finer than a tenth");
    }

    @Test
    void tenthsMustBeWhole() {
        // A fraction of a tenth does not compile: the only constructor takes an int.
        Types.constructorsTakeOnly(Angle.class, int.class);
    }

    @Test
    void anAngleFoldsIntoAFullTurn() {
        assertThat(deg(370)).isEqualTo(deg(10));
        assertThat(deg(-10)).isEqualTo(deg(350));
    }

    @Test
    void aCrackAxisFoldsOntoAHalfTurn() {
        assertThat(deg(275).folded()).isEqualTo(deg(95));
        assertThat(deg(95).folded()).isEqualTo(deg(95));
        assertThat(deg(180).folded()).isEqualTo(deg(0));
    }

    @Test
    void separationTakesTheShortWayRound() {
        assertThat(deg(10).separation(deg(350))).isEqualTo(deg(20));
        assertThat(deg(350).separation(deg(10))).isEqualTo(deg(20));
        assertThat(deg(0).separation(deg(180))).isEqualTo(deg(180));
    }

    @Test
    void axisSeparationIgnoresWhichEndYouMeasuredFrom() {
        assertThat(deg(275).axisSeparation(deg(95))).isEqualTo(deg(0));
        assertThat(deg(10).axisSeparation(deg(170))).isEqualTo(deg(20));
    }

    @Test
    void anglesAddAndSubtractAndStayOnTheTurn() {
        assertThat(deg(350).plus(deg(20))).isEqualTo(deg(10));
        assertThat(deg(10).minus(deg(20))).isEqualTo(deg(350));
    }

    @Test
    void anglesOrderAndHashByValue() {
        assertThat(deg(10)).isLessThan(deg(20));
        List<Angle> sorted = new ArrayList<>(List.of(deg(90), deg(0), deg(45)));
        sorted.sort(null);
        assertThat(sorted).containsExactly(deg(0), deg(45), deg(90));
        assertThat(new HashSet<>(List.of(deg(90), deg(450)))).hasSize(1);
    }

    @Test
    void mixingAnAngleWithANumberIsRefused() {
        // Adding a bare number does not compile: plus takes only another angle.
        Types.methodTakesOnly(Angle.class, "plus", Angle.class);
    }

    @ParameterizedTest
    @CsvSource({
        "0, LONGITUDINAL",
        "14, LONGITUDINAL",
        "166, LONGITUDINAL",
        "180, LONGITUDINAL",
        "90, CIRCUMFERENTIAL",
        "76, CIRCUMFERENTIAL",
        "104, CIRCUMFERENTIAL",
        "270, CIRCUMFERENTIAL",
        "45, OBLIQUE",
        "30, OBLIQUE",
        "135, OBLIQUE"
    })
    void aBearingLandsOnAnAxisOrBetweenThem(int bearing, Orientation expected) {
        assertThat(Angle.orientationOf(deg(bearing))).isEqualTo(expected);
    }

    @Test
    void theBandEdgeBelongsToTheAxis() {
        assertThat(Angle.orientationOf(Angle.AXIS_BAND)).isEqualTo(Orientation.LONGITUDINAL);
        assertThat(Angle.orientationOf(deg(15).plus(deg(1)))).isEqualTo(Orientation.OBLIQUE);
    }

    @Test
    void theCountersinkIsTheAngleItSaysItIs() {
        assertThat(Angle.COUNTERSINK_100.degrees()).isEqualByComparingTo("100.0");
    }

    @Test
    void reprRoundTrips() {
        assertThat(deg("12.5").repr()).isEqualTo("Angle.ofDegrees(\"12.5\")");
    }
}
