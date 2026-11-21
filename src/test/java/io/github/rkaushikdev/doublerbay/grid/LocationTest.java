package io.github.rkaushikdev.doublerbay.grid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.support.Types;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocationTest {

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    private static FuselagePoint barrel(String aft, String down, Stringer stringer, int frame) {
        return new FuselagePoint(new Frame(frame), inch(aft), stringer, inch(down));
    }

    private static FuselagePoint barrel(String aft, String down) {
        return barrel(aft, down, new Stringer(14, Side.LEFT), 41);
    }

    private static FuselagePoint barrel(Stringer stringer) {
        return barrel("12.5", "3.0", stringer, 41);
    }

    private static FuselagePoint barrel() {
        return barrel("12.5", "3.0");
    }

    private static WingPoint panel(String outboard, String aft, Surface surface, Rib rib) {
        return new WingPoint(rib, inch(outboard), Spar.FRONT, inch(aft), surface);
    }

    private static WingPoint panel(String outboard, String aft) {
        return panel(outboard, aft, Surface.LOWER, new Rib(4, Side.LEFT));
    }

    private static WingPoint panel(Surface surface) {
        return panel("8.0", "14.0", surface, new Rib(4, Side.LEFT));
    }

    private static WingPoint panel(Rib rib) {
        return panel("8.0", "14.0", Surface.LOWER, rib);
    }

    private static WingPoint panel() {
        return panel("8.0", "14.0");
    }

    @Test
    void aBarrelPointReadsBackTheWayItWasWritten() {
        assertThat(barrel()).hasToString("FR41 12.500 aft, S-14L 3.000 down");
    }

    @Test
    void anOffsetTheOtherWaySaysSo() {
        assertThat(barrel("-6.0", "-2.0")).hasToString("FR41 6.000 fwd, S-14L 2.000 up");
    }

    @Test
    void aPointSittingOnAMemberSaysOn() {
        assertThat(barrel("0", "0")).hasToString("FR41 on, S-14L on");
        assertThat(barrel("0", "0").isOnAFrame()).isTrue();
        assertThat(barrel("0", "0").isOnAStringer()).isTrue();
    }

    @Test
    void aPointOffAMemberIsNotOnIt() {
        assertThat(barrel().isOnAFrame()).isFalse();
        assertThat(barrel().isOnAStringer()).isFalse();
    }

    @Test
    void aBarrelPointTakesItsHandFromTheStringer() {
        assertThat(barrel().side()).isSameAs(Side.LEFT);
        assertThat(barrel(new Stringer(14, Side.RIGHT)).side()).isSameAs(Side.RIGHT);
    }

    @Test
    void aBarrelPointBelongsToTheFuselageChapter() {
        assertThat(barrel().chapter()).isSameAs(Chapter.FUSELAGE);
        assertThat(Point.chapterOf(barrel())).isSameAs(Chapter.FUSELAGE);
    }

    @Test
    void anOffsetPastEveryBayOnTheAircraftIsABadReading() {
        assertThatThrownBy(() -> barrel("61.0", "3.0"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("aft of frame is further than any bay");
        assertThatThrownBy(() -> barrel("12.5", "-61.0"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("below stringer is further than any bay");
    }

    @Test
    void mirroringABarrelPointKeepsTheOffsets() {
        FuselagePoint mirrored = barrel().mirrored();
        assertThat(mirrored.stringer()).isEqualTo(new Stringer(14, Side.RIGHT));
        assertThat(mirrored.aftOfFrame()).isEqualTo(inch("12.5"));
        assertThat(mirrored.belowStringer()).isEqualTo(inch("3.0"));
    }

    @Test
    void aWingPointReadsBackWithItsSurface() {
        assertThat(panel()).hasToString("RIB4L 8.000 outbd, front spar 14.000 aft, lower");
    }

    @Test
    void aWingPointBelongsToTheWingChapter() {
        assertThat(panel().chapter()).isSameAs(Chapter.WINGS);
        assertThat(Point.chapterOf(panel())).isSameAs(Chapter.WINGS);
    }

    @Test
    void aWingPointTakesItsHandFromTheRib() {
        assertThat(panel().side()).isSameAs(Side.LEFT);
        assertThat(panel(new Rib(4, Side.RIGHT)).side()).isSameAs(Side.RIGHT);
    }

    @Test
    void theLowerSurfaceCarriesItsCriticalityOntoThePoint() {
        assertThat(panel(Surface.LOWER).isTensionCritical()).isTrue();
        assertThat(panel(Surface.UPPER).isTensionCritical()).isFalse();
    }

    @Test
    void aPointForwardOfTheSparItWasMeasuredFromIsRefused() {
        assertThatThrownBy(() -> panel("8.0", "-1.0"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("not forward of it");
    }

    @Test
    void aWingOffsetPastEveryBayIsABadReading() {
        assertThatThrownBy(() -> panel("61.0", "14.0"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("outboard of rib is further");
        assertThatThrownBy(() -> panel("8.0", "61.0"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("aft of spar is further");
    }

    @Test
    void mirroringAWingPointCrossesToTheOtherWing() {
        assertThat(panel().mirrored().rib()).isEqualTo(new Rib(4, Side.RIGHT));
        assertThat(panel().mirrored().surface()).isSameAs(Surface.LOWER);
    }

    @Test
    void twoPointsOnTheSameHandAgreeAcrossTheTwoKinds() {
        assertThat(Point.sameSide(barrel(), panel())).isTrue();
        assertThat(Point.sameSide(barrel(), panel(new Rib(4, Side.RIGHT)))).isFalse();
    }

    @Test
    void pointsAreFrozenAndHashByValue() {
        assertThat(new HashSet<>(List.of(barrel(), barrel()))).hasSize(1);
        // Reassigning an offset does not compile: every field is final and there is no setter.
        Types.isFrozen(FuselagePoint.class);
    }
}
