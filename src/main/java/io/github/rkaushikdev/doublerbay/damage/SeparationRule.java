package io.github.rkaushikdev.doublerbay.damage;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * How much clear skin the book asks for between two damages.
 *
 * <p>The multiple is applied to the larger of the two extents and the floor is what a pair of very
 * small damages get instead. Both come off a manual page rather than out of this type, which only
 * knows how to apply them.
 */
public record SeparationRule(BigDecimal multiple, Length floor) {

    /** A rule with a positive multiple and a floor that does not run backwards. */
    public SeparationRule {
        Objects.requireNonNull(multiple, "multiple");
        Objects.requireNonNull(floor, "floor");
        if (multiple.signum() <= 0) {
            throw new BadReading("a separation multiple is a multiple");
        }
        if (floor.mils() < 0) {
            throw new BadReading("a separation floor does not run backwards");
        }
    }

    /** Once the larger extent, never less than {@link Clusters#SEPARATION_FLOOR}. */
    public SeparationRule() {
        this(BigDecimal.ONE, Clusters.SEPARATION_FLOOR);
    }

    /** A multiple of the larger extent, never less than {@link Clusters#SEPARATION_FLOOR}. */
    public SeparationRule(BigDecimal multiple) {
        this(multiple, Clusters.SEPARATION_FLOOR);
    }

    /** The clear separation asked for between two damages of these extents. */
    public Length between(Length first, Length second) {
        return Length.max(Length.max(first, second).times(multiple), floor);
    }

    /** {@code 1 times the larger, floor 1.000}. */
    @Override
    public String toString() {
        return Decimals.text(multiple) + " times the larger, floor "
                + floor.inches().toPlainString();
    }
}
