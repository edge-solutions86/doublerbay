package io.github.rkaushikdev.doublerbay.tolerance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.measure.Length;
import java.util.List;
import org.junit.jupiter.api.Test;

class MethodTest {

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    @Test
    void aMethodNamesItselfTheWayTheCardDoes() {
        assertThat(InspectionMethod.HFEC).hasToString("high frequency eddy current");
        assertThat(InspectionMethod.GVI).hasToString("general visual");
        assertThat(InspectionMethod.fromValue("general visual")).isSameAs(InspectionMethod.GVI);
        assertThatThrownBy(() -> InspectionMethod.fromValue("a quick look"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("'a quick look' is not a valid InspectionMethod");
    }

    @Test
    void theVisualMethodsAreNotNonDestructiveTesting() {
        assertThat(InspectionMethod.GVI.isNdt()).isFalse();
        assertThat(InspectionMethod.DVI.isNdt()).isFalse();
        assertThat(InspectionMethod.HFEC.isNdt()).isTrue();
        assertThat(InspectionMethod.UT.isNdt()).isTrue();
    }

    @Test
    void onlyEddyCurrentNeedsThePaintOff() {
        assertThat(InspectionMethod.HFEC.needsPaintOff()).isTrue();
        assertThat(InspectionMethod.LFEC.needsPaintOff()).isTrue();
        assertThat(InspectionMethod.UT.needsPaintOff()).isFalse();
        assertThat(InspectionMethod.DVI.needsPaintOff()).isFalse();
    }

    @Test
    void theMethodsThatSeeTheFarSideOfTheSkin() {
        assertThat(InspectionMethod.LFEC.reachesTheFarSide()).isTrue();
        assertThat(InspectionMethod.UT.reachesTheFarSide()).isTrue();
        assertThat(InspectionMethod.XRAY.reachesTheFarSide()).isTrue();
        assertThat(InspectionMethod.HFEC.reachesTheFarSide()).isFalse();
        assertThat(InspectionMethod.GVI.reachesTheFarSide()).isFalse();
    }

    @Test
    void aWalkRoundFindsABigCrackAndNotASmallOne() {
        assertThat(InspectionMethod.GVI.canFind(inch("2.000"))).isTrue();
        assertThat(InspectionMethod.GVI.canFind(inch("1.999"))).isFalse();
    }

    @Test
    void eddyCurrentFindsWhatTheEyeNeverWould() {
        assertThat(InspectionMethod.HFEC.canFind(inch("0.050"))).isTrue();
        assertThat(InspectionMethod.DVI.canFind(inch("0.050"))).isFalse();
    }

    @Test
    void nothingHasNoSizeToFind() {
        assertThatThrownBy(() -> InspectionMethod.GVI.canFind(inch("0")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("no size to find");
    }

    @Test
    void theMethodsListInOrderOfCostAndNotOfSensitivity() {
        List<InspectionMethod> ordered = InspectionMethod.byCost();
        assertThat(ordered.get(0)).isSameAs(InspectionMethod.GVI);
        assertThat(ordered.get(ordered.size() - 1)).isSameAs(InspectionMethod.HFEC);
        assertThat(ordered.get(0).findsDownTo())
                .isGreaterThan(ordered.get(ordered.size() - 1).findsDownTo());
    }

    @Test
    void theCheapestMethodThatCanDoTheJobIsTheOneChosen() {
        assertThat(InspectionMethod.cheapestThatFinds(inch("3.000"))).isSameAs(InspectionMethod.GVI);
        assertThat(InspectionMethod.cheapestThatFinds(inch("1.000"))).isSameAs(InspectionMethod.DVI);
        assertThat(InspectionMethod.cheapestThatFinds(inch("0.300"))).isSameAs(InspectionMethod.XRAY);
        assertThat(InspectionMethod.cheapestThatFinds(inch("0.060"))).isSameAs(InspectionMethod.HFEC);
    }

    @Test
    void theBoundaryBelongsToTheCheaperMethod() {
        assertThat(InspectionMethod.cheapestThatFinds(inch("2.000"))).isSameAs(InspectionMethod.GVI);
        assertThat(InspectionMethod.cheapestThatFinds(inch("1.999"))).isSameAs(InspectionMethod.DVI);
    }

    @Test
    void lookingForSomethingOnTheFarSideRulesOutHalfTheMethods() {
        assertThat(InspectionMethod.cheapestThatFinds(inch("3.000"), true)).isSameAs(InspectionMethod.XRAY);
        assertThat(InspectionMethod.cheapestThatFinds(inch("0.150"), true))
                .isIn(InspectionMethod.UT, InspectionMethod.LFEC);
    }

    @Test
    void nothingFindsACrackSmallerThanTheBestMethodCan() {
        assertThatThrownBy(() -> InspectionMethod.cheapestThatFinds(inch("0.010")))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("nothing on file finds");
    }

    @Test
    void nothingOnTheFarSideIsFoundBelowWhatUltrasonicCanDo() {
        assertThatThrownBy(() -> InspectionMethod.cheapestThatFinds(inch("0.060"), true))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("nothing on file finds");
    }

    @Test
    void aMethodThatWouldNotFindItIsNotCredible() {
        assertThat(InspectionMethod.isCredible(InspectionMethod.GVI, inch("3.000")))
                .isTrue();
        assertThat(InspectionMethod.isCredible(InspectionMethod.GVI, inch("0.100")))
                .isFalse();
    }

    @Test
    void aNearSideMethodIsNotCredibleForFarSideDamage() {
        assertThat(InspectionMethod.isCredible(InspectionMethod.HFEC, inch("3.000"), true))
                .isFalse();
        assertThat(InspectionMethod.isCredible(InspectionMethod.UT, inch("3.000"), true))
                .isTrue();
    }

    @Test
    void everyMethodCarriesASizeItFindsDownTo() {
        for (InspectionMethod method : InspectionMethod.values()) {
            assertThat(method.findsDownTo().mils()).isPositive();
        }
    }
}
