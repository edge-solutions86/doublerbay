package io.github.rkaushikdev.doublerbay.service;

import java.util.Objects;

/**
 * One line off a counter file, as it was read, every field still text.
 *
 * @param at the line's place in the file, the header being line one
 * @param serial the serial column
 * @param taken the day the counters were read
 * @param cycles the cycles column
 * @param hours the hours column
 */
public record ReadingLine(int at, String serial, String taken, String cycles, String hours) {

    public ReadingLine {
        Objects.requireNonNull(serial, "serial");
        Objects.requireNonNull(taken, "taken");
        Objects.requireNonNull(cycles, "cycles");
        Objects.requireNonNull(hours, "hours");
    }

    /** {@code line 2, 5127 on 2025-06-01}. */
    @Override
    public String toString() {
        return "line " + at + ", " + serial + " on " + taken;
    }
}
