package io.github.rkaushikdev.doublerbay.service;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.DoublerbayError;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.assess.Placement;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.disposition.DispositionRecord;
import io.github.rkaushikdev.doublerbay.disposition.DispositionState;
import io.github.rkaushikdev.doublerbay.fleet.TypeCatalogue;
import io.github.rkaushikdev.doublerbay.ids.RepairId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.repair.Clash;
import io.github.rkaushikdev.doublerbay.repair.RepairPair;
import io.github.rkaushikdev.doublerbay.repair.RepairRecord;
import io.github.rkaushikdev.doublerbay.store.StoreContext;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionMethod;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionSchedule;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionTask;
import io.github.rkaushikdev.doublerbay.tolerance.Schedules;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Embodying a repair, which closes three things that were left open.
 *
 * <p>The repair itself is the obvious write. The other two are the ones that get forgotten, and both
 * of them are what a fleet audit finds two years later.
 *
 * <p>A card that has been repaired is no longer open damage. If the disposition is not moved, the
 * card sits on every open damage report forever and the desk learns to ignore that report. And a card
 * that was deferred is flying against a limit that no longer applies, so the deferral has to be
 * lifted on the day the repair went on, not whenever somebody notices.
 *
 * <p>A category B repair also carries a repeat inspection, and that gets written here too. The
 * schedule is not something the shop chooses. It falls out of the crack growth basis for the panel
 * and the smallest crack the chosen method can be relied on to find, so a repair fitted without its
 * task is a repair nobody will ever look at again.
 *
 * <p>Removing a repair is not the reverse of embodying it. The card does not reopen, because taking a
 * doubler off to fit a bigger one is not the damage coming back. But the repeat inspection goes with
 * it, because a work card against a doubler that is no longer on the aeroplane wastes an afternoon.
 */
public final class Repairs {

    /** What a permanent repair leaves the card as. */
    public static final DispositionState PERMANENT_STATE = DispositionState.CLOSED;

    /** What a temporary repair leaves the card as. */
    public static final DispositionState TEMPORARY_STATE = DispositionState.IN_WORK;

    /**
     * What the shop reaches for unless somebody says otherwise. Eddy current finds a shorter crack
     * than a detailed visual, which buys a longer interval.
     */
    public static final InspectionMethod DEFAULT_METHOD = InspectionMethod.HFEC;

    private Repairs() {}

    /** Where a repair leaves the card it was raised against. */
    public static DispositionState stateAfter(RepairRecord record) {
        return record.scheme().category().isPermanent() ? PERMANENT_STATE : TEMPORARY_STATE;
    }

    /** Fit a repair on the day it went on, inspected with {@link #DEFAULT_METHOD}. */
    public static Embodiment embody(UnitOfWork unit, RepairRecord record, Serial serial, String by) {
        return embody(unit, record, serial, by, null, DEFAULT_METHOD, null);
    }

    /** Fit a repair signed off on a day, or on the day it went on when the day is null. */
    public static Embodiment embody(
            UnitOfWork unit, RepairRecord record, Serial serial, String by, LocalDate onOrNull) {
        return embody(unit, record, serial, by, onOrNull, DEFAULT_METHOD, null);
    }

    /** Fit a repair whose repeat inspection is done with a method of the shop's choosing. */
    public static Embodiment embody(
            UnitOfWork unit,
            RepairRecord record,
            Serial serial,
            String by,
            LocalDate onOrNull,
            InspectionMethod method) {
        return embody(unit, record, serial, by, onOrNull, method, null);
    }

