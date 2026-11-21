package io.github.rkaushikdev.doublerbay.damage;

import io.github.rkaushikdev.doublerbay.util.Literal;

/**
 * What put the damage there, as far as anyone can tell.
 *
 * <p>A crack found where a bulletin says cracks appear is a fleet problem being managed. The same
 * crack reported the morning after a belt loader hit the aeroplane is an impact and the whole
 * assessment changes, because impact damage does not stop at the surface.
 */
public enum DamageOrigin {
    IMPACT("impact"),
    FATIGUE("fatigue"),
    ENVIRONMENT("environment"),
    HANDLING("ground handling"),
    LIGHTNING("lightning"),
    MANUFACTURING("manufacturing"),
    UNKNOWN("unknown");

    private final String value;

    DamageOrigin(String value) {
        this.value = value;
    }

    /** The word the report form uses. */
    public String value() {
        return value;
    }

    /**
     * The origin the report form names with this word.
     *
     * @throws IllegalArgumentException when no origin is
     */
    public static DamageOrigin fromValue(String value) {
        for (DamageOrigin origin : values()) {
            if (origin.value.equals(value)) {
                return origin;
            }
        }
        throw new IllegalArgumentException(Literal.of(value) + " is not a valid DamageOrigin");
    }

    /** Causes that happened at a moment, which the flight log can be searched for. */
    public boolean isAnEvent() {
        return this == IMPACT || this == HANDLING || this == LIGHTNING;
    }

    /** Causes that get worse on their own between inspections. */
    public boolean isProgressive() {
        return this == FATIGUE || this == ENVIRONMENT;
    }

    @Override
    public String toString() {
        return value;
    }
}
