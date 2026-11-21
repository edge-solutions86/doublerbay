package io.github.rkaushikdev.doublerbay.damage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.structure.Footprint;
import io.github.rkaushikdev.doublerbay.support.Types;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class DentTest {

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    private static Dent dent(String depth, String along, String around, boolean sharp, boolean cracked) {
        return new Dent(inch(depth), inch(along), inch(around), sharp, cracked);
    }

    private static Dent dent(String depth, String along, String around) {
        return dent(depth, along, around, false, false);
    }

    private static Dent dent(boolean sharp, boolean cracked) {
        return dent("0.030", "3.000", "2.000", sharp, cracked);
    }

    private static Dent dent(String depth) {
        return dent(depth, "3.000", "2.000");
    }

    private static Dent dent() {
        return dent("0.030");
    }

    @Test
    void aDentReadsBackTheThreeNumbers() {
        assertThat(dent()).hasToString("0.030 deep, 3.000 by 2.000");
    }

    @Test
    void theTwoJudgementsShowUpInTheReading() {
        assertThat(dent(true, false).toString()).endsWith(", sharp");
        assertThat(dent(false, true).toString()).endsWith(", cracked");
        assertThat(dent(true, true).toString()).endsWith(", cracked");
    }

    @Test
    void aDentNeedsADepthAndBothDiameters() {
        assertThatThrownBy(() -> dent("0")).isInstanceOf(BadReading.class).hasMessageContaining("a dent has a depth");
        assertThatThrownBy(() -> dent("0.030", "0", "2.000"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("a diameter both ways");
        assertThatThrownBy(() -> dent("0.030", "3.000", "0"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("a diameter both ways");
    }

    @Test
    void aDentDeeperThanItIsWideIsNotADent() {
        assertThatThrownBy(() -> dent("2.000", "3.000", "2.000"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is a puncture, not a dent");
    }

    @Test
    void theWideAndNarrowDiametersComeOutEitherWayRound() {
        assertThat(dent("0.030", "3.000", "2.000").wide()).isEqualTo(inch("3.000"));
        assertThat(dent("0.030", "3.000", "2.000").narrow()).isEqualTo(inch("2.000"));
        assertThat(dent("0.030", "2.000", "3.000").wide()).isEqualTo(inch("3.000"));
        assertThat(dent("0.030", "2.000", "3.000").narrow()).isEqualTo(inch("2.000"));
    }

    @Test
    void theSharpnessIsTakenOnTheNarrowDiameter() {
        assertThat(dent().sharpness().value()).isEqualByComparingTo("0.0150");
    }

    @Test
    void takingTheWideDiameterWouldHaveClearedItAndDoesNot() {
        var narrowGraded = dent("0.030", "12.000", "1.000").sharpness();
        var wideGraded = dent("0.030", "1.000", "1.000").sharpness();
        assertThat(narrowGraded).isEqualTo(wideGraded);
        assertThat(narrowGraded.value()).isEqualByComparingTo("0.0300");
    }

    @Test
    void aDentWithACleanBottomAndNoCrackIsSmooth() {
        assertThat(dent().isSmooth()).isTrue();
        assertThat(dent(true, false).isSmooth()).isFalse();
        assertThat(dent(false, true).isSmooth()).isFalse();
    }

    @Test
    void aDentTwiceAsLongAsItIsWideIsATrough() {
        assertThat(dent("0.030", "4.000", "2.000").isElongated()).isTrue();
        assertThat(dent("0.030", "3.900", "2.000").isElongated()).isFalse();
        assertThat(dent("0.030", "2.000", "4.000").isElongated()).isTrue();
    }

    @Test
    void theFootprintCoversTheSkinTheDentIsOn() {
        Offset centre = new Offset(inch("100.000"), inch("50.000"));
        Footprint covered = dent().footprint(centre);
        assertThat(covered.along()).isEqualTo(inch("3.000"));
        assertThat(covered.around()).isEqualTo(inch("2.000"));
        assertThat(covered.cornerRadius()).isEqualTo(inch("1.000"));
        assertThat(covered.contains(centre)).isTrue();
    }

    @Test
    void twoDentsCompareOnDepth() {
        assertThat(dent("0.040").deeperThan(dent("0.030"))).isTrue();
        assertThat(dent("0.030").deeperThan(dent("0.040"))).isFalse();
    }

    @Test
    void aDentIsFrozenAndHashesByValue() {
        assertThat(new HashSet<>(List.of(dent(), dent()))).hasSize(1);
        // Reassigning a dent's depth does not compile: every field is final and there is no setter.
        Types.isFrozen(Dent.class);
    }
}
