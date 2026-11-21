package io.github.rkaushikdev.doublerbay.report;

import io.github.rkaushikdev.doublerbay.ids.FindingId;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What one revision of the manual did to the cards already open.
 *
 * @param before the day the old book was read as of
 * @param after the day the new book was read as of
 * @param lookedAt how many open cards were reassessed
 * @param moved the cards whose answer changed, worst answer first
 * @param unreadable the cards that could not be assessed on one side or the other
 */
public record Sweep(LocalDate before, LocalDate after, int lookedAt, List<Moved> moved, List<FindingId> unreadable) {

    public Sweep {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        moved = List.copyOf(moved);
        unreadable = List.copyOf(unreadable);
    }

    /** The cards the revision made worse. */
    public List<Moved> worse() {
        return moved.stream().filter(Moved::gotWorse).toList();
    }

    /** The cards the revision made better. */
    public List<Moved> better() {
        return moved.stream().filter(Moved::gotBetter).toList();
    }

    /** Whether nothing moved and everything could be read. */
    public boolean isQuiet() {
        return moved.isEmpty() && unreadable.isEmpty();
    }

    /** {@code 3 cards looked at, 1 worse, 1 better}, or {@code nothing moved} when it was quiet. */
    @Override
    public String toString() {
        if (isQuiet()) {
            return lookedAt + " cards looked at, nothing moved";
        }
        List<String> parts = new ArrayList<>();
        if (!worse().isEmpty()) {
            parts.add(worse().size() + " worse");
        }
        if (!better().isEmpty()) {
            parts.add(better().size() + " better");
        }
        if (!unreadable.isEmpty()) {
            parts.add(unreadable.size() + " could not be assessed");
        }
        return lookedAt + " cards looked at, " + String.join(", ", parts);
    }
}
