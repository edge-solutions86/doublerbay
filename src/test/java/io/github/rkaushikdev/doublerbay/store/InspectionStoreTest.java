package io.github.rkaushikdev.doublerbay.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.assess.Placement;
import io.github.rkaushikdev.doublerbay.fleet.UtilisationLog;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.grid.Reference;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.ids.RepairId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Cycles;
import io.github.rkaushikdev.doublerbay.measure.Interval;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import io.github.rkaushikdev.doublerbay.repair.RepairRecord;
import io.github.rkaushikdev.doublerbay.repair.Schemes;
import io.github.rkaushikdev.doublerbay.support.Desk;
import io.github.rkaushikdev.doublerbay.support.Sample;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionMethod;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionSchedule;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionTask;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InspectionStoreTest {

    private static final Reference PAGE = Reference.parse("53-40-01");
    private static final LocalDate FITTED = LocalDate.of(2025, 4, 2);
    private static final Serial MSN = new Serial("5127");
    private static final Serial OTHER = new Serial("5240");
    private static final PanelId PART = new PanelId(53, 10, 143, "L");
    private static final RepairId FIRST = new RepairId(53, 1);

    private Database database;

    private static Placement placed() {
        return Placement.place(Sample.tailContext(), Sample.card(Sample.cabinPointOn(10)));
    }

    private static RepairRecord repair(int sequence) {
        return new RepairRecord(
                new RepairId(53, sequence),
                PART,
                new FindingId(2025, sequence),
                Schemes.selectScheme(Sample.inch("2.000"), Sample.inch("2.000"), placed(), PAGE),
                new Offset(Sample.inch("468.000"), Sample.inch("91.000")),
                FITTED,
                usage(13900, 24400));
    }

    private static Usage usage(int cycles, long hours) {
        return UtilisationLog.usage(cycles, hours);
    }

    private static InspectionSchedule schedule(String threshold, String repeat) {
        return InspectionSchedule.of(
                InspectionMethod.HFEC, Interval.parse(threshold), repeat == null ? null : Interval.parse(repeat));
    }

    private static InspectionSchedule schedule() {
        return schedule("8000 FC", "4000 FC");
    }

    private static InspectionTask task(int sequence, InspectionSchedule made) {
        return new InspectionTask(new RepairId(53, sequence), PART, made, FITTED, usage(13900, 24400));
    }

    private static InspectionTask task(int sequence) {
        return task(sequence, schedule());
    }

    private static InspectionTask task(InspectionSchedule made) {
        return task(1, made);
    }

    private static InspectionTask task() {
        return task(1);
    }

    @BeforeEach
    void oneRepairFitted() {
        database = Desk.stocked();
        UnitOfWork.run(database, unit -> unit.repairs().embody(repair(1), MSN));
    }

    private void added(InspectionTask one) {
        UnitOfWork.run(database, unit -> unit.inspections().add(one));
    }

    private void added() {
        added(task());
    }

    private InspectionTask held() {
        return UnitOfWork.apply(database, unit -> unit.inspections().get(FIRST));
    }

    @Test
    void aTaskGoesInAndComesBack() {
        added();
        InspectionTask held = held();
        assertThat(held.part()).isEqualTo(PART);
        assertThat(held.schedule().method()).isSameAs(InspectionMethod.HFEC);
    }

    @Test
    void theThresholdAndTheRepeatStayApart() {
        added(task(schedule("8000 FC", "4000 FC")));
        InspectionSchedule held = held().schedule();
        assertThat(held.threshold().cycles()).contains(new Cycles(8000));
        assertThat(held.repeat().orElseThrow().cycles()).contains(new Cycles(4000));
    }

    @Test
    void aReplacementLimitHasNoRepeat() {
        added(task(schedule("18 MO", null)));
        InspectionTask held = held();
        assertThat(held.schedule().repeat()).isEmpty();
        assertThat(held.isAOneOff()).isTrue();
    }

    @Test
    void aThresholdInMonthsSurvives() {
        added(task(schedule("18 MO", null)));
        assertThat(held().schedule().threshold().months()).contains(18);
    }

    @Test
    void aThresholdInHoursIsRefusedRatherThanDropped() {
        assertThatThrownBy(() -> Decisions.taskToRow(task(schedule("500 FH", null))))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("threshold in hours");
    }

    @Test
    void aRepeatInDaysIsRefusedRatherThanDropped() {
        assertThatThrownBy(() -> Decisions.taskToRow(task(schedule("8000 FC", "90 DY"))))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("repeat in days");
    }

    @Test
    void aTaskOnARepairThatIsNotOnFileIsRefused() {
        assertThatThrownBy(() -> added(task(9)))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("not a repair on file");
    }

    @Test
    void aSecondTaskOnOneRepairIsRefused() {
        added();
        assertThatThrownBy(this::added).isInstanceOf(BadReading.class).hasMessageContaining("already carries");
    }

    @Test
    void aRepairCarryingNoTaskIsRefused() {
        assertThatThrownBy(this::held).isInstanceOf(NotOnFile.class).hasMessageContaining("carries no repeat");
    }

    @Test
    void aNewTaskHasNeverBeenDone() {
        added();
        UnitOfWork.run(database, unit -> {
            assertThat(unit.inspections().get(FIRST).hasBeenDone()).isFalse();
            assertThat(unit.inspections().neverDone(MSN)).hasSize(1);
        });
    }

    @Test
    void signingOffRecordsTheDayAndTheCounters() {
        added();
        UnitOfWork.run(
                database, unit -> unit.inspections().signOff(FIRST, LocalDate.of(2026, 1, 5), usage(15000, 26000)));
        InspectionTask held = held();
        assertThat(held.lastDone()).contains(LocalDate.of(2026, 1, 5));
        assertThat(held.atLastDone().orElseThrow().cycles()).isEqualTo(new Cycles(15000));
    }

    @Test
    void aSignedOffTaskIsNoLongerNeverDone() {
        added();
        UnitOfWork.run(
                database, unit -> unit.inspections().signOff(FIRST, LocalDate.of(2026, 1, 5), usage(15000, 26000)));
        List<InspectionTask> never =
                UnitOfWork.apply(database, unit -> unit.inspections().neverDone(MSN));
        assertThat(never).isEmpty();
    }

    @Test
    void signingOffBeforeTheRepairWentOnIsRefused() {
        added();
        assertThatThrownBy(() -> UnitOfWork.run(database, unit -> unit.inspections()
                        .signOff(FIRST, LocalDate.of(2024, 1, 1), usage(15000, 26000))))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("inspected before");
    }

    @Test
    void signingOffATaskThatIsNotThereIsRefused() {
        assertThatThrownBy(() -> UnitOfWork.run(
                        database, unit -> unit.inspections().signOff(new RepairId(53, 9), FITTED, usage(15000, 26000))))
                .isInstanceOf(NotOnFile.class);
    }

    @Test
    void aReplacementLimitIsOnlySignedOffOnce() {
        added(task(schedule("18 MO", null)));
        UnitOfWork.run(
                database, unit -> unit.inspections().signOff(FIRST, LocalDate.of(2026, 1, 5), usage(15000, 26000)));
        assertThatThrownBy(() -> UnitOfWork.run(database, unit -> unit.inspections()
                        .signOff(FIRST, LocalDate.of(2027, 1, 5), usage(16000, 27000))))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("only done once");
    }

    @Test
    void aTaskCanBeDropped() {
        added();
        UnitOfWork.run(database, unit -> unit.inspections().drop(FIRST));
        List<InspectionTask> left =
                UnitOfWork.apply(database, unit -> unit.inspections().onTail(MSN));
        assertThat(left).isEmpty();
    }

    @Test
    void droppingOneThatIsNotThereIsRefused() {
        assertThatThrownBy(() ->
                        UnitOfWork.run(database, unit -> unit.inspections().drop(new RepairId(53, 9))))
                .isInstanceOf(NotOnFile.class);
    }

    @Test
    void aTaskOnARemovedRepairDropsOffTheTail() {
        added();
        UnitOfWork.run(database, unit -> unit.repairs().remove(FIRST, LocalDate.of(2026, 1, 5)));
        List<InspectionTask> left =
                UnitOfWork.apply(database, unit -> unit.inspections().onTail(MSN));
        assertThat(left).isEmpty();
    }

    @Test
    void anotherTailsTasksAreNotOnThisOne() {
        added();
        Desk.enrolled(database, "5240");
        List<InspectionTask> other =
                UnitOfWork.apply(database, unit -> unit.inspections().onTail(OTHER));
        assertThat(other).isEmpty();
    }

    @Test
    void nothingIsDueBeforeTheThreshold() {
        added();
        List<InspectionTask> due =
                UnitOfWork.apply(database, unit -> unit.inspections().dueOn(MSN, usage(14000, 24500), FITTED));
        assertThat(due).isEmpty();
    }

    @Test
    void aTaskPastItsThresholdIsDue() {
        added();
        List<InspectionTask> due = UnitOfWork.apply(
                database, unit -> unit.inspections().dueOn(MSN, usage(30000, 50000), LocalDate.of(2030, 1, 1)));
        assertThat(due).hasSize(1);
    }

    @Test
    void aRowWithHalfASignOffIsRefused() {
        InspectionRow row = Decisions.taskToRow(task());
        row.setLastDone(LocalDate.of(2026, 1, 5));
        assertThatThrownBy(() -> Decisions.rowToTask(row))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("half a sign off");
    }

    @Test
    void aRowWithNoThresholdLeftOnItIsRefused() {
        InspectionRow row = new InspectionRow()
                .setAgainst(FIRST)
                .setPart(PART)
                .setMethod("high frequency eddy current")
                .setThresholdCycles(null)
                .setThresholdMonths(null)
                .setRepeatCycles(null)
                .setEmbodied(FITTED)
                .setAtCycles(usage(13900, 24400).cycles())
                .setAtHours(usage(13900, 24400).hours());
        assertThatThrownBy(() -> Decisions.rowToTask(row))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("no threshold on it");
    }
}
