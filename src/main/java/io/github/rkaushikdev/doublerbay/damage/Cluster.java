package io.github.rkaushikdev.doublerbay.damage;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.structure.Footprint;
import java.math.BigDecimal;
import java.util.List;

/**
 * One or more findings that have to be taken together.
 *
 * @see Clusters#clusters(List)
 */
public record Cluster(List<Placed> members) {

    private static final BigDecimal HALF = new BigDecimal("0.5");

    /** A cluster with something in it. */
    public Cluster {
        members = List.copyOf(members);
        if (members.isEmpty()) {
            throw new BadReading("a cluster has something in it");
        }
    }

    /** How many findings are in it. */
    public int size() {
        return members.size();
    }

    /** Whether it is one finding standing on its own. */
    public boolean isSingle() {
        return size() == 1;
    }

    /** The box that covers every member, which is what the limits are read against. */
    public Footprint envelope() {
        List<Footprint> boxes = members.stream().map(Placed::footprint).toList();
        Length firstAlong = null;
        Length lastAlong = null;
        Length firstAround = null;
        Length lastAround = null;
        for (Footprint box : boxes) {
            Length startAlong = box.centre().along().minus(box.along().times(HALF));
            Length endAlong = box.centre().along().plus(box.along().times(HALF));
            Length startAround = box.centre().around().minus(box.around().times(HALF));
            Length endAround = box.centre().around().plus(box.around().times(HALF));
            firstAlong = firstAlong == null ? startAlong : Length.min(firstAlong, startAlong);
            lastAlong = lastAlong == null ? endAlong : Length.max(lastAlong, endAlong);
            firstAround = firstAround == null ? startAround : Length.min(firstAround, startAround);
            lastAround = lastAround == null ? endAround : Length.max(lastAround, endAround);
        }
        Offset centre = new Offset(
                firstAlong.plus(lastAlong.minus(firstAlong).times(HALF)),
                firstAround.plus(lastAround.minus(firstAround).times(HALF)));
        return Footprint.square(centre, lastAlong.minus(firstAlong), lastAround.minus(firstAround));
    }

    /** The report numbers of the members, as printed, in member order. */
    public List<String> references() {
        return members.stream()
                .map(placed -> placed.finding().reference().toString())
                .toList();
    }

    /** The member with the largest extent, which is what governs the cluster; the first of equals. */
    public Placed deepest() {
        Placed largest = members.get(0);
        for (Placed placed : members) {
            if (placed.extent().mils() > largest.extent().mils()) {
                largest = placed;
            }
        }
        return largest;
    }

    /** The one member as it reads, or {@code 2 together, envelope 8.000}. */
    @Override
    public String toString() {
        if (isSingle()) {
            return members.get(0).toString();
        }
        return size() + " together, envelope "
                + envelope().longerSide().inches().toPlainString();
    }
}
