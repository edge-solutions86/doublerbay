package io.github.rkaushikdev.doublerbay.assess;

import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.manual.CleanupRule;
import io.github.rkaushikdev.doublerbay.manual.LimitSet;
import io.github.rkaushikdev.doublerbay.measure.Length;
import java.util.Objects;
import java.util.Optional;

/** One damage, where it is, and what the book says about it. */
public record Case(Finding finding, Placement placement, LimitSet limits, CleanupRule cleanup) {

    /** A damage with its placement, its limits and the blend rule for its kind. */
    public Case {
        Objects.requireNonNull(finding, "finding");
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(cleanup, "cleanup");
    }

    /** The metal at the damage. */
    public Length thickness() {
        return placement.thickness();
    }

    /** The depth the limits are read against, for a kind that has one. */
    public Optional<Length> depth() {
        return finding.geometry().limitDepth();
    }

    /** How deep a cleanup would go, which is past the bottom of the damage. */
    public Optional<Length> cutDepth() {
        return depth().map(cleanup::cutDepth);
    }

    /** Whether grinding is on the table at all, given the kind and the region. */
    public boolean canBeBlendedHere() {
        return cleanup.allowedIn(placement.region());
    }
}
