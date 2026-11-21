package io.github.rkaushikdev.doublerbay.grid;

import io.github.rkaushikdev.doublerbay.measure.Length;

/**
 * Where the damage is, written the way the mechanic wrote it: a point on the barrel or on a wing.
 *
 * <p>Nobody measures a body station on an aeroplane. They find the frame with the tape, read the
 * number off the stencil, and measure aft of it. So a location is a reference member and an offset
 * from it, and turning that into an absolute station is a later step that needs the type definition
 * loaded.
 *
 * <p>Keeping the reference member means the record survives a rebuild of the grid. It also means two
 * people who measured from different frames end up with different records of the same point, which
 * is the reason the grid gets asked to normalise a location before anything is compared.
 */
public sealed interface Point permits FuselagePoint, WingPoint {

    /**
     * No frame bay on the V320 is anywhere near this wide, so an offset past it means the wrong
     * reference member was written on the card. The real check happens against the loaded grid,
     * where the actual pitch is known.
     */
    Length SANITY_OFFSET = Length.ofInches("60.000");

    /** The manual chapter the point's structure is filed under. */
    Chapter chapter();

    /** The hand the point is on. */
    Side side();

    /** The chapter a point belongs to. */
    static Chapter chapterOf(Point point) {
        return point.chapter();
    }

    /** Whether two points are on the same hand, whatever kind each one is. */
    static boolean sameSide(Point first, Point second) {
        return first.side() == second.side();
    }
}
