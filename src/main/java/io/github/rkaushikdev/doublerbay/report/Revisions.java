package io.github.rkaushikdev.doublerbay.report;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.DoublerbayError;
import io.github.rkaushikdev.doublerbay.assess.Assessor;
import io.github.rkaushikdev.doublerbay.assess.Disposition;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.fleet.Aircraft;
import io.github.rkaushikdev.doublerbay.fleet.TailContext;
import io.github.rkaushikdev.doublerbay.fleet.TypeCatalogue;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.manual.Book;
import io.github.rkaushikdev.doublerbay.store.StoreContext;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * What a new revision of the manual does to the cards already on the register.
 *
 * <p>A revision lands and the desk has a problem nobody schedules time for: every open card was
 * dispositioned against the old book, and some number of them would be dispositioned differently
 * now. Nothing tells anybody which ones. The cards do not change, so no report moves, and the first
 * sign of it is an audit asking why a card is being flown against a limit that was withdrawn.
 *
 * <p>So this reassesses every open card twice, once as of a day before the revision and once as of a
 * day after it, and lists the ones where the answer moved. The list is usually short, which is the
 * point: an engineer can work through nine cards in an afternoon and cannot work through four
 * hundred.
 *
 * <p>It moves in both directions on purpose. A revision that relaxes a limit turns somebody's
 * grounded aeroplane back into a flying one, and that is worth an afternoon too.
 */
public final class Revisions {

    private Revisions() {}

    /** What the book makes of one card on one day, or nothing if it cannot say. */
    private static Optional<Disposition> answer(
            UnitOfWork unit, Book book, TypeCatalogue catalogue, Finding finding, LocalDate on) {
        try {
            TailContext tail = StoreContext.contextFor(unit, catalogue, finding.serial(), on);
            return Optional.of(Assessor.assess(book, tail, finding).disposition());
        } catch (DoublerbayError cannotSay) {
            return Optional.empty();
        }
    }

    /** Reassess every open card on both sides of a revision and list what moved. */
    public static Sweep sweep(UnitOfWork unit, Book book, TypeCatalogue catalogue, LocalDate before, LocalDate after) {
        if (!after.isAfter(before)) {
            throw new BadReading("the day after a revision comes after the day before it");
        }

        List<Moved> moved = new ArrayList<>();
        List<FindingId> unreadable = new ArrayList<>();
        int lookedAt = 0;
        for (Aircraft aircraft : unit.aircraft().inServiceOn(after)) {
            for (Finding finding : unit.findings().openOn(aircraft.serial())) {
                lookedAt++;
                Optional<Disposition> was = answer(unit, book, catalogue, finding, before);
                Optional<Disposition> now = answer(unit, book, catalogue, finding, after);
                if (was.isEmpty() || now.isEmpty()) {
                    unreadable.add(finding.reference());
                    continue;
                }
                if (was.get() != now.get()) {
                    moved.add(new Moved(finding.reference(), finding.serial(), was.get(), now.get()));
                }
            }
        }

        moved.sort(Comparator.comparingInt((Moved one) -> -one.now().rank())
                .thenComparing(one -> one.reference().toString()));
        unreadable.sort(Comparator.comparing(FindingId::toString));
        return new Sweep(before, after, lookedAt, moved, unreadable);
    }

    /** The cards on one tail that the revision made worse. */
    public static List<Moved> worseOn(Sweep sweep, Serial serial) {
        return sweep.worse().stream().filter(one -> one.serial().equals(serial)).toList();
    }
}
