package io.github.rkaushikdev.doublerbay.store;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.disposition.Deferral;
import io.github.rkaushikdev.doublerbay.disposition.EngineeringOrder;
import io.github.rkaushikdev.doublerbay.disposition.OrderId;
import io.github.rkaushikdev.doublerbay.disposition.OrderState;
import io.github.rkaushikdev.doublerbay.disposition.RepairApproval;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.grid.Reference;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.FastenerSize;
import io.github.rkaushikdev.doublerbay.measure.Interval;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import io.github.rkaushikdev.doublerbay.repair.DoublerPlan;
import io.github.rkaushikdev.doublerbay.repair.RepairCategory;
import io.github.rkaushikdev.doublerbay.repair.RepairForm;
import io.github.rkaushikdev.doublerbay.repair.RepairRecord;
import io.github.rkaushikdev.doublerbay.repair.RepairScheme;
import io.github.rkaushikdev.doublerbay.structure.Material;
import io.github.rkaushikdev.doublerbay.tolerance.CorrosionEntry;
import io.github.rkaushikdev.doublerbay.tolerance.CorrosionLevel;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionMethod;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionSchedule;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionTask;
import io.github.rkaushikdev.doublerbay.util.Literal;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Turning what the desk decided into rows, and back again.
 *
 * <p>The deferral is the awkward one. Its limit is an interval, which is four optional clocks, and
 * there is no honest way to put four optional clocks in one column. Storing the printed form and
 * parsing it back would work until somebody writes a limit the parser cannot round trip, and then
 * the aeroplane is flying against a limit nobody can read. So the four clocks get four columns. It
 * is wider than it looks like it needs to be, and it means a query can ask which deferrals are
 * against a calendar clock at all, which is the question the planning desk asks every Monday.
 *
 * <p>A lifted deferral keeps its row. The record that an engineer once allowed this aeroplane to fly
 * with this damage does not stop being true because the damage was repaired afterwards.
 */
public final class Decisions {

    /** What separates one condition from the next in the stored column. */
    public static final String CONDITION_BREAK = "\n";

    /**
     * The clocks an inspection row can carry a threshold against. A schedule against flight hours or
     * a day count would be stored as nothing at all, so it is refused instead.
     */
    public static final List<String> THRESHOLD_CLOCKS = List.of("cycles", "months");

    /** The clocks an inspection row can carry a repeat against. */
    public static final List<String> REPEAT_CLOCKS = List.of("cycles");

    private Decisions() {}

    /** One granted deferral, spread across its four clocks. */
    public static DeferralRow deferralToRow(Deferral deferral) {
        Interval limit = deferral.limit();
        return new DeferralRow()
                .setAgainst(deferral.against())
                .setGranted(deferral.granted())
                .setAtCycles(deferral.atUsage().cycles())
                .setAtHours(deferral.atUsage().hours())
                .setLimitCycles(limit.cycles().orElse(null))
                .setLimitHours(limit.hours().orElse(null))
                .setLimitMonths(limit.months().orElse(null))
                .setLimitDays(limit.days().orElse(null))
                .setMethod(deferral.reInspectWith().value())
                .setGrantedBy(deferral.by())
                .setReason(deferral.reason());
    }

    /** Rebuild a deferral, refusing a row whose clocks have all gone. */
    public static Deferral rowToDeferral(DeferralRow row) {
        Interval limit;
        try {
            limit = Interval.of(
                    row.getLimitCycles().orElse(null),
                    row.getLimitHours().orElse(null),
                    row.getLimitMonths().orElse(null),
                    row.getLimitDays().orElse(null));
        } catch (BadReading unreadable) {
            throw new BadReading("the stored deferral against " + row.getAgainst() + " has no limit on it");
        }
        return new Deferral(
                row.getAgainst(),
                row.getGranted(),
                new Usage(row.getAtCycles(), row.getAtHours()),
                limit,
                InspectionMethod.fromValue(row.getMethod()),
                row.getGrantedBy(),
                row.getReason() == null ? "" : row.getReason());
    }

