package io.github.rkaushikdev.doublerbay.measure;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.math.BigDecimal;

/**
 * Time under load, which is what a wing cares about, kept in whole minutes because the log book
 * is.
 */
public final class Hours implements Comparable<Hours> {

    /** Minutes in an hour. */
    public static final int MINUTES_PER_HOUR = 60;

    private final int minutes;

    /** Flight time of a whole number of minutes, never negative. */
    public Hours(int minutes) {
        if (minutes < 0) {
            throw new BadReading("an aircraft cannot fly a negative hour");
        }
        this.minutes = minutes;
    }

    /** Flight time in decimal hours, refused if it is finer than a minute. */
    public static Hours ofHours(BigDecimal value) {
        BigDecimal scaled = Decimals.times(value, MINUTES_PER_HOUR);
        if (!Decimals.isWhole(scaled)) {
            throw new BadReading(Decimals.text(value) + " h is finer than a minute");
        }
        return new Hours(Decimals.toInt(scaled));
    }

    /** Flight time in decimal hours, as text. */
    public static Hours ofHours(String value) {
        return ofHours(Decimals.parse(value));
    }

    /** A whole number of hours. */
    public static Hours ofHours(long value) {
        return ofHours(BigDecimal.valueOf(value));
    }

    /** The time in minutes. */
    public int minutes() {
        return minutes;
    }

    /** The time in hours, to a tenth. */
    public BigDecimal hours() {
        return Decimals.quantize(Decimals.over(BigDecimal.valueOf(minutes), MINUTES_PER_HOUR), 1);
    }

    /** The two times together. */
    public Hours plus(Hours other) {
        return new Hours(minutes + other.minutes);
    }

    /** This time less another, refused if it would go below none. */
    public Hours minus(Hours other) {
        return new Hours(minutes - other.minutes);
    }

    /** Whether this time is below another. */
    public boolean isLessThan(Hours other) {
        return minutes < other.minutes;
    }

    /** Whether this time has reached another. */
    public boolean isAtLeast(Hours other) {
        return minutes >= other.minutes;
    }

    /** Whether this time is past another. */
    public boolean isGreaterThan(Hours other) {
        return minutes > other.minutes;
    }

    /** Whether this time is no more than another. */
    public boolean isAtMost(Hours other) {
        return minutes <= other.minutes;
    }

    @Override
    public int compareTo(Hours other) {
        return Integer.compare(minutes, other.minutes);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Hours hours && hours.minutes == minutes;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(minutes);
    }

    /** The expression that builds this time again: {@code new Hours(90)}. */
    public String repr() {
        return "new Hours(" + minutes + ")";
    }

    /** {@code 1.5 FH}. */
    @Override
    public String toString() {
        return hours().toPlainString() + " FH";
    }
}
