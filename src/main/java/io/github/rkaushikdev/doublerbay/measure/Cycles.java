package io.github.rkaushikdev.doublerbay.measure;

import io.github.rkaushikdev.doublerbay.BadReading;

/**
 * A count of pressurisations, which is what grows a crack in a fuselage skin.
 *
 * <p>Cycles and {@link Hours} are never collapsed into one number, and they never mix: a cycle
 * count only adds to, subtracts from and compares with another cycle count.
 */
public final class Cycles implements Comparable<Cycles> {

    private final int count;

    /** A count of flight cycles, never negative. */
    public Cycles(int count) {
        if (count < 0) {
            throw new BadReading("an aircraft cannot fly a negative cycle");
        }
        this.count = count;
    }

    /** The count. */
    public int count() {
        return count;
    }

    /** The two counts together. */
    public Cycles plus(Cycles other) {
        return new Cycles(count + other.count);
    }

    /** This count less another, refused if it would go below none. */
    public Cycles minus(Cycles other) {
        return new Cycles(count - other.count);
    }

    /** Whether this count is below another. */
    public boolean isLessThan(Cycles other) {
        return count < other.count;
    }

    /** Whether this count has reached another. */
    public boolean isAtLeast(Cycles other) {
        return count >= other.count;
    }

    /** Whether this count is past another. */
    public boolean isGreaterThan(Cycles other) {
        return count > other.count;
    }

    /** Whether this count is no more than another. */
    public boolean isAtMost(Cycles other) {
        return count <= other.count;
    }

    @Override
    public int compareTo(Cycles other) {
        return Integer.compare(count, other.count);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Cycles cycles && cycles.count == count;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(count);
    }

    /** The expression that builds this count again: {@code new Cycles(1200)}. */
    public String repr() {
        return "new Cycles(" + count + ")";
    }

    /** {@code 1200 FC}. */
    @Override
    public String toString() {
        return count + " FC";
    }
}
