package io.github.rkaushikdev.doublerbay.damage;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.structure.Footprint;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * A hole or a puncture, which is the same thing measured two ways.
 *
 * <p>Holes, buckles and disbonds are the three that end the conversation early, and none of them is
 * graded on a limit table. A hole has no allowable size because there is nothing left to carry load
 * through, so what is measured is what the repair has to cover.
 *
 * @see Buckle
 * @see Disbond
 */
public record Opening(Length along, Length around, boolean tornEdges, boolean wasAFastenerHole) implements Geometry {

    private static final BigDecimal HALF = new BigDecimal("0.5");

    /** An opening with a size both ways. */
    public Opening {
        Objects.requireNonNull(along, "along");
        Objects.requireNonNull(around, "around");
        if (along.mils() <= 0 || around.mils() <= 0) {
            throw new BadReading("an opening has a size both ways");
        }
    }

    /** A clean edged opening in open skin. */
    public Opening(Length along, Length around) {
        this(along, around, false, false);
    }

    /** The larger of the two sizes. */
    public Length wide() {
        return Length.max(along, around);
    }

    /** The smaller of the two sizes. */
    public Length narrow() {
        return Length.min(along, around);
    }

    /** Whether it measures the same both ways. */
    public boolean isRound() {
        return along.equals(around);
    }

    /** A torn edge is a row of crack starters and gets cut back to clean metal. */
    public boolean needsTrimmingBeforeRepair() {
        return tornEdges;
    }

    /** No depth reads off this, an opening has gone all the way through. */
    @Override
    public Optional<Length> limitDepth() {
        return Optional.empty();
    }

    /** The opening once the torn edges have been cut out, which is what gets covered. */
    public Opening trimmed(Length backBy) {
        if (backBy.mils() < 0) {
            throw new BadReading("trimming does not make an opening smaller");
        }
        return new Opening(along.plus(backBy.times(2)), around.plus(backBy.times(2)), false, wasAFastenerHole);
    }

    @Override
    public Footprint footprint(Offset centre) {
        return new Footprint(centre, along, around, narrow().times(HALF));
    }

    /** {@code 0.500 by 0.500, worked fastener hole, torn}. */
    @Override
    public String toString() {
        String head = along.inches().toPlainString() + " by " + around.inches().toPlainString();
        if (wasAFastenerHole) {
            head = head + ", worked fastener hole";
        }
        return tornEdges ? head + ", torn" : head;
    }
}
