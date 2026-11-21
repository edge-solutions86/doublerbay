package io.github.rkaushikdev.doublerbay.grid;

import io.github.rkaushikdev.doublerbay.util.Literal;

/** Which hand of the aircraft something is on, or the centreline, which belongs to neither. */
public enum Side {
    LEFT("L"),
    RIGHT("R"),
    CENTRE("C");

    private final String value;

    Side(String value) {
        this.value = value;
    }

    /** The side this letter stands for. */
    public static Side fromValue(String value) {
        for (Side side : values()) {
            if (side.value.equals(value)) {
                return side;
            }
        }
        throw new IllegalArgumentException(Literal.of(value) + " is not a valid Side");
    }

    /** The letter, {@code L}. */
    public String value() {
        return value;
    }

    /** {@code L}, {@code R} or {@code C}. */
    @Override
    public String toString() {
        return value;
    }
}
