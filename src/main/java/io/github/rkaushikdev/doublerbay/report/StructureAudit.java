package io.github.rkaushikdev.doublerbay.report;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.repair.Clash;
import io.github.rkaushikdev.doublerbay.repair.RepairCategory;
import io.github.rkaushikdev.doublerbay.repair.RepairPair;
import io.github.rkaushikdev.doublerbay.repair.RepairRecord;
import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One tail's accumulated repair picture on one day, which is a different question from what is due.
 *
 * <p>The due list is about time. This is about accumulation. An aeroplane with thirty repairs on it is
 * not in trouble because any one of them is due, it is in trouble because the next damage in that
 * area has nowhere to go, and nothing on the due list will ever say that.
 *
 * <p>Three numbers carry it. How many repairs are on one panel, because a panel with four doublers on
 * it is a panel the shop should be arguing about replacing. How long the temporary repairs have been
 * fitted, because category C is meant to last until the next check and some of them are five years
 * old. And which pairs are too close together, because that is the one the audit finds.
 *
 * <p>None of this is a refusal. Every one of these repairs was approved when it went on. The report
 * exists so somebody sees the pattern before the fleet audit does. {@link #audit} builds one.
 */
public record StructureAudit(
        Serial serial,
        LocalDate on,
        List<PanelLoad> panels,
        List<OverdueTemporary> temporary,
        List<RepairPair> clashing) {

    /** A panel carrying this many repairs is worth a conversation about replacing it. */
    public static final int CROWDED_PANEL = 3;

    /** How long a temporary repair is meant to last before it is replaced, in months. */
    public static final int TEMPORARY_MONTHS = 18;

    /** The days in an average month, which turn a repair's age in days into months. */
    public static final BigDecimal DAYS_A_MONTH = new BigDecimal("30.44");

    /** The places a repair's age in months is given to. */
    public static final int PLACES = 1;

    /** A tail's picture on a day. */
    public StructureAudit {
        Objects.requireNonNull(serial, "serial");
        Objects.requireNonNull(on, "on");
        panels = List.copyOf(panels);
        temporary = List.copyOf(temporary);
        clashing = List.copyOf(clashing);
    }

    /** Every repair on every panel. */
    public int totalRepairs() {
        return panels.stream().mapToInt(PanelLoad::repairs).sum();
    }

    /** The panels carrying enough repairs to be worth a conversation. */
    public List<PanelLoad> crowded() {
        return panels.stream().filter(PanelLoad::isCrowded).toList();
    }

    /** The temporary repairs on longer than they were meant to last. */
    public List<OverdueTemporary> overdue() {
        return temporary.stream().filter(OverdueTemporary::isOverdue).toList();
    }

    /** Whether nothing is crowded, overdue or clashing. */
    public boolean isClean() {
        return crowded().isEmpty() && overdue().isEmpty() && clashing.isEmpty();
    }

    /** How many repairs one panel carries, none for a panel the audit does not list. */
    public int loadOn(PanelId part) {
        int found = 0;
        for (PanelLoad one : panels) {
            if (one.part().equals(part)) {
                found = one.repairs();
            }
        }
        return found;
    }

    /**
     * {@code MSN 5127: 4 repairs, 1 crowded panels, 1 temporary repairs overdue, 1 clashes}, naming only
     * what was flagged, or {@code nothing flagged}, or {@code no repairs on file}.
     */
    @Override
    public String toString() {
        if (totalRepairs() == 0) {
            return serial + ": no repairs on file";
        }
        List<String> flags = new ArrayList<>();
        if (!crowded().isEmpty()) {
            flags.add(crowded().size() + " crowded panels");
        }
        if (!overdue().isEmpty()) {
            flags.add(overdue().size() + " temporary repairs overdue");
        }
        if (!clashing.isEmpty()) {
            flags.add(clashing.size() + " clashes");
        }
        String state = flags.isEmpty() ? "nothing flagged" : String.join(", ", flags);
        return serial + ": " + totalRepairs() + " repairs, " + state;
    }

    /**
     * How long a repair has been fitted, in months to a tenth, a half tenth rounding up.
     *
     * @throws BadReading when the day is before the repair went on
     */
    public static BigDecimal monthsBetween(LocalDate embodied, LocalDate on) {
        if (on.isBefore(embodied)) {
            throw new BadReading("a repair cannot have been fitted for a negative time");
        }
        BigDecimal days = BigDecimal.valueOf(ChronoUnit.DAYS.between(embodied, on));
        return Decimals.over(days, DAYS_A_MONTH).setScale(PLACES, RoundingMode.HALF_UP);
    }

    /** How much each panel is carrying, busiest first, then by part number. */
    public static List<PanelLoad> panelLoads(List<RepairRecord> fitted) {
        Map<PanelId, Integer> counted = new LinkedHashMap<>();
        Map<PanelId, Integer> temporary = new LinkedHashMap<>();
        for (RepairRecord one : fitted) {
            counted.merge(one.part(), 1, Integer::sum);
            if (one.scheme().category() == RepairCategory.C) {
                temporary.merge(one.part(), 1, Integer::sum);
            }
        }
        List<PanelLoad> loads = new ArrayList<>();
        counted.forEach((part, count) -> loads.add(new PanelLoad(part, count, temporary.getOrDefault(part, 0))));
        loads.sort(Comparator.comparingInt((PanelLoad one) -> -one.repairs())
                .thenComparing(one -> one.part().toString()));
        return List.copyOf(loads);
    }

    /** Every temporary repair in the list with its age on a day, longest fitted first, then by repair number. */
    public static List<OverdueTemporary> temporaries(List<RepairRecord> fitted, LocalDate on) {
        List<OverdueTemporary> found = new ArrayList<>();
        for (RepairRecord one : fitted) {
            if (one.scheme().category() == RepairCategory.C) {
                found.add(new OverdueTemporary(
                        one.reference(), one.part(), one.embodied(), monthsBetween(one.embodied(), on)));
            }
        }
        found.sort(Comparator.comparing(OverdueTemporary::monthsFitted, Comparator.reverseOrder())
                .thenComparing(one -> one.reference().toString()));
        return List.copyOf(found);
    }

    /** Build one tail's accumulated repair picture on a day. */
    public static StructureAudit audit(Serial serial, List<RepairRecord> fitted, LocalDate on) {
        return new StructureAudit(
                serial,
                on,
                panelLoads(fitted),
                temporaries(fitted, on),
                RepairRecord.clashes(fitted).stream().map(Clash::numbers).toList());
    }
}
