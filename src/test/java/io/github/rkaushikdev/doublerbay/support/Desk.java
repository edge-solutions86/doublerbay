package io.github.rkaushikdev.doublerbay.support;

import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.disposition.DispositionRecord;
import io.github.rkaushikdev.doublerbay.disposition.DispositionState;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import java.time.LocalDate;
import java.util.Map;

/**
 * The fixtures every storage, service, route and command test starts from: a database with the
 * schema on it, a tail enrolled with its log open, and cards raised and assessed against it.
 *
 * <p>The pinned day is the part worth keeping in one place. Every test answers as of the same
 * fourteenth of March, so the answers of any two tests can be compared.
 */
public final class Desk {

    /** The day every test in this suite answers as of. */
    public static final LocalDate TODAY = LocalDate.of(2025, 3, 14);

    /** The development key for each role, as the header a client sends. */
    public static final Map<String, String> KEYS = Map.of(
            "reader", "reader-key-0001",
            "mechanic", "mechanic-key-0001",
            "engineer", "engineer-key-0001",
            "chief", "chief-key-0001");

    /** The header every request carries its key in. */
    public static final String KEY_HEADER = "x-api-key";

    private Desk() {}

    /** The headers a client acting as one role sends: that role's key under {@link #KEY_HEADER}. */
    public static Map<String, String> headersFor(String role) {
        String key = KEYS.get(role);
        if (key == null) {
            throw new IllegalArgumentException(role + " is not a role with a key");
        }
        return Map.of(KEY_HEADER, key);
    }

    /** The day a request is answered as of when the suite pins it, which is {@link #TODAY}. */
    public static LocalDate answeredOn() {
        return answeredOn(TODAY);
    }

    /** The day a request is answered as of: the pinned day, or the real today when none is pinned. */
    public static LocalDate answeredOn(LocalDate pinned) {
        return pinned != null ? pinned : LocalDate.now();
    }

    /** An empty database with the schema on it, thrown away after the test. */
    public static Database newDatabase() {
        return Databases.fresh();
    }

    /** A database with one tail on it and its log open, which is where most tests start. */
    public static Database stocked() {
        Database made = newDatabase();
        enrolled(made);
        return made;
    }

    /** Put the sample tail on the register with its log open. */
    public static void enrolled(Database database) {
        enrolled(database, "5127");
    }

    /** Put one tail on the register with its log open. */
    public static void enrolled(Database database, String serial) {
        UnitOfWork.run(database, unit -> {
            unit.aircraft().add(Sample.aircraft(serial));
            unit.aircraft().openLog(Sample.flyingLog(serial));
        });
    }

    /** Put the default card on the register as card one, without dispositioning it. */
    public static FindingId raiseCard(Database database) {
        return raiseCard(database, Sample.card(1));
    }

    /** Put the default card on the register under a sequence number. */
    public static FindingId raiseCard(Database database, int sequence) {
        return raiseCard(database, Sample.card(sequence));
    }

    /** Put the default card on another tail under a sequence number. */
    public static FindingId raiseCard(Database database, int sequence, String serial) {
        return raiseCard(database, Sample.card(sequence, serial));
    }

    /** Put one card on the register and open its disposition, as raised. */
    public static FindingId raiseCard(Database database, Finding card) {
        UnitOfWork.run(database, unit -> {
            unit.findings().raiseFinding(card);
            unit.dispositions().save(DispositionRecord.raised(card.reference()));
        });
        return card.reference();
    }

    /** Move a card to assessed on the pinned day, which is where every decision starts. */
    public static FindingId assessCard(Database database, FindingId reference) {
        return assessCard(database, reference, TODAY, "a.okonkwo");
    }

    /** Move a card to assessed on a day, by someone. */
    public static FindingId assessCard(Database database, FindingId reference, LocalDate on, String by) {
        UnitOfWork.run(database, unit -> {
            DispositionRecord held = unit.dispositions().get(reference);
            unit.dispositions().save(held.movedTo(DispositionState.ASSESSED, on, by));
        });
        return reference;
    }

    /** A card on file that has been looked at, which is what a deferral needs. */
    public static FindingId assessedCard(Database database) {
        return assessCard(database, raiseCard(database));
    }

    /** The same, under a sequence number. */
    public static FindingId assessedCard(Database database, int sequence) {
        return assessCard(database, raiseCard(database, sequence));
    }

    /** The same, under a sequence number on another tail. */
    public static FindingId assessedCard(Database database, int sequence, String serial) {
        return assessCard(database, raiseCard(database, sequence, serial));
    }

    /** The same, for a card of the caller's own. */
    public static FindingId assessedCard(Database database, Finding card) {
        return assessCard(database, raiseCard(database, card));
    }
}
