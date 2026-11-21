package io.github.rkaushikdev.doublerbay.support;

import io.github.rkaushikdev.doublerbay.fleet.Aircraft;
import io.github.rkaushikdev.doublerbay.fleet.Fleet;
import io.github.rkaushikdev.doublerbay.fleet.Series;
import io.github.rkaushikdev.doublerbay.fleet.Snapshot;
import io.github.rkaushikdev.doublerbay.fleet.UtilisationLog;
import io.github.rkaushikdev.doublerbay.ids.Registration;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import java.time.LocalDate;
import java.util.List;

/**
 * A tail on the real type, its log, and a register holding it, for tests that need an aeroplane
 * rather than a structure.
 *
 * <p>MSN 5127 wearing N412VD, a V320-200 delivered on the twelfth of April 2016, with two readings
 * a year apart: 12000 cycles and 21000 hours at the start of 2024, 13800 and 24200 at the start of
 * 2025.
 */
public final class Tails {

    /** The serial every test tail carries unless it says otherwise. */
    public static final Serial MSN = new Serial("5127");

    /** The day the desk answers as of. */
    public static final LocalDate DAY = LocalDate.of(2025, 3, 14);

    private Tails() {}

    /** A reading of both counters, the hours whole. */
    public static Usage flown(int cycles, long hours) {
        return UtilisationLog.usage(cycles, hours);
    }

    /** A reading of both counters, the hours as decimal text. */
    public static Usage flown(int cycles, String hours) {
        return UtilisationLog.usage(cycles, hours);
    }

    /** MSN 5127, a V320-200. */
    public static Aircraft aircraft() {
        return aircraft("5127");
    }

    /** The test tail under another serial. */
    public static Aircraft aircraft(String serial) {
        return aircraft(serial, Series.STANDARD);
    }

    /** The test tail under another serial and series. */
    public static Aircraft aircraft(String serial, Series series) {
        return new Aircraft(new Serial(serial), new Registration("N412VD"), series, 1140, LocalDate.of(2016, 4, 12));
    }

    /** The two readings on MSN 5127. */
    public static UtilisationLog flyingLog() {
        return flyingLog("5127");
    }

    /** The same two readings, posted against another serial. */
    public static UtilisationLog flyingLog(String serial) {
        return new UtilisationLog(
                new Serial(serial),
                List.of(
                        new Snapshot(LocalDate.of(2024, 1, 1), flown(12000, 21000)),
                        new Snapshot(LocalDate.of(2025, 1, 1), flown(13800, 24200))));
    }

    /** A register holding MSN 5127 and its log. */
    public static Fleet register() {
        return register("5127");
    }

    /** A register holding the test tail under another serial. */
    public static Fleet register(String serial) {
        return register(serial, Series.STANDARD);
    }

    /** A register holding the test tail under another serial and series. */
    public static Fleet register(String serial, Series series) {
        Fleet held = new Fleet();
        held.enrol(aircraft(serial, series), flyingLog(serial));
        return held;
    }
}
