package io.github.rkaushikdev.doublerbay.store;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Damage cards, every revision of every one of them. */
public final class FindingRepository {

    private final EntityManager session;

    FindingRepository(EntityManager session) {
        this.session = session;
    }

    /** Put a new card on the register at revision one, refusing a number already used. */
    public void raiseFinding(Finding finding) {
        if (!revisions(finding.reference()).isEmpty()) {
            throw new BadReading(finding.reference() + " has already been raised");
        }
        if (finding.revision() != 1) {
            throw new BadReading(finding.reference() + " is raised at revision one");
        }
        session.persist(Mapping.findingToRow(finding));
    }

    /** Store a later revision of a card that is already on file. */
    public void recordRevision(Finding finding) {
        List<FindingRow> existing = revisions(finding.reference());
        if (existing.isEmpty()) {
            throw new NotOnFile(finding.reference() + " is not a finding on this register");
        }
        int latest = existing.get(existing.size() - 1).getRevision();
        if (finding.revision() <= latest) {
            throw new BadReading(finding.reference() + " is already at revision " + latest);
        }
        session.persist(Mapping.findingToRow(finding));
    }

    /**
     * Close a card because another one now carries the damage.
     *
     * <p>This is not a re-measurement and it does not make a revision. The latest row is marked in
     * place, because the measurements did not change and a revision that says nothing new is a
     * revision nobody can read.
     */
    public Finding supersede(FindingId reference, FindingId by) {
        List<FindingRow> rows = revisions(reference);
        if (rows.isEmpty()) {
            throw new NotOnFile(reference + " is not a finding on this register");
        }
        FindingRow last = rows.get(rows.size() - 1);
        Finding closed = Mapping.rowToFinding(last).superseded(by);
        last.setSupersededBy(by);
        return closed;
    }

    /** The latest revision of one card. */
    public Finding latest(FindingId reference) {
        List<FindingRow> rows = revisions(reference);
        if (rows.isEmpty()) {
            throw new NotOnFile(reference + " is not a finding on this register");
        }
        return Mapping.rowToFinding(rows.get(rows.size() - 1));
    }

    /** Every revision of one card, first to last. */
    public List<Finding> history(FindingId reference) {
        List<FindingRow> rows = revisions(reference);
        if (rows.isEmpty()) {
            throw new NotOnFile(reference + " is not a finding on this register");
        }
        return rows.stream().map(Mapping::rowToFinding).toList();
    }

    /**
     * Every card on one tail, latest revision only, in one query. The open damage report calls this
     * once per aeroplane, so reading each card back on its own would be a round trip per card.
     */
    public List<Finding> onTail(Serial serial) {
        return latestOf("f.serial = :value", serial.number());
    }

    /** The cards on one tail that are still open. */
    public List<Finding> openOn(Serial serial) {
        return onTail(serial).stream().filter(Finding::isOpen).toList();
    }

    /** Every card of one kind across the fleet, latest revision only. */
    public List<Finding> ofKind(DamageKind kind) {
        return latestOf("f.kind = :value", kind.value());
    }

    /**
     * Which tail each of a set of cards is on, in one query rather than one per card. A card that is
     * not on file is simply absent, and an empty set runs no query at all.
     */
    public Map<FindingId, Serial> serialsFor(Collection<FindingId> references) {
        if (references.isEmpty()) {
            return Map.of();
        }
        List<String> keys = references.stream().map(FindingId::toString).toList();
        Map<FindingId, Serial> found = new LinkedHashMap<>();
        for (FindingRow row : session.createQuery(
                        "select f from FindingRow f where f.reference in :keys", FindingRow.class)
                .setParameter("keys", keys)
                .getResultList()) {
            found.put(row.getReference(), row.getSerial());
        }
        return found;
    }

    /** The next report number in a year, carrying on from the highest one used. */
    public FindingId nextReference(int year) {
        int highest = 0;
        for (String used : session.createQuery("select distinct f.reference from FindingRow f", String.class)
                .getResultList()) {
            FindingId one = FindingId.parse(used);
            if (one.year() == year) {
                highest = Math.max(highest, one.sequence());
            }
        }
        return new FindingId(year, highest + 1);
    }

    /**
     * The latest revision of every card matching a condition, in one read. Ordered by reference
     * then revision, so walking the rows in order leaves the last one seen per reference as the
     * latest.
     */
    private List<Finding> latestOf(String where, String value) {
        Map<String, FindingRow> latest = new LinkedHashMap<>();
        for (FindingRow row : session.createQuery(
                        "select f from FindingRow f where " + where + " order by f.reference, f.revision",
                        FindingRow.class)
                .setParameter("value", value)
                .getResultList()) {
            latest.put(row.getReference().toString(), row);
        }
        List<String> references = new ArrayList<>(latest.keySet());
        references.sort(Comparator.naturalOrder());
        return references.stream()
                .map(one -> Mapping.rowToFinding(latest.get(one)))
                .toList();
    }

    private List<FindingRow> revisions(FindingId reference) {
        return session.createQuery(
                        "select f from FindingRow f where f.reference = :reference order by f.revision",
                        FindingRow.class)
                .setParameter("reference", reference.toString())
                .getResultList();
    }
}
