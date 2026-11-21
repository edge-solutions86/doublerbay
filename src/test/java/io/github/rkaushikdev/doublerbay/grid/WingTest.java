package io.github.rkaushikdev.doublerbay.grid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.WrongMeasure;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WingTest {

    private static WingBay wingBay(int inboard, int outboard, Spar forward, Spar aft, Surface surface) {
        return new WingBay(new Rib(inboard, Side.LEFT), new Rib(outboard, Side.LEFT), forward, aft, surface);
    }

    private static WingBay wingBay(Surface surface) {
        return wingBay(4, 5, Spar.FRONT, Spar.REAR, surface);
    }

    private static WingBay wingBay() {
        return wingBay(Surface.LOWER);
    }

    @Test
    void aWingStationReadsWithOrWithoutItsLetter() {
        assertThat(WingStation.parse("WS 240.5").inches()).isEqualByComparingTo("240.500");
        assertThat(WingStation.parse("240.5")).isEqualTo(WingStation.ofInches("240.5"));
        assertThat(WingStation.ofInches("240.5")).hasToString("WS 240.500");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "STA 240", "outboard", "2.4.0"})
    void aWingStationRefusesWhatIsNotOne(String text) {
        assertThatThrownBy(() -> WingStation.parse(text))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not a wing station");
    }

    @Test
    void aWingStationIsNotABodyStation() {
        assertThat((Axis) WingStation.ofInches("240")).isNotEqualTo(Station.ofInches("240"));
        assertThatThrownBy(() -> WingStation.ofInches("240").isLessThan(Station.ofInches("240")))
                .isInstanceOf(WrongMeasure.class)
                .hasMessageContaining("different rulers");
    }

    @Test
    void theLowerSurfaceIsTheOneFatigueIsWrittenAgainst() {
        assertThat(Surface.LOWER.isTensionCritical()).isTrue();
        assertThat(Surface.UPPER.isTensionCritical()).isFalse();
        assertThat(Surface.LOWER).hasToString("lower");
    }

    @Test
    void theSparsRunFrontToRear() {
        assertThat(Spar.FRONT.isForwardOf(Spar.MID)).isTrue();
        assertThat(Spar.MID.isForwardOf(Spar.REAR)).isTrue();
        assertThat(Spar.REAR.isForwardOf(Spar.FRONT)).isFalse();
        assertThat(Spar.FRONT).hasToString("front spar");
    }

    @Test
    void aRibCarriesANumberAndAWing() {
        assertThat(new Rib(12, Side.LEFT)).hasToString("RIB12L");
        assertThat(Rib.parse("RIB12L")).isEqualTo(new Rib(12, Side.LEFT));
        assertThat(Rib.parse("rib-12r")).isEqualTo(new Rib(12, Side.RIGHT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "RIB12", "12L", "RIB100L", "RIBL"})
    void theRibParserRefusesAnythingElse(String text) {
        assertThatThrownBy(() -> Rib.parse(text)).isInstanceOf(BadReading.class).hasMessageContaining("not a rib");
    }

    @Test
    void aRibBelongsToOneWing() {
        assertThatThrownBy(() -> new Rib(12, Side.CENTRE))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("one wing or the other");
    }

    @Test
    void aRibNumberOffTheWingIsRefused() {
        assertThatThrownBy(() -> new Rib(0, Side.LEFT))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not on this wing");
        assertThatThrownBy(() -> new Rib(41, Side.LEFT))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not on this wing");
    }

    @Test
    void ribsRunOutboardByNumberWithinOneWing() {
        assertThat(new Rib(4, Side.LEFT).isInboardOf(new Rib(12, Side.LEFT))).isTrue();
        assertThatThrownBy(() -> new Rib(4, Side.LEFT).isInboardOf(new Rib(12, Side.RIGHT)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("opposite wings do not compare");
    }

    @Test
    void mirroringARibSwapsTheWing() {
        assertThat(new Rib(12, Side.LEFT).mirrored()).isEqualTo(new Rib(12, Side.RIGHT));
    }

    @Test
    void ribsSortByWingThenNumber() {
        List<Rib> ordered =
                new ArrayList<>(List.of(new Rib(12, Side.RIGHT), new Rib(4, Side.LEFT), new Rib(12, Side.LEFT)));
        ordered.sort(null);
        assertThat(ordered.stream().map(Rib::toString).toList()).containsExactly("RIB4L", "RIB12L", "RIB12R");
    }

    @Test
    void aWingBayIsTwoRibsTwoSparsAndASurface() {
        assertThat(wingBay()).hasToString("RIB4L-RIB5L / front spar to rear spar / lower");
        assertThat(wingBay().side()).isSameAs(Side.LEFT);
        assertThat(wingBay().ribPitches()).isEqualTo(1);
    }

    @Test
    void aLowerWingBayIsTensionCriticalAndAnUpperOneIsNot() {
        assertThat(wingBay(Surface.LOWER).isTensionCritical()).isTrue();
        assertThat(wingBay(Surface.UPPER).isTensionCritical()).isFalse();
    }

    @Test
    void aWingBayNeedsItsBoundsTheRightWayRound() {
        assertThatThrownBy(() -> wingBay(5, 4, Spar.FRONT, Spar.REAR, Surface.LOWER))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is not inboard of");
        assertThatThrownBy(() -> wingBay(4, 5, Spar.REAR, Spar.FRONT, Surface.LOWER))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is not forward of");
    }

    @Test
    void aWingBayDoesNotCrossTheCentreline() {
        assertThatThrownBy(() -> new WingBay(
                        new Rib(4, Side.LEFT), new Rib(5, Side.RIGHT), Spar.FRONT, Spar.REAR, Surface.LOWER))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("does not cross the centreline");
    }
}
