package io.github.rkaushikdev.doublerbay.grid;

import io.github.rkaushikdev.doublerbay.util.Literal;

/**
 * Which face of the wing box the skin is on.
 *
 * <p>The upper skin spends the flight in compression and the lower skin spends it in tension, and
 * tension is what grows cracks, so the same quarter inch scratch is a monitor on top and a repair
 * underneath.
 */
public enum Surface {
    UPPER("upper"),
    LOWER("lower");

    private final String value;

    Surface(String value) {
        this.value = value;
    }

    /** The surface this word names. */
    public static Surface fromValue(String value) {
        for (Surface surface : values()) {
            if (surface.value.equals(value)) {
                return surface;
            }
        }
        throw new IllegalArgumentException(Literal.of(value) + " is not a valid Surface");
    }

    /** The word, {@code upper} or {@code lower}. */
    public String value() {
        return value;
    }

    /** The lower skin is the one fatigue is written against. */
    public boolean isTensionCritical() {
        return this == LOWER;
    }

    /** {@code lower}. */
    @Override
    public String toString() {
        return value;
    }
}
