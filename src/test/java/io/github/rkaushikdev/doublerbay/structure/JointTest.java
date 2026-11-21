package io.github.rkaushikdev.doublerbay.structure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Frame;
import io.github.rkaushikdev.doublerbay.grid.Side;
import io.github.rkaushikdev.doublerbay.grid.Stringer;
import io.github.rkaushikdev.doublerbay.measure.FastenerSize;
import io.github.rkaushikdev.doublerbay.measure.Length;
import java.util.List;
import org.junit.jupiter.api.Test;

class JointTest {

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    private static FastenerRow row(int dash, String pitch, String offset, boolean countersunk) {
        return new FastenerRow(new FastenerSize(dash), inch(pitch), inch(offset), countersunk);
    }

    private static FastenerRow row(int dash, String pitch, String offset) {
        return row(dash, pitch, offset, false);
    }

    private static FastenerRow row(String offset) {
        return row(5, "0.750", offset);
    }

    private static FastenerRow row() {
        return row("0.500");
    }

    private static Joint lap(List<FastenerRow> rows) {
        return new Joint(JointKind.LAP, new Stringer(14, Side.LEFT), rows);
    }

    private static Joint lap() {
        return lap(List.of(row("0.500"), row("1.375"), row("2.250")));
    }

    @Test
    void aRowReadsAsItsSizeAndPitch() {
        assertThat(row()).hasToString("-5 at 0.750");
        assertThat(row(5, "0.750", "0.500", true)).hasToString("-5 at 0.750 csk");
    }

    @Test
    void aRowPitchReadsBackInDiameters() {
        assertThat(row().pitchInDiameters()).isEqualByComparingTo("4.80");
    }

    @Test
    void aPitchInsideTheMinimumIsRefused() {
        assertThatThrownBy(() -> row(5, "0.500", "0.500"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("closer than the");
    }

    @Test
    void aRowWithNoPitchIsRefused() {
        assertThatThrownBy(() -> new FastenerRow(new FastenerSize(5), inch("0"), inch("0.500")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("a row has a pitch");
    }

    @Test
    void aRunCarriesBothItsEndFasteners() {
        assertThat(row().fastenerCount(inch("0"))).isEqualTo(1);
        assertThat(row().fastenerCount(inch("0.750"))).isEqualTo(2);
        assertThat(row().fastenerCount(inch("7.500"))).isEqualTo(11);
    }

    @Test
    void aPartPitchAtTheEndOfARunDoesNotBuyAFastener() {
        assertThat(row().fastenerCount(inch("1.400"))).isEqualTo(2);
    }

    @Test
    void aRunTheWrongWayIsRefused() {
        assertThatThrownBy(() -> row().fastenerCount(inch("-1.000")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("does not go backwards");
    }

    @Test
    void theRunAndTheCountInvertEachOther() {
        assertThat(row().runFor(11)).isEqualTo(inch("7.500"));
        assertThat(row().fastenerCount(row().runFor(11))).isEqualTo(11);
    }

    @Test
    void aRowHoldsAtLeastOneFastener() {
        assertThatThrownBy(() -> row().runFor(0))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("at least one fastener");
    }

    @Test
    void aLapJointRunsAlongAStringerAndAButtJointAroundAFrame() {
        assertThat(lap().isLongitudinal()).isTrue();
        Joint butt = new Joint(JointKind.BUTT, new Frame(41), List.of(row()));
        assertThat(butt.isLongitudinal()).isFalse();
    }

    @Test
    void aLapJointOnAFrameIsRefused() {
        assertThatThrownBy(() -> new Joint(JointKind.LAP, new Frame(41), List.of(row())))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("runs along a stringer");
    }

    @Test
    void aButtJointOnAStringerIsRefused() {
        assertThatThrownBy(() -> new Joint(JointKind.BUTT, new Stringer(14, Side.LEFT), List.of(row())))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("runs around a frame");
    }

    @Test
    void lapAndSpliceAreTheFatigueCriticalKinds() {
        assertThat(JointKind.LAP.isFatigueCritical()).isTrue();
        assertThat(JointKind.SPLICE.isFatigueCritical()).isTrue();
        assertThat(JointKind.BUTT.isFatigueCritical()).isFalse();
        assertThat(JointKind.BONDED.isFatigueCritical()).isFalse();
        assertThat(lap().isFatigueCritical()).isTrue();
    }

    @Test
    void aBondedJointCarriesNoRowsAndIsAllowedNone() {
        Joint bonded = new Joint(JointKind.BONDED, new Stringer(14, Side.LEFT), List.of());
        assertThat(bonded.rowCount()).isZero();
        assertThat(bonded.width()).isEqualTo(new Length(0));
    }

    @Test
    void aRivetedJointWithNoRowsIsRefused() {
        assertThatThrownBy(() -> new Joint(JointKind.LAP, new Stringer(14, Side.LEFT), List.of()))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("has fastener rows");
    }

    @Test
    void rowsAreListedFromTheMemberOutwards() {
        assertThatThrownBy(() -> lap(List.of(row("2.250"), row("0.500"))))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("from the member outwards");
    }

    @Test
    void twoRowsOnTheSameLineAreRefused() {
        assertThatThrownBy(() -> lap(List.of(row("0.500"), row("0.500"))))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("same line");
    }

    @Test
    void rowsCloserThanTheMinimumSpacingAreRefused() {
        assertThatThrownBy(() -> lap(List.of(row("0.500"), row("0.900"))))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("inside the");
    }

    @Test
    void theRowSpacingMinimumComesFromTheLargerOfTheTwoSizes() {
        Joint mixed = lap(List.of(row(4, "0.750", "0.500"), row(8, "1.000", "1.500")));
        assertThat(mixed.rowCount()).isEqualTo(2);
        assertThatThrownBy(() -> lap(List.of(row(4, "0.750", "0.500"), row(8, "1.000", "1.499"))))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("inside the");
    }

    @Test
    void theJointBandRunsOutToTheLastRow() {
        assertThat(lap().width()).isEqualTo(inch("2.250"));
        assertThat(lap().isWithin(inch("0"))).isTrue();
        assertThat(lap().isWithin(inch("2.250"))).isTrue();
        assertThat(lap().isWithin(inch("2.251"))).isFalse();
        assertThat(lap().isWithin(inch("-0.001"))).isFalse();
    }

    @Test
    void theOutermostRowIsTheOneThatCracksFirst() {
        assertThat(lap().criticalRow().offset()).isEqualTo(inch("2.250"));
    }

    @Test
    void aJointWithNoRowsHasNoCriticalRow() {
        Joint bonded = new Joint(JointKind.BONDED, new Stringer(14, Side.LEFT), List.of());
        assertThatThrownBy(bonded::criticalRow)
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("no rows to be critical");
        assertThatThrownBy(() -> bonded.rowNearest(inch("1.000")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("no rows to measure to");
    }

    @Test
    void aDamageIsMeasuredToWhicheverRowIsNearest() {
        assertThat(lap().rowNearest(inch("0.600")).offset()).isEqualTo(inch("0.500"));
        assertThat(lap().rowNearest(inch("1.300")).offset()).isEqualTo(inch("1.375"));
        assertThat(lap().distanceToNearestRow(inch("1.300"))).isEqualTo(inch("0.075"));
    }

    @Test
    void aDamageOutsideTheBandStillMeasuresToTheLastRow() {
        assertThat(lap().distanceToNearestRow(inch("4.000"))).isEqualTo(inch("1.750"));
    }

    @Test
    void aJointReadsBackWhatItIsAndWhere() {
        assertThat(lap()).hasToString("lap joint on S-14L, 3 rows");
    }
}
