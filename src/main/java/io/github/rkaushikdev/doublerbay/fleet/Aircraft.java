package io.github.rkaushikdev.doublerbay.fleet;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.ids.Registration;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One tail, as the records office holds it: keyed on the serial and labelled with the marks.
 *
 * <p>The serial is the aeroplane and the registration is what is painted on it this month. Leases
 * end, marks get handed back to the authority and reissued to somebody else's aeroplane, and a
 * repair filed under a registration follows the paint rather than the metal. So the serial is the
 * key and the registration is a label, and changing the label makes a new record rather than
 * editing the old one.
 *
 * <p>The configuration is the other half of identity. Two tails off the same line a month apart
 * can have different structure if one of them has a bulletin embodied and the other does not, and
 * that is what decides which page of the manual answers for a damage on it.
 */
public record Aircraft(
        Serial serial,
        Registration registration,
        Series series,
        int lineNumber,
        LocalDate delivered,
        Set<Bulletin> embodied,
        Optional<LocalDate> withdrawn) {

    /** Days in an average year, which is what an age in years is counted in. */
    public static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365.25");

    /** A tail with a line number from one, withdrawn no earlier than it was delivered. */
    public Aircraft {
        Objects.requireNonNull(serial, "serial");
        Objects.requireNonNull(registration, "registration");
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(delivered, "delivered");
        Objects.requireNonNull(withdrawn, "withdrawn");
        embodied = Set.copyOf(embodied);
        if (lineNumber < 1) {
            throw new BadReading(serial + ": line numbers start at one");
        }
        if (withdrawn.isPresent() && withdrawn.get().isBefore(delivered)) {
            throw new BadReading(serial + ": withdrawn before it was delivered");
        }
    }

    /** A tail as delivered: nothing embodied and still in service. */
    public Aircraft(Serial serial, Registration registration, Series series, int lineNumber, LocalDate delivered) {
        this(serial, registration, series, lineNumber, delivered, Set.of(), Optional.empty());
    }

    /** A tail from what the records hold; a null withdrawal is a tail still in service. */
    public static Aircraft of(
            Serial serial,
            Registration registration,
            Series series,
            int lineNumber,
            LocalDate delivered,
            Set<Bulletin> embodied,
            LocalDate withdrawn) {
        return new Aircraft(
                serial, registration, series, lineNumber, delivered, embodied, Optional.ofNullable(withdrawn));
    }

    /** The marks, which is what people call the aeroplane. */
    public String tail() {
        return registration.toString();
    }

    /** Whether the tail was on the operator's books on a day: from delivery, up to withdrawal. */
    public boolean inServiceOn(LocalDate day) {
        if (day.isBefore(delivered)) {
            return false;
        }
        return withdrawn.isEmpty() || day.isBefore(withdrawn.get());
    }

    /** How old the airframe is in years, to two places, which is what a calendar limit is measured against. */
    public BigDecimal ageYears(LocalDate on) {
        if (on.isBefore(delivered)) {
            throw new BadReading(serial + ": " + on + " is before it was delivered");
        }
        long days = ChronoUnit.DAYS.between(delivered, on);
        return Decimals.quantize(Decimals.over(BigDecimal.valueOf(days), DAYS_PER_YEAR), 2);
    }

    /** Whether a bulletin is on this tail. */
    public boolean hasEmbodied(Bulletin bulletin) {
        return embodied.contains(bulletin);
    }

    /** The same tail after a bulletin goes on, which is a new record; this one when it is already on. */
    public Aircraft withEmbodied(Bulletin bulletin) {
        if (hasEmbodied(bulletin)) {
            return this;
        }
        Set<Bulletin> more = new HashSet<>(embodied);
        more.add(bulletin);
        return new Aircraft(serial, registration, series, lineNumber, delivered, more, withdrawn);
    }

    /** The same airframe wearing different marks; this one when the marks are the same. */
    public Aircraft reRegistered(Registration marks) {
        if (marks.equals(registration)) {
            return this;
        }
        return new Aircraft(serial, marks, series, lineNumber, delivered, embodied, withdrawn);
    }

    /** The same tail, leaving service on a day. */
    public Aircraft withdrawnOn(LocalDate day) {
        return new Aircraft(serial, registration, series, lineNumber, delivered, embodied, Optional.of(day));
    }

    /** Whether a manual page written for a range answers for this tail. */
    public boolean coveredBy(Effectivity effectivity) {
        return effectivity.covers(series, serial, embodied);
    }

    /** {@code N412VD (MSN 5127, V320-200)}. */
    @Override
    public String toString() {
        return registration + " (" + serial + ", " + series + ")";
    }
}
