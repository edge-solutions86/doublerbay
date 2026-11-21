package io.github.rkaushikdev.doublerbay.grid;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.measure.Length;
import java.util.Objects;

/**
 * A point on the barrel, as an offset aft of a frame and below a stringer:
 * {@code FR41 12.500 aft, S-14L 3.000 down}.
 */
public record FuselagePoint(Frame frame, Length aftOfFrame, Stringer stringer, Length belowStringer) implements Point {

    /** A point whose offsets are inside any bay on this aircraft. */
    public FuselagePoint {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(aftOfFrame, "aftOfFrame");
        Objects.requireNonNull(stringer, "stringer");
        Objects.requireNonNull(belowStringer, "belowStringer");
        sane(aftOfFrame, "aft of frame");
        sane(belowStringer, "below stringer");
    }

    private static void sane(Length offset, String what) {
        if (offset.abs().isGreaterThan(SANITY_OFFSET)) {
            throw new BadReading(offset + " " + what + " is further than any bay on this aircraft");
        }
    }

    /** Barrel skin is filed under the fuselage chapter. */
    @Override
    public Chapter chapter() {
        return Chapter.FUSELAGE;
    }

    /** The hand the point is on, which is the stringer's. */
    @Override
    public Side side() {
        return stringer.side();
    }

    /** Whether the point sits on its frame. */
    public boolean isOnAFrame() {
        return aftOfFrame.mils() == 0;
    }

    /** Whether the point sits on its stringer. */
    public boolean isOnAStringer() {
        return belowStringer.mils() == 0;
    }

    /** The same offsets off the same members on the other hand. */
    public FuselagePoint mirrored() {
        return new FuselagePoint(frame, aftOfFrame, stringer.mirrored(), belowStringer);
    }

    /** {@code FR41 12.500 aft, S-14L 3.000 down}, or {@code on} for a point on a member. */
    @Override
    public String toString() {
        String aft = written(aftOfFrame, "aft", "fwd");
        String down = written(belowStringer, "down", "up");
        return frame + " " + aft + ", " + stringer + " " + down;
    }

    private static String written(Length offset, String positive, String negative) {
        if (offset.mils() == 0) {
            return "on";
        }
        String word = offset.mils() > 0 ? positive : negative;
        return offset.abs().inches().toPlainString() + " " + word;
    }
}
