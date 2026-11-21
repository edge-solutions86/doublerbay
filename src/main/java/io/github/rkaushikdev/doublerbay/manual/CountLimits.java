package io.github.rkaushikdev.doublerbay.manual;

import io.github.rkaushikdev.doublerbay.BadReading;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * How many separate damages the book allows in each unit of structure.
 *
 * <p>Every limit that measures a damage is about one damage and how big it is. These are about how
 * many, and they are the ones a desk working card by card never sees. The book allows a dent of a
 * size. It separately allows two of them in a bay and six on a panel, and it says how many may be
 * taken together as one group. A limit the page does not state is never exceeded.
 */
public record CountLimits(Optional<Integer> perBay, Optional<Integer> perPanel, Optional<Integer> perCluster) {

    /** Limits of at least one each, with a bay holding no more than the panel it is in. */
    public CountLimits {
        Objects.requireNonNull(perBay, "perBay");
        Objects.requireNonNull(perPanel, "perPanel");
        Objects.requireNonNull(perCluster, "perCluster");
        atLeastOne("per bay", perBay);
        atLeastOne("per panel", perPanel);
        atLeastOne("per cluster", perCluster);
        if (perBay.isPresent() && perPanel.isPresent() && perBay.get() > perPanel.get()) {
            throw new BadReading("a bay cannot hold more than the panel it is in");
        }
    }

    /** A page that counts nothing. */
    public CountLimits() {
        this(Optional.empty(), Optional.empty(), Optional.empty());
    }

    /** A page that counts in every unit. */
    public CountLimits(int perBay, int perPanel, int perCluster) {
        this(Optional.of(perBay), Optional.of(perPanel), Optional.of(perCluster));
    }

    /** Limits from what was to hand; a null limit is one the page does not state. */
    public static CountLimits of(Integer perBay, Integer perPanel, Integer perCluster) {
        return new CountLimits(
                Optional.ofNullable(perBay), Optional.ofNullable(perPanel), Optional.ofNullable(perCluster));
    }

    private static void atLeastOne(String name, Optional<Integer> value) {
        if (value.isPresent() && value.get() < 1) {
            throw new BadReading("a count limit " + name + " allows at least one");
        }
    }

    /** The same limits with a count per bay. */
    public CountLimits withPerBay(int limit) {
        return new CountLimits(Optional.of(limit), perPanel, perCluster);
    }

    /** The same limits with a count per panel. */
    public CountLimits withPerPanel(int limit) {
        return new CountLimits(perBay, Optional.of(limit), perCluster);
    }

    /** The same limits with a count per group. */
    public CountLimits withPerCluster(int limit) {
        return new CountLimits(perBay, perPanel, Optional.of(limit));
    }

    /** Whether the page counts anything at all. */
    public boolean countsAnything() {
        return perBay.isPresent() || perPanel.isPresent() || perCluster.isPresent();
    }

    /** Whether this many in one bay is over the count, which a count at the limit is not. */
    public boolean bayExceeded(int count) {
        return perBay.isPresent() && count > perBay.get();
    }

    /** Whether this many on one panel is over the count. */
    public boolean panelExceeded(int count) {
        return perPanel.isPresent() && count > perPanel.get();
    }

    /** Whether this many taken together as one group is over the count. */
    public boolean clusterExceeded(int count) {
        return perCluster.isPresent() && count > perCluster.get();
    }

    /** How many more the panel can take, never fewer than none, or nothing if the book does not count. */
    public Optional<Integer> roomLeftOnPanel(int count) {
        return perPanel.map(limit -> Math.max(limit - count, 0));
    }

    /** {@code 3 in a group, 2 a bay, 6 a panel}, or {@code not counted}. */
    @Override
    public String toString() {
        List<String> parts = new ArrayList<>();
        perCluster.ifPresent(limit -> parts.add(limit + " in a group"));
        perBay.ifPresent(limit -> parts.add(limit + " a bay"));
        perPanel.ifPresent(limit -> parts.add(limit + " a panel"));
        return parts.isEmpty() ? "not counted" : String.join(", ", parts);
    }
}
