package io.github.rkaushikdev.doublerbay.assess;

import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.fleet.TailContext;
import io.github.rkaushikdev.doublerbay.manual.Book;
import io.github.rkaushikdev.doublerbay.manual.LimitRow;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Walking one damage through the book and coming out with an answer.
 *
 * <p>Every rule runs, not just the ones up to the first failure. An engineer looking at the trail
 * wants to see that the depth was fine and the edge margin was not, because that tells them where to
 * put the repair. Stopping at the first no throws away the half of the answer that is useful.
 *
 * <p>The disposition is the worst thing any rule said, which is the only combination rule that is
 * safe. Three rules saying allowable and one saying repair is a repair. It is never an average and it
 * is never the last one that ran.
 *
 * <p>When the book has no page at all for this combination the answer is not a failure of the
 * software, it is the manual running out, and that has its own disposition. That is exactly the case
 * that goes to the manufacturer, and it is worth it landing there by itself rather than through an
 * exception somebody catches and turns into a shrug.
 */
public final class Assessor {

    private Assessor() {}

    private static Verdict noPage(NotOnFile miss) {
        return new Verdict(Disposition.OUT_OF_LIMITS, List.of(new Reason("page", miss.getMessage(), false)));
    }

    /**
     * Walk one card through every rule the book has for it.
     *
     * <p>A card the book has no row for comes back outside the manual, citing no page and carrying the
     * miss as its one reason.
     */
    public static Assessment assess(Book book, TailContext tail, Finding finding) {
        Placement placement = Placement.place(tail, finding);
        LimitRow row;
        try {
            row = book.limitsFor(
                    placement.chapter(),
                    tail.on(),
                    tail.aircraft(),
                    finding.kind(),
                    placement.panelKind(),
                    placement.region());
        } catch (NotOnFile miss) {
            return new Assessment(finding, placement, noPage(miss), Optional.empty());
        }

        int revision = book.revisionOn(placement.chapter(), tail.on()).number();
        Case held = new Case(finding, placement, row.limits(), book.cleanup().forKind(finding.kind()));

        List<Check> answers = new ArrayList<>();
        for (Function<Case, Optional<Check>> check : Rules.CHECKS) {
            check.apply(held).ifPresent(answers::add);
        }
        // The joint rules run after the ordinary ones and only ever make the answer worse. A damage
        // in a fastener row still has to clear every limit that applies away from one.
        answers.addAll(Joints.checksFor(held, placement.joint()));
        // The region rules follow from the feature the damage landed on, which the limit tables
        // already vary by. These are the ones about the feature itself rather than about how big the
        // damage is.
        answers.addAll(Regions.checksFor(held));

        List<Reason> reasons = new ArrayList<>();
        Disposition worst = Disposition.ALLOWABLE;
        for (Check answer : answers) {
            reasons.add(answer.reason());
            if (answer.outcome().worseThan(worst)) {
                worst = answer.outcome();
            }
        }

        Verdict verdict = new Verdict(worst, reasons, row.reference(), revision);
        return new Assessment(finding, placement, verdict, row);
    }

    /** Assess a run of cards on one tail, in the order they were raised. */
    public static List<Assessment> assessAll(Book book, TailContext tail, List<Finding> findings) {
        return findings.stream()
                .sorted(Comparator.comparing(card -> card.reference().toString()))
                .map(finding -> assess(book, tail, finding))
                .toList();
    }

    /** The worst answer across a set, which is what a panel or a tail reports; nothing open is allowable. */
    public static Disposition worstDisposition(List<Assessment> assessments) {
        Disposition worst = Disposition.ALLOWABLE;
        for (Assessment one : assessments) {
            if (one.disposition().worseThan(worst)) {
                worst = one.disposition();
            }
        }
        return worst;
    }
}
