package io.github.rkaushikdev.doublerbay.assess;

import io.github.rkaushikdev.doublerbay.damage.Crack;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.structure.Region;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.math.BigDecimal;
import java.util.Objects;

/** One damage and the feature of the panel it landed on. */
public record RegionCase(Case subject) {

    /** A damage, read for the feature under it. */
    public RegionCase {
        Objects.requireNonNull(subject, "subject");
    }

    /** Where on the panel the damage sits. */
    public Region region() {
        return subject.placement().region();
    }

    /** What is actually under the damage on the thin side of a step, whole mils cut down. */
    public Length thinSide() {
        Length nominal = subject.thickness();
        return new Length(Decimals.times(BigDecimal.valueOf(nominal.mils()), Regions.STEP_SHARE)
                .intValue());
    }

    /** Whether the damage is a crack. */
    public boolean isACrack() {
        return subject.finding().geometry() instanceof Crack;
    }

    /** {@code DR-2025-000001 in open field}. */
    @Override
    public String toString() {
        return subject.finding().reference() + " " + region();
    }
}