    /**
     * Write a tightened limit onto the row already there.
     *
     * <p>Tightening is an amendment of the same decision rather than a new one, so it keeps the
     * reference, the day it was granted and the counters it was granted at. Only the limit and who
     * set it move.
     */
    public static void applyToRow(DeferralRow row, Deferral deferral) {
        if (!row.getAgainst().equals(deferral.against())) {
            throw new BadReading("a deferral row is not amended with another card's deferral");
        }
        Interval limit = deferral.limit();
        row.setLimitCycles(limit.cycles().orElse(null))
                .setLimitHours(limit.hours().orElse(null))
                .setLimitMonths(limit.months().orElse(null))
                .setLimitDays(limit.days().orElse(null))
                .setMethod(deferral.reInspectWith().value())
                .setGrantedBy(deferral.by())
                .setReason(deferral.reason());
    }

    /** Whether the deferral on a row has been lifted. */
    public static boolean isLifted(DeferralRow row) {
        return row.getLifted().isPresent();
    }

    /** The card a deferral row is against. */
    public static FindingId referenceOf(DeferralRow row) {
        return row.getAgainst();
    }

    /**
     * One repair with its whole drawing on the row.
     *
     * <p>The material goes down as the callout a person writes on the drawing rather than as five
     * columns of alloy, temper, clad and two strengths. The strengths belong to the alloy, not to this
     * repair, and a repair that carried its own copy of them would keep whatever the catalogue said on
     * the day it was embodied.
     *
     * <p>The category goes down as its letter, which is what fits the column and what the drawing
     * says. The tail comes in beside the record because a panel number is a part number: the same
     * part hangs on every aeroplane in the fleet, so the panel does not say which one this repair is
     * on and the record does not either.
     */
    public static RepairRow repairToRow(RepairRecord record, Serial serial) {
        RepairScheme scheme = record.scheme();
        DoublerPlan plan = scheme.doubler();
        return new RepairRow()
                .setReference(record.reference())
                .setSerial(serial)
                .setPart(record.part())
                .setAgainst(record.against())
                .setForm(scheme.form().value())
                .setCategory(scheme.category().name())
                .setSealed(scheme.sealed())
                .setPage(scheme.reference().toString())
                .setMaterial(plan.material().toString())
                .setGauge(plan.gauge())
                .setAlong(plan.along())
                .setAround(plan.around())
                .setFiller(scheme.filler().orElse(null))
                .setFastenerDash(plan.size().dash())
                .setRowsEachSide(plan.rowsEachSide())
                .setPerRow(plan.perRow())
                .setPitch(plan.pitch())
                .setRowSpacing(plan.rowSpacing())
                .setEdgeDistance(plan.edgeDistance())
                .setCentreAlong(record.centre().along())
                .setCentreAround(record.centre().around())
                .setEmbodied(record.embodied())
                .setAtCycles(record.atUsage().cycles())
                .setAtHours(record.atUsage().hours())
                .setRemoved(record.removed().orElse(null));
    }

    /** Rebuild a repair, taking every number back through its own validation. */
    public static RepairRecord rowToRepair(RepairRow row) {
        DoublerPlan plan = new DoublerPlan(
                row.getGauge(),
                Material.lookup(row.getMaterial()),
                row.getAlong(),
                row.getAround(),
                new FastenerSize(row.getFastenerDash()),
                row.getRowsEachSide(),
                row.getPerRow(),
                row.getPitch(),
                row.getRowSpacing(),
                row.getEdgeDistance());
        RepairScheme scheme = new RepairScheme(
                RepairForm.fromValue(row.getForm()),
                categoryLettered(row.getCategory()),
                plan,
                row.isSealed(),
                Reference.parse(row.getPage()),
                row.getFiller());
        return new RepairRecord(
                row.getReference(),
                row.getPart(),
                row.getAgainst(),
                scheme,
                new Offset(row.getCentreAlong(), row.getCentreAround()),
                row.getEmbodied(),
                new Usage(row.getAtCycles(), row.getAtHours()),
                row.getRemoved());
    }

    /** One graded corrosion finding, filed under the tail and the card, not reported yet. */
    public static CorrosionRow corrosionToRow(CorrosionEntry entry, Serial serial, FindingId against) {
        return corrosionToRow(entry, serial, against, null);
    }