    /**
     * Fit a repair, move the card, and lift whatever it was flying against.
     *
     * <p>A null day signs the repair off on the day it went on, and a null catalogue places it on the
     * standard type definitions.
     */
    public static Embodiment embody(
            UnitOfWork unit,
            RepairRecord record,
            Serial serial,
            String by,
            LocalDate onOrNull,
            InspectionMethod method,
            TypeCatalogue catalogueOrNull) {
        if (Text.strip(by).isEmpty()) {
            throw new BadReading("a repair is embodied by somebody");
        }
        LocalDate day = onOrNull != null ? onOrNull : record.embodied();
        if (day.isBefore(record.embodied())) {
            throw new BadReading(record.reference() + " cannot be signed off before it was fitted");
        }

        Finding finding = unit.findings().latest(record.against());
        if (!finding.serial().equals(serial)) {
            throw new BadReading(record.against() + " is not on " + serial);
        }

        unit.repairs().embody(record, serial);
        boolean lifted = liftIfDeferred(unit, record, day);
        DispositionState state = stateAfter(record);
        walkTo(unit, record, state, day, by);
        unit.flush();
        List<RepairPair> clashes = clashPairs(unit, serial, record);
        Optional<InspectionSchedule> inspection = scheduleTask(unit, record, finding, method, catalogueOrNull);
        return new Embodiment(record.reference(), state, lifted, clashes, inspection);
    }

    /**
     * Put the repeat inspection on the repair, if its category carries one.
     *
     * <p>The schedule is worked out rather than chosen. It comes off the crack growth basis for the
     * panel kind and the smallest crack the method finds, so two engineers fitting the same repair on
     * the same panel get the same interval, which is the entire point of having a programme.
     */
    private static Optional<InspectionSchedule> scheduleTask(
            UnitOfWork unit,
            RepairRecord record,
            Finding finding,
            InspectionMethod method,
            TypeCatalogue catalogueOrNull) {
        if (!record.needsInspection()) {
            return Optional.empty();
        }
        TypeCatalogue catalogue = catalogueOrNull != null ? catalogueOrNull : new TypeCatalogue();
        Placement placement =
                Placement.place(StoreContext.contextFor(unit, catalogue, finding.serial(), record.embodied()), finding);
        Optional<InspectionSchedule> schedule =
                Schedules.scheduleFor(record.scheme().category(), placement.panelKind(), method);
        if (schedule.isEmpty()) {
            return Optional.empty();
        }
        unit.inspections()
                .add(new InspectionTask(
                        record.reference(), record.part(), schedule.get(), record.embodied(), record.atUsage()));
        return schedule;
    }

    /**
     * Move the card to where the repair leaves it, through in work if it has to.
     *
     * <p>A deferred card does not go straight to closed, and that is not an obstruction to work around.
     * A repair puts the aeroplane in the shop and then signs the card off, so both moves happened and
     * both are recorded on the day the doubler went on.
     */
    private static void walkTo(UnitOfWork unit, RepairRecord record, DispositionState state, LocalDate day, String by) {
        DispositionRecord held = unit.dispositions().get(record.against());
        String note = "repaired by " + record.reference();
        if (held.state() == state) {
            return;
        }
        if (!held.state().canMoveTo(state) && held.state().canMoveTo(TEMPORARY_STATE)) {
            held = held.movedTo(TEMPORARY_STATE, day, by, note);
            if (held.state() == state) {
                unit.dispositions().save(held);
                return;
            }
        }
        unit.dispositions().save(held.movedTo(state, day, by, note));
    }

    /** Lift the deferral this repair answers, if there was one. */
    private static boolean liftIfDeferred(UnitOfWork unit, RepairRecord record, LocalDate day) {
        try {
            unit.deferrals().lift(record.against(), day);
            return true;
        } catch (DoublerbayError nothingLive) {
            return false;
        }
    }

    /** The pairs on this panel that are now too close, this repair included. */
    private static List<RepairPair> clashPairs(UnitOfWork unit, Serial serial, RepairRecord record) {
        return unit.repairs().clashingOn(serial, record.part()).stream()
                .map(Clash::numbers)
                .toList();
    }

    /** Take a repair off, and take its repeat inspection with it. */
    public static void remove(UnitOfWork unit, RepairId reference, LocalDate on) {
        unit.repairs().remove(reference, on);
        try {
            unit.inspections().drop(reference);
        } catch (NotOnFile noTask) {
            return;
        }
    }
}
