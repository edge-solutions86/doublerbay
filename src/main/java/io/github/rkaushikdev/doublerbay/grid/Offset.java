package io.github.rkaushikdev.doublerbay.grid;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.measure.Angle;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * A separation on the developed skin, split into its two components: along the body and around it.
 *
 * <p>Distance here is measured on the skin, not through it. Two dents four inches apart on a drawing
 * are further apart than that on the aeroplane, because the drawing is flat and the barrel is not.
 * Every separation rule in the manual is written for a tape laid on the skin, so distances are on
 * the developed surface, with the arc taken as the measurement rather than the chord.
 *
 * <p>The straight line between two points on that developed surface is the one the separation rules
 * mean. It is computed in whole mils with an integer square root, which keeps it exact enough to sit
 * on a limit boundary without a floating point value deciding which side of it the damage falls.
 */
public record Offset(Length along, Length around) {

    /** No separation at all. */
    public static final Offset ZERO = new Offset(new Length(0), new Length(0));

    /** Pi to the places the arc arithmetic carries. */
    private static final BigDecimal PI = new BigDecimal("3.141592653589793");

    /** Tenths of a degree in a full turn. */
    private static final int TENTHS_PER_TURN = 3600;

    /** A separation along and around. */
    public Offset {
        Objects.requireNonNull(along, "along");
        Objects.requireNonNull(around, "around");
    }

    /** The third side, to the nearest mil, without going through a floating point value. */
    public static Length hypotenuse(Length first, Length second) {
        long squared = (long) first.mils() * first.mils() + (long) second.mils() * second.mils();
        long root = isqrt(squared);
        // The integer root truncates. Take the nearer of the two candidates so a limit that lands
        // on the boundary is not quietly cleared by a rounding down.
        if ((root + 1) * (root + 1) - squared < squared - root * root) {
            root += 1;
        }
        return new Length(Math.toIntExact(root));
    }

    /** The largest whole number whose square is no more than the value. */
    private static long isqrt(long value) {
        long root = (long) Math.sqrt((double) value);
        while (root * root > value) {
            root -= 1;
        }
        while ((root + 1) * (root + 1) <= value) {
            root += 1;
        }
        return root;
    }

    /** How far round the barrel a given angle takes you at a given radius. */
    public static Length arcLength(Length radius, Angle subtended) {
        if (radius.mils() <= 0) {
            throw new BadReading("an arc needs a radius");
        }
        BigDecimal turn = Decimals.over(BigDecimal.valueOf(subtended.tenths()), TENTHS_PER_TURN);
        BigDecimal circumference = Decimals.times(Decimals.times(BigDecimal.valueOf(radius.mils()), 2), PI);
        return new Length(Decimals.toInt(Decimals.times(circumference, turn)));
    }

    /** The angle an arc of that length covers, which is the inverse of {@link #arcLength}. */
    public static Angle subtendedAngle(Length radius, Length alongArc) {
        if (radius.mils() <= 0) {
            throw new BadReading("an arc needs a radius");
        }
        BigDecimal circumference = Decimals.times(Decimals.times(BigDecimal.valueOf(radius.mils()), 2), PI);
        BigDecimal turn = Decimals.over(BigDecimal.valueOf(alongArc.mils()), circumference);
        return new Angle(Decimals.toInt(Decimals.times(turn, TENTHS_PER_TURN)));
    }

    /** The tape measure answer, which is what a separation limit is written for. */
    public Length direct() {
        return hypotenuse(along.abs(), around.abs());
    }

    /** Whether there is no separation at all. */
    public boolean isZero() {
        return along.mils() == 0 && around.mils() == 0;
    }

    /** Which way the offset points, zero along the body and ninety around it. */
    public Angle bearing() {
        if (isZero()) {
            throw new BadReading("an offset of nothing has no bearing");
        }
        double radians = Math.atan2(around.mils(), along.mils());
        return new Angle((int) Math.rint(Math.toDegrees(radians) * 10));
    }

    /**
     * Whether the direct distance is inside a radius, without taking a root.
     *
     * <p>Comparing squares avoids the rounding in the root entirely, which matters when the radius is
     * a limit and the answer decides a disposition.
     */
    public boolean within(Length radius) {
        long reach = (long) along.mils() * along.mils() + (long) around.mils() * around.mils();
        return reach <= (long) radius.mils() * radius.mils();
    }

    /** Both components scaled, each rounded to the nearest mil. */
    public Offset scaled(BigDecimal factor) {
        return new Offset(along.times(factor), around.times(factor));
    }

    /** Both components scaled by a factor written as decimal text. */
    public Offset scaled(String factor) {
        return scaled(Decimals.parse(factor));
    }

    /** The two separations end to end. */
    public Offset plus(Offset other) {
        return new Offset(along.plus(other.along), around.plus(other.around));
    }

    /** The same separation the other way. */
    public Offset negate() {
        return new Offset(along.negate(), around.negate());
    }

    /** {@code 3.000 along, 4.000 around}. */
    @Override
    public String toString() {
        return along.inches().toPlainString() + " along, " + around.inches().toPlainString() + " around";
    }
}
