package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.grid.Rib;
import io.github.rkaushikdev.doublerbay.grid.Side;
import io.github.rkaushikdev.doublerbay.grid.Spar;
import io.github.rkaushikdev.doublerbay.grid.WingBay;
import io.github.rkaushikdev.doublerbay.grid.WingMember;
import io.github.rkaushikdev.doublerbay.grid.WingPoint;
import io.github.rkaushikdev.doublerbay.grid.WingStation;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One wing's ribs, with the spar positions expressed as a fraction of chord. The wing box is the same
 * idea as the barrel with one thing that is not.
 *
 * <p>Ribs take the place of frames and spars take the place of stringers, and a card is written the
 * same way: a member and an offset from it. What breaks the symmetry is that the wing tapers. The
 * chord at the side of body is more than twice the chord at the tip, so the front and rear spars are
 * not a fixed distance apart, and how far aft of the front spar a damage sits means a different
 * fraction of the box depending on how far outboard it is.
 *
 * <p>So the chord is carried per rib and interpolated between them, and the spar positions come out
 * of that rather than out of a constant. Getting that wrong puts a damage in the wrong half of the
 * box, and the two halves have different limits because the rear spar carries the flap loads.
 *
 * @param sparFractions how far aft of the leading edge each spar runs, as a fraction of the local
 *     chord written as decimal text
 */
public record WingGrid(Side side, List<RibStation> ribs, Map<Spar, String> sparFractions) {

    /** A grid of at least two ribs on one wing, climbing outboard, placing the front and rear spars. */
    public WingGrid {
        Objects.requireNonNull(side, "side");
        ribs = List.copyOf(ribs);
        sparFractions = Map.copyOf(sparFractions);
        if (ribs.size() < 2) {
            throw new BadReading("a wing grid needs at least two ribs");
        }
        List<Integer> numbers = ribs.stream().map(entry -> entry.rib().number()).toList();
        List<Integer> spans = ribs.stream().map(entry -> entry.at().at().mils()).toList();
        if (!Ladders.climbs(numbers)) {
            throw new BadReading("ribs are listed inboard to outboard with no repeats");
        }
        if (!Ladders.climbs(spans)) {
            throw new BadReading("rib stations climb outboard with no two together");
        }
        for (RibStation entry : ribs) {
            if (entry.rib().side() != side) {
                throw new BadReading("every rib on a wing grid belongs to that wing");
            }
        }
        for (Spar spar : List.of(Spar.FRONT, Spar.REAR)) {
            if (!sparFractions.containsKey(spar)) {
                throw new BadReading("a wing grid places the " + spar);
            }
        }
    }

    private int[] spanMils() {
        return ribs.stream().mapToInt(entry -> entry.at().at().mils()).toArray();
    }

    /** Where a rib sits on the span. */
    public WingStation stationOf(Rib rib) {
        return entry(rib).at();
    }

    /** The chord at a rib. */
    public Length chordOf(Rib rib) {
        return entry(rib).chord();
    }

    private RibStation entry(Rib rib) {
        for (RibStation entry : ribs) {
            if (entry.rib().equals(rib)) {
                return entry;
            }
        }
        throw new NotOnFile(rib + " is not on this wing grid");
    }

    /** The chord anywhere along the span, straight lined between ribs. */
    public Length chordAt(Length span) {
        int[] spans = spanMils();
        int index = Ladders.countAtOrBelow(spans, span.mils()) - 1;
        if (index < 0 || index >= ribs.size() - 1) {
            if (span.mils() == spans[spans.length - 1]) {
                return ribs.get(ribs.size() - 1).chord();
            }
            throw new BadReading(span.inches().toPlainString() + " is off the ribs this wing carries");
        }
        RibStation inboard = ribs.get(index);
        RibStation outboard = ribs.get(index + 1);
        Length run = outboard.at().at().minus(inboard.at().at());
        Length into = span.minus(inboard.at().at());
        Length drop = inboard.chord().minus(outboard.chord());
        BigDecimal taken = Decimals.times(
                Decimals.over(BigDecimal.valueOf(into.mils()), run.mils()), BigDecimal.valueOf(drop.mils()));
        return new Length(inboard.chord().mils() - Decimals.toInt(taken));
    }

    /** How far aft of the leading edge a spar runs at this point on the span. */
    public Length sparAt(Spar spar, Length span) {
        String fraction = sparFractions.get(spar);
        if (fraction == null) {
            throw new NotOnFile("this wing has no " + spar);
        }
        return chordAt(span).times(Decimals.parse(fraction));
    }

    /** The point as chord aft of the leading edge, along, and span outboard of the centreline, around. */
    public Offset absolute(WingPoint point) {
        Length span = stationOf(point.rib()).at().plus(point.outboardOfRib());
        Length aft = sparAt(point.spar(), span).plus(point.aftOfSpar());
        return new Offset(aft, span);
    }

    private int bracket(Length span) {
        int index = Ladders.countAtOrBelow(spanMils(), span.mils()) - 1;
        if (index < 0 || index >= ribs.size() - 1) {
            throw new BadReading(span.inches().toPlainString() + " is off the ribs this wing carries");
        }
        return index;
    }

    /** Which rib bay a written wing location falls in, front spar to rear spar. */
    public WingBay bayOf(WingPoint point) {
        Offset here = absolute(point);
        int index = bracket(here.around());
        return new WingBay(ribs.get(index).rib(), ribs.get(index + 1).rib(), Spar.FRONT, Spar.REAR, point.surface());
    }

    /** The nearest of the two ribs and two spars bounding the bay, and how far off it is. The first wins a tie. */
    public WingMargin marginOf(WingPoint point) {
        Offset here = absolute(point);
        int index = bracket(here.around());
        RibStation inboard = ribs.get(index);
        RibStation outboard = ribs.get(index + 1);
        Length front = sparAt(Spar.FRONT, here.around());
        Length rear = sparAt(Spar.REAR, here.around());
        int[] distances = {
            here.around().minus(inboard.at().at()).mils(),
            outboard.at().at().minus(here.around()).mils(),
            here.along().minus(front).abs().mils(),
            rear.minus(here.along()).abs().mils()
        };
        WingMember[] members = {inboard.rib(), outboard.rib(), Spar.FRONT, Spar.REAR};
        int nearest = 0;
        for (int candidate = 1; candidate < distances.length; candidate++) {
            if (distances[candidate] < distances[nearest]) {
                nearest = candidate;
            }
        }
        return new WingMargin(members[nearest], new Length(distances[nearest]));
    }
}
