package io.github.rkaushikdev.doublerbay.fleet;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Cycles;
import io.github.rkaushikdev.doublerbay.measure.Hours;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Every posted reading for one tail, oldest first: what it has flown, as the log book records it.
 *
 * <p>Nobody reads a counter continuously. The maintenance system posts a reading every so often,
 * usually at a check and sometimes at a month end, and everything else is worked out between two
 * of those readings. So utilisation is a list of snapshots and not a number, and the answer to how
 * much has this aeroplane flown is always as of a date.
 *
 * <p>Counters never go backwards. A reading lower than the one before it is a transposed digit or a
 * reading posted against the wrong tail, and it gets refused here rather than quietly turning a
 * repair that was due into a repair that is not.
 */
public record UtilisationLog(Serial serial, List<Snapshot> snapshots) {

    /** How far back a rate is taken unless the caller says otherwise, in days. */
    public static final int RATE_WINDOW_DAYS = 365;

    /** A log of at least one reading, posted oldest first, one a day, with neither counter falling. */
    public UtilisationLog {
        Objects.requireNonNull(serial, "serial");
        snapshots = List.copyOf(snapshots);
        if (snapshots.isEmpty()) {
            throw new BadReading(serial + ": a log has at least one reading in it");
        }
        for (int index = 1; index < snapshots.size(); index++) {
            if (snapshots.get(index).taken().isBefore(snapshots.get(index - 1).taken())) {
                throw new BadReading(serial + ": readings are posted oldest first");
            }
        }
        Set<LocalDate> days = new HashSet<>();
        for (Snapshot snapshot : snapshots) {
            days.add(snapshot.taken());
        }
        if (days.size() != snapshots.size()) {
            throw new BadReading(serial + ": two readings posted on the same day");
        }
        for (int index = 1; index < snapshots.size(); index++) {
            Usage earlier = snapshots.get(index - 1).usage();
            Usage later = snapshots.get(index).usage();
            if (later.cycles().isLessThan(earlier.cycles())) {
                throw new BadReading(serial + ": cycles fell between two readings");
            }
            if (later.hours().isLessThan(earlier.hours())) {
                throw new BadReading(serial + ": hours fell between two readings");
            }
        }
    }

    /** A brand new airframe, which has flown nothing. */
    public static UtilisationLog opened(Serial serial, LocalDate on) {
        return new UtilisationLog(serial, List.of(new Snapshot(on, Usage.zero())));
    }

    /** A reading, written the way the maintenance system posts one: cycles and whole hours. */
    public static Usage usage(int cycles, long hours) {
        return new Usage(new Cycles(cycles), Hours.ofHours(hours));
    }

    /** A reading, written the way the maintenance system posts one: cycles and decimal hours. */
    public static Usage usage(int cycles, String hours) {
        return new Usage(new Cycles(cycles), Hours.ofHours(hours));
    }

    /** The day of the first reading. */
    public LocalDate openedOn() {
        return snapshots.get(0).taken();
    }

    /** The most recent reading. */
    public Snapshot latest() {
        return snapshots.get(snapshots.size() - 1);
    }

    /** The log with one more reading on it, the log it came from left as it was. */
    public UtilisationLog posted(Snapshot snapshot) {
        List<Snapshot> more = new ArrayList<>(snapshots);
        more.add(snapshot);
        return new UtilisationLog(serial, more);
    }

    /** What the counters read as of a day, taking the last posting on or before it. */
    public Usage at(LocalDate day) {
        if (day.isBefore(openedOn())) {
            throw new NotOnFile(serial + ": nothing posted on or before " + day);
        }
        Snapshot found = snapshots.get(0);
        for (Snapshot snapshot : snapshots) {
            if (snapshot.taken().isAfter(day)) {
                break;
            }
            found = snapshot;
        }
        return found.usage();
    }

    /** How much was flown across a window, on the postings that bracket it. */
    public Usage flownBetween(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw new BadReading(serial + ": " + end + " is before " + start);
        }
        return at(end).minus(at(start));
    }

    /** Cycles a day and hours a day over the last year of the log. */
    public DailyRate dailyRate() {
        return dailyRate(RATE_WINDOW_DAYS);
    }

    /**
     * Cycles a day and hours a day, taken over the last stretch of the log.
     *
     * <p>The rate runs from the first reading inside the window to the latest one. When nothing but
     * the latest reading is inside the window, it runs over the whole log instead. Two readings are
     * the minimum, and a log with only one is a tail that has not flown yet rather than one flying
     * at zero.
     */
    public DailyRate dailyRate(int over) {
        if (snapshots.size() < 2) {
            throw new NotOnFile(serial + ": one reading is not a rate");
        }
        Snapshot last = latest();
        LocalDate windowStart = last.taken().minusDays(over);
        Snapshot earliest = snapshots.get(0);
        for (Snapshot snapshot : snapshots) {
            if (!snapshot.taken().isBefore(windowStart)) {
                earliest = snapshot;
                break;
            }
        }
        long days = ChronoUnit.DAYS.between(earliest.taken(), last.taken());
        if (days <= 0) {
            earliest = snapshots.get(0);
            days = ChronoUnit.DAYS.between(earliest.taken(), last.taken());
        }
        if (days <= 0) {
            throw new NotOnFile(serial + ": no time between readings to take a rate over");
        }
        Usage flown = last.usage().minus(earliest.usage());
        BigDecimal cycles = Decimals.over(BigDecimal.valueOf(flown.cycles().count()), days);
        BigDecimal hours =
                Decimals.over(Decimals.over(BigDecimal.valueOf(flown.hours().minutes()), Hours.MINUTES_PER_HOUR), days);
        return new DailyRate(Decimals.quantize(cycles, 3), Decimals.quantize(hours, 3));
    }

    /** {@code MSN 5127: 2025-01-01 13800 FC / 24200.0 FH}, the latest reading. */
    @Override
    public String toString() {
        return serial + ": " + latest();
    }
}
