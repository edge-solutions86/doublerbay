package io.github.rkaushikdev.doublerbay.report;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The open damage count across the register, tail by tail and in total, worst tail first.
 *
 * @see DamageCount
 */
public record FleetDamage(LocalDate on, List<DamageCount> tails) {

    /** A day and the count on each tail, in the order given. */
    public FleetDamage {
        Objects.requireNonNull(on, "on");
        tails = List.copyOf(tails);
    }

    /** Count the whole register, counting the last thirty days as recent. */
    public static FleetDamage countFleet(Map<Serial, List<Finding>> holdings, LocalDate on) {
        return countFleet(holdings, on, DamageCount.RECENT_DAYS);
    }

    /** Count the whole register, most open first and in serial order among equals. */
    public static FleetDamage countFleet(Map<Serial, List<Finding>> holdings, LocalDate on, int recentDays) {
        List<DamageCount> counts = new ArrayList<>();
        holdings.forEach((serial, found) -> counts.add(DamageCount.countOpen(serial, found, on, recentDays)));
        counts.sort(Comparator.comparingInt((DamageCount one) -> -one.total())
                .thenComparing(one -> one.serial().number()));
        return new FleetDamage(on, counts);
    }

    /** How many cards are open across the register. */
    public int total() {
        return tails.stream().mapToInt(DamageCount::total).sum();
    }

    /** The tails with nothing open, in report order. */
    public List<Serial> cleanTails() {
        return tails.stream()
                .filter(DamageCount::isClean)
                .map(DamageCount::serial)
                .toList();
    }

    /** The tail with the most open, or nothing when every tail is clean. */
    public Optional<DamageCount> worstTail() {
        return !tails.isEmpty() && !tails.get(0).isClean() ? Optional.of(tails.get(0)) : Optional.empty();
    }

    /** One tail's count out of the report. */
    public DamageCount forTail(Serial serial) {
        for (DamageCount one : tails) {
            if (one.serial().equals(serial)) {
                return one;
            }
        }
        throw new BadReading(serial + " is not in this report");
    }

    /** {@code 2 tails, 1 open on 2025-03-14}. */
    @Override
    public String toString() {
        return tails.size() + " tails, " + total() + " open on " + on;
    }
}
