package io.github.rkaushikdev.doublerbay.report;

import io.github.rkaushikdev.doublerbay.ids.Serial;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One tail's work for one check: what the check can clear, and what is waiting on somebody else.
 *
 * @param serial the tail
 * @param opens the first day of the check
 * @param closes the last day of the check
 * @param deferrals the deferrals falling due
 * @param inspections the repeat inspections falling due
 * @param waiting the orders out to the manufacturer, which no check can clear
 */
public record WorkPack(
        Serial serial,
        LocalDate opens,
        LocalDate closes,
        List<PackLine> deferrals,
        List<PackLine> inspections,
        List<PackLine> waiting) {

    public WorkPack {
        Objects.requireNonNull(serial, "serial");
        Objects.requireNonNull(opens, "opens");
        Objects.requireNonNull(closes, "closes");
        deferrals = List.copyOf(deferrals);
        inspections = List.copyOf(inspections);
        waiting = List.copyOf(waiting);
    }

    /** The ones the check can actually clear: the deferrals, then the inspections. */
    public List<PackLine> dueLines() {
        List<PackLine> lines = new ArrayList<>(deferrals);
        lines.addAll(inspections);
        return List.copyOf(lines);
    }

    /** Every line on the pack. */
    public int total() {
        return dueLines().size() + waiting.size();
    }

    /** Whether the check owes nothing. */
    public boolean isEmpty() {
        return total() == 0;
    }

    /** The lines the check can clear that were already over. */
    public List<PackLine> overrun() {
        return dueLines().stream().filter(PackLine::overrun).toList();
    }

    /** How many days the check runs, both ends counted. */
    public int days() {
        return Math.toIntExact(ChronoUnit.DAYS.between(opens, closes) + 1);
    }

    /** {@code MSN 5127: 2 items over 8 days, 1 already over}, or that there is nothing for this check. */
    @Override
    public String toString() {
        if (isEmpty()) {
            return serial + ": nothing for this check";
        }
        String over = overrun().isEmpty() ? "" : ", " + overrun().size() + " already over";
        return serial + ": " + total() + " items over " + days() + " days" + over;
    }
}
