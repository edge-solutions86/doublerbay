package io.github.rkaushikdev.doublerbay.damage;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.measure.Angle;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.measure.Orientation;
import io.github.rkaushikdev.doublerbay.measure.Taper;
import io.github.rkaushikdev.doublerbay.structure.Footprint;
import java.util.Objects;
import java.util.Optional;

/**
 * Scratches, gouges and nicks, which are the same measurement with different words.
 *
 * <p>All three are metal taken off along a line. What separates them on the report form is how much:
 * a scratch is a mark you can catch a fingernail in, a gouge has displaced metal at its edges, and a
 * nick is short and local, usually where something was dropped on an edge. The manual files all three
 * in one section and gives them one set of limits keyed on depth, so the geometry is one type with the
 * kind carried alongside.
 *
 * <p>The direction matters and it is not intuition. Skin is worked hardest across the hoop direction,
 * so a score lying along the body axis is being pulled open by the larger stress. The same 0.010 deep
 * score is a blend job in one orientation and a hold in the other, and the bearing is what says which.
 */
public record Scoring(Length depth, Length length, Length width, Angle bearing, boolean sharpBottom)
        implements Geometry {

    /** A score with a depth, a length and a width, and no wider than it is long. */
    public Scoring {
        Objects.requireNonNull(depth, "depth");
        Objects.requireNonNull(length, "length");
        Objects.requireNonNull(width, "width");
        Objects.requireNonNull(bearing, "bearing");
        if (depth.mils() <= 0) {
            throw new BadReading("a score has a depth");
        }
        if (length.mils() <= 0) {
            throw new BadReading("a score has a length");
        }
        if (width.mils() <= 0) {
            throw new BadReading("a score has a width");
        }
        if (width.isGreaterThan(length)) {
            throw new BadReading("a score wider than it is long is not a score");
        }
    }

    /** A score with a clean bottom. */
    public Scoring(Length depth, Length length, Length width, Angle bearing) {
        this(depth, length, width, bearing, false);
    }

    /** Which axis the score lies along, or neither. */
    public Orientation orientation() {
        return Angle.orientationOf(bearing);
    }

    /** Lying along the body, so hoop stress is pulling it open. */
    public boolean isHoopLoaded() {
        return orientation() == Orientation.LONGITUDINAL;
    }

    /** Lying around the barrel. */
    public boolean isAcrossTheBarrel() {
        return orientation() == Orientation.CIRCUMFERENTIAL;
    }

    /** How long the cleanup runs once both ends are feathered out. */
    public Length blendLength(Taper taper) {
        return length.plus(taper.runout(depth).times(2));
    }

    /** How wide the cleanup runs once both sides are feathered out. */
    public Length blendWidth(Taper taper) {
        return width.plus(taper.runout(depth).times(2));
    }

    /** The skin the score covers, laid out along and across the body. */
    @Override
    public Footprint footprint(Offset centre) {
        if (isAcrossTheBarrel()) {
            return Footprint.square(centre, width, length);
        }
        return Footprint.square(centre, length, width);
    }

    /** A score is graded on how much metal is gone under it. */
    @Override
    public Optional<Length> limitDepth() {
        return Optional.of(depth);
    }

    /** {@code 0.010 deep, 2.000 long, 0.0 deg, sharp}. */
    @Override
    public String toString() {
        String head =
                depth.inches().toPlainString() + " deep, " + length.inches().toPlainString() + " long, " + bearing;
        return sharpBottom ? head + ", sharp" : head;
    }
}
