package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.util.Literal;

/** What a hole in the skin is for, which decides whether it is a pressure boundary. */
public enum CutoutKind {
    WINDOW("window"),
    DOOR("door"),
    ACCESS_PANEL("access panel"),
    ANTENNA("antenna"),
    DRAIN("drain"),
    VENT("vent");

    private final String value;

    CutoutKind(String value) {
        this.value = value;
    }

    /** The kind this word names. */
    public static CutoutKind fromValue(String value) {
        for (CutoutKind kind : values()) {
            if (kind.value.equals(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException(Literal.of(value) + " is not a valid CutoutKind");
    }

    /** The words, {@code access panel}. */
    public String value() {
        return value;
    }

    /** A hole that is plugged by something holding cabin pressure. */
    public boolean isPressureBoundary() {
        return this == WINDOW || this == DOOR;
    }

    /** A hole left open to let air or water through. */
    public boolean isVented() {
        return this == DRAIN || this == VENT;
    }

    /** {@code access panel}. */
    @Override
    public String toString() {
        return value;
    }
}
