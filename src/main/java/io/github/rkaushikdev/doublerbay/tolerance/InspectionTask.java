package io.github.rkaushikdev.doublerbay.tolerance;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.ids.RepairId;
import io.github.rkaushikdev.doublerbay.measure.DueAt;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * One repeat inspection on one repair.
 *
 * <p>A task counts from the last time it was actually done and not from when the repair went on.
 * Signing an inspection off resets its clock, and the next one is due a repeat interval after that,
 * not two repeat intervals after the repair. A completed inspection carries both the day it was done
 * and the reading the aeroplane was at.
 */
public record InspectionTask(
        RepairId against,
        PanelId part,
        InspectionSchedule schedule,
        LocalDate embodied,
        Usage atEmbodiment,
        Optional<LocalDate> lastDone,
        Optional<Usage> atLastDone) {

    /** A task last done, if ever, with a date and a reading together and not before the repair went on. */
    public InspectionTask {
        Objects.requireNonNull(against, "against");
        Objects.requireNonNull(part, "part");
        Objects.requireNonNull(schedule, "schedule");
        Objects.requireNonNull(embodied, "embodied");
        Objects.requireNonNull(atEmbodiment, "atEmbodiment");
        Objects.requireNonNull(lastDone, "lastDone");
        Objects.requireNonNull(atLastDone, "atLastDone");
        if (lastDone.isEmpty() != atLastDone.isEmpty()) {
            throw new BadReading(against + ": a completed inspection has a date and a reading");
        }
        if (lastDone.isPresent() && lastDone.get().isBefore(embodied)) {
            throw new BadReading(against + ": inspected before the repair went on");
        }
    }

    /** A task nobody has done yet. */
    public InspectionTask(
            RepairId against, PanelId part, InspectionSchedule schedule, LocalDate embodied, Usage atEmbodiment) {
        this(against, part, schedule, embodied, atEmbodiment, Optional.empty(), Optional.empty());
    }

    /** A task from what was to hand; a null date and reading are a task nobody has done yet. */
    public static InspectionTask of(
            RepairId against,
            PanelId part,
            InspectionSchedule schedule,
            LocalDate embodied,
            Usage atEmbodiment,
            LocalDate lastDone,
            Usage atLastDone) {
        return new InspectionTask(
                against,
                part,
                schedule,
                embodied,
                atEmbodiment,
                Optional.ofNullable(lastDone),
                Optional.ofNullable(atLastDone));
    }

    /** Whether anybody has done it. */
    public boolean hasBeenDone() {
        return lastDone.isPresent();
    }

    /** A temporary repair's replacement limit, which does not come round again. */
    public boolean isAOneOff() {
        return !schedule.isRepeating();
    }

    /**
     * When this task next falls due, counting from the last time it was done.
     *
     * @throws BadReading for a replacement limit that has been done, which does not come round again
     */
    public DueAt due() {
        if (lastDone.isEmpty() || atLastDone.isEmpty()) {
            return schedule.firstDue(atEmbodiment, embodied);
        }
        if (isAOneOff()) {
            throw new BadReading(against + ": a replacement limit does not come round again");
        }
        return schedule.nextDue(atLastDone.get(), lastDone.get());
    }

    /** Whether the task has landed; a replacement limit that has been done never does again. */
    public boolean isDue(Usage flown, LocalDate today) {
        if (hasBeenDone() && isAOneOff()) {
            return false;
        }
        return due().isDue(flown, today);
    }

    /**
     * The task after somebody has done it, which resets its clock. The task it came from is left as it
     * was.
     *
     * @throws BadReading for a replacement limit that has already been done
     */
    public InspectionTask signedOff(LocalDate on, Usage flown) {
        if (hasBeenDone() && isAOneOff()) {
            throw new BadReading(against + ": a replacement limit is only done once");
        }
        return new InspectionTask(against, part, schedule, embodied, atEmbodiment, Optional.of(on), Optional.of(flown));
    }

    /** {@code RP-53-0001 on 5310-143L, detailed visual, last done 2026-01-01}, or {@code last done never}. */
    @Override
    public String toString() {
        String when = lastDone.map(LocalDate::toString).orElse("never");
        return against + " on " + part + ", " + schedule.method() + ", last done " + when;
    }
}
