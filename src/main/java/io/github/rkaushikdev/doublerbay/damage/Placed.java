package io.github.rkaushikdev.doublerbay.damage;

import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.structure.Footprint;
import java.util.Objects;

/** A finding with its centre resolved into skin coordinates. */
public record Placed(Finding finding, Offset centre) {

    /** A finding and where its centre lands on the skin. */
    public Placed {
        Objects.requireNonNull(finding, "finding");
        Objects.requireNonNull(centre, "centre");
    }

    /** The skin the damage covers, at the place it landed. */
    public Footprint footprint() {
        return finding.footprint(centre);
    }

    /** The longer side of what it covers, which is what separation is written against. */
    public Length extent() {
        return footprint().longerSide();
    }

    /** {@code DR-2025-000001 at 100.000 along, 50.000 around}. */
    @Override
    public String toString() {
        return finding.reference() + " at " + centre;
    }
}
