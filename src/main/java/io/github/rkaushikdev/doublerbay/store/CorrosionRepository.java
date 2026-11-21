package io.github.rkaushikdev.doublerbay.store;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.tolerance.CorrosionEntry;
import io.github.rkaushikdev.doublerbay.tolerance.CorrosionLevel;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;

/**
 * The corrosion programme's own register, which is not the damage register.
 *
 * <p>Every entry here started as a damage card, and the two are kept apart because they answer to
 * different people. The card is closed when the corrosion is blended out. The programme entry stays,
 * because the question it answers is whether this place keeps corroding, and that is only visible
 * over years.
 */
public final class CorrosionRepository {

    private final EntityManager session;

    CorrosionRepository(EntityManager session) {
        this.session = session;
    }

    /** File a graded finding against the tail and the card it came off, not reported yet. */
    public void record(CorrosionEntry entry, Serial serial, FindingId against) {
        record(entry, serial, against, null);
    }

    /** File a graded finding against the tail and the card it came off; a null day is not reported. */
    public void record(CorrosionEntry entry, Serial serial, FindingId against, LocalDate reported) {
        session.persist(Decisions.corrosionToRow(entry, serial, against, reported));
    }

    /** Every graded finding on one aeroplane, oldest first. */
    public List<CorrosionEntry> onTail(Serial serial) {
        return rowsOn(serial).stream().map(Decisions::rowToCorrosion).toList();
    }

    /** Every graded finding on one panel of one aeroplane, oldest first. */
    public List<CorrosionEntry> onPanel(Serial serial, PanelId part) {
        return session
                .createQuery(
                        "select c from CorrosionRow c where c.serial = :serial and c.part = :part"
                                + " order by c.foundOn",
                        CorrosionRow.class)
                .setParameter("serial", serial.number())
                .setParameter("part", part)
                .getResultList()
                .stream()
                .map(Decisions::rowToCorrosion)
                .toList();
    }

    /** Every finding across the fleet graded at one level, oldest first. */
    public List<CorrosionEntry> atLevel(CorrosionLevel level) {
        return session
                .createQuery(
                        "select c from CorrosionRow c where c.level = :level order by c.foundOn", CorrosionRow.class)
                .setParameter("level", level.value())
                .getResultList()
                .stream()
                .map(Decisions::rowToCorrosion)
                .toList();
    }

    /** Level three findings the authority has not been told about yet, oldest first. */
    public List<CorrosionEntry> unreported() {
        return session
                .createQuery(
                        "select c from CorrosionRow c where c.level = :level and c.reportedOn is null"
                                + " order by c.foundOn",
                        CorrosionRow.class)
                .setParameter("level", CorrosionLevel.THREE.value())
                .getResultList()
                .stream()
                .map(Decisions::rowToCorrosion)
                .toList();
    }

    /** Record that a level three finding went out, refusing a date before it was found. */
    public void markReported(FindingId against, LocalDate on) {
        List<CorrosionRow> rows = session.createQuery(
                        "select c from CorrosionRow c where c.against = :against order by c.id", CorrosionRow.class)
                .setParameter("against", against)
                .setMaxResults(1)
                .getResultList();
        if (rows.isEmpty()) {
            throw new NotOnFile(against + " is not on the corrosion register");
        }
        CorrosionRow held = rows.get(0);
        if (on.isBefore(held.getFoundOn())) {
            throw new BadReading(against + " cannot be reported before it was found");
        }
        held.setReportedOn(on);
    }

    /** Whether this panel has corroded before a day, which is what makes a level two. */
    public boolean hasFormHere(Serial serial, PanelId part, LocalDate before) {
        return Decisions.seenHereBefore(rowsOn(serial), part, before);
    }

    private List<CorrosionRow> rowsOn(Serial serial) {
        return session.createQuery(
                        "select c from CorrosionRow c where c.serial = :serial order by c.foundOn", CorrosionRow.class)
                .setParameter("serial", serial.number())
                .getResultList();
    }
}
