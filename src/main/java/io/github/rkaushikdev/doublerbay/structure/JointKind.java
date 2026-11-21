package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.util.Literal;

/** How two sheets are joined, which decides how the limits are written. */
public enum JointKind {
    LAP("lap"),
    BUTT("butt"),
    SPLICE("splice"),
    BONDED("bonded");

    private final String value;

    JointKind(String value) {
        this.value = value;
    }

    /** The kind this word names. */
    public static JointKind fromValue(String value) {
        for (JointKind kind : values()) {
            if (kind.value.equals(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException(Literal.of(value) + " is not a valid JointKind");
    }

    /** The word, {@code lap}. */
    public String value() {
        return value;
    }

    /** Anything that carries load through fasteners in shear. */
    public boolean isFatigueCritical() {
        return this == LAP || this == SPLICE;
    }

    /** Anything held by fasteners rather than adhesive. */
    public boolean isRiveted() {
        return this != BONDED;
    }

    /** {@code lap joint}. */
    @Override
    public String toString() {
        return value + " joint";
    }
}
