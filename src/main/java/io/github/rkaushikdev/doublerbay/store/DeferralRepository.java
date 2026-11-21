package io.github.rkaushikdev.doublerbay.store;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.disposition.Deferral;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Deferrals, one live one per card, keyed on the card it is against. */
public final class DeferralRepository {

    private final EntityManager session;

    DeferralRepository(EntityManager session) {
        this.session = session;
    }

    /**
     * Record a deferral, refusing a second live one against the same card. A lifted one against the
     * card gives up its row to the new one.
     */
    public void grant(Deferral deferral) {
        DeferralRow held = row(deferral.against());
        if (held != null && held.getLifted().isEmpty()) {
            throw new BadReading(deferral.against() + " is already deferred");
        }
        if (held != null) {
            session.remove(held);
            session.flush();
        }
        session.persist(Decisions.deferralToRow(deferral));
    }

    /** Amend the live deferral against a card to a shorter limit. */
    public void tighten(Deferral deferral) {
        Decisions.applyToRow(live(deferral.against()), deferral);
    }

    /** Close a deferral because the damage under it has been dealt with. */
    public void lift(FindingId reference, LocalDate on) {
        DeferralRow held = live(reference);
        if (on.isBefore(held.getGranted())) {
            throw new BadReading(reference + ": a deferral cannot be lifted before it was granted");
        }
        held.setLifted(on);
    }

    /** The live deferral against one card. */
    public Deferral get(FindingId reference) {
        return Decisions.rowToDeferral(live(reference));
    }

    /** Every deferral still being flown against, in the order they were granted. */
    public List<Deferral> live() {
        return session
                .createQuery("select d from DeferralRow d where d.lifted is null order by d.granted", DeferralRow.class)
                .getResultList()
                .stream()
                .map(Decisions::rowToDeferral)
                .toList();
    }

    /** The live deferrals against a named set of cards, in the order asked for. */
    public List<Deferral> against(List<FindingId> references) {
        Map<FindingId, Deferral> held = new HashMap<>();
        for (Deferral one : live()) {
            held.put(one.against(), one);
        }
        List<Deferral> asked = new ArrayList<>();
        for (FindingId reference : references) {
            Deferral one = held.get(reference);
            if (one != null) {
                asked.add(one);
            }
        }
        return List.copyOf(asked);
    }

    /** Everything granted in a window, both days inside it, lifted or not, for the monthly report. */
    public List<Deferral> grantedBetween(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw new BadReading("a window ends after it starts");
        }
        return session
                .createQuery(
                        "select d from DeferralRow d where d.granted >= :start and d.granted <= :end"
                                + " order by d.granted",
                        DeferralRow.class)
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList()
                .stream()
                .map(Decisions::rowToDeferral)
                .toList();
    }

    private DeferralRow row(FindingId reference) {
        return session.find(DeferralRow.class, reference.toString());
    }

    private DeferralRow live(FindingId reference) {
        DeferralRow held = row(reference);
        if (held == null || held.getLifted().isPresent()) {
            throw new NotOnFile(reference + " is not deferred");
        }
        return held;
    }
}
