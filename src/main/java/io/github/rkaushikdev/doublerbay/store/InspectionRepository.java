package io.github.rkaushikdev.doublerbay.store;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.ids.RepairId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionTask;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;

/**
 * The repeat inspections the fitted repairs carry.
 *
 * <p>A task hangs off its repair by the repair's own number. That is the whole key, because a repair
 * carries at most one repeat inspection, and a second one against the same doubler means somebody
 * has raised the same task twice rather than tightened the first.
 */
public final class InspectionRepository {

    private final EntityManager session;

    InspectionRepository(EntityManager session) {
        this.session = session;
    }

    /** Put a repeat inspection on a repair, refusing a second one. */
    public void add(InspectionTask task) {
        if (session.find(RepairRow.class, task.against().toString()) == null) {
            throw new NotOnFile(task.against() + " is not a repair on file");
        }
        if (session.find(InspectionRow.class, task.against().toString()) != null) {
            throw new BadReading(task.against() + " already carries a repeat inspection");
        }
        session.persist(Decisions.taskToRow(task));
    }

    /** The repeat inspection one repair carries. */
    public InspectionTask get(RepairId reference) {
        return Decisions.rowToTask(row(reference));
    }

    /** Record that the inspection was done, at the counters it was done at. */
    public InspectionTask signOff(RepairId reference, LocalDate on, Usage flown) {
        InspectionRow held = row(reference);
        InspectionTask signed = Decisions.rowToTask(held).signedOff(on, flown);
        held.setLastDone(signed.lastDone().orElse(null));
        held.setLastCycles(flown.cycles());
        held.setLastHours(flown.hours());
        return signed;
    }

    /** Take a repeat inspection off, which is what removing its repair does. */
    public void drop(RepairId reference) {
        session.remove(row(reference));
    }

    /** Every repeat inspection on the fitted repairs of one aeroplane, oldest repair first. */
    public List<InspectionTask> onTail(Serial serial) {
        return session
                .createQuery(
                        "select i from InspectionRow i join i.repair r"
                                + " where r.serial = :serial and r.removed is null order by i.embodied",
                        InspectionRow.class)
                .setParameter("serial", serial.number())
                .getResultList()
                .stream()
                .map(Decisions::rowToTask)
                .toList();
    }

    /** The ones nobody has signed off yet, which is where the threshold bites. */
    public List<InspectionTask> neverDone(Serial serial) {
        return onTail(serial).stream().filter(one -> !one.hasBeenDone()).toList();
    }

    /** Everything on one tail that has come due, oldest repair first. */
    public List<InspectionTask> dueOn(Serial serial, Usage flown, LocalDate today) {
        return onTail(serial).stream().filter(one -> one.isDue(flown, today)).toList();
    }

    private InspectionRow row(RepairId reference) {
        InspectionRow held = session.find(InspectionRow.class, reference.toString());
        if (held == null) {
            throw new NotOnFile(reference + " carries no repeat inspection");
        }
        return held;
    }
}
