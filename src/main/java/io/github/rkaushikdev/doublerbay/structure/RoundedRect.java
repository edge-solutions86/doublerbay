package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.measure.Length;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * A rectangle with radiused corners, positioned in skin coordinates, which is the shape everything
 * on the skin is.
 *
 * <p>A window is one, a doubler is one, an access hatch is one, and a repair patch is one. They were
 * being drawn twice with the same arithmetic in two places, and the two copies had already started
 * to drift: one of them let a zero corner radius through and the other refused it. That is exactly
 * the sort of difference nobody finds until a limit lands on the boundary.
 *
 * <p>The arithmetic is the standard one. Shrink the rectangle by the corner radius, measure how far
 * outside that smaller box the point is on each axis, take the hypotenuse, and subtract the radius.
 * Inside the box both components come out as zero and the answer is zero, which is what inside is
 * supposed to mean.
 */
public sealed class RoundedRect permits Footprint {

    /** One half, for halving a side. */
    public static final BigDecimal HALF = new BigDecimal("0.5");

    private final Offset centre;
    private final Length along;
    private final Length around;
    private final Length cornerRadius;

    /** A rounded rectangle with a size and a corner radius no further than the middle. */
    public RoundedRect(Offset centre, Length along, Length around, Length cornerRadius) {
        this.centre = Objects.requireNonNull(centre, "centre");
        this.along = Objects.requireNonNull(along, "along");
        this.around = Objects.requireNonNull(around, "around");
        this.cornerRadius = Objects.requireNonNull(cornerRadius, "cornerRadius");
        if (along.mils() <= 0 || around.mils() <= 0) {
            throw new BadReading("a rounded rectangle has a size");
        }
        if (cornerRadius.mils() < 0) {
            throw new BadReading("a corner radius does not run backwards");
        }
        if (cornerRadius.isGreaterThan(Length.min(along, around).times(HALF))) {
            throw new BadReading("a corner radius past the middle is not a rectangle");
        }
    }

    /** Where the shape is centred. */
    public Offset centre() {
        return centre;
    }

    /** The size along the body. */
    public Length along() {
        return along;
    }

    /** The size around the barrel. */
    public Length around() {
        return around;
    }

    /** The radius the corners are struck with. */
    public Length cornerRadius() {
        return cornerRadius;
    }

    /** A radius that has eaten the shorter side leaves a circle or a slot end. */
    public boolean isRound() {
        return cornerRadius.times(2).equals(Length.min(along, around));
    }

    /** The longer of the two sides. */
    public Length longerSide() {
        return Length.max(along, around);
    }

    /** How far the point sits outside the box the corner radii are struck from, on each axis. */
    public Offset outsideBox(Offset point) {
        Length halfAlong = along.times(HALF).minus(cornerRadius);
        Length halfAround = around.times(HALF).minus(cornerRadius);
        return new Offset(
                Length.max(point.along().minus(centre.along()).abs().minus(halfAlong), new Length(0)),
                Length.max(point.around().minus(centre.around()).abs().minus(halfAround), new Length(0)));
    }

    /** Distance out to the nearest edge, zero anywhere inside. */
    public Length edgeDistance(Offset point) {
        Offset out = outsideBox(point);
        Length reach = Offset.hypotenuse(out.along(), out.around());
        if (reach.isAtMost(cornerRadius)) {
            return new Length(0);
        }
        return reach.minus(cornerRadius);
    }

    /** Whether the point is inside the shape, its edge included. */
    public boolean contains(Offset point) {
        return edgeDistance(point).mils() == 0;
    }

    /** Whether the point is off a corner rather than off a flat side. */
    public boolean offACorner(Offset point) {
        Offset out = outsideBox(point);
        return out.along().mils() > 0 && out.around().mils() > 0;
    }

    /** The same shape with a margin all round it, which is how clearance is tested. */
    public RoundedRect grown(Length by) {
        if (by.mils() < 0) {
            throw new BadReading("a rounded rectangle does not grow inwards");
        }
        return new RoundedRect(centre, along.plus(by.times(2)), around.plus(by.times(2)), cornerRadius.plus(by));
    }

    /** The gap along the body between the two enclosing rectangles, zero if they overlap. */
    public Length gapAlong(RoundedRect other) {
        Length overlap = along.plus(other.along).times(HALF);
        Length apart = other.centre.along().minus(centre.along()).abs();
        return Length.max(apart.minus(overlap), new Length(0));
    }

    /** The gap around the barrel between the two enclosing rectangles, zero if they overlap. */
    public Length gapAround(RoundedRect other) {
        Length overlap = around.plus(other.around).times(HALF);
        Length apart = other.centre.around().minus(centre.around()).abs();
        return Length.max(apart.minus(overlap), new Length(0));
    }

    /** Two shapes sharing skin, taken on the enclosing rectangles. */
    public boolean overlaps(RoundedRect other) {
        return gapAlong(other).mils() == 0 && gapAround(other).mils() == 0;
    }

    /** The shortest gap between the two enclosing rectangles, zero if they touch. */
    public Length clearanceTo(RoundedRect other) {
        Length gapAlong = gapAlong(other);
        Length gapAround = gapAround(other);
        if (gapAlong.mils() == 0) {
            return gapAround;
        }
        if (gapAround.mils() == 0) {
            return gapAlong;
        }
        return Offset.hypotenuse(gapAlong, gapAround);
    }

    /** Whether the two shapes are at least a distance apart. */
    public boolean clears(RoundedRect other, Length by) {
        return clearanceTo(other).isAtLeast(by);
    }

    /** Two shapes are equal when they are the same kind of shape at the same place and size. */
    @Override
    public boolean equals(Object other) {
        return other instanceof RoundedRect shape
                && shape.getClass() == getClass()
                && shape.centre.equals(centre)
                && shape.along.equals(along)
                && shape.around.equals(around)
                && shape.cornerRadius.equals(cornerRadius);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass(), centre, along, around, cornerRadius);
    }

    /** {@code 6.000 by 4.000 at 10.000 along, 10.000 around}. */
    @Override
    public String toString() {
        return along.inches().toPlainString() + " by " + around.inches().toPlainString() + " at " + centre;
    }
}
