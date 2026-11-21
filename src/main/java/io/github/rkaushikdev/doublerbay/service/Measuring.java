package io.github.rkaushikdev.doublerbay.service;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.DoublerbayError;
import io.github.rkaushikdev.doublerbay.assess.Assessor;
import io.github.rkaushikdev.doublerbay.assess.Disposition;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.damage.Geometry;
import io.github.rkaushikdev.doublerbay.fleet.TailContext;
import io.github.rkaushikdev.doublerbay.fleet.TypeCatalogue;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.manual.Book;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import io.github.rkaushikdev.doublerbay.store.StoreContext;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import java.time.LocalDate;
import java.util.List;

/**
 * Measuring a damage again, and what the new numbers oblige the desk to do.
 *
 * <p>A re measurement is not an edit. The old numbers stay, because the whole argument for leaving a
 * crack alone rests on how fast it was growing, and that argument disappears the moment somebody
 * overwrites the first reading. So every look adds a revision and nothing is ever replaced.
 *
 * <p>A damage that has grown past what it was deferred against is a damage the deferral no longer
 * covers, whatever the limit says, and the desk has to know that on the day the mechanic writes the
 * card rather than at the next audit. So a re measurement re assesses, compares the two answers, and
 * says out loud when the answer got worse.
 *
 * <p>It does not act on that by itself. Tightening a deferral or grounding an aeroplane is an
 * engineer's decision, and a service that made it automatically would be a service engineers learn
 * to work around.
 */
public final class Measuring {

    private Measuring() {}

    /** Record a new set of measurements at what the log says was flown that day. */
    public static ReMeasured reMeasure(
            UnitOfWork unit, Book book, TypeCatalogue catalogue, FindingId reference, Geometry geometry, LocalDate on) {
        return reMeasure(unit, book, catalogue, reference, geometry, on, null);
    }

    /** Record a new set of measurements and say how the disposition moved. */
    public static ReMeasured reMeasure(
            UnitOfWork unit,
            Book book,
            TypeCatalogue catalogue,
            FindingId reference,
            Geometry geometry,
            LocalDate on,
            Usage flownOrNull) {
        Finding current = unit.findings().latest(reference);
        if (on.isBefore(current.foundOn())) {
            throw new BadReading(reference + " cannot be measured before it was found");
        }
        if (current.supersededBy().isPresent()) {
            throw new BadReading(reference + " was superseded, measure "
                    + current.supersededBy().get() + " instead");
        }

        Disposition before = disposition(unit, book, catalogue, current, current.foundOn());
        Usage at = flownOrNull != null
                ? flownOrNull
                : unit.aircraft().logFor(current.serial()).at(on);
        Finding updated = current.reMeasured(geometry, on, at);
        unit.findings().recordRevision(updated);
        Disposition after = disposition(unit, book, catalogue, updated, on);
        unit.flush();
        return new ReMeasured(updated, before, after, isDeferred(unit, reference));
    }

    private static boolean isDeferred(UnitOfWork unit, FindingId reference) {
        try {
            unit.deferrals().get(reference);
            return true;
        } catch (DoublerbayError notDeferred) {
            return false;
        }
    }

    /** Every revision of one card, oldest first. */
    public static List<Finding> historyOf(UnitOfWork unit, FindingId reference) {
        return unit.findings().history(reference);
    }

    /**
     * Whether the worst measurement on the card is not the first one.
     *
     * <p>Deliberately crude. It answers whether anything moved at all, which is the question that
     * decides if the growth report is worth opening. How much it moved depends on the kind and belongs
     * with the kind.
     */
    public static boolean grewBetween(UnitOfWork unit, FindingId reference) {
        List<Finding> revisions = historyOf(unit, reference);
        if (revisions.size() < 2) {
            return false;
        }
        Geometry first = revisions.get(0).geometry();
        return revisions.subList(1, revisions.size()).stream()
                .anyMatch(one -> !one.geometry().equals(first));
    }

    private static Disposition disposition(
            UnitOfWork unit, Book book, TypeCatalogue catalogue, Finding finding, LocalDate on) {
        TailContext tail = StoreContext.contextFor(unit, catalogue, finding.serial(), on);
        return Assessor.assess(book, tail, finding).disposition();
    }
}
