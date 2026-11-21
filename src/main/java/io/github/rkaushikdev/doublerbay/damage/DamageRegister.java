package io.github.rkaushikdev.doublerbay.damage;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every damage report the desk holds, with every revision of each of them: the dent map, kept as a
 * card per damage and a revision per look at it.
 *
 * <p>A finding is not a row that gets updated. It is a card that gets re measured, and the earlier
 * measurement is the thing an engineer needs when the question is how fast it is growing. So the
 * register keeps every revision and hands back the latest one unless somebody asks for the history.
 *
 * <p>Superseding is separate from re measuring and the two get confused. A re measurement is the same
 * damage looked at again. A supersession is this record being closed because another record now
 * describes the damage, which happens when two cards turn out to be the same dent found twice, and it
 * is the only way a finding leaves the open list without being repaired.
 *
 * <p>Every list it hands back holds the latest revision of each card in report number order, unless
 * it says otherwise.
 */
public final class DamageRegister implements Iterable<Finding> {

    private static final Comparator<Finding> BY_REFERENCE =
            Comparator.comparing(card -> card.reference().toString());

    private final Map<FindingId, List<Finding>> cards = new LinkedHashMap<>();

    /** An empty register. */
    public DamageRegister() {}

    /** Put a new card on the register, which has to be at its first revision and not already on it. */
    public void raiseFinding(Finding finding) {
        if (cards.containsKey(finding.reference())) {
            throw new BadReading(finding.reference() + " has already been raised");
        }
        if (finding.revision() != 1) {
            throw new BadReading(finding.reference() + " is raised at revision one");
        }
        List<Finding> revisions = new ArrayList<>();
        revisions.add(finding);
        cards.put(finding.reference(), revisions);
    }

    /** The card as it stands now. */
    public Finding latest(FindingId reference) {
        List<Finding> revisions = revisions(reference);
        return revisions.get(revisions.size() - 1);
    }

    /** Every revision of one card, oldest first. */
    public List<Finding> history(FindingId reference) {
        return List.copyOf(revisions(reference));
    }

    /** Look at an open card again and file what was found as its next revision. */
    public Finding reMeasure(FindingId reference, Geometry geometry, LocalDate on, Usage atUsage) {
        Finding current = latest(reference);
        if (!current.isOpen()) {
            throw new BadReading(reference + " has been superseded and is not re measured");
        }
        Finding updated = current.reMeasured(geometry, on, atUsage);
        cards.get(reference).add(updated);
        return updated;
    }

    /** Close an open card because another card on the register now describes the damage. */
    public Finding supersede(FindingId reference, FindingId by) {
        if (!cards.containsKey(by)) {
            throw new NotOnFile(by + " is not a finding on this register");
        }
        Finding current = latest(reference);
        if (!current.isOpen()) {
            throw new BadReading(reference + " has already been superseded");
        }
        Finding closed = current.superseded(by);
        cards.get(reference).add(closed);
        return closed;
    }

    /** Every card raised against one tail, superseded ones included. */
    public List<Finding> onTail(Serial serial) {
        return latestCards().stream()
                .filter(card -> card.serial().equals(serial))
                .toList();
    }

    /** The cards on one tail that nothing has superseded. */
    public List<Finding> openOn(Serial serial) {
        return onTail(serial).stream().filter(Finding::isOpen).toList();
    }

    /** Every card of one kind. */
    public List<Finding> ofKind(DamageKind kind) {
        return latestCards().stream().filter(card -> card.kind() == kind).toList();
    }

    /**
     * Cards raised in a window, both ends included, taken on the day the damage was first found.
     *
     * <p>The cards come back as first raised, earliest first and in report number order on one day.
     */
    public List<Finding> foundBetween(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw new BadReading(end + " is before " + start);
        }
        return cards.values().stream()
                .map(revisions -> revisions.get(0))
                .filter(card ->
                        !card.foundOn().isBefore(start) && !card.foundOn().isAfter(end))
                .sorted(Comparator.comparing(Finding::foundOn).thenComparing(BY_REFERENCE))
                .toList();
    }

    /** The next unused number in a year, which is how the shop raises a card. */
    public FindingId nextReference(int year) {
        int highest = 0;
        for (FindingId reference : cards.keySet()) {
            if (reference.year() == year) {
                highest = Math.max(highest, reference.sequence());
            }
        }
        return new FindingId(year, highest + 1);
    }

    private List<Finding> revisions(FindingId reference) {
        List<Finding> held = cards.get(reference);
        if (held == null) {
            throw new NotOnFile(reference + " is not a finding on this register");
        }
        return held;
    }

    private List<Finding> latestCards() {
        return cards.values().stream()
                .map(revisions -> revisions.get(revisions.size() - 1))
                .sorted(BY_REFERENCE)
                .toList();
    }

    /** The latest revision of every card, in report number order. */
    @Override
    public Iterator<Finding> iterator() {
        return latestCards().iterator();
    }

    /** How many cards are on the register, however often each has been re measured. */
    public int size() {
        return cards.size();
    }

    /** Whether a card with this number is on the register. */
    public boolean contains(FindingId reference) {
        return cards.containsKey(reference);
    }

    /** {@code 2 cards, 1 open}. */
    @Override
    public String toString() {
        long open = latestCards().stream().filter(Finding::isOpen).count();
        return cards.size() + " cards, " + open + " open";
    }
}
