package io.github.rkaushikdev.doublerbay.measure;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.math.BigDecimal;

/**
 * A bearing on the skin surface, to a tenth of a degree, folded into a full turn.
 *
 * <p>Zero runs aft along the body axis and ninety runs around the barrel, which is how the damage
 * report form is laid out. Damage that has a direction has to be reported with one, because on a
 * pressurised barrel the direction is most of the answer: hoop stress runs circumferentially and it
 * is roughly twice the axial stress, so a crack lying along the body axis is being pulled open by
 * the larger of the two. A crack has an axis and not a heading, so its orientation folds onto a half
 * turn before anything is decided from it.
 */
public final class Angle implements Comparable<Angle> {

    /** Tenths in a degree. */
    public static final int TENTHS_PER_DEGREE = 10;

    /** A full turn, in tenths. */
    public static final int FULL_TURN = 360 * TENTHS_PER_DEGREE;

    /** A half turn, in tenths. */
    public static final int HALF_TURN = 180 * TENTHS_PER_DEGREE;

    /**
     * Anything within this of an axis is called out as lying on it. Wider than the report form's
     * own resolution on purpose, because a tape measure held against a curved skin does not do
     * better.
     */
    public static final Angle AXIS_BAND = ofDegrees(15);

    /** Along the body axis. */
    public static final Angle LONGITUDINAL = ofDegrees(0);

    /** Around the barrel. */
    public static final Angle CIRCUMFERENTIAL = ofDegrees(90);

    /** The countersink angle of a standard flush fastener. */
    public static final Angle COUNTERSINK_100 = ofDegrees(100);

    /** The countersink angle of the older flush fasteners. */
    public static final Angle COUNTERSINK_82 = ofDegrees(82);

    private final int tenths;

    /** An angle of a whole number of tenths of a degree, folded into one turn. */
    public Angle(int tenths) {
        this.tenths = Math.floorMod(tenths, FULL_TURN);
    }

    /** An angle in degrees, refused if it is finer than a tenth. */
    public static Angle ofDegrees(BigDecimal value) {
        BigDecimal scaled = Decimals.times(value, TENTHS_PER_DEGREE);
        if (!Decimals.isWhole(scaled)) {
            throw new BadReading(Decimals.text(value) + " deg is finer than a tenth");
        }
        return new Angle(Decimals.toInt(scaled));
    }

    /** An angle in degrees, as text. */
    public static Angle ofDegrees(String value) {
        return ofDegrees(Decimals.parse(value));
    }

    /** A whole number of degrees. */
    public static Angle ofDegrees(long value) {
        return ofDegrees(BigDecimal.valueOf(value));
    }

    /** Which axis a bearing is lying along, or neither. The band edge belongs to the axis. */
    public static Orientation orientationOf(Angle bearing) {
        if (bearing.axisSeparation(LONGITUDINAL).compareTo(AXIS_BAND) <= 0) {
            return Orientation.LONGITUDINAL;
        }
        if (bearing.axisSeparation(CIRCUMFERENTIAL).compareTo(AXIS_BAND) <= 0) {
            return Orientation.CIRCUMFERENTIAL;
        }
        return Orientation.OBLIQUE;
    }

    /** The angle in tenths of a degree, inside one turn. */
    public int tenths() {
        return tenths;
    }

    /** The angle in degrees, to one place. */
    public BigDecimal degrees() {
        return BigDecimal.valueOf(tenths, 1);
    }

    /** The same line, reported on the half turn a crack axis lives on. */
    public Angle folded() {
        return new Angle(tenths % HALF_TURN);
    }

    /** The smaller of the two ways round between two bearings. */
    public Angle separation(Angle other) {
        int gap = Math.abs(tenths - other.tenths);
        return new Angle(Math.min(gap, FULL_TURN - gap));
    }

    /** The same, for two lines that have no direction. */
    public Angle axisSeparation(Angle other) {
        int gap = Math.abs(folded().tenths - other.folded().tenths);
        return new Angle(Math.min(gap, HALF_TURN - gap));
    }

    /** The two turns added, folded back into one turn. */
    public Angle plus(Angle other) {
        return new Angle(tenths + other.tenths);
    }

    /** One turn taken from the other, folded back into one turn. */
    public Angle minus(Angle other) {
        return new Angle(tenths - other.tenths);
    }

    @Override
    public int compareTo(Angle other) {
        return Integer.compare(tenths, other.tenths);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Angle angle && angle.tenths == tenths;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(tenths);
    }

    /** The expression that builds this angle again: {@code Angle.ofDegrees("12.5")}. */
    public String repr() {
        return "Angle.ofDegrees(\"" + degrees().toPlainString() + "\")";
    }

    /** {@code 12.5 deg}. */
    @Override
    public String toString() {
        return degrees().toPlainString() + " deg";
    }
}
