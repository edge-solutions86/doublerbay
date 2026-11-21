package io.github.rkaushikdev.doublerbay.report;

import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * Everything that happened to one card, oldest first.
 *
 * @param reference the card
 * @param serial the tail it is on
 * @param entries every entry from every register, in the order they happened
 */
public record Story(FindingId reference, Serial serial, List<Entry> entries) {

    public Story {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(serial, "serial");
        entries = List.copyOf(entries);
    }

    /** The day of the first entry. */
    public LocalDate opened() {
        return entries.get(0).on();
    }

    /** The day of the last entry. */
    public LocalDate latest() {
        return entries.get(entries.size() - 1).on();
    }

    /** Days from the first entry to the last. */
    public int daysOpen() {
        return Math.toIntExact(ChronoUnit.DAYS.between(opened(), latest()));
    }

    /** The entries that came off one register, in order. */
    public List<Entry> fromRegister(String register) {
        return entries.stream().filter(one -> one.register().equals(register)).toList();
    }

    /** {@code DR-2025-000001 on MSN 5127: 1 entries over 0 days}. */
    @Override
    public String toString() {
        return reference + " on " + serial + ": " + entries.size() + " entries over " + daysOpen() + " days";
    }
}
