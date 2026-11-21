package io.github.rkaushikdev.doublerbay.grid;

import io.github.rkaushikdev.doublerbay.util.Literal;

/** The three spanwise beams the wing box is built around, front to rear. */
public enum Spar implements WingMember {
    FRONT("front", 0),
    MID("mid", 1),
    REAR("rear", 2);

    private final String value;
    private final int order;

    Spar(String value, int order) {
        this.value = value;
        this.order = order;
    }

    /** The spar this word names. */
    public static Spar fromValue(String value) {
        for (Spar spar : values()) {
            if (spar.value.equals(value)) {
                return spar;
            }
        }
        throw new IllegalArgumentException(Literal.of(value) + " is not a valid Spar");
    }

    /** The word, {@code front}. */
    public String value() {
        return value;
    }

    /** Where the spar sits counting from the front, front spar first. */
    public int order() {
        return order;
    }

    /** Whether this spar is forward of another. */
    public boolean isForwardOf(Spar other) {
        return order < other.order;
    }

    /** {@code front spar}. */
    @Override
    public String toString() {
        return value + " spar";
    }
}
