package io.github.rkaushikdev.doublerbay.measure;

import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * One length over another, carried to four places.
 *
 * <p>A dent is judged on how sharp it is, not only on how deep it is, and sharpness is depth over
 * the diameter it spreads across. A quarter inch dip across six inches of skin is nothing. The same
 * quarter inch across half an inch is a crease and the panel comes off.
 */
public final class Ratio implements Comparable<Ratio> {

    /** The places every ratio is carried to. */
    public static final int PLACES = 4;

    private final BigDecimal value;

    /** A ratio, rounded to four places with a tie going to the even neighbour. */
    public Ratio(BigDecimal value) {
        this.value = Decimals.quantize(Objects.requireNonNull(value, "value"), PLACES);
    }

    /** A ratio written as decimal text. */
    public Ratio(String value) {
        this(Decimals.parse(value));
    }

    /** A whole-number ratio. */
    public Ratio(long value) {
        this(BigDecimal.valueOf(value));
    }

    /**
     * One length over another.
     *
     * @throws ArithmeticException when the denominator is no length at all, which says nothing
     */
    public static Ratio between(Length numerator, Length denominator) {
        if (denominator.mils() == 0) {
            throw new ArithmeticException("a ratio over a zero length says nothing");
        }
        return new Ratio(Decimals.over(BigDecimal.valueOf(numerator.mils()), denominator.mils()));
    }

    /** Depth over the width it spreads across, which is how a dent is graded. */
    public static Ratio sharpness(Length depth, Length across) {
        return between(depth, across);
    }

    /** The ratio as a decimal, to four places. */
    public BigDecimal value() {
        return value;
    }

    /** The ratio as a percentage, to two places. */
    public BigDecimal percent() {
        return Decimals.quantize(value.multiply(BigDecimal.valueOf(100)), 2);
    }

    /** This ratio of a length, rounded to the nearest thousandth. */
    public Length of(Length length) {
        return length.times(value);
    }

    /** Whether this ratio is below another. */
    public boolean isLessThan(Ratio other) {
        return compareTo(other) < 0;
    }

    /** Whether this ratio is no more than another. */
    public boolean isAtMost(Ratio other) {
        return compareTo(other) <= 0;
    }

    /** Whether this ratio is above another. */
    public boolean isGreaterThan(Ratio other) {
        return compareTo(other) > 0;
    }

    /** Whether this ratio is at least another. */
    public boolean isAtLeast(Ratio other) {
        return compareTo(other) >= 0;
    }

    @Override
    public int compareTo(Ratio other) {
        return value.compareTo(other.value);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Ratio ratio && ratio.value.compareTo(value) == 0;
    }

    @Override
    public int hashCode() {
        return value.stripTrailingZeros().hashCode();
    }

    /** The expression that builds this ratio again: {@code new Ratio("0.0200")}. */
    public String repr() {
        return "new Ratio(\"" + value.toPlainString() + "\")";
    }

    /** {@code 2.50%}. */
    @Override
    public String toString() {
        return percent().toPlainString() + "%";
    }
}
