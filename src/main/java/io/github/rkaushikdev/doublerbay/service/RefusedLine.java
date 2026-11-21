package io.github.rkaushikdev.doublerbay.service;

import java.util.Objects;

/**
 * A line off a counter file that did not land, and what stopped it.
 *
 * @param line the line as it was read
 * @param why the refusal, in the words the register gave it
 */
public record RefusedLine(ReadingLine line, String why) {

    public RefusedLine {
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(why, "why");
    }

    /** {@code line 2, 9999 on 2025-06-01: MSN 9999 has no log on the register}. */
    @Override
    public String toString() {
        return line + ": " + why;
    }
}
