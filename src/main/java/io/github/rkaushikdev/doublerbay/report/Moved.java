package io.github.rkaushikdev.doublerbay.report;

import io.github.rkaushikdev.doublerbay.assess.Disposition;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import java.util.Objects;

/**
 * One card whose answer is different under the new book.
 *
 * @param reference the card
 * @param serial the tail it is on
 * @param was what the book made of it before the revision
 * @param now what the book makes of it after
 */
public record Moved(FindingId reference, Serial serial, Disposition was, Disposition now) {

    public Moved {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(serial, "serial");
        Objects.requireNonNull(was, "was");
        Objects.requireNonNull(now, "now");
    }

    /** Whether the revision made the answer worse. */
    public boolean gotWorse() {
        return now.rank() > was.rank();
    }

    /** Whether the revision made the answer better. */
    public boolean gotBetter() {
        return now.rank() < was.rank();
    }

    /** {@code DR-2025-000001 on MSN 5127: allowable as it stands to repair from the manual (worse)}. */
    @Override
    public String toString() {
        String way = gotWorse() ? "worse" : "better";
        return reference + " on " + serial + ": " + was.value() + " to " + now.value() + " (" + way + ")";
    }
}
