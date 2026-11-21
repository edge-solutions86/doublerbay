package io.github.rkaushikdev.doublerbay.assess;

import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.manual.LimitRow;
import java.util.Objects;
import java.util.Optional;

/**
 * One damage assessed, with everything that went into it: the card, where it sits, the answer, and
 * the row the answer was read off, which there is none of when the book ran out.
 */
public record Assessment(Finding finding, Placement placement, Verdict verdict, Optional<LimitRow> row) {

    /** A card, its placement, its verdict and the row that answered. */
    public Assessment {
        Objects.requireNonNull(finding, "finding");
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(row, "row");
    }

    /** An assessment read off a row. */
    public Assessment(Finding finding, Placement placement, Verdict verdict, LimitRow row) {
        this(finding, placement, verdict, Optional.of(row));
    }

    /** What happens to the damage. */
    public Disposition disposition() {
        return verdict.disposition();
    }

    /** Whether the answer puts the aeroplane in a hangar. */
    public boolean needsWork() {
        return verdict.needsWork();
    }

    /** The same card with another verdict on it. */
    public Assessment withVerdict(Verdict replaced) {
        return new Assessment(finding, placement, replaced, row);
    }

    /** {@code DR-2025-000001 on 5310-142L: allowable as it stands per 53-10-01 rev 12: every limit met}. */
    @Override
    public String toString() {
        return finding.reference() + " on " + placement.part() + ": " + verdict;
    }
}
