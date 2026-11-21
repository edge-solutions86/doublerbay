package io.github.rkaushikdev.doublerbay.store;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.ids.RepairId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.repair.Clash;
import io.github.rkaushikdev.doublerbay.repair.RepairRecord;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;

/** Repairs, keyed on their own number and read back by tail or by panel. */
public final class RepairRepository {

    private final EntityManager session;

    RepairRepository(EntityManager session) {
        this.session = session;
    }

    /** Record a repair as fitted to one tail, refusing a number already used. */
    public void embody(RepairRecord record, Serial serial) {
        if (row(record.reference()) != null) {
            throw new BadReading(record.reference() + " is already on file");
        }
        session.persist(Decisions.repairToRow(record, serial));
    }

    /** One repair by its number, fitted or not. */
    public RepairRecord get(RepairId reference) {
        RepairRow held = row(reference);
        if (held == null) {
            throw new NotOnFile(reference + " is not a repair on file");
        }
        return Decisions.rowToRepair(held);
    }

    /** Mark a repair as taken off, which is not the same as deleting it. */
    public void remove(RepairId reference, LocalDate on) {
        RepairRow held = row(reference);
        if (held == null) {
            throw new NotOnFile(reference + " is not a repair on file");
        }
        if (held.getRemoved().isPresent()) {
            throw new BadReading(
                    reference + " was already removed on " + held.getRemoved().get());
        }
        if (on.isBefore(held.getEmbodied())) {
            throw new BadReading(reference + " cannot come off before it went on");
        }
        held.setRemoved(on);
    }

    /** Every repair still fitted to one aeroplane, oldest first. */
    public List<RepairRecord> onTail(Serial serial) {
        return onTail(serial, true);
    }

    /** Every repair on one aeroplane, oldest first: still fitted, or ever fitted. */
    public List<RepairRecord> onTail(Serial serial, boolean fittedOnly) {
        String fitted = fittedOnly ? " and r.removed is null" : "";
        return session
                .createQuery(
                        "select r from RepairRow r where r.serial = :serial" + fitted + " order by r.embodied",
                        RepairRow.class)
                .setParameter("serial", serial.number())
                .getResultList()
                .stream()
                .map(Decisions::rowToRepair)
                .toList();
    }

    /**
     * Every repair still fitted to one panel of one tail, oldest first.
     *
     * <p>The tail is part of the question. A panel number names a part, and asking what is fitted to
     * that part without saying which aeroplane gathers up the whole fleet's repairs into one clash
     * check.
     */
    public List<RepairRecord> onPanel(Serial serial, PanelId part) {
        return session
                .createQuery(
                        "select r from RepairRow r where r.serial = :serial and r.part = :part"
                                + " and r.removed is null order by r.embodied",
                        RepairRow.class)
                .setParameter("serial", serial.number())
                .setParameter("part", part)
                .getResultList()
                .stream()
                .map(Decisions::rowToRepair)
                .toList();
    }

    /** Every repair raised against one damage card, oldest first. */
    public List<RepairRecord> against(FindingId reference) {
        return session
                .createQuery(
                        "select r from RepairRow r where r.against = :against order by r.embodied", RepairRow.class)
                .setParameter("against", reference)
                .getResultList()
                .stream()
                .map(Decisions::rowToRepair)
                .toList();
    }

    /** Pairs of fitted repairs on one panel of one tail that are too close together. */
    public List<Clash> clashingOn(Serial serial, PanelId part) {
        return RepairRecord.clashes(onPanel(serial, part));
    }

    /** The fitted repairs on one tail that carry a repeat inspection. */
    public List<RepairRecord> needingInspection(Serial serial) {
        return onTail(serial).stream().filter(RepairRecord::needsInspection).toList();
    }

    private RepairRow row(RepairId reference) {
        return session.find(RepairRow.class, reference.toString());
    }
}
