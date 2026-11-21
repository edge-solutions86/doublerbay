package io.github.rkaushikdev.doublerbay.fleet;

import io.github.rkaushikdev.doublerbay.util.Literal;

/** The three the operator flies. They differ in length and in gross weight. */
public enum Series {
    SHORT("100"),
    STANDARD("200"),
    LONG_RANGE("200LR");

    private final String value;

    Series(String value) {
        this.value = value;
    }

    /** The dash number the type certificate gives the series. */
    public String value() {
        return value;
    }

    /**
     * The series with this dash number.
     *
     * @throws IllegalArgumentException when no series has it
     */
    public static Series fromValue(String value) {
        for (Series series : values()) {
            if (series.value.equals(value)) {
                return series;
            }
        }
        throw new IllegalArgumentException(Literal.of(value) + " is not a valid Series");
    }

    /** Whether this is one of the longer bodies. */
    public boolean isStretched() {
        return this != SHORT;
    }

    /** {@code V320-200}, the way the manual names it. */
    @Override
    public String toString() {
        return "V320-" + value;
    }
}
