package io.github.rkaushikdev.doublerbay.damage;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.measure.Ratio;
import io.github.rkaushikdev.doublerbay.structure.Footprint;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * A dent, measured the way the manual wants it measured.
 *
 * <p>Three numbers and two questions. The depth is taken from a straight edge laid across the dent,
 * the two diameters are taken along the body and around it, and then somebody has to say whether the
 * bottom of it is smooth and whether there is a crack in it. The last two are not measurements and
 * they are what most dispositions actually turn on, because a smooth shallow dent is very often
 * allowable as it stands and a dent with a sharp bottom never is.
 *
 * <p>The ratio the limit tables are written against is the depth over the smaller of the two
 * diameters, not the larger. A long shallow trough that is narrow across is graded on the narrow
 * direction, because that is where the metal is being worked hardest, and taking the wide direction
 * gives an answer that clears something it should not.
 */
public record Dent(Length depth, Length along, Length around, boolean sharpBottom, boolean cracked)
        implements Geometry {

    private static final BigDecimal HALF = new BigDecimal("0.5");

    /** A dent with a depth, a diameter both ways, and narrower across than it is deep. */
    public Dent {
        Objects.requireNonNull(depth, "depth");
        Objects.requireNonNull(along, "along");
        Objects.requireNonNull(around, "around");
        if (depth.mils() <= 0) {
            throw new BadReading("a dent has a depth");
        }
        if (along.mils() <= 0 || around.mils() <= 0) {
            throw new BadReading("a dent has a diameter both ways");
        }
        if (depth.isAtLeast(Length.min(along, around))) {
            throw new BadReading("a dent deeper than it is wide is a puncture, not a dent");
        }
    }

    /** A dent with a smooth bottom and no crack in it. */
    public Dent(Length depth, Length along, Length around) {
        this(depth, along, around, false, false);
    }

    /** The larger of the two diameters. */
    public Length wide() {
        return Length.max(along, around);
    }

    /** The smaller of the two diameters. */
    public Length narrow() {
        return Length.min(along, around);
    }

    /** Depth over the narrow diameter, which is what the tables are keyed on. */
    public Ratio sharpness() {
        return Ratio.between(depth, narrow());
    }

    /** A dent with a clean bottom and no crack, which is the only kind that can be left. */
    public boolean isSmooth() {
        return !sharpBottom && !cracked;
    }

    /** A dent twice as long as it is wide, which the manual treats as a trough. */
    public boolean isElongated() {
        return wide().isAtLeast(narrow().times(2));
    }

    /** The skin it covers, for working out what it is close to. */
    @Override
    public Footprint footprint(Offset centre) {
        return new Footprint(centre, along, around, narrow().times(HALF));
    }

    /** A dent is graded on how far the metal went in. */
    @Override
    public Optional<Length> limitDepth() {
        return Optional.of(depth);
    }

    /** Whether this dent went further in than another. */
    public boolean deeperThan(Dent other) {
        return depth.isGreaterThan(other.depth);
    }

    /** {@code 0.030 deep, 3.000 by 2.000}, then {@code , cracked} or {@code , sharp}. */
    @Override
    public String toString() {
        String head = depth.inches().toPlainString() + " deep, "
                + along.inches().toPlainString() + " by " + around.inches().toPlainString();
        if (cracked) {
            return head + ", cracked";
        }
        return sharpBottom ? head + ", sharp" : head;
    }
}
