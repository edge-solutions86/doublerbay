package io.github.rkaushikdev.doublerbay.report;

import io.github.rkaushikdev.doublerbay.DoublerbayError;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.disposition.Deferral;
import io.github.rkaushikdev.doublerbay.disposition.DispositionRecord;
import io.github.rkaushikdev.doublerbay.disposition.EngineeringOrder;
import io.github.rkaushikdev.doublerbay.disposition.Transition;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.repair.RepairRecord;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * One card's whole story, gathered from every register that touched it.
 *
 * <p>A damage card leaves a trail in five places. The card itself carries its revisions. The
 * disposition carries the moves. The deferral register knows what it was flying against. The order
 * register knows whether it went out to the manufacturer. The repair register knows what finally
 * answered it.
 *
 * <p>Nobody can hold five queries in their head at half past six, and the question they are actually
 * asking is always the same one: what happened to this, in order. So this assembles the five into one
 * list, sorted by the day each thing happened, and says which register each line came off.
 *
 * <p>Ties are broken by the register rather than left to chance. A card raised and dispositioned on
 * the same day should read raised first, because it was, and a sort that put them the other way round
 * would have somebody reading the story backwards.
 */
public final class History {

    /** Which register a line came off, and the order they read in on one day. */
    public static final List<String> ORDER = List.of("card", "disposition", "deferral", "engineering", "repair");

    private History() {}

    /** One line per revision, saying what was measured. */
    private static List<Entry> cardEntries(UnitOfWork unit, FindingId reference) {
        List<Entry> entries = new ArrayList<>();
        for (Finding one : unit.findings().history(reference)) {
            String what = one.revision() == 1 ? "raised" : "measured again, revision " + one.revision();
            entries.add(new Entry(one.foundOn(), "card", what + ", " + one.geometry()));
            if (one.supersededBy().isPresent() && one.revision() == lastRevision(unit, reference)) {
                entries.add(new Entry(
                        one.foundOn(),
                        "card",
                        "superseded by " + one.supersededBy().get()));
            }
        }
        return entries;
    }

    private static int lastRevision(UnitOfWork unit, FindingId reference) {
        return unit.findings().history(reference).stream()
                .mapToInt(Finding::revision)
                .max()
                .orElseThrow();
    }

    /** One line per move, with who made it. */
    private static List<Entry> dispositionEntries(UnitOfWork unit, FindingId reference) {
        DispositionRecord record;
        try {
            record = unit.dispositions().get(reference);
        } catch (DoublerbayError noDisposition) {
            return List.of();
        }
        List<Entry> entries = new ArrayList<>();
        for (Transition move : record.history()) {
            entries.add(new Entry(move.on(), "disposition", move.was() + " to " + move.now() + " by " + move.by()));
        }
        return entries;
    }

    /** The live deferral, if there is one, and what it is due against. */
    private static List<Entry> deferralEntries(UnitOfWork unit, FindingId reference) {
        Deferral deferral;
        try {
            deferral = unit.deferrals().get(reference);
        } catch (DoublerbayError notDeferred) {
            return List.of();
        }
        return List.of(new Entry(
                deferral.granted(),
                "deferral",
                "granted by " + deferral.by() + " to " + deferral.due() + ", " + deferral.reInspectWith()));
    }

    /** A line for each order raised, and a second when it was answered. */
    private static List<Entry> orderEntries(UnitOfWork unit, FindingId reference) {
        List<Entry> entries = new ArrayList<>();
        for (EngineeringOrder order : unit.orders().against(reference)) {
            entries.add(new Entry(order.raised(), "engineering", order.reference() + " raised"));
            order.sent().ifPresent(sent -> entries.add(new Entry(sent, "engineering", order.reference() + " sent")));
            order.answered()
                    .ifPresent(answered ->
                            entries.add(new Entry(answered, "engineering", order.reference() + " " + order.state())));
        }
        return entries;
    }

    /** A line when a repair went on, and another when it came off. */
    private static List<Entry> repairEntries(UnitOfWork unit, FindingId reference) {
        List<Entry> entries = new ArrayList<>();
        for (RepairRecord repair : unit.repairs().against(reference)) {
            entries.add(new Entry(
                    repair.embodied(),
                    "repair",
                    repair.reference() + " fitted, " + repair.scheme().category()));
            repair.removed()
                    .ifPresent(removed -> entries.add(new Entry(removed, "repair", repair.reference() + " removed")));
        }
        return entries;
    }

    /** Assemble one card's whole story, oldest first. */
    public static Story storyOf(UnitOfWork unit, FindingId reference) {
        Finding finding = unit.findings().latest(reference);
        List<Entry> entries = new ArrayList<>();
        entries.addAll(cardEntries(unit, reference));
        entries.addAll(dispositionEntries(unit, reference));
        entries.addAll(deferralEntries(unit, reference));
        entries.addAll(orderEntries(unit, reference));
        entries.addAll(repairEntries(unit, reference));
        entries.sort(
                Comparator.comparing(Entry::on).thenComparingInt(Entry::rank).thenComparing(Entry::what));
        return new Story(reference, finding.serial(), entries);
    }
}
