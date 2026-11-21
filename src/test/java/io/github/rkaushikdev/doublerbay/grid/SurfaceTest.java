package io.github.rkaushikdev.doublerbay.grid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.measure.Angle;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.support.Types;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SurfaceTest {

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    private static Offset offset(String along, String around) {
        return new Offset(inch(along), inch(around));
    }

    private static Offset offset() {
        return offset("3.0", "4.0");
    }

    @Test
    void theThirdSideComesOutExactOnAWholeTriangle() {
        assertThat(Offset.hypotenuse(inch("3.0"), inch("4.0"))).isEqualTo(inch("5.0"));
        assertThat(Offset.hypotenuse(inch("0"), inch("4.0"))).isEqualTo(inch("4.0"));
    }

    @Test
    void theThirdSideRoundsToTheNearerMil() {
        assertThat(Offset.hypotenuse(inch("1.0"), inch("1.0"))).isEqualTo(new Length(1414));
    }

    @Test
    void anOffsetReadsAsItsTwoComponents() {
        assertThat(offset()).hasToString("3.000 along, 4.000 around");
        assertThat(offset().direct()).isEqualTo(inch("5.0"));
    }

    @Test
    void aDirectionDoesNotChangeTheDistance() {
        assertThat(offset("-3.0", "-4.0").direct()).isEqualTo(inch("5.0"));
    }

    @Test
    void anOffsetOfNothingIsZeroAndHasNoBearing() {
        assertThat(Offset.ZERO.isZero()).isTrue();
        assertThat(offset("0", "0").isZero()).isTrue();
        assertThatThrownBy(Offset.ZERO::bearing).isInstanceOf(BadReading.class).hasMessageContaining("has no bearing");
    }

    @Test
    void anOffsetAlongTheBodyBearsZeroAndAroundItBearsNinety() {
        assertThat(offset("10.0", "0").bearing()).isEqualTo(Angle.ofDegrees(0));
        assertThat(offset("0", "10.0").bearing()).isEqualTo(Angle.ofDegrees(90));
        assertThat(offset("10.0", "10.0").bearing()).isEqualTo(Angle.ofDegrees(45));
    }

    @Test
    void aBearingTheOtherWayLandsOnTheFarSideOfTheTurn() {
        assertThat(offset("-10.0", "0").bearing()).isEqualTo(Angle.ofDegrees(180));
        assertThat(offset("0", "-10.0").bearing()).isEqualTo(Angle.ofDegrees(270));
    }

    @Test
    void aRadiusCheckAgreesWithTheDistanceItAvoidsComputing() {
        assertThat(offset().within(inch("5.0"))).isTrue();
        assertThat(offset().within(inch("6.0"))).isTrue();
        assertThat(offset().within(inch("4.999"))).isFalse();
    }

    @Test
    void theRadiusCheckAdmitsItsOwnBoundary() {
        assertThat(offset("0", "4.0").within(inch("4.0"))).isTrue();
        assertThat(offset("0", "4.001").within(inch("4.0"))).isFalse();
    }

    @Test
    void offsetsAddAndNegate() {
        assertThat(offset().plus(offset())).isEqualTo(new Offset(inch("6.0"), inch("8.0")));
        assertThat(offset().negate()).isEqualTo(new Offset(inch("-3.0"), inch("-4.0")));
    }

    @Test
    void addingSomethingThatIsNotAnOffsetIsRefused() {
        // Adding a bare length does not compile: plus takes only another offset.
        Types.methodTakesOnly(Offset.class, "plus", Offset.class);
    }

    @Test
    void scalingMovesBothComponents() {
        assertThat(offset().scaled("0.5")).isEqualTo(new Offset(inch("1.5"), inch("2.0")));
        assertThat(offset().scaled(new BigDecimal("2"))).isEqualTo(new Offset(inch("6.0"), inch("8.0")));
    }

    @Test
    void anArcIsLongerThanTheAngleSuggestsOnAWideBarrel() {
        Length quarter = Offset.arcLength(inch("74.0"), Angle.ofDegrees(90));
        assertThat(quarter).isEqualTo(new Length(116239));
    }

    @Test
    void aFullTurnIsTheCircumference() {
        assertThat(Offset.arcLength(inch("100"), Angle.ofDegrees(0))).isEqualTo(new Length(0));
        assertThat(Offset.arcLength(inch("100"), Angle.ofDegrees(180))).isEqualTo(new Length(314159));
    }

    @Test
    void theArcAndTheAngleInvertEachOther() {
        Length radius = inch("74.0");
        Angle back = Offset.subtendedAngle(radius, Offset.arcLength(radius, Angle.ofDegrees(90)));
        assertThat(back).isEqualTo(Angle.ofDegrees(90));
    }

    @Test
    void anArcWithoutARadiusIsRefused() {
        assertThatThrownBy(() -> Offset.arcLength(inch("0"), Angle.ofDegrees(90)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("an arc needs a radius");
        assertThatThrownBy(() -> Offset.subtendedAngle(inch("0"), inch("10")))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("an arc needs a radius");
    }
}
