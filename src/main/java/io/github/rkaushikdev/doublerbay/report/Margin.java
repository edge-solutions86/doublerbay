package io.github.rkaushikdev.doublerbay.report;

import java.util.Objects;

/**
 * How long is left against one limit, in days, and which clock said so.
 *
 * <p>A limit already passed carries a negative margin, because the desk needs the overrun in days
 * rather than a flat "expired".
 */
public record Margin(int days, Clock clock) {

    /** A margin on a clock. */
    public Margin {
        Objects.requireNonNull(clock, "clock");
    }

    /** Whether the limit has already been passed. */
    public boolean isOverrun() {
        return days < 0;
    }

    /** Whether the limit lands today or has already been passed. */
    public boolean isDueNow() {
        return days <= 0;
    }

    /** {@code 10 days on flight cycles}, or {@code 3 days over on calendar}. */
    @Override
    public String toString() {
        if (isOverrun()) {
            return Math.abs(days) + " days over on " + clock;
        }
        return days + " days on " + clock;
    }
}
