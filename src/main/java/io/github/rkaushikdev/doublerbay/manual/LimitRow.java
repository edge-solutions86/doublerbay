package io.github.rkaushikdev.doublerbay.manual;

import io.github.rkaushikdev.doublerbay.fleet.Effectivity;
import io.github.rkaushikdev.doublerbay.grid.Reference;
import java.util.Objects;

/** One row of one table, with the limits it carries and who it applies to. */
public record LimitRow(LimitKey key, LimitSet limits, Reference reference, Effectivity effectivity) {

    /** A row with its key, its limits, its page and the tails it applies to. */
    public LimitRow {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(effectivity, "effectivity");
    }

    /** A row that applies to every tail. */
    public LimitRow(LimitKey key, LimitSet limits, Reference reference) {
        this(key, limits, reference, Effectivity.allAircraft());
    }

    /** {@code 53-10-03 dent on pressurised skin anywhere: depth 0.010}. */
    @Override
    public String toString() {
        return reference + " " + key + ": " + limits;
    }
}
