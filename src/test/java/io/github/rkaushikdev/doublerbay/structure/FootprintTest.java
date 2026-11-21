package io.github.rkaushikdev.doublerbay.structure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.measure.Length;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FootprintTest {

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    private static Offset at(String along, String around) {
        return new Offset(inch(along), inch(around));
    }

    private static Footprint patch(String along, String around, String radius, Offset centre) {
        return new Footprint(centre, inch(along), inch(around), inch(radius));
    }

    private static Footprint patch(String along, String around, String radius) {
        return patch(along, around, radius, at("10.000", "10.000"));
    }

    private static Footprint patch(Offset centre) {
        return patch("6.000", "4.000", "1.000", centre);
    }

    private static Footprint patch() {
        return patch("6.000", "4.000", "1.000");
    }

    private static InstalledDoubler doubler(String part, String gauge) {
        return new InstalledDoubler(
                part,
                DoublerForm.EXTERNAL,
                patch(),
                Material.lookup("2024-T3 CLAD"),
                inch(gauge),
                LocalDate.of(2023, 6, 14));
    }

    private static InstalledDoubler doubler() {
        return doubler("RP-53-0142-1", "0.050");
    }

    @Test
    void aFootprintReadsAsItsSizeAndPlace() {
        assertThat(patch()).hasToString("6.000 by 4.000 at 10.000 along, 10.000 around");
        assertThat(patch().areaBound()).isEqualTo(inch("6.000"));
    }

    @Test
    void aFootprintNeedsASize() {
        assertThatThrownBy(() -> patch("0", "4.000", "1.000"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("has a size");
    }

    @Test
    void aCornerRadiusPastTheMiddleIsRefused() {
        assertThatThrownBy(() -> patch("6.000", "4.000", "2.001"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("past the middle");
        assertThatThrownBy(() -> patch("6.000", "4.000", "-0.001"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("does not run backwards");
    }

    @Test
    void aSquareFootprintHasNoCornerRadius() {
        Footprint square = Footprint.square(at("0", "0"), inch("6.000"), inch("4.000"));
        assertThat(square.cornerRadius()).isEqualTo(new Length(0));
        assertThat(square.contains(at("3.000", "2.000"))).isTrue();
        assertThat(square.contains(at("3.001", "2.000"))).isFalse();
    }

    @Test
    void aPointInsideThePatchIsContained() {
        assertThat(patch().contains(at("10.000", "10.000"))).isTrue();
        assertThat(patch().contains(at("13.000", "10.000"))).isTrue();
        assertThat(patch().contains(at("13.001", "10.000"))).isFalse();
    }

    @Test
    void aPointOffACornerMeasuresRoundTheRadius() {
        assertThat(patch().distanceTo(at("13.000", "12.000"))).isEqualTo(new Length(414));
        assertThat(patch().distanceTo(at("10.000", "10.000"))).isEqualTo(new Length(0));
    }

    @Test
    void growingAPatchPushesEveryEdgeOut() {
        Footprint grown = patch().grown(inch("0.500"));
        assertThat(grown.along()).isEqualTo(inch("7.000"));
        assertThat(grown.around()).isEqualTo(inch("5.000"));
        assertThat(grown.cornerRadius()).isEqualTo(inch("1.500"));
        assertThat(grown.centre()).isEqualTo(patch().centre());
    }

    @Test
    void aPatchDoesNotGrowInwards() {
        assertThatThrownBy(() -> patch().grown(inch("-0.100")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("does not grow inwards");
    }

    @Test
    void twoPatchesFarApartDoNotOverlap() {
        Footprint near = patch();
        Footprint far = patch(at("30.000", "10.000"));
        assertThat(near.overlaps(far)).isFalse();
        assertThat(near.gapAlong(far)).isEqualTo(inch("14.000"));
        assertThat(near.gapAround(far)).isEqualTo(new Length(0));
    }

    @Test
    void twoPatchesSharingSkinOverlap() {
        Footprint near = patch();
        Footprint onTop = patch(at("12.000", "10.000"));
        assertThat(near.overlaps(onTop)).isTrue();
        assertThat(near.clearanceTo(onTop)).isEqualTo(new Length(0));
    }

    @Test
    void patchesEdgeToEdgeAreTouchingAndNotClear() {
        Footprint near = patch();
        Footprint beside = patch(at("16.000", "10.000"));
        assertThat(near.overlaps(beside)).isTrue();
        assertThat(near.clearanceTo(beside)).isEqualTo(new Length(0));
    }

    @Test
    void theClearanceBetweenTwoPatchesIsTheShortestGap() {
        Footprint near = patch();
        Footprint beside = patch(at("20.000", "10.000"));
        assertThat(near.clearanceTo(beside)).isEqualTo(inch("4.000"));
        assertThat(near.clears(beside, inch("4.000"))).isTrue();
        assertThat(near.clears(beside, inch("4.001"))).isFalse();
    }

    @Test
    void aDiagonalGapTakesBothComponents() {
        Footprint near = patch();
        Footprint offCorner = patch(at("19.000", "17.000"));
        assertThat(near.gapAlong(offCorner)).isEqualTo(inch("3.000"));
        assertThat(near.gapAround(offCorner)).isEqualTo(inch("3.000"));
        assertThat(near.clearanceTo(offCorner)).isEqualTo(new Length(4243));
    }

    @Test
    void aPatchBesideButOverlappingOnOneAxisGapsOnTheOtherOnly() {
        Footprint near = patch();
        Footprint beside = patch(at("20.000", "11.000"));
        assertThat(near.gapAround(beside)).isEqualTo(new Length(0));
        assertThat(near.clearanceTo(beside)).isEqualTo(inch("4.000"));
    }

    @Test
    void theFormsSayWhatTheyDoToTheAirflow() {
        assertThat(DoublerForm.EXTERNAL.isInAirflow()).isTrue();
        assertThat(DoublerForm.INTERNAL.isInAirflow()).isFalse();
        assertThat(DoublerForm.FLUSH.isFlush()).isTrue();
        assertThat(DoublerForm.EXTERNAL).hasToString("external");
    }

    @Test
    void anInstalledDoublerReadsAsItsPartFormAndGauge() {
        assertThat(doubler()).hasToString("RP-53-0142-1 external 0.050");
    }

    @Test
    void anInstalledDoublerNeedsAPartNumberAndAThickness() {
        assertThatThrownBy(() -> doubler("  ", "0.050"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("has a part number");
        assertThatThrownBy(() -> doubler("RP-53-0142-1", "0"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("a doubler has a thickness");
    }
}
