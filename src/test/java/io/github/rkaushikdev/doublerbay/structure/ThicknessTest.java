package io.github.rkaushikdev.doublerbay.structure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Bay;
import io.github.rkaushikdev.doublerbay.grid.Frame;
import io.github.rkaushikdev.doublerbay.grid.Side;
import io.github.rkaushikdev.doublerbay.grid.Stringer;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.measure.Taper;
import org.junit.jupiter.api.Test;

class ThicknessTest {

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    private static Bay bay(int forward) {
        return new Bay(
                new Frame(forward), new Frame(forward + 1), new Stringer(12, Side.LEFT), new Stringer(13, Side.LEFT));
    }

    private static Bay bay() {
        return bay(41);
    }

    private static ThicknessMap milled(String land, String pocket, String width, Taper step) {
        return new ThicknessMap(inch(land), inch(pocket), inch(width), step);
    }

    private static ThicknessMap milled(Taper step) {
        return milled("0.063", "0.040", "1.000", step);
    }

    private static ThicknessMap milled() {
        return milled(new Taper(20));
    }

    @Test
    void aMilledPanelReadsAsItsTwoGauges() {
        assertThat(milled()).hasToString("0.063 land / 0.040 pocket");
        assertThat(milled().isFlat()).isFalse();
    }

    @Test
    void anUnmilledPanelIsOneGaugeEverywhere() {
        ThicknessMap flat = ThicknessMap.flat(inch("0.050"));
        assertThat(flat.isFlat()).isTrue();
        assertThat(flat).hasToString("flat 0.050");
        assertThat(flat.at(inch("0"))).isEqualTo(inch("0.050"));
        assertThat(flat.at(inch("10"))).isEqualTo(inch("0.050"));
    }

    @Test
    void theStepRunComesFromTheDepthAndTheTaper() {
        assertThat(milled().stepDepth()).isEqualTo(inch("0.023"));
        assertThat(milled().stepRun()).isEqualTo(inch("0.460"));
        assertThat(milled().pocketStartsAt()).isEqualTo(inch("1.460"));
    }

    @Test
    void aGentlerStepRunsOutFurther() {
        assertThat(milled(new Taper(40)).stepRun()).isEqualTo(inch("0.920"));
    }

    @Test
    void theLandHoldsFullGaugeOutToItsOwnWidth() {
        ThicknessMap panel = milled();
        assertThat(panel.at(inch("0"))).isEqualTo(inch("0.063"));
        assertThat(panel.at(inch("1.000"))).isEqualTo(inch("0.063"));
    }

    @Test
    void thePocketFloorStartsWhereTheTaperEnds() {
        ThicknessMap panel = milled();
        assertThat(panel.at(inch("1.460"))).isEqualTo(inch("0.040"));
        assertThat(panel.at(inch("3.000"))).isEqualTo(inch("0.040"));
    }

    @Test
    void theTaperFallsBetweenTheTwo() {
        ThicknessMap panel = milled();
        Length onTaper = panel.at(inch("1.230"));
        assertThat(inch("0.040").isLessThan(onTaper) && onTaper.isLessThan(inch("0.063")))
                .isTrue();
        assertThat(onTaper).isEqualTo(new Length(51));
    }

    @Test
    void theStepIsWhereTheManualWillNotLetYouBlend() {
        ThicknessMap panel = milled();
        assertThat(panel.onTheStep(inch("1.000"))).isFalse();
        assertThat(panel.onTheStep(inch("1.001"))).isTrue();
        assertThat(panel.onTheStep(inch("1.459"))).isTrue();
        assertThat(panel.onTheStep(inch("1.460"))).isFalse();
    }

    @Test
    void aFlatPanelHasNoStepToBeOn() {
        assertThat(ThicknessMap.flat(inch("0.050")).onTheStep(inch("2.000"))).isFalse();
    }

    @Test
    void aMarginMeasuredTheWrongWayIsRefused() {
        assertThatThrownBy(() -> milled().at(inch("-0.100")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("measured in from a member");
    }

    @Test
    void aPocketThickerThanItsLandIsRefused() {
        assertThatThrownBy(() -> new ThicknessMap(inch("0.040"), inch("0.063"), inch("1.000")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is thicker than land");
    }

    @Test
    void aMapWithNoThicknessIsRefused() {
        assertThatThrownBy(() -> new ThicknessMap(inch("0"), inch("0"), inch("1.000")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("a land has a thickness");
        assertThatThrownBy(() -> new ThicknessMap(inch("0.063"), inch("0"), inch("1.000")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("a pocket has a thickness");
    }

    @Test
    void aPadOverridesTheMillAcrossItsWholeBay() {
        ThicknessMap panel = milled().withPad(new Pad("fitting pad", bay(), inch("0.100")));
        assertThat(panel.at(inch("3.000"), bay())).isEqualTo(inch("0.100"));
        assertThat(panel.at(inch("0"), bay())).isEqualTo(inch("0.100"));
    }

    @Test
    void aPadOnAnotherBayChangesNothingHere() {
        ThicknessMap panel = milled().withPad(new Pad("fitting pad", bay(50), inch("0.100")));
        assertThat(panel.at(inch("3.000"), bay())).isEqualTo(inch("0.040"));
        assertThat(panel.padOn(bay())).isEmpty();
        assertThat(panel.padOn(null)).isEmpty();
    }

    @Test
    void aPadThinnerThanTheLandIsNotAPad() {
        assertThatThrownBy(() -> milled().withPad(new Pad("fitting pad", bay(), inch("0.050"))))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is thinner than the land");
    }

    @Test
    void aPadNeedsANameAndAThickness() {
        assertThatThrownBy(() -> new Pad("  ", bay(), inch("0.100")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("a pad has a name");
        assertThatThrownBy(() -> new Pad("fitting pad", bay(), inch("0")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("a pad has a thickness");
    }

    @Test
    void aPadReadsBackWhereItSits() {
        assertThat(new Pad("fitting pad", bay(), inch("0.100")).toString()).startsWith("fitting pad 0.100 at FR41");
    }

    @Test
    void addingAPadLeavesTheOriginalMapAlone() {
        ThicknessMap panel = milled();
        panel.withPad(new Pad("fitting pad", bay(), inch("0.100")));
        assertThat(panel.pads()).isEmpty();
    }
}
