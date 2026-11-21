package io.github.rkaushikdev.doublerbay.manual;

import io.github.rkaushikdev.doublerbay.util.Literal;

/** Which way a limit moved between two revisions. */
public enum Direction {
    TIGHTER("tighter"),
    LOOSER("looser"),
    SIDEWAYS("sideways");

    private final String value;

    Direction(String value) {
        this.value = value;
    }

    /** The direction this word names. */
    public static Direction fromValue(String value) {
        for (Direction direction : values()) {
            if (direction.value.equals(value)) {
                return direction;
            }
        }
        throw new IllegalArgumentException(Literal.of(value) + " is not a valid Direction");
    }

    /** The word, {@code tighter}. */
    public String value() {
        return value;
    }

    /** Anything not looser puts open findings back on the desk. */
    public boolean needsReview() {
        return this != LOOSER;
    }

    /** {@code tighter}. */
    @Override
    public String toString() {
        return value;
    }
}
