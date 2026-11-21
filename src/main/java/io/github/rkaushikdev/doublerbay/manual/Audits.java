package io.github.rkaushikdev.doublerbay.manual;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.complaints.Complaint;
import io.github.rkaushikdev.doublerbay.complaints.Severity;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.grid.Chapter;
import io.github.rkaushikdev.doublerbay.grid.Reference;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.structure.PanelKind;
import io.github.rkaushikdev.doublerbay.structure.Region;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reading the book against itself before anybody dispositions damage with it.
 *
 * <p>A limit table is transcribed by hand and the mistakes it makes are quiet ones. A kind with no
 * row at all reads as a lookup miss, which somebody works around. A row that allows something and
 * caps nothing reads as an answer, and clears everything. A depth cap that leaves less metal than the
 * residual floor demands is a contradiction that fires only on the thicknesses where the two cross,
 * so it passes on most of the fleet and fails on the heavy panels.
 *
 * <p>The sweep here walks the whole cross product of damage kind, panel kind and region and asks the
 * table to answer each one, which is the only way to find the pairs of rows that tie. That is a few
 * hundred lookups and it costs nothing next to getting one of them wrong on an aeroplane.
 */
public final class Audits {

    /**
     * The thicknesses a fractional limit is checked against. Thin enough and thick enough to catch a
     * cap and a floor that cross somewhere in between.
     */
    public static final List<Length> PROBE_THICKNESSES = List.of(
            Length.ofInches("0.016"),
            Length.ofInches("0.040"),
            Length.ofInches("0.063"),
            Length.ofInches("0.125"),
            Length.ofInches("0.250"));

    private Audits() {}

    /** Ask the table every question it could be asked and report what it cannot answer. */
    private static List<Complaint> sweep(LimitTable table) {
        List<Complaint> found = new ArrayList<>();
        for (DamageKind damage : DamageKind.values()) {
            for (PanelKind panel : PanelKind.values()) {
                for (Region region : Region.values()) {
                    try {
                        table.lookup(damage, panel, region);
                    } catch (NotOnFile miss) {
                        found.add(new Complaint(
                                Severity.ADVISORY,
                                table.reference().toString(),
                                "no row answers for " + damage + " on " + panel.value() + " " + region.value()));
                    } catch (BadReading clash) {
                        found.add(new Complaint(
                                Severity.BLOCKING, table.reference().toString(), clash.getMessage()));
                    }
                }
            }
        }
        return found;
    }

    private static List<Complaint> openRows(LimitTable table) {
        return table.rows().stream()
                .filter(row -> row.limits().isOpen())
                .map(row -> new Complaint(
                        Severity.BLOCKING, row.reference().toString(), row.key() + " allows damage and caps nothing"))
                .toList();
    }

    /** Rows whose depth cap leaves less metal than their own residual floor. */
    private static List<Complaint> crossedLimits(LimitTable table) {
        List<Complaint> found = new ArrayList<>();
        for (LimitRow row : table.rows()) {
            for (Length thickness : PROBE_THICKNESSES) {
                Optional<Length> left = row.limits().impliedResidual(thickness);
                Optional<Length> floor = row.limits().residualFloor(thickness);
                if (left.isEmpty() || floor.isEmpty()) {
                    continue;
                }
                if (left.get().isLessThan(floor.get())) {
                    found.add(new Complaint(
                            Severity.BLOCKING,
                            row.reference().toString(),
                            row.key() + " leaves " + left.get().inches().toPlainString() + " at "
                                    + thickness.inches().toPlainString() + " and asks for "
                                    + floor.get().inches().toPlainString()));
                    break;
                }
            }
        }
        return found;
    }

    private static List<Complaint> uncoveredKinds(LimitTable table) {
        Set<DamageKind> missing = EnumSet.allOf(DamageKind.class);
        missing.removeAll(table.kindsCovered());
        return missing.stream()
                .sorted(Comparator.comparing(DamageKind::toString))
                .map(kind ->
                        new Complaint(Severity.BLOCKING, table.reference().toString(), "no row anywhere for a " + kind))
                .toList();
    }

    /** Rows pointing at a page that is not in the book at this revision. */
    private static List<Complaint> orphanPages(LimitTable table, List<Page> pages) {
        Set<Reference> live = new LinkedHashSet<>();
        for (Page page : pages) {
            if (page.isLiveAt(table.revision())) {
                live.add(page.reference());
            }
        }
        Set<Reference> cited = new LinkedHashSet<>();
        table.rows().forEach(row -> cited.add(row.reference()));
        cited.removeAll(live);
        return cited.stream()
                .sorted(Comparator.comparing(Reference::toString))
                .map(reference -> new Complaint(
                        Severity.ADVISORY,
                        reference.toString(),
                        "cited by the table and not in the book at revision " + table.revision()))
                .toList();
    }

    /** Walk one table with no pages beside it, so every page it cites is reported missing. */
    public static List<Complaint> auditTable(LimitTable table) {
        return auditTable(table, List.of());
    }

    /** Walk one table and report everything it cannot answer or contradicts itself on. */
    public static List<Complaint> auditTable(LimitTable table, List<Page> pages) {
        List<Complaint> found = new ArrayList<>(uncoveredKinds(table));
        found.addAll(openRows(table));
        found.addAll(crossedLimits(table));
        found.addAll(orphanPages(table, pages));
        found.addAll(sweep(table));
        return Complaint.sorted(found);
    }

    /** Walk every chapter on the shelf at its latest revision. */
    public static List<Complaint> auditBook(Book book) {
        return auditBook(book, Optional.empty());
    }

    /** Walk every chapter on the shelf, at the revision given for its chapter number or its latest. */
    public static List<Complaint> auditBook(Book book, Map<String, Integer> revisionByChapter) {
        return auditBook(book, Optional.of(revisionByChapter));
    }

    private static List<Complaint> auditBook(Book book, Optional<Map<String, Integer>> revisionByChapter) {
        List<Complaint> found = new ArrayList<>();
        List<Chapter> chapters = book.chapters().stream()
                .sorted(Comparator.comparing(Chapter::toString))
                .toList();
        for (Chapter chapter : chapters) {
            Holding holding = book.holding(chapter);
            int revision = holding.manual().latest().number();
            if (revisionByChapter.isPresent()) {
                revision = revisionByChapter.get().getOrDefault(chapter.toString(), revision);
            }
            LimitTable table = holding.tableAt(revision);
            found.addAll(auditTable(table, holding.pagesAt(revision)));
        }
        return Complaint.sorted(found);
    }
}
