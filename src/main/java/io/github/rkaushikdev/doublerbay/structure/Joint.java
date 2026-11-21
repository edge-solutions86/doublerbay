package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Frame;
import io.github.rkaushikdev.doublerbay.grid.JointMember;
import io.github.rkaushikdev.doublerbay.grid.Stringer;
import io.github.rkaushikdev.doublerbay.measure.Length;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * A run of joined skin, following one member, with its fastener rows. This is where the skin is
 * most likely to crack.
 *
 * <p>Two sheets of skin meet either by overlapping, which is a lap joint and runs along a stringer,
 * or by butting up against a strap behind them, which is a butt joint and runs around a frame.
 * Either way there are two or three rows of fasteners holding it, and the row nearest the free edge
 * of the upper sheet is the one that carries the load first and cracks first.
 *
 * <p>That is why the limits at a joint are tighter than the limits in the middle of a bay, and why
 * finding which row a damage is nearest to is a step in its own right. A quarter inch scratch two
 * inches into a bay is nothing. The same scratch through the upper row of a lap joint is a hold,
 * because it is sitting in the place the fleet already cracks without any help.
 */
public record Joint(JointKind kind, JointMember follows, List<FastenerRow> rows) {

    /** A joint whose rows run outwards from its member, no two closer than their sizes allow. */
    public Joint {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(follows, "follows");
        rows = List.copyOf(rows);
        if (kind.isRiveted() && rows.isEmpty()) {
            throw new BadReading("a " + kind + " has fastener rows");
        }
        List<Length> offsets = rows.stream().map(FastenerRow::offset).toList();
        List<Length> sorted = new ArrayList<>(offsets);
        sorted.sort(null);
        if (!offsets.equals(sorted)) {
            throw new BadReading("rows are listed from the member outwards");
        }
        if (new HashSet<>(offsets).size() != offsets.size()) {
            throw new BadReading("two rows cannot sit on the same line");
        }
        if (kind == JointKind.LAP && !(follows instanceof Stringer)) {
            throw new BadReading("a lap joint runs along a stringer");
        }
        if (kind == JointKind.BUTT && !(follows instanceof Frame)) {
            throw new BadReading("a butt joint runs around a frame");
        }
        checkRowSpacing(rows);
    }

    private static void checkRowSpacing(List<FastenerRow> rows) {
        for (int index = 0; index + 1 < rows.size(); index++) {
            FastenerRow near = rows.get(index);
            FastenerRow far = rows.get(index + 1);
            Length spacing = far.offset().minus(near.offset());
            Length required = Length.max(near.size().minRowSpacing(), far.size().minRowSpacing());
            if (spacing.isLessThan(required)) {
                throw new BadReading("rows " + spacing + " apart are inside the " + required + " minimum");
            }
        }
    }

    /** Whether the joint runs along the body, which a joint on a stringer does. */
    public boolean isLongitudinal() {
        return follows instanceof Stringer;
    }

    /** Whether the joint carries load through fasteners in shear. */
    public boolean isFatigueCritical() {
        return kind.isFatigueCritical();
    }

    /** How many rows the joint has. */
    public int rowCount() {
        return rows.size();
    }

    /** From the member out to the last row, which is the band the tighter limits cover. */
    public Length width() {
        if (rows.isEmpty()) {
            return new Length(0);
        }
        return rows.get(rows.size() - 1).offset();
    }

    /** The outermost row, which is the one that carries load first and cracks first. */
    public FastenerRow criticalRow() {
        if (rows.isEmpty()) {
            throw new BadReading("a " + kind + " has no rows to be critical");
        }
        return rows.get(rows.size() - 1);
    }

    /** Whichever row a damage this far off the member is closest to, the inner one on a tie. */
    public FastenerRow rowNearest(Length margin) {
        if (rows.isEmpty()) {
            throw new BadReading("a " + kind + " has no rows to measure to");
        }
        FastenerRow nearest = rows.get(0);
        int best = nearest.offset().minus(margin).abs().mils();
        for (FastenerRow row : rows.subList(1, rows.size())) {
            int gap = row.offset().minus(margin).abs().mils();
            if (gap < best) {
                nearest = row;
                best = gap;
            }
        }
        return nearest;
    }

    /** How far a damage this far off the member is from the row nearest it. */
    public Length distanceToNearestRow(Length margin) {
        return rowNearest(margin).offset().minus(margin).abs();
    }

    /** Whether a point this far off the member is inside the joint band, both edges included. */
    public boolean isWithin(Length margin) {
        return new Length(0).isAtMost(margin) && margin.isAtMost(width());
    }

    /** {@code lap joint on S-14L, 3 rows}. */
    @Override
    public String toString() {
        return kind + " on " + follows + ", " + rowCount() + " rows";
    }
}
