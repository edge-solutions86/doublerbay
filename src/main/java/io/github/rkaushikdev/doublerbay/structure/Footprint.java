package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.measure.Length;

/**
 * The skin a part covers, and whether two of them are in each other's way.
 *
 * <p>A doubler is not a point, it is a patch with a size, and once one is on the aeroplane the next
 * repair in that area has to clear it. Not touch it, clear it, because two repairs whose fastener
 * rows come within a few diameters of each other transfer load into the same holes and neither one
 * was sized for that.
 *
 * <p>The geometry is the rounded rectangle every other shape on the skin uses. What a footprint adds
 * is the two things a repair drawing asks for: the square patch the shop cuts when the corners do not
 * matter, and the longer side, which is what a size limit is written against.
 */
public final class Footprint extends RoundedRect {

    /** A footprint with a size and a corner radius no further than the middle. */
    public Footprint(Offset centre, Length along, Length around, Length cornerRadius) {
        super(centre, along, around, cornerRadius);
    }

    /** A patch with no corner radius, which is how a small doubler is cut. */
    public static Footprint square(Offset centre, Length along, Length around) {
        return new Footprint(centre, along, around, new Length(0));
    }

    /** The longer side, which a size limit is written against. */
    public Length areaBound() {
        return longerSide();
    }

    /** Distance from a point out to the footprint's edge, zero anywhere inside. */
    public Length distanceTo(Offset point) {
        return edgeDistance(point);
    }

    /** The same footprint with a margin all round it. */
    @Override
    public Footprint grown(Length by) {
        RoundedRect shape = super.grown(by);
        return new Footprint(shape.centre(), shape.along(), shape.around(), shape.cornerRadius());
    }
}
