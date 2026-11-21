package io.github.rkaushikdev.doublerbay.damage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.measure.Angle;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.measure.Orientation;
import org.junit.jupiter.api.Test;

class OpeningTest {

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    private static Angle deg(int value) {
        return Angle.ofDegrees(value);
    }

    private static Opening hole(String along, String around, boolean torn, boolean wasFastener) {
        return new Opening(inch(along), inch(around), torn, wasFastener);
    }

    private static Opening hole(boolean torn, boolean wasFastener) {
        return hole("0.500", "0.500", torn, wasFastener);
    }

    private static Opening hole(String along) {
        return hole(along, "0.500", false, false);
    }

    private static Opening hole() {
        return hole("0.500");
    }

    private static Buckle buckle(String length, String height, int bearing, boolean permanent) {
        return new Buckle(inch(length), inch(height), deg(bearing), permanent);
    }

    private static Buckle buckle(int bearing) {
        return buckle("6.000", "0.120", bearing, true);
    }

    private static Buckle buckle() {
        return buckle(90);
    }

    private static Buckle notConfirmed() {
        return buckle("6.000", "0.120", 90, false);
    }

    private static Disbond disbond(String along, String around, boolean tapped, boolean edge) {
        return new Disbond(inch(along), inch(around), tapped, edge);
    }

    private static Disbond disbond(boolean tapped, boolean edge) {
        return disbond("3.000", "2.000", tapped, edge);
    }

    private static Disbond disbond() {
        return disbond(true, false);
    }

    @Test
    void anOpeningReadsBackBothWaysAcross() {
        assertThat(hole()).hasToString("0.500 by 0.500");
        assertThat(hole(true, false).toString()).endsWith(", torn");
        assertThat(hole(false, true).toString()).contains("worked fastener hole");
    }

    @Test
    void anOpeningHasASizeBothWays() {
        assertThatThrownBy(() -> hole("0")).isInstanceOf(BadReading.class).hasMessageContaining("a size both ways");
    }

    @Test
    void aRoundOpeningMeasuresTheSameBothWays() {
        assertThat(hole().isRound()).isTrue();
        assertThat(hole("0.750").isRound()).isFalse();
        assertThat(hole("0.750").wide()).isEqualTo(inch("0.750"));
        assertThat(hole("0.750").narrow()).isEqualTo(inch("0.500"));
    }

    @Test
    void aTornEdgeGetsCutBackBeforeARepairGoesOn() {
        assertThat(hole(true, false).needsTrimmingBeforeRepair()).isTrue();
        assertThat(hole().needsTrimmingBeforeRepair()).isFalse();
    }

    @Test
    void trimmingGrowsTheOpeningAndLeavesItClean() {
        Opening trimmed = hole(true, false).trimmed(inch("0.125"));
        assertThat(trimmed.along()).isEqualTo(inch("0.750"));
        assertThat(trimmed.around()).isEqualTo(inch("0.750"));
        assertThat(trimmed.tornEdges()).isFalse();
    }

    @Test
    void trimmingCarriesTheFastenerHoleFlagThrough() {
        assertThat(hole(false, true).trimmed(inch("0.125")).wasAFastenerHole()).isTrue();
    }

    @Test
    void trimmingBackwardsIsRefused() {
        assertThatThrownBy(() -> hole().trimmed(inch("-0.010")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("does not make an opening smaller");
    }

    @Test
    void theOpeningFootprintCoversTheHole() {
        Offset centre = new Offset(inch("100.000"), inch("50.000"));
        assertThat(hole().footprint(centre).contains(centre)).isTrue();
        assertThat(hole().footprint(centre).along()).isEqualTo(inch("0.500"));
    }

    @Test
    void aBuckleReadsBackItsLengthAndHowFarOutItIs() {
        assertThat(buckle()).hasToString("6.000 long, 0.120 out of plane");
        assertThat(notConfirmed().toString()).endsWith(", not confirmed permanent");
    }

    @Test
    void aBuckleHasALengthAndAHeight() {
        assertThatThrownBy(() -> buckle("0", "0.120", 90, true))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("a buckle has a length");
        assertThatThrownBy(() -> buckle("6.000", "0", 90, true))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("a height out of plane");
    }

    @Test
    void aBuckleRoundTheBarrelLiesAcrossTheLoadThatMadeIt() {
        assertThat(buckle(90).isAcrossTheLoad()).isTrue();
        assertThat(buckle(0).isAcrossTheLoad()).isFalse();
        assertThat(buckle(0).orientation()).isSameAs(Orientation.LONGITUDINAL);
    }

    @Test
    void aBuckleNobodyHasConfirmedMayStillBeOilCanning() {
        assertThat(notConfirmed().isOilCanning()).isTrue();
        assertThat(buckle().isOilCanning()).isFalse();
    }

    @Test
    void theBuckleFootprintLiesTheWayTheBuckleLies() {
        Offset centre = new Offset(inch("100.000"), inch("50.000"));
        assertThat(buckle(90).footprint(centre).around()).isEqualTo(inch("6.000"));
        assertThat(buckle(0).footprint(centre).along()).isEqualTo(inch("6.000"));
    }

    @Test
    void aShallowBuckleStillGetsAWorkableFootprintAcrossIt() {
        Offset centre = new Offset(inch("100.000"), inch("50.000"));
        assertThat(buckle("6.000", "0.020", 0, true).footprint(centre).around()).isEqualTo(inch("0.500"));
    }

    @Test
    void aDisbondReadsBackItsArea() {
        assertThat(disbond()).hasToString("6.00 sq in");
        assertThat(disbond().area()).isEqualByComparingTo("6.00");
    }

    @Test
    void aDisbondCoversAnArea() {
        assertThatThrownBy(() -> disbond("0", "2.000", true, false))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("covers an area");
    }

    @Test
    void aDisbondNobodyHasTappedOutHasNoKnownExtent() {
        assertThat(disbond().isBounded()).isTrue();
        assertThat(disbond(false, false).isBounded()).isFalse();
        assertThat(disbond(false, false).toString()).endsWith(", extent not tapped out");
    }

    @Test
    void aDisbondRunningToAFreeEdgeCanSpread() {
        assertThat(disbond(true, true).canSpread()).isTrue();
        assertThat(disbond().canSpread()).isFalse();
        assertThat(disbond(true, true).toString()).contains("runs to an edge");
    }
}
