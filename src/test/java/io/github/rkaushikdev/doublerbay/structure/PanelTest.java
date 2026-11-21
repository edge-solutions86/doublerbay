package io.github.rkaushikdev.doublerbay.structure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Bay;
import io.github.rkaushikdev.doublerbay.grid.Chapter;
import io.github.rkaushikdev.doublerbay.grid.Frame;
import io.github.rkaushikdev.doublerbay.grid.Rib;
import io.github.rkaushikdev.doublerbay.grid.Side;
import io.github.rkaushikdev.doublerbay.grid.Spar;
import io.github.rkaushikdev.doublerbay.grid.Stringer;
import io.github.rkaushikdev.doublerbay.grid.Surface;
import io.github.rkaushikdev.doublerbay.grid.WingBay;
import io.github.rkaushikdev.doublerbay.grid.Zone;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.measure.Length;
import java.util.List;
import org.junit.jupiter.api.Test;

class PanelTest {

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    private static Stringer left(int number) {
        return new Stringer(number, Side.LEFT);
    }

    private static FuselagePanel skin(
            int forward, int aft, Stringer upper, Stringer lower, PanelKind kind, String nominal) {
        return new FuselagePanel(
                new PanelId(53, 10, 142, "L"),
                new Frame(forward),
                new Frame(aft),
                upper,
                lower,
                new Zone(231),
                kind,
                Material.lookup("2024-T3 CLAD"),
                inch(nominal));
    }

    private static FuselagePanel skin(int forward, int aft, Stringer upper, Stringer lower) {
        return skin(forward, aft, upper, lower, PanelKind.PRESSURISED_SKIN, "0.040");
    }

    private static FuselagePanel skin(Stringer upper, Stringer lower) {
        return skin(41, 44, upper, lower);
    }

    private static FuselagePanel skin(PanelKind kind, String nominal) {
        return skin(41, 44, left(12), left(15), kind, nominal);
    }

    private static FuselagePanel skin() {
        return skin(left(12), left(15));
    }

    private static WingPanel wing(int inboard, int outboard, Surface surface, PanelKind kind) {
        return new WingPanel(
                new PanelId(57, 10, 22, "L"),
                new Rib(inboard, Side.LEFT),
                new Rib(outboard, Side.LEFT),
                Spar.FRONT,
                Spar.REAR,
                surface,
                new Zone(531),
                kind,
                Material.lookup("2024-T351"),
                inch("0.160"));
    }

    private static WingPanel wing(Surface surface, PanelKind kind) {
        return wing(4, 7, surface, kind);
    }

    private static WingPanel wing() {
        return wing(Surface.LOWER, PanelKind.WING_LOWER);
    }

    @Test
    void aPanelNamesItselfByPartAndKind() {
        assertThat(skin()).hasToString("5310-142L (pressurised skin)");
        assertThat(skin().chapter()).isSameAs(Chapter.FUSELAGE);
        assertThat(skin().side()).isSameAs(Side.LEFT);
    }

    @Test
    void aPanelCoversAGridOfBays() {
        assertThat(skin().framePitches()).isEqualTo(3);
        assertThat(skin().stringerPitches()).isEqualTo(3);
        assertThat(skin().bayCount()).isEqualTo(9);
        assertThat(skin().bays()).hasSize(9);
    }

    @Test
    void everyBayItYieldsIsASingleBayInsideIt() {
        FuselagePanel panel = skin();
        for (Bay bay : panel.bays()) {
            assertThat(bay.isSingle()).isTrue();
            assertThat(panel.covers(bay)).isTrue();
        }
    }

    @Test
    void theBaysComeOutForwardToAftAndCrownDown() {
        List<Bay> bays = skin().bays();
        assertThat(bays.get(0)).hasToString("FR41-FR42 / S-12L-S-13L");
        assertThat(bays.get(1)).hasToString("FR41-FR42 / S-13L-S-14L");
    }

    @Test
    void aPanelHangingOffTheCrownYieldsTheCrownStringer() {
        FuselagePanel panel = skin(new Stringer(0, Side.CENTRE), left(2));
        assertThat(panel.bays().get(0).upper()).isEqualTo(new Stringer(0));
    }

