package io.github.rkaushikdev.doublerbay.fleet;

import io.github.rkaushikdev.doublerbay.measure.Usage;
import java.time.LocalDate;
import java.util.Objects;

/** One posted reading of both counters, and the day it was taken. */
public record Snapshot(LocalDate taken, Usage usage) {

    /** A reading on a day. */
    public Snapshot {
        Objects.requireNonNull(taken, "taken");
        Objects.requireNonNull(usage, "usage");
    }

    /** {@code 2025-01-01 13800 FC / 24200.0 FH}. */
    @Override
    public String toString() {
        return taken + " " + usage;
    }
}
