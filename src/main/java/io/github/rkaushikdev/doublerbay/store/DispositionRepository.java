package io.github.rkaushikdev.doublerbay.store;

import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.disposition.DispositionRecord;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import jakarta.persistence.EntityManager;
import java.util.List;

/** Where each card is, and how it got there. */
public final class DispositionRepository {

    private final EntityManager session;

    DispositionRepository(EntityManager session) {
        this.session = session;
    }

    /**
     * Write the state and the whole history, replacing whatever was there. The old row goes with
     * all its moves before the new one goes in, so the new moves never collide with the old ones on
     * their sequence.
     */
    public void save(DispositionRecord record) {
        DispositionRow held =
                session.find(DispositionRow.class, record.reference().toString());
        if (held != null) {
            session.remove(held);
        }
        session.flush();
        session.persist(Mapping.dispositionToRow(record));
    }

    /** Where one card is. */
    public DispositionRecord get(FindingId reference) {
        DispositionRow row = session.find(DispositionRow.class, reference.toString());
        if (row == null) {
            throw new NotOnFile(reference + " has no disposition on file");
        }
        return Mapping.rowToDisposition(row);
    }

    /** Every card in one state, by the state's stored word. */
    public List<DispositionRecord> inState(String state) {
        return session
                .createQuery("select d from DispositionRow d where d.state = :state", DispositionRow.class)
                .setParameter("state", state)
                .getResultList()
                .stream()
                .map(Mapping::rowToDisposition)
                .toList();
    }

    /** Every card's disposition, in reference order. */
    public List<DispositionRecord> all() {
        return session
                .createQuery("select d from DispositionRow d order by d.reference", DispositionRow.class)
                .getResultList()
                .stream()
                .map(Mapping::rowToDisposition)
                .toList();
    }
}
