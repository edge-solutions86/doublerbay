package io.github.rkaushikdev.doublerbay.grid;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.measure.Length;
import java.util.Objects;

/**
 * A point on a wing surface, as an offset outboard of a rib and aft of a spar:
 * {@code RIB4L 8.000 outbd, front spar 14.000 aft, lower}.
 */
public record WingPoint(Rib rib, Length outboardOfRib, Spar spar, Length aftOfSpar, Surface surface) implements Point {

    /** A point whose offsets are inside any bay on this aircraft, aft of the spar it was taken off. */
    public WingPoint {
        Objects.requireNonNull(rib, "rib");
        Objects.requireNonNull(outboardOfRib, "outboardOfRib");
        Objects.requireNonNull(spar, "spar");
        Objects.requireNonNull(aftOfSpar, "aftOfSpar");
        Objects.requireNonNull(surface, "surface");
        sane(outboardOfRib, "outboard of rib");
        sane(aftOfSpar, "aft of spar");
        if (aftOfSpar.mils() < 0) {
            throw new BadReading("a point aft of a spar is not forward of it");
        }
    }

    private static void sane(Length offset, String what) {
        if (offset.abs().isGreaterThan(SANITY_OFFSET)) {
            throw new BadReading(offset + " " + what + " is further than any bay on this aircraft");
        }
    }

    /** Wing skin is filed under the wings chapter. */
    @Override
    public Chapter chapter() {
        return Chapter.WINGS;
    }

    /** The wing the point is on, which is the rib's. */
    @Override
    public Side side() {
        return rib.side();
    }

    /** Whether the point is on the surface fatigue is written against. */
    public boolean isTensionCritical() {
        return surface.isTensionCritical();
    }

    /** The same offsets off the same members on the other wing. */
    public WingPoint mirrored() {
        return new WingPoint(rib.mirrored(), outboardOfRib, spar, aftOfSpar, surface);
    }

    /** {@code RIB4L 8.000 outbd, front spar 14.000 aft, lower}. */
    @Override
    public String toString() {
        return rib + " " + outboardOfRib.inches().toPlainString() + " outbd, " + spar + " "
                + aftOfSpar.inches().toPlainString() + " aft, " + surface;
    }
}