    @Test
    void aPanelSpansItsOwnBoundariesAndNothingPastThem() {
        FuselagePanel panel = skin();
        assertThat(panel.spansFrame(new Frame(41))).isTrue();
        assertThat(panel.spansFrame(new Frame(44))).isTrue();
        assertThat(panel.spansFrame(new Frame(45))).isFalse();
        assertThat(panel.spansStringer(left(12))).isTrue();
        assertThat(panel.spansStringer(left(16))).isFalse();
    }

    @Test
    void aPanelDoesNotSpanTheOtherHand() {
        assertThat(skin().spansStringer(new Stringer(13, Side.RIGHT))).isFalse();
    }

    @Test
    void aBayOffThePanelIsNotCovered() {
        Bay outside = new Bay(new Frame(45), new Frame(46), left(12), left(13));
        assertThat(skin().covers(outside)).isFalse();
    }

    @Test
    void aPanelNeedsItsBoundsTheRightWayRound() {
        assertThatThrownBy(() -> skin(44, 41, left(12), left(15)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is not forward of");
        assertThatThrownBy(() -> skin(left(15), left(12)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is not above");
    }

    @Test
    void aPanelDoesNotStraddleTheCrown() {
        assertThatThrownBy(() -> skin(left(2), new Stringer(4, Side.RIGHT)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("does not straddle the crown");
    }

    @Test
    void aPanelHasAThickness() {
        assertThatThrownBy(() -> skin(PanelKind.PRESSURISED_SKIN, "0"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("a panel has a thickness");
    }

    @Test
    void aWingKindIsNotAFuselagePanel() {
        assertThatThrownBy(() -> skin(PanelKind.WING_LOWER, "0.040"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is not a fuselage panel");
    }

    @Test
    void thePressurisedKindsKnowTheyAre() {
        assertThat(PanelKind.PRESSURISED_SKIN.isPressurised()).isTrue();
        assertThat(PanelKind.BELLY_SKIN.isPressurised()).isTrue();
        assertThat(PanelKind.WING_LOWER.isPressurised()).isFalse();
        assertThat(PanelKind.FAIRING.isPressurised()).isFalse();
    }

    @Test
    void aFairingIsNotPrimaryStructure() {
        assertThat(PanelKind.PRESSURISED_SKIN.isPrimary()).isTrue();
        assertThat(PanelKind.FAIRING.isPrimary()).isFalse();
    }

    @Test
    void aWingPanelCoversARunOfRibBays() {
        assertThat(wing().ribPitches()).isEqualTo(3);
        assertThat(wing().bayCount()).isEqualTo(3);
        List<WingBay> bays = wing().bays();
        assertThat(bays).hasSize(3);
        assertThat(bays).allMatch(WingBay::isTensionCritical);
    }

    @Test
    void aWingPanelNamesItsSurfaceInItsKind() {
        assertThatThrownBy(() -> wing(Surface.UPPER, PanelKind.WING_LOWER))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is not on the upper surface");
    }

    @Test
    void aFairingSitsOnEitherSurface() {
        assertThat(wing(Surface.LOWER, PanelKind.FAIRING).kind()).isSameAs(PanelKind.FAIRING);
    }

    @Test
    void aWingPanelSpansItsOwnRibsOnItsOwnWing() {
        WingPanel panel = wing();
        assertThat(panel.spansRib(new Rib(4, Side.LEFT))).isTrue();
        assertThat(panel.spansRib(new Rib(7, Side.LEFT))).isTrue();
        assertThat(panel.spansRib(new Rib(8, Side.LEFT))).isFalse();
        assertThat(panel.spansRib(new Rib(5, Side.RIGHT))).isFalse();
    }

    @Test
    void aWingPanelNeedsItsBoundsTheRightWayRound() {
        assertThatThrownBy(() -> wing(7, 4, Surface.LOWER, PanelKind.WING_LOWER))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is not inboard of");
    }
}
