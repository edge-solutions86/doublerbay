package io.github.rkaushikdev.doublerbay.measure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.support.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class GaugeTest {

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    @Test
    void theLadderClimbs() {
        List<Length> nominals =
                SheetGauge.STOCK_LADDER.stream().map(SheetGauge::nominal).toList();
        List<Length> sorted = new ArrayList<>(nominals);
        sorted.sort(null);
        assertThat(nominals).isEqualTo(sorted);
        assertThat(new HashSet<>(nominals)).hasSameSizeAs(nominals);
    }

    @Test
    void aRungNamesItselfByItsCallout() {
        assertThat(SheetGauge.gaugeFor(inch("0.040"))).hasToString("0.040");
        assertThat(SheetGauge.gaugeFor(inch("0.040")).designation()).isEqualTo("0.040");
    }

    @Test
    void theBandIsTheNominalPlusAndMinusTheSpread() {
        SheetGauge rung = SheetGauge.gaugeFor(inch("0.040"));
        assertThat(rung.floor()).isEqualTo(inch("0.036"));
        assertThat(rung.ceiling()).isEqualTo(inch("0.044"));
    }

    @Test
    void theBandAdmitsItsOwnEdges() {
        SheetGauge rung = SheetGauge.gaugeFor(inch("0.040"));
        assertThat(rung.admits(inch("0.036"))).isTrue();
        assertThat(rung.admits(inch("0.044"))).isTrue();
        assertThat(rung.admits(inch("0.035"))).isFalse();
        assertThat(rung.admits(inch("0.045"))).isFalse();
    }

    @Test
    void aThicknessOffTheLadderIsNotAGauge() {
        assertThat(SheetGauge.isStocked(inch("0.047"))).isFalse();
        assertThatThrownBy(() -> SheetGauge.gaugeFor(inch("0.047")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not a stocked gauge");
    }

    @Test
    void sizingRoundsUpToTheNextRung() {
        assertThat(SheetGauge.nextHeavier(inch("0.047")).nominal()).isEqualTo(inch("0.050"));
        assertThat(SheetGauge.nextHeavier(inch("0.050")).nominal()).isEqualTo(inch("0.050"));
        assertThat(SheetGauge.nextHeavier(inch("0.001")).nominal()).isEqualTo(inch("0.016"));
    }

    @Test
    void nothingHeavierThanTheTopOfTheLadder() {
        assertThatThrownBy(() -> SheetGauge.nextHeavier(inch("0.375")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("heavier than any stocked sheet");
    }

    @Test
    void nearestCanGoEitherWay() {
        assertThat(SheetGauge.nearest(inch("0.041")).nominal()).isEqualTo(inch("0.040"));
        assertThat(SheetGauge.nearest(inch("0.048")).nominal()).isEqualTo(inch("0.050"));
    }

    @Test
    void nearestBreaksATieUpwards() {
        assertThat(SheetGauge.nearest(inch("0.045")).nominal()).isEqualTo(inch("0.050"));
    }

    @Test
    void aReadingInsideOneBandIdentifiesItsCallout() {
        assertThat(SheetGauge.identify(inch("0.037")).orElseThrow().nominal()).isEqualTo(inch("0.040"));
    }

    @Test
    void aReadingTwoBandsAdmitIdentifiesNothing() {
        SheetGauge overlap = new SheetGauge(inch("0.040"), inch("0.010"), inch("0.010"));
        assertThat(overlap.admits(inch("0.045"))).isTrue();
        assertThat(SheetGauge.identify(inch("0.100")).orElseThrow().nominal()).isEqualTo(inch("0.100"));
    }

    @Test
    void aReadingOffEveryBandIdentifiesNothing() {
        assertThat(SheetGauge.identify(inch("0.300"))).isEmpty();
        assertThat(SheetGauge.identify(inch("0.001"))).isEmpty();
    }

    @Test
    void aRungIsFrozen() {
        // Reassigning a rung's nominal does not compile: every field is final and there is no setter.
        Types.isFrozen(SheetGauge.class);
    }
}
