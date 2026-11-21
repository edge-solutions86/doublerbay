package io.github.rkaushikdev.doublerbay.tolerance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.fleet.UtilisationLog;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.ids.RepairId;
import io.github.rkaushikdev.doublerbay.measure.DueAt;
import io.github.rkaushikdev.doublerbay.measure.Usage;
import io.github.rkaushikdev.doublerbay.repair.RepairCategory;
import io.github.rkaushikdev.doublerbay.structure.PanelKind;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProgrammeTest {

    private static final PanelId PART = new PanelId(53, 10, 143, "L");
    private static final LocalDate EMBODIED = LocalDate.of(2025, 4, 2);
    private static final Usage AT_EMBODIMENT = UtilisationLog.usage(13800, 24200);

    private static Usage usage(int cycles, long hours) {
        return UtilisationLog.usage(cycles, hours);
    }

    private static InspectionSchedule repeating() {
        return Schedules.scheduleFor(RepairCategory.B, PanelKind.PRESSURISED_SKIN, InspectionMethod.DVI)
                .orElseThrow();
    }

    private static InspectionSchedule oneOff() {
        return Schedules.scheduleFor(RepairCategory.C, PanelKind.PRESSURISED_SKIN, InspectionMethod.GVI)
                .orElseThrow();
    }

    private static InspectionTask task(
            int sequence, InspectionSchedule schedule, LocalDate lastDone, Usage atLastDone) {
        return InspectionTask.of(
                new RepairId(53, sequence),
                PART,
                schedule != null ? schedule : repeating(),
                EMBODIED,
                AT_EMBODIMENT,
                lastDone,
                atLastDone);
    }

    private static InspectionTask task(int sequence) {
        return task(sequence, null, null, null);
    }

    private static InspectionTask task() {
        return task(1);
    }

    private static int cycles(DueAt due) {
        return due.cycles().orElseThrow().count();
    }

    private static int repeatCycles() {
        return repeating().repeat().orElseThrow().cycles().orElseThrow().count();
    }

    private static List<Integer> sequences(Iterable<InspectionTask> tasks) {
        List<Integer> found = new ArrayList<>();
        for (InspectionTask one : tasks) {
            found.add(one.against().sequence());
        }
        return found;
    }

    @Test
    void aTaskReadsBackWhatItIsAndWhenItWasLastDone() {
        assertThat(task().toString()).endsWith("last done never");
        InspectionTask done = task(1, null, LocalDate.of(2026, 1, 1), usage(15000, 26000));
        assertThat(done.toString()).endsWith("last done 2026-01-01");
    }

    @Test
    void aCompletedInspectionNeedsADateAndAReadingTogether() {
        assertThatThrownBy(() -> task(1, null, LocalDate.of(2026, 1, 1), null))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("a date and a reading");
        assertThatThrownBy(() -> task(1, null, null, usage(15000, 26000)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("a date and a reading");
    }

    @Test
    void anInspectionBeforeTheRepairWentOnIsRefused() {
        assertThatThrownBy(() -> task(1, null, LocalDate.of(2024, 1, 1), usage(13000, 23000)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("inspected before the repair went on");
    }

    @Test
    void theFirstLookCountsFromTheRepairGoingOn() {
        DueAt due = task().due();
        assertThat(cycles(due))
                .isEqualTo(
                        13800 + repeating().threshold().cycles().orElseThrow().count());
    }

    @Test
    void theFirstLookIsFurtherOutThanTheOnesAfterIt() {
        DueAt first = task().due();
        DueAt after = task(1, null, EMBODIED, AT_EMBODIMENT).due();
        assertThat(cycles(first)).isGreaterThan(cycles(after));
    }

    @Test
    void everyLookAfterThatCountsFromTheLastOne() {
        InspectionTask done = task(1, null, LocalDate.of(2026, 1, 1), usage(15000, 26000));
        assertThat(cycles(done.due())).isEqualTo(15000 + repeatCycles());
    }

    @Test
    void signingATaskOffResetsItsClock() {
        InspectionTask before = task();
        InspectionTask after = before.signedOff(LocalDate.of(2026, 1, 1), usage(15000, 26000));
        assertThat(after.hasBeenDone()).isTrue();
        assertThat(before.hasBeenDone()).isFalse();
        assertThat(cycles(after.due())).isEqualTo(15000 + repeatCycles());
    }

    @Test
    void aTaskThatIsNotYetAtItsThresholdIsNotDue() {
        assertThat(task().isDue(usage(14000, 24500), LocalDate.of(2026, 1, 1))).isFalse();
    }

    @Test
    void aTaskPastItsThresholdIsDue() {
        int beyond = 13800 + repeating().threshold().cycles().orElseThrow().count();
        assertThat(task().isDue(usage(beyond, 90000), LocalDate.of(2026, 1, 1))).isTrue();
    }

    @Test
    void aReplacementLimitFallsDueOnTheCalendar() {
        InspectionTask limit = task(1, oneOff(), null, null);
        assertThat(limit.isDue(AT_EMBODIMENT, LocalDate.of(2026, 3, 1))).isFalse();
        assertThat(limit.isDue(AT_EMBODIMENT, LocalDate.of(2026, 4, 3))).isTrue();
    }

    @Test
    void aReplacementLimitDoesNotComeRoundAgain() {
        InspectionTask done = task(1, oneOff(), null, null).signedOff(LocalDate.of(2026, 1, 1), usage(15000, 26000));
        assertThat(done.isDue(usage(99000, 99000), LocalDate.of(2099, 1, 1))).isFalse();
        assertThatThrownBy(done::due).isInstanceOf(BadReading.class).hasMessageContaining("does not come round again");
    }

    @Test
    void aReplacementLimitIsOnlySignedOffOnce() {
        InspectionTask done = task(1, oneOff(), null, null).signedOff(LocalDate.of(2026, 1, 1), usage(15000, 26000));
        assertThatThrownBy(() -> done.signedOff(LocalDate.of(2027, 1, 1), usage(16000, 27000)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("only done once");
    }

    @Test
    void theProgrammeCountsWhatTheTailIsCarrying() {
        InspectionProgramme held = new InspectionProgramme();
        held.add(task(1));
        held.add(task(2));
        assertThat(held.size()).isEqualTo(2);
        assertThat(held).hasToString("2 repeat inspections");
        assertThat(held.contains(new RepairId(53, 1))).isTrue();
    }

    @Test
    void aRepairOnlyGetsOneInspectionOnTheProgramme() {
        InspectionProgramme held = new InspectionProgramme();
        held.add(task(1));
        assertThatThrownBy(() -> held.add(task(1)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("already has an inspection");
    }

    @Test
    void aRepairWithNoInspectionIsALookupMiss() {
        InspectionProgramme held = new InspectionProgramme();
        assertThatThrownBy(() -> held.taskFor(new RepairId(53, 9)))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("has no inspection on the programme");
        assertThatThrownBy(() -> held.drop(new RepairId(53, 9)))
                .isInstanceOf(NotOnFile.class)
                .hasMessageContaining("has no inspection on the programme");
    }

    @Test
    void signingOffThroughTheProgrammeKeepsTheNewState() {
        InspectionProgramme held = new InspectionProgramme();
        held.add(task(1));
        held.signOff(new RepairId(53, 1), LocalDate.of(2026, 1, 1), usage(15000, 26000));
        assertThat(held.taskFor(new RepairId(53, 1)).hasBeenDone()).isTrue();
    }

    @Test
    void aTaskComesOffWhenItsRepairDoes() {
        InspectionProgramme held = new InspectionProgramme();
        held.add(task(1));
        held.drop(new RepairId(53, 1));
        assertThat(held.size()).isZero();
    }

    @Test
    void theDueListIsTheTasksThatHaveLanded() {
        InspectionProgramme held = new InspectionProgramme();
        held.add(task(1));
        held.add(task(2, oneOff(), null, null));
        List<InspectionTask> due = held.dueOn(AT_EMBODIMENT, LocalDate.of(2026, 5, 1));
        assertThat(sequences(due)).containsExactly(2);
    }

    @Test
    void theNeverDoneListIsTheOnesNobodyHasLookedAt() {
        InspectionProgramme held = new InspectionProgramme();
        held.add(task(1));
        held.add(task(2, null, LocalDate.of(2026, 1, 1), usage(15000, 26000)));
        assertThat(sequences(held.neverDone())).containsExactly(1);
    }

    @Test
    void theProgrammeIteratesInRepairNumberOrder() {
        InspectionProgramme held = new InspectionProgramme();
        held.add(task(3));
        held.add(task(1));
        held.add(task(2));
        assertThat(sequences(held)).containsExactly(1, 2, 3);
    }
}
