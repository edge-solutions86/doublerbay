package io.github.rkaushikdev.doublerbay.structure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.support.Types;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class CutoutTest {

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    private static Offset at(String along, String around) {
        return new Offset(inch(along), inch(around));
    }

    private static Cutout hatch(String along, String around, String radius, CutoutKind kind) {
        return new Cutout("191AL", kind, at("10.000", "10.000"), inch(along), inch(around), inch(radius));
    }

    private static Cutout hatch(String along, String around, String radius) {
        return hatch(along, around, radius, CutoutKind.ACCESS_PANEL);
    }

    private static Cutout hatch() {
        return hatch("8.000", "6.000", "1.000");
    }

    @Test
    void aCutoutNamesItselfAndWhatItIsFor() {
        assertThat(hatch()).hasToString("191AL (access panel)");
    }

    @Test
    void thePluggedHolesAreThePressureBoundary() {
        assertThat(CutoutKind.WINDOW.isPressureBoundary()).isTrue();
        assertThat(CutoutKind.DOOR.isPressureBoundary()).isTrue();
        assertThat(CutoutKind.DRAIN.isPressureBoundary()).isFalse();
        assertThat(CutoutKind.DRAIN.isVented()).isTrue();
        assertThat(CutoutKind.WINDOW.isVented()).isFalse();
    }

    @Test
    void aCutoutNeedsANameAndASize() {
        assertThatThrownBy(() -> new Cutout(" ", CutoutKind.DRAIN, at("0", "0"), inch("1"), inch("1"), inch("0.5")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("a cutout has a name");
        assertThatThrownBy(() -> hatch("0", "6.000", "1.000"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("has a size");
    }

    @Test
    void aSquareCornerIsNotACutout() {
        assertThatThrownBy(() -> hatch("8.000", "6.000", "0"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("a square corner is not a cutout");
    }

    @Test
    void aRadiusPastTheMiddleIsRefused() {
        assertThatThrownBy(() -> hatch("8.000", "6.000", "3.001"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("past the middle");
    }

    @Test
    void aRadiusThatMeetsInTheMiddleIsARoundHole() {
        assertThat(hatch("1.000", "1.000", "0.500").isRound()).isTrue();
        assertThat(hatch().isRound()).isFalse();
    }

    @Test
    void theCentreOfAHoleIsInsideIt() {
        assertThat(hatch().contains(at("10.000", "10.000"))).isTrue();
        assertThat(hatch().edgeDistance(at("10.000", "10.000"))).isEqualTo(new Length(0));
    }

    @Test
    void theEdgeOfAHoleCountsAsInsideIt() {
        assertThat(hatch().contains(at("14.000", "10.000"))).isTrue();
        assertThat(hatch().contains(at("10.000", "13.000"))).isTrue();
    }

    @Test
    void justOutsideTheEdgeMeasuresFromTheEdge() {
        assertThat(hatch().edgeDistance(at("15.000", "10.000"))).isEqualTo(inch("1.000"));
        assertThat(hatch().edgeDistance(at("10.000", "14.000"))).isEqualTo(inch("1.000"));
    }

    @Test
    void aPointOffACornerMeasuresRoundTheRadius() {
        assertThat(hatch().edgeDistance(at("14.000", "13.000"))).isEqualTo(new Length(414));
    }

    @Test
    void theKeepOutBandIsTwiceTheRadius() {
        assertThat(hatch().keepOut()).isEqualTo(inch("2.000"));
        assertThat(hatch("8.000", "6.000", "2.000").keepOut()).isEqualTo(inch("4.000"));
    }

    @Test
    void theKeepOutBandNeverFallsBelowItsFloor() {
        Cutout drain = hatch("1.000", "1.000", "0.125", CutoutKind.DRAIN);
        assertThat(drain.keepOut()).isEqualTo(inch("1.000"));
    }

    @Test
    void aPointInsideTheBandIsAssessedUnderTheCutoutRules() {
        assertThat(hatch().inKeepOut(at("15.000", "10.000"))).isTrue();
        assertThat(hatch().inKeepOut(at("16.000", "10.000"))).isTrue();
        assertThat(hatch().inKeepOut(at("17.000", "10.000"))).isFalse();
    }

    @Test
    void aPointInsideTheHoleIsInsideTheBandAsWell() {
        assertThat(hatch().inKeepOut(at("10.000", "10.000"))).isTrue();
    }

    @Test
    void aPointOffAFlatSideIsNotACornerPoint() {
        assertThat(hatch().nearACorner(at("15.000", "10.000"))).isFalse();
        assertThat(hatch().nearACorner(at("10.000", "14.000"))).isFalse();
    }

    @Test
    void aPointOffTheDiagonalIsACornerPoint() {
        assertThat(hatch().nearACorner(at("14.000", "13.000"))).isTrue();
        assertThat(hatch().nearACorner(at("16.000", "15.000"))).isTrue();
    }

    @Test
    void theHoleIsPositionedWhereItWasPutAndNotAtTheOrigin() {
        Cutout moved = new Cutout(
                "191AL", CutoutKind.ACCESS_PANEL, at("40.000", "10.000"), inch("8.000"), inch("6.000"), inch("1.000"));
        assertThat(moved.contains(at("40.000", "10.000"))).isTrue();
        assertThat(moved.contains(at("10.000", "10.000"))).isFalse();
    }

    @Test
    void aCutoutIsFrozenAndHashesByValue() {
        assertThat(new HashSet<>(List.of(hatch(), hatch()))).hasSize(1);
        // Reassigning the corner radius does not compile: every field is final and there is no setter.
        Types.isFrozen(Cutout.class);
    }
}