    /**
     * One graded corrosion finding, filed under the tail and the card; a null day is one not reported.
     *
     * <p>The level goes down as its number rather than its name. The programme talks about levels
     * one, two and three, the authority does too, and a row that said "level two" in words would have
     * to be translated back every time somebody sorted on it.
     */
    public static CorrosionRow corrosionToRow(
            CorrosionEntry entry, Serial serial, FindingId against, LocalDate reported) {
        return new CorrosionRow()
                .setSerial(serial)
                .setAgainst(against)
                .setPart(entry.part())
                .setLevel(entry.level().value())
                .setFoundOn(entry.foundOn())
                .setReportedOn(reported);
    }

    /** Rebuild a graded entry, refusing a level the programme does not have. */
    public static CorrosionEntry rowToCorrosion(CorrosionRow row) {
        CorrosionLevel level;
        try {
            level = CorrosionLevel.fromValue(row.getLevel());
        } catch (IllegalArgumentException unknown) {
            throw new BadReading(row.getPart() + ": level " + row.getLevel() + " is not one the programme grades");
        }
        return new CorrosionEntry(row.getPart(), level, row.getFoundOn());
    }

    /**
     * Whether this panel has been graded already, which is what makes a level two.
     *
     * <p>The date matters. Grading a finding against entries raised after it would let a later
     * inspection reach back and turn an old level one into a level two, and the programme reads those
     * numbers as a rate.
     */
    public static boolean seenHereBefore(List<CorrosionRow> rows, PanelId part, LocalDate before) {
        return rows.stream()
                .anyMatch(row -> row.getPart().equals(part) && row.getFoundOn().isBefore(before));
    }

    /** Fold the conditions onto one column, or nothing if there were none. */
    public static Optional<String> conditionsToText(List<String> conditions) {
        List<String> kept = new ArrayList<>();
        for (String one : conditions) {
            String stripped = Text.strip(one);
            if (!stripped.isEmpty()) {
                kept.add(stripped);
            }
        }
        if (kept.isEmpty()) {
            return Optional.empty();
        }
        if (kept.stream().anyMatch(one -> one.contains(CONDITION_BREAK))) {
            throw new BadReading("a condition is one line");
        }
        return Optional.of(String.join(CONDITION_BREAK, kept));
    }

    /** Read them back, dropping the blank lines a hand edit leaves behind; a null column is none. */
    public static List<String> conditionsFromText(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        List<String> kept = new ArrayList<>();
        for (String line : text.split(CONDITION_BREAK, -1)) {
            String stripped = Text.strip(line);
            if (!stripped.isEmpty()) {
                kept.add(stripped);
            }
        }
        return List.copyOf(kept);
    }

    /**
     * One engineering order, with its approval flattened onto the row.
     *
     * <p>The approval is four fields and it is either all there or none of it is, so it lives on the
     * order row rather than in a table of its own. A row with a sheet number and no category is a row
     * that was written by something that did not go through here.
     */
    public static OrderRow orderToRow(EngineeringOrder order) {
        OrderRow row = new OrderRow()
                .setReference(order.reference().toString())
                .setAgainst(order.against())
                .setQuestion(order.question())
                .setRaised(order.raised())
                .setState(order.state().value())
                .setSent(order.sent().orElse(null))
                .setAnswered(order.answered().orElse(null));
        order.approval().ifPresent(approval -> row.setSheet(approval.sheet())
                .setApprovedCategory(approval.category().name())
                .setValidUntil(approval.validUntil().orElse(null))
                .setConditions(conditionsToText(approval.conditions()).orElse(null)));
        return row;
    }

    /** Rebuild an order, refusing a half written approval by name. */
    public static EngineeringOrder rowToOrder(OrderRow row) {
        RepairApproval approval = null;
        if (row.getSheet().isPresent() || row.getApprovedCategory().isPresent()) {
            if (row.getSheet().isEmpty() || row.getApprovedCategory().isEmpty()) {
                throw new BadReading(row.getReference() + " carries half an approval");
            }
            approval = new RepairApproval(
                    row.getSheet().get(),
                    categoryNamed(row.getApprovedCategory().get()),
                    conditionsFromText(row.getConditions().orElse(null)),
                    row.getValidUntil());
        }
        return EngineeringOrder.of(
                OrderId.parse(row.getReference()),
                row.getAgainst(),
                row.getQuestion(),
                row.getRaised(),
                OrderState.fromValue(row.getState()),
                row.getSent().orElse(null),
                row.getAnswered().orElse(null),
                approval);
    }

