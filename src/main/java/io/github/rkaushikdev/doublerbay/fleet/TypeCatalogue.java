package io.github.rkaushikdev.doublerbay.fleet;

import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.v320.Fuselage;
import io.github.rkaushikdev.doublerbay.v320.Wing;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Which structure definition answers for which tail, keyed on series.
 *
 * <p>The assessor needs four things before it can look at a damage: the aeroplane, what it has flown,
 * the barrel definition for its series, and the wing. Fetching those in four places is how a report
 * ends up describing a short series barrel with a standard series tail number on it, so they come out
 * together or not at all, through {@link #contextFor}.
 *
 * <p>The series difference is real and it is only length. All three share the cross section, the
 * stringer spacing and the gauges, and the short one simply has fewer frames between the wing box and
 * the tail cone. So the catalogue holds a frame count per series rather than three transcriptions of
 * the same manual.
 */
public final class TypeCatalogue {

    /**
     * Frames between the nose bulkhead and the pressure dome, per series. The short one loses eight
     * bays out of the tail cone and nothing else changes.
     */
    public static final Map<Series, Integer> FRAMES_BY_SERIES = framesBySeries();

    private final Map<Series, Integer> frames;

    /** The catalogue the desk holds: every series the fleet flies. */
    public TypeCatalogue() {
        this(Map.of());
    }

    /** A catalogue holding these frame counts; an empty map holds the desk's own. */
    public TypeCatalogue(Map<Series, Integer> framesBySeries) {
        Map<Series, Integer> held = new EnumMap<>(Series.class);
        held.putAll(framesBySeries.isEmpty() ? FRAMES_BY_SERIES : framesBySeries);
        this.frames = held;
    }

    private static Map<Series, Integer> framesBySeries() {
        Map<Series, Integer> held = new EnumMap<>(Series.class);
        held.put(Series.SHORT, 53);
        held.put(Series.STANDARD, Fuselage.FRAME_COUNT);
        held.put(Series.LONG_RANGE, Fuselage.FRAME_COUNT);
        return Collections.unmodifiableMap(held);
    }

    /** Whether there is a structure definition on file for a series. */
    public boolean carries(Series series) {
        return frames.containsKey(series);
    }

    /** Every series there is a structure definition on file for, in dash number order. */
    public List<Series> series() {
        return frames.keySet().stream()
                .sorted(Comparator.comparing(Series::value))
                .toList();
    }

    /** The barrel and wing for a series, the barrel shared with every other definition of its length. */
    public TypeDefinition definitionFor(Series series) {
        return new TypeDefinition(series, Fuselage.barrelFor(framesOn(series)), Wing.wingBox());
    }

    /** How many frames the barrel of a series has. */
    public int framesOn(Series series) {
        Integer held = frames.get(series);
        if (held == null) {
            throw new NotOnFile("no structure definition on file for " + series);
        }
        return held;
    }

    /** How many series are on file. */
    public int size() {
        return frames.size();
    }

    /**
     * The aeroplane, its counters and its structure, resolved together.
     *
     * @throws NotOnFile when the tail is not on the register, was not in service on the day, or flies a
     *     series the catalogue has no definition for
     */
    public static TailContext contextFor(Fleet fleet, TypeCatalogue catalogue, Serial serial, LocalDate on) {
        Aircraft aircraft = fleet.tail(serial);
        if (!aircraft.inServiceOn(on)) {
            throw new NotOnFile(serial + " was not in service on " + on);
        }
        return new TailContext(aircraft, on, fleet.logFor(serial).at(on), catalogue.definitionFor(aircraft.series()));
    }
}
