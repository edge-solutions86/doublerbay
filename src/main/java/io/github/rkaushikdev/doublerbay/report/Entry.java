package io.github.rkaushikdev.doublerbay.report;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One thing that happened to one card, and the register it came off.
 *
 * @param on the day it happened
 * @param register the register that holds it, one of {@link History#ORDER}
 * @param what what happened, in words
 */
public record Entry(LocalDate on, String register, String what) {

    public Entry {
        Objects.requireNonNull(on, "on");
        Objects.requireNonNull(register, "register");
        Objects.requireNonNull(what, "what");
    }

    /** Where the register reads among the others on one day; a register not listed reads last. */
    public int rank() {
        int at = History.ORDER.indexOf(register);
        return at >= 0 ? at : History.ORDER.size();
    }

    /** {@code 2025-03-14 card: raised}. */
    @Override
    public String toString() {
        return on + " " + register + ": " + what;
    }
}
