package io.github.rkaushikdev.doublerbay.manual;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.fleet.Aircraft;
import io.github.rkaushikdev.doublerbay.grid.Reference;
import io.github.rkaushikdev.doublerbay.structure.PanelKind;
import io.github.rkaushikdev.doublerbay.structure.Region;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The allowable damage table for one subject, at one revision.
 *
 * <p>Finding the right row is where most of a manual page actually lives. A limit page is a table
 * with three keys: what kind of damage, what kind of skin it is on, and what part of the panel it is
 * in. Almost every row leaves at least one of those blank, meaning any, and a page that spelt out
 * every combination would run to hundreds of rows nobody could check.
 *
 * <p>So the lookup is a specificity match, weighed as {@link LimitKey} sets out. Two rows that still
 * match equally specifically is not a tie to be broken by ordering. It is a contradiction in the
 * book, and it is raised rather than silently resolved by whichever happened to be typed in first.
 */
public record LimitTable(Reference reference, int revision, List<LimitRow> rows) {

    /** A table issued at a revision, with at least one row. */
    public LimitTable {
        Objects.requireNonNull(reference, "reference");
        rows = List.copyOf(rows);
        if (revision < 1) {
            throw new BadReading(reference + " is issued at a revision");
        }
        if (rows.isEmpty()) {
            throw new BadReading(reference + " has rows in it");
        }
    }

    /** Every row that could answer, most specific first, rows of one specificity in table order. */
    public List<LimitRow> candidates(DamageKind damage, PanelKind panel, Region region) {
        return rows.stream()
                .filter(row -> row.key().matches(damage, panel, region))
                .sorted(Comparator.comparingInt((LimitRow row) -> row.key().specificity())
                        .reversed())
                .toList();
    }

    /**
     * The row that answers.
     *
     * @throws NotOnFile when no row matches
     * @throws BadReading when two rows match equally specifically, which is the book contradicting
     *     itself
     */
    public LimitRow lookup(DamageKind damage, PanelKind panel, Region region) {
        List<LimitRow> matching = candidates(damage, panel, region);
        if (matching.isEmpty()) {
            throw new NotOnFile(
                    reference + " has no row for " + damage + " on " + panel.value() + " " + region.value());
        }
        int best = matching.get(0).key().specificity();
        List<LimitRow> tied =
                matching.stream().filter(row -> row.key().specificity() == best).toList();
        if (tied.size() > 1) {
            throw new BadReading(reference + " has " + tied.size() + " rows that answer for " + damage + " on "
                    + panel.value() + " " + region.value());
        }
        return tied.get(0);
    }

    /** Whether any row could answer. */
    public boolean hasARowFor(DamageKind damage, PanelKind panel, Region region) {
        return !candidates(damage, panel, region).isEmpty();
    }

    /**
     * The same table with the rows that do not apply to this tail taken out.
     *
     * <p>A row written for a block of serials looks like an answer on a tail outside it, so the
     * filtering happens before the lookup rather than being checked afterwards by whoever remembers
     * to.
     */
    public LimitTable effectiveFor(Aircraft aircraft) {
        List<LimitRow> applicable = rows.stream()
                .filter(row -> aircraft.coveredBy(row.effectivity()))
                .toList();
        if (applicable.isEmpty()) {
            throw new NotOnFile(reference + " has no rows that apply to " + aircraft.serial());
        }
        return new LimitTable(reference, revision, applicable);
    }

    /** Every damage kind some row is keyed on. */
    public Set<DamageKind> kindsCovered() {
        Set<DamageKind> kinds = EnumSet.noneOf(DamageKind.class);
        rows.forEach(row -> kinds.add(row.key().damageKind()));
        return Collections.unmodifiableSet(kinds);
    }

    /** The same table with one more row at the end. */
    public LimitTable withRow(LimitRow row) {
        List<LimitRow> longer = new ArrayList<>(rows);
        longer.add(Objects.requireNonNull(row, "row"));
        return new LimitTable(reference, revision, longer);
    }

    /** How many rows the table has. */
    public int size() {
        return rows.size();
    }

    /** {@code 53-10-03 rev 12, 3 rows}. */
    @Override
    public String toString() {
        return reference + " rev " + revision + ", " + rows.size() + " rows";
    }
}
