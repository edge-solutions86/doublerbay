package io.github.rkaushikdev.doublerbay.grid;

import io.github.rkaushikdev.doublerbay.BadReading;
import java.util.Objects;

/**
 * One panel of wing skin, bounded by two ribs and two spars, on one surface.
 *
 * <p>What has no equivalent on the barrel is the surface: the lower skin is in tension in flight and
 * tension is what grows cracks, so a lower bay is the one fatigue is written against.
 */
public record WingBay(Rib inboard, Rib outboard, Spar forward, Spar aft, Surface surface) {

    /** A wing bay on one wing with its bounds the right way round. */
    public WingBay {
        Objects.requireNonNull(inboard, "inboard");
        Objects.requireNonNull(outboard, "outboard");
        Objects.requireNonNull(forward, "forward");
        Objects.requireNonNull(aft, "aft");
        Objects.requireNonNull(surface, "surface");
        if (inboard.side() != outboard.side()) {
            throw new BadReading("a wing bay does not cross the centreline");
        }
        if (!inboard.isInboardOf(outboard)) {
            throw new BadReading(inboard + " is not inboard of " + outboard);
        }
        if (!forward.isForwardOf(aft)) {
            throw new BadReading(forward + " is not forward of " + aft);
        }
    }

    /** The wing the bay is on. */
    public Side side() {
        return inboard.side();
    }

    /** How many rib pitches the bay spans. */
    public int ribPitches() {
        return outboard.number() - inboard.number();
    }

    /** Whether the bay is on the surface fatigue is written against. */
    public boolean isTensionCritical() {
        return surface.isTensionCritical();
    }

    /** {@code RIB4L-RIB5L / front spar to rear spar / lower}. */
    @Override
    public String toString() {
        return inboard + "-" + outboard + " / " + forward + " to " + aft + " / " + surface;
    }
}
