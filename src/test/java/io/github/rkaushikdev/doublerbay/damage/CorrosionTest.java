package io.github.rkaushikdev.doublerbay.damage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.measure.Taper;
import org.junit.jupiter.api.Test;

class CorrosionTest {

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    private static Corrosion rot(String along, String around, String depth, boolean pitted, boolean exfoliating) {
        return new Corrosion(inch(along), inch(around), inch(depth), pitted, exfoliating);
    }

    private static Corrosion rot(String depth, boolean pitted, boolean exfoliating) {
        return rot("4.000", "3.000", depth, pitted, exfoliating);
    }

    private static Corrosion rot(String depth) {
        return rot(depth, false, false);
    }

    private static Corrosion rot() {
        return rot("0.006");
    }

    private static LightningBurn burn(
            String along, String around, int pits, String worst, boolean onFastener, boolean entry) {
        return new LightningBurn(inch(along), inch(around), pits, inch(worst), onFastener, entry);
    }

    private static LightningBurn burn(int pits, String worst, boolean onFastener) {
        return burn("2.000", "2.000", pits, worst, onFastener, true);
    }

    private static LightningBurn burn(String worst, boolean onFastener) {
        return burn(4, worst, onFastener);
    }

    private static LightningBurn burn(int pits) {
        return burn(pits, "0.008", false);
    }

    private static LightningBurn burn() {
        return burn(4);
    }

    @Test
    void anAreaComesOutInSquareInches() {
        assertThat(Corrosion.squareInches(inch("4.000"), inch("3.000"))).isEqualByComparingTo("12.00");
        assertThat(Corrosion.squareInches(inch("0.500"), inch("0.500"))).isEqualByComparingTo("0.25");
    }

    @Test
    void corrosionReadsBackItsAreaAndWorstDepth() {
        assertThat(rot()).hasToString("12.00 sq in, 0.006 deep");
        assertThat(rot().area()).isEqualByComparingTo("12.00");
    }

    @Test
    void theFlagsShowUpInTheReading() {
        assertThat(rot("0.006", true, false).toString()).endsWith(", pitted");
        assertThat(rot("0.006", true, true).toString()).endsWith(", pitted and exfoliating");
    }

    @Test
    void corrosionCoversAnArea() {
        assertThatThrownBy(() -> rot("0", "3.000", "0.006", false, false))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("covers an area");
    }

    @Test
    void aDepthThatRunsBackwardsIsRefused() {
        assertThatThrownBy(() -> rot("-0.001"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("does not run backwards");
    }

    @Test
    void aStainWithNoLossUnderItIsSurfaceOnly() {
        assertThat(rot("0").isSurfaceOnly()).isTrue();
        assertThat(rot("0").reachesBelowTheSurface()).isFalse();
        assertThat(rot().reachesBelowTheSurface()).isTrue();
    }

    @Test
    void exfoliationWithNoDepthIsNotExfoliation() {
        assertThatThrownBy(() -> rot("0", false, true))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not a surface stain");
    }

    @Test
    void exfoliationGoesToARepairWhateverTheDepthSays() {
        assertThat(rot("0.001", false, true).needsARepairWhateverTheDepth()).isTrue();
        assertThat(rot("0.020").needsARepairWhateverTheDepth()).isFalse();
    }

    @Test
    void theGoverningDepthIsTheWorstPitAndNeverAnAverage() {
        assertThat(rot("0.012", true, false).governingDepth()).isEqualTo(inch("0.012"));
    }

    @Test
    void theBlendAreaGrowsPastTheStainAllRound() {
        assertThat(rot().blendArea(new Taper(20))).isEqualByComparingTo("13.74");
        assertThat(rot().blendArea(new Taper(40))).isGreaterThan(rot().blendArea(new Taper(20)));
    }

    @Test
    void theFootprintCoversTheStain() {
        Offset centre = new Offset(inch("100.000"), inch("50.000"));
        assertThat(rot().footprint(centre).along()).isEqualTo(inch("4.000"));
        assertThat(rot().footprint(centre).contains(centre)).isTrue();
    }

    @Test
    void exfoliationIsWorseThanAnythingDeeperThatIsNot() {
        assertThat(rot("0.002", false, true).worseThan(rot("0.030"))).isTrue();
        assertThat(rot("0.030").worseThan(rot("0.002", false, true))).isFalse();
    }

    @Test
    void twoOrdinaryPatchesCompareOnDepth() {
        assertThat(rot("0.020").worseThan(rot("0.010"))).isTrue();
    }

    @Test
    void aStrikeReadsBackWhichEndItIsAndWhatItLeft() {
        assertThat(burn()).hasToString("entry, 4 pits, worst 0.008");
        assertThat(burn("2.000", "2.000", 4, "0.008", false, false).toString()).startsWith("exit");
        assertThat(burn("0.008", true).toString()).endsWith(", on a fastener");
    }

    @Test
    void aStrikeWithNoPitsIsNotAStrike() {
        assertThatThrownBy(() -> burn(0)).isInstanceOf(BadReading.class).hasMessageContaining("not a strike");
    }

    @Test
    void aPitHasADepthAndABurnHasAnArea() {
        assertThatThrownBy(() -> burn("0", false))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("a pit has a depth");
        assertThatThrownBy(() -> burn("0", "2.000", 4, "0.008", false, true))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("a burn covers an area");
    }

    @Test
    void enoughAttachmentPointsMakeItASweptStroke() {
        assertThat(burn(12).isSwept()).isTrue();
        assertThat(burn(11).isSwept()).isFalse();
    }

    @Test
    void thePitDensitySeparatesAClusterFromASweep() {
        assertThat(burn(4).pitDensity()).isEqualByComparingTo("1.00");
        assertThat(burn("8.000", "2.000", 4, "0.008", false, true).pitDensity()).isEqualByComparingTo("0.25");
    }

    @Test
    void aStrikeOnAFastenerTakesTheFastenerOut() {
        assertThat(burn("0.008", true).needsTheFastenerOut()).isTrue();
        assertThat(burn().needsTheFastenerOut()).isFalse();
    }

    @Test
    void aStrikeOnAFastenerIsWorseThanADeeperOneThatIsNot() {
        assertThat(burn("0.002", true).worseThan(burn("0.030", false))).isTrue();
        assertThat(burn("0.030", false).worseThan(burn("0.002", true))).isFalse();
    }

    @Test
    void twoOpenSkinStrikesCompareOnTheirWorstPit() {
        assertThat(burn("0.020", false).worseThan(burn("0.010", false))).isTrue();
    }
}
