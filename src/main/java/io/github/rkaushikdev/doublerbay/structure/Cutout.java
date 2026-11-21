package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.util.Objects;

/**
 * A hole in the skin, with the band around it that damage has to keep out of.
 *
 * <p>Every cutout is a stress raiser and every rectangular cutout is a stress raiser concentrated
 * into four corners. That is why no window on an aeroplane has a square corner and why the corner
 * radius is on the drawing rather than left to the shop. It is also why damage near a cutout is not
 * assessed with the same numbers as damage in the open field of a bay.
 *
 * <p>The band is measured from the edge of the hole and not from its centre, so a big door and a
 * small drain hole with the same corner radius keep the same distance clear. Inside that band the
 * manual sends most things to a repair that would have been allowable two inches further out. The
 * shape itself is a {@link RoundedRect}; what is here is the part that is about holes rather than
 * about geometry.
 */
public record Cutout(String name, CutoutKind kind, Offset centre, Length along, Length around, Length cornerRadius) {

    /** The keep out band is this many corner radii wide. */
    public static final int KEEP_OUT_FACTOR = 2;

    /** The band is never narrower than an inch, because a drain hole still needs a working margin. */
    public static final Length KEEP_OUT_FLOOR = Length.ofInches("1.000");

    /** A named hole with radiused corners, never a circle when it holds cabin pressure. */
    public Cutout {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(centre, "centre");
        Objects.requireNonNull(along, "along");
        Objects.requireNonNull(around, "around");
        Objects.requireNonNull(cornerRadius, "cornerRadius");
        if (Text.isBlank(name)) {
            throw new BadReading("a cutout has a name");
        }
        if (cornerRadius.mils() <= 0) {
            throw new BadReading(name + ": a square corner is not a cutout");
        }
        RoundedRect shape;
        try {
            shape = new RoundedRect(centre, along, around, cornerRadius);
        } catch (BadReading refusal) {
            throw new BadReading(name + ": " + refusal.getMessage());
        }
        if (shape.isRound() && kind.isPressureBoundary()) {
            throw new BadReading(name + ": a pressure boundary hole is not a circle");
        }
    }

    /** The hole's outline. */
    public RoundedRect shape() {
        return new RoundedRect(centre, along, around, cornerRadius);
    }

    /** Whether the hole is a circle or has slot ends. */
    public boolean isRound() {
        return shape().isRound();
    }

    /** How far clear of the edge damage has to stay: twice the corner radius, never under an inch. */
    public Length keepOut() {
        Length band = cornerRadius.times(KEEP_OUT_FACTOR);
        return Length.max(band, KEEP_OUT_FLOOR);
    }

    /** Distance from a point out to the edge of the hole, zero anywhere inside it. */
    public Length edgeDistance(Offset point) {
        return shape().edgeDistance(point);
    }

    /** Whether a point is inside the hole, its edge included. */
    public boolean contains(Offset point) {
        return shape().contains(point);
    }

    /** Whether a point is off a corner of the hole rather than off a flat side. */
    public boolean nearACorner(Offset point) {
        return shape().offACorner(point);
    }

    /** Whether a point is close enough to be assessed under the cutout rules. */
    public boolean inKeepOut(Offset point) {
        return edgeDistance(point).isAtMost(keepOut());
    }

    /** {@code 191AL (access panel)}. */
    @Override
    public String toString() {
        return name + " (" + kind + ")";
    }
}
