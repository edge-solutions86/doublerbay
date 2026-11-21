package io.github.rkaushikdev.doublerbay.fleet;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** One check on one tail, with the counters as they read at the time. */
public record CheckEvent(Serial serial, CheckKind kind, LocalDate started, LocalDate finished, Usage atUsage) {

    /** A check that finishes no earlier than it starts. */
    public CheckEvent {
        Objects.requireNonNull(serial, "serial");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(started, "started");
        Objects.requireNonNull(finished, "finished");
        Objects.requireNonNull(atUsage, "atUsage");
        if (finished.isBefore(started)) {
            throw new BadReading(serial + ": a check does not finish before it starts");
        }
    }

    /** How many days the aeroplane was in the check, counting both ends. */
    public int days() {
        return (int) ChronoUnit.DAYS.between(started, finished) + 1;
    }

    /** Whether a day falls inside the check, both ends included. */
    public boolean covers(LocalDate day) {
        return !day.isBefore(started) && !day.isAfter(finished);
    }

    /** {@code MSN 5127 hangar check 2025-01-06}. */
    @Override
    public String toString() {
        return serial + " " + kind + " " + started;
    }
}
