package io.github.rkaushikdev.doublerbay.manual;

import io.github.rkaushikdev.doublerbay.BadReading;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * One issue of the manual, and the day it starts to govern.
 *
 * <p>A revision has an issue date and an effective date and they are not the same day. The book
 * arrives, the operator has a window to work it into the maintenance programme, and until the
 * effective date the previous revision is still the one that governs.
 */
public record Revision(int number, LocalDate issued, LocalDate effective) {

    /** A revision numbered from one that does not take effect before it was issued. */
    public Revision {
        Objects.requireNonNull(issued, "issued");
        Objects.requireNonNull(effective, "effective");
        if (number < 1) {
            throw new BadReading("revisions are numbered from one");
        }
        if (effective.isBefore(issued)) {
            throw new BadReading("revision " + number + " takes effect before it was issued");
        }
    }

    /** How long the operator had to work it in before it started to govern, in days. */
    public int leadDays() {
        return (int) ChronoUnit.DAYS.between(issued, effective);
    }

    /** Whether this revision had started by a day, ignoring anything later. */
    public boolean inForceOn(LocalDate day) {
        return !day.isBefore(effective);
    }

    /** {@code rev 12 of 2024-12-01}. */
    @Override
    public String toString() {
        return "rev " + number + " of " + effective;
    }
}