    /**
     * One repeat inspection, hanging off the repair that carries it.
     *
     * <p>The threshold and the repeat are two different clocks and they go in separate columns. A
     * threshold is how long after embodiment the first look happens; the repeat is how often after
     * that. Storing one interval and a flag would lose the case where the first look is later than
     * every one after it, which is the normal shape for a crack growth threshold.
     */
    public static InspectionRow taskToRow(InspectionTask task) {
        InspectionSchedule schedule = task.schedule();
        Interval threshold = checked(schedule.threshold(), THRESHOLD_CLOCKS, "threshold");
        Optional<Interval> repeat = schedule.repeat().map(one -> checked(one, REPEAT_CLOCKS, "repeat"));
        return new InspectionRow()
                .setAgainst(task.against())
                .setPart(task.part())
                .setMethod(schedule.method().value())
                .setThresholdCycles(threshold.cycles().orElse(null))
                .setThresholdMonths(threshold.months().orElse(null))
                .setRepeatCycles(repeat.flatMap(Interval::cycles).orElse(null))
                .setEmbodied(task.embodied())
                .setAtCycles(task.atEmbodiment().cycles())
                .setAtHours(task.atEmbodiment().hours())
                .setLastDone(task.lastDone().orElse(null))
                .setLastCycles(task.atLastDone().map(Usage::cycles).orElse(null))
                .setLastHours(task.atLastDone().map(Usage::hours).orElse(null));
    }

    /** Rebuild a repeat inspection, refusing a half written sign off. */
    public static InspectionTask rowToTask(InspectionRow row) {
        if (row.getLastDone().isEmpty() != row.getLastCycles().isEmpty()) {
            throw new BadReading(row.getAgainst() + " carries half a sign off");
        }
        if (row.getThresholdCycles().isEmpty() && row.getThresholdMonths().isEmpty()) {
            throw new BadReading(row.getAgainst() + " carries a schedule with no threshold on it");
        }
        InspectionSchedule schedule = new InspectionSchedule(
                InspectionMethod.fromValue(row.getMethod()),
                Interval.of(
                        row.getThresholdCycles().orElse(null),
                        null,
                        row.getThresholdMonths().orElse(null),
                        null),
                row.getRepeatCycles().map(Interval::ofCycles));
        Usage done = row.getLastCycles().isPresent() && row.getLastHours().isPresent()
                ? new Usage(row.getLastCycles().get(), row.getLastHours().get())
                : null;
        return InspectionTask.of(
                row.getAgainst(),
                row.getPart(),
                schedule,
                row.getEmbodied(),
                new Usage(row.getAtCycles(), row.getAtHours()),
                row.getLastDone().orElse(null),
                done);
    }

    /** Refuse an interval against a clock this row has no column for. */
    private static Interval checked(Interval interval, List<String> clocks, String what) {
        List<String> named = new ArrayList<>();
        interval.cycles().ifPresent(one -> named.add("cycles"));
        interval.hours().ifPresent(one -> named.add("hours"));
        interval.months().ifPresent(one -> named.add("months"));
        interval.days().ifPresent(one -> named.add("days"));
        for (String name : named) {
            if (!clocks.contains(name)) {
                throw new BadReading("a " + what + " in " + name + " is not one this desk stores");
            }
        }
        return interval;
    }

    /** The category a repair row names by its letter. */
    private static RepairCategory categoryLettered(String letter) {
        for (RepairCategory category : RepairCategory.values()) {
            if (category.name().equals(letter)) {
                return category;
            }
        }
        throw new IllegalArgumentException(Literal.of(letter) + " is not a valid RepairCategory");
    }

    /** The category an approval names by its letter, refusing a letter there is no category for. */
    private static RepairCategory categoryNamed(String letter) {
        for (RepairCategory category : RepairCategory.values()) {
            if (category.name().equals(letter)) {
                return category;
            }
        }
        throw new NoSuchElementException(Literal.of(letter));
    }
}
