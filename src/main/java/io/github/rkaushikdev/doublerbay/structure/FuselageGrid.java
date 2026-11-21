package io.github.rkaushikdev.doublerbay.structure;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.grid.Bay;
import io.github.rkaushikdev.doublerbay.grid.Frame;
import io.github.rkaushikdev.doublerbay.grid.FuselagePoint;
import io.github.rkaushikdev.doublerbay.grid.JointMember;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.grid.Side;
import io.github.rkaushikdev.doublerbay.grid.Station;
import io.github.rkaushikdev.doublerbay.grid.Stringer;
import io.github.rkaushikdev.doublerbay.measure.Length;
import java.util.List;

/**
 * The barrel's two ladders, in order: frames against stations and stringers against arc from the
 * crown. This is what turns a written location into a place.
 *
 * <p>A damage card says FR41 plus twelve and a half aft. Nothing can be decided from that until
 * somebody knows that FR41 is at station 620 and that the next frame back is at station 640, because
 * the twelve and a half is either most of the way across the bay or a third of it depending on the
 * pitch, and the margin to the nearest member is what every limit table is keyed on.
 *
 * <p>So the grid answers three questions: where a point is in absolute terms, which bay it falls in,
 * and how far it is from the nearest member bounding that bay. Everything downstream is built on
 * those three.
 */
public record FuselageGrid(List<FrameStation> frames, List<StringerLine> stringers) {

    /** Two ladders of at least two rungs each, each climbing with no repeats. */
    public FuselageGrid {
        frames = List.copyOf(frames);
        stringers = List.copyOf(stringers);
        if (frames.size() < 2) {
            throw new BadReading("a grid needs at least two frames");
        }
        if (stringers.size() < 2) {
            throw new BadReading("a grid needs at least two stringers");
        }
        List<Integer> numbers =
                frames.stream().map(entry -> entry.frame().number()).toList();
        List<Integer> stations =
                frames.stream().map(entry -> entry.station().at().mils()).toList();
        if (!Ladders.climbs(numbers)) {
            throw new BadReading("frames are listed forward to aft with no repeats");
        }
        if (!Ladders.climbs(stations)) {
            throw new BadReading("frame stations climb aft with no two on the same station");
        }
        List<Integer> rungs =
                stringers.stream().map(entry -> entry.stringer().number()).toList();
        List<Integer> arcs =
                stringers.stream().map(entry -> entry.around().mils()).toList();
        if (!Ladders.climbs(rungs)) {
            throw new BadReading("stringers are listed crown down with no repeats");
        }
        if (!Ladders.climbs(arcs)) {
            throw new BadReading("stringer arcs climb from the crown with no two together");
        }
    }

    private int[] stationMils() {
        return frames.stream().mapToInt(entry -> entry.station().at().mils()).toArray();
    }

    private int[] arcMils() {
        return stringers.stream().mapToInt(entry -> entry.around().mils()).toArray();
    }

    /** The station a frame sits at. */
    public Station stationOf(Frame frame) {
        for (FrameStation entry : frames) {
            if (entry.frame().equals(frame)) {
                return entry.station();
            }
        }
        throw new NotOnFile(frame + " is not on this grid");
    }

    /** How far round from the crown a stringer runs, the same on either hand. */
    public Length aroundOf(Stringer stringer) {
        for (StringerLine entry : stringers) {
            if (entry.stringer().number() == stringer.number()) {
                return entry.around();
            }
        }
        throw new NotOnFile(stringer + " is not on this grid");
    }

    /** The gap from a frame back to the next frame aft. */
    public Length framePitchAftOf(Frame frame) {
        for (int index = 0; index + 1 < frames.size(); index++) {
            FrameStation near = frames.get(index);
            if (near.frame().equals(frame)) {
                return frames.get(index + 1).station().at().minus(near.station().at());
            }
        }
        throw new NotOnFile(frame + " has no frame aft of it on this grid");
    }

    /** The gap from a stringer down to the next stringer below. */
    public Length stringerPitchBelow(Stringer stringer) {
        for (int index = 0; index + 1 < stringers.size(); index++) {
            StringerLine upper = stringers.get(index);
            if (upper.stringer().number() == stringer.number()) {
                return stringers.get(index + 1).around().minus(upper.around());
            }
        }
        throw new NotOnFile(stringer + " has no stringer below it on this grid");
    }

    /** The point in barrel coordinates, aft of the datum and round from the crown. */
    public Offset absolute(FuselagePoint point) {
        Length along = stationOf(point.frame()).at().plus(point.aftOfFrame());
        Length around = aroundOf(point.stringer()).plus(point.belowStringer());
        return new Offset(along, around);
    }

    private int bracketFrames(Length along) {
        int index = Ladders.countAtOrBelow(stationMils(), along.mils()) - 1;
        if (index < 0 || index >= frames.size() - 1) {
            throw new BadReading(along.inches().toPlainString() + " is off the frames this grid carries");
        }
        return index;
    }

    private int bracketStringers(Length around) {
        int index = Ladders.countAtOrBelow(arcMils(), around.mils()) - 1;
        if (index < 0 || index >= stringers.size() - 1) {
            throw new BadReading(around.inches().toPlainString() + " is off the stringers this grid carries");
        }
        return index;
    }

    /** Which single bay a written location actually falls in. A point on a member is in the bay aft of and below it. */
    public Bay bayOf(FuselagePoint point) {
        Offset here = absolute(point);
        int frame = bracketFrames(here.along());
        int stringer = bracketStringers(here.around());
        Side side = point.side() != Side.CENTRE ? point.side() : Side.LEFT;
        return new Bay(
                frames.get(frame).frame(),
                frames.get(frame + 1).frame(),
                hand(stringers.get(stringer).stringer(), side),
                hand(stringers.get(stringer + 1).stringer(), side));
    }

    private static Stringer hand(Stringer stringer, Side side) {
        if (stringer.isCrown()) {
            return stringer;
        }
        return new Stringer(stringer.number(), side);
    }

    /** The nearest of the four members bounding the bay, and how far off it is. The first wins a tie. */
    public Margin marginOf(FuselagePoint point) {
        Offset here = absolute(point);
        int frame = bracketFrames(here.along());
        int stringer = bracketStringers(here.around());
        FrameStation forward = frames.get(frame);
        FrameStation aft = frames.get(frame + 1);
        StringerLine upper = stringers.get(stringer);
        StringerLine lower = stringers.get(stringer + 1);
        Side side = point.side() != Side.CENTRE ? point.side() : Side.LEFT;
        int[] distances = {
            here.along().minus(forward.station().at()).mils(),
            aft.station().at().minus(here.along()).mils(),
            here.around().minus(upper.around()).mils(),
            lower.around().minus(here.around()).mils()
        };
        JointMember[] members = {
            forward.frame(), aft.frame(), hand(upper.stringer(), side), hand(lower.stringer(), side)
        };
        int nearest = 0;
        for (int index = 1; index < distances.length; index++) {
            if (distances[index] < distances[nearest]) {
                nearest = index;
            }
        }
        return new Margin(members[nearest], new Length(distances[nearest]));
    }

    /**
     * The same place, rewritten off the frame and stringer that actually bound it.
     *
     * <p>Two people measuring the same dent from different members write different cards. This is
     * what makes those two cards compare equal.
     */
    public FuselagePoint normalised(FuselagePoint point) {
        Bay bay = bayOf(point);
        Offset here = absolute(point);
        return new FuselagePoint(
                bay.forward(),
                here.along().minus(stationOf(bay.forward()).at()),
                bay.upper(),
                here.around().minus(aroundOf(bay.upper())));
    }
}
