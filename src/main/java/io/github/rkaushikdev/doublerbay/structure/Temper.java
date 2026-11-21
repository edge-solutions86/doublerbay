package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.util.Literal;

/** How the sheet was worked and aged. */
public enum Temper {
    ANNEALED("O"),
    T3("T3"),
    T4("T4"),
    T6("T6"),
    T42("T42"),
    T351("T351"),
    T7451("T7451");

    private final String value;

    Temper(String value) {
        this.value = value;
    }

    /** The temper this designation names. */
    public static Temper fromValue(String value) {
        for (Temper temper : values()) {
            if (temper.value.equals(value)) {
                return temper;
            }
        }
        throw new IllegalArgumentException(Literal.of(value) + " is not a valid Temper");
    }

    /** The designation the callout writes, {@code T3} or {@code O}. */
    public String value() {
        return value;
    }

    /** Whether the sheet is annealed. */
    public boolean isAnnealed() {
        return this == ANNEALED;
    }

    /** {@code T3}. */
    @Override
    public String toString() {
        return value;
    }
}
