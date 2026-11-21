package io.github.rkaushikdev.doublerbay.damage;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.measure.Angle;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.measure.Orientation;
import io.github.rkaushikdev.doublerbay.structure.Footprint;
import java.util.Objects;
import java.util.Optional;

/**
 * A panel that has gone out of plane and stayed there.
 *
 * <p>A buckle means the panel has already failed in compression and gone somewhere it does not come
 * back from, so the question is not the buckle but what pushed it. Like a hole it is not graded on a
 * limit table. What is measured is what the repair has to cover, and whether anybody has established
 * that it is permanent rather than a panel oil canning under its own weight on a warm day.
 */
public record Buckle(Length length, Length height, Angle bearing, boolean confirmedPermanent) implements Geometry {

    /** A buckle with a length and a height out of plane. */
    public Buckle {
        Objects.requireNonNull(length, "length");
        Objects.requireNonNull(height, "height");
        Objects.requireNonNull(bearing, "bearing");
        if (length.mils() <= 0) {
            throw new BadReading("a buckle has a length");
        }
        if (height.mils() <= 0) {
            throw new BadReading("a buckle has a height out of plane");
        }
    }

    /** A buckle nobody has yet confirmed as permanent. */
    public Buckle(Length length, Length height, Angle bearing) {
        this(length, height, bearing, false);
    }

    /** Which axis the buckle runs along, or neither. */
    public Orientation orientation() {
        return Angle.orientationOf(bearing);
    }

    /** A buckle running around the barrel is across the compression that made it. */
    public boolean isAcrossTheLoad() {
        return orientation() == Orientation.CIRCUMFERENTIAL;
    }

    /** No depth reads off this, a buckle is out of plane, not into the metal. */
    @Override
    public Optional<Length> limitDepth() {
        return Optional.empty();
    }

    /** Not yet established as permanent, so it may pop back and may be nothing. */
    public boolean isOilCanning() {
        return !confirmedPermanent;
    }

    /** The skin it disturbs: its length one way and never less than half an inch the other. */
    @Override
    public Footprint footprint(Offset centre) {
        Length across = Length.max(height.times(4), new Length(500));
        if (orientation() == Orientation.CIRCUMFERENTIAL) {
            return Footprint.square(centre, across, length);
        }
        return Footprint.square(centre, length, across);
    }

    /** {@code 6.000 long, 0.120 out of plane, not confirmed permanent}. */
    @Override
    public String toString() {
        String head =
                length.inches().toPlainString() + " long, " + height.inches().toPlainString() + " out of plane";
        return confirmedPermanent ? head : head + ", not confirmed permanent";
    }
}
