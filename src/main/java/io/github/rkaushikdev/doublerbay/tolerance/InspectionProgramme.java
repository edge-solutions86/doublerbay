package io.github.rkaushikdev.doublerbay.tolerance;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.ids.RepairId;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The repeat inspections one aeroplane is carrying, and which of them are due.
 *
 * <p>Every category B repair puts a task on the aeroplane for the rest of its life, and a tail that has
 * been flying for fifteen years carries a lot of them. The programme is that list, and the only two
 * questions ever asked of it are what is due now and what is due by the time it next comes into a
 * hangar.
 *
 * <p>Signing an inspection off resets its clock, which is why the programme is a record that changes
 * rather than a calculation. A repair has at most one task on it, and the tasks come back in repair
 * number order.
 */
public final class InspectionProgramme implements Iterable<InspectionTask> {

    private final Map<RepairId, InspectionTask> tasks = new HashMap<>();

    /** An empty programme. */
    public InspectionProgramme() {}

    /**
     * Put a task on the programme.
     *
     * @throws BadReading when the repair already has one
     */
    public void add(InspectionTask task) {
        if (tasks.containsKey(task.against())) {
            throw new BadReading(task.against() + " already has an inspection on the programme");
        }
        tasks.put(task.against(), task);
    }

    /**
     * The task on a repair.
     *
     * @throws NotOnFile when the repair has none
     */
    public InspectionTask taskFor(RepairId repair) {
        InspectionTask task = tasks.get(repair);
        if (task == null) {
            throw new NotOnFile(repair + " has no inspection on the programme");
        }
        return task;
    }

    /**
     * Sign a repair's task off, keeping the new state, and hand it back.
     *
     * @throws NotOnFile when the repair has no task
     */
    public InspectionTask signOff(RepairId repair, LocalDate on, Usage flown) {
        InspectionTask signed = taskFor(repair).signedOff(on, flown);
        tasks.put(repair, signed);
        return signed;
    }

    /**
     * Take a task off, which happens when the repair it belongs to comes off.
     *
     * @throws NotOnFile when the repair has no task
     */
    public void drop(RepairId repair) {
        if (!tasks.containsKey(repair)) {
            throw new NotOnFile(repair + " has no inspection on the programme");
        }
        tasks.remove(repair);
    }

    /** The tasks that have landed, in repair number order. */
    public List<InspectionTask> dueOn(Usage flown, LocalDate today) {
        return ordered().stream().filter(task -> task.isDue(flown, today)).toList();
    }

    /** The tasks nobody has done yet, in repair number order. */
    public List<InspectionTask> neverDone() {
        return ordered().stream().filter(task -> !task.hasBeenDone()).toList();
    }

    private List<InspectionTask> ordered() {
        return tasks.values().stream()
                .sorted(Comparator.comparing(task -> task.against().toString()))
                .toList();
    }

    /** Every task, in repair number order. */
    @Override
    public Iterator<InspectionTask> iterator() {
        return ordered().iterator();
    }

    /** How many tasks the tail is carrying. */
    public int size() {
        return tasks.size();
    }

    /** Whether a repair has a task on the programme. */
    public boolean contains(RepairId repair) {
        return tasks.containsKey(repair);
    }

    /** {@code 2 repeat inspections}. */
    @Override
    public String toString() {
        return tasks.size() + " repeat inspections";
    }
}
