package io.github.rkaushikdev.doublerbay.assess;

import io.github.rkaushikdev.doublerbay.util.Literal;

/**
 * What happens to the damage, worst last.
 *
 * <p>Four answers. Leave it and record it, grind it out and then leave it, repair it from the book,
 * or the book has run out and this goes to the manufacturer. They are ordered, because a damage
 * assessed against several rules takes the worst answer any of them gave and not the last one that
 * ran.
 */
public enum Disposition {
    ALLOWABLE("allowable as it stands"),
    REWORKABLE("blend out and re inspect"),
    REPAIRABLE("repair from the manual"),
    OUT_OF_LIMITS("outside the manual");

    private final String value;

    Disposition(String value) {
        this.value = value;
    }

    /** The disposition these words name. */
    public static Disposition fromValue(String value) {
        for (Disposition disposition : values()) {
            if (disposition.value.equals(value)) {
                return disposition;
            }
        }
        throw new IllegalArgumentException(Literal.of(value) + " is not a valid Disposition");
    }

    /** The words, {@code allowable as it stands}. */
    public String value() {
        return value;
    }

    /** Where the answer stands in the order, nought for the mildest. */
    public int rank() {
        return ordinal();
    }

    /** Anything that puts the aeroplane in a hangar. */
    public boolean needsWork() {
        return this != ALLOWABLE;
    }

    /** Whether the book has run out and the damage goes to the manufacturer. */
    public boolean needsTheManufacturer() {
        return this == OUT_OF_LIMITS;
    }

    /** Whether this answer is worse than another. */
    public boolean worseThan(Disposition other) {
        return rank() > other.rank();
    }

    /** {@code allowable as it stands}. */
    @Override
    public String toString() {
        return value;
    }
}
