package io.github.rkaushikdev.doublerbay.report;

import io.github.rkaushikdev.doublerbay.util.Literal;

/** Which of the three limits is the one that actually bites. */
public enum Clock {
    CYCLES("flight cycles"),
    HOURS("flight hours"),
    CALENDAR("calendar");

    private final String value;

    Clock(String value) {
        this.value = value;
    }

    /** The words the due list prints. */
    public String value() {
        return value;
    }

    /**
     * The clock the due list names with these words.
     *
     * @throws IllegalArgumentException when no clock is
     */
    public static Clock fromValue(String value) {
        for (Clock clock : values()) {
            if (clock.value.equals(value)) {
                return clock;
            }
        }
        throw new IllegalArgumentException(Literal.of(value) + " is not a valid Clock");
    }

    @Override
    public String toString() {
        return value;
    }
}
