package io.github.rkaushikdev.doublerbay.service;

import io.github.rkaushikdev.doublerbay.assess.Disposition;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import java.util.Objects;

/**
 * One card looked at again, and how the answer moved.
 *
 * @param finding the card as it now stands
 * @param was what the book made of it before
 * @param now what the book makes of it with the new numbers
 * @param stillDeferred whether it is still flying against a deferral
 */
public record ReMeasured(Finding finding, Disposition was, Disposition now, boolean stillDeferred) {

    public ReMeasured {
        Objects.requireNonNull(finding, "finding");
        Objects.requireNonNull(was, "was");
        Objects.requireNonNull(now, "now");
    }

    /** Which look at the damage this was. */
    public int revision() {
        return finding.revision();
    }

    /** Whether the new numbers made the answer worse. */
    public boolean gotWorse() {
        return now.rank() > was.rank();
    }

    /** Whether the card is still flying against a limit it no longer fits. */
    public boolean outgrewItsDeferral() {
        return stillDeferred && gotWorse();
    }

    /** {@code DR-2025-000001 revision 2, allowable as it stands to repair from the manual}. */
    @Override
    public String toString() {
        String moved = gotWorse() ? was.value() + " to " + now.value() : now.value();
        return finding.reference() + " revision " + revision() + ", " + moved;
    }
}
