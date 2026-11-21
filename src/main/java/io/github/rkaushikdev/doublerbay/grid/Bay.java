package io.github.rkaushikdev.doublerbay.grid;

import io.github.rkaushikdev.doublerbay.BadReading;
import java.util.Objects;

/**
 * One square of skin, bounded forward and aft by frames and above and below by stringers.
 *
 * <p>Almost every limit in the manual is written per bay, because a bay is what carries the load
 * between its edges and what a crack has to cross before it reaches something that will stop it.
 */
public record Bay(Frame forward, Frame aft, Stringer upper, Stringer lower) {

    /** A bay with its bounds the right way round, on one hand or hanging off the crown. */
    public Bay {
        Objects.requireNonNull(forward, "forward");
        Objects.requireNonNull(aft, "aft");
        Objects.requireNonNull(upper, "upper");
        Objects.requireNonNull(lower, "lower");
        if (forward.isAtLeast(aft)) {
            throw new BadReading(forward + " is not forward of " + aft);
        }
        if (!upper.isAbove(lower)) {
            throw new BadReading(upper + " is not above " + lower);
        }
        if (upper.side() != lower.side() && !upper.isCrown()) {
            throw new BadReading("a bay does not straddle the crown");
        }
    }

    /** The hand the bay is on, which is the lower stringer's. */
    public Side side() {
        return lower.side();
    }

    /** How many frame pitches the bay spans. */
    public int framePitches() {
        return forward.spansTo(aft);
    }

    /** How many stringer pitches the bay spans. */
    public int stringerPitches() {
        return upper.spansTo(lower);
    }

    /** A bay of one pitch each way, which is what a limit is written against. */
    public boolean isSingle() {
        return framePitches() == 1 && stringerPitches() == 1;
    }

    /** {@code FR41-FR42 / S-14L-S-15L}. */
    @Override
    public String toString() {
        return forward + "-" + aft + " / " + upper + "-" + lower;
    }
}
