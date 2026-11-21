package io.github.rkaushikdev.doublerbay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.assess.Placement;
import io.github.rkaushikdev.doublerbay.disposition.DispositionRecord;
import io.github.rkaushikdev.doublerbay.disposition.DispositionState;
import io.github.rkaushikdev.doublerbay.fleet.UtilisationLog;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.grid.Reference;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.ids.RepairId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.repair.RepairCategory;
import io.github.rkaushikdev.doublerbay.repair.RepairRecord;
import io.github.rkaushikdev.doublerbay.repair.Schemes;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.support.Desk;
import io.github.rkaushikdev.doublerbay.support.Sample;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionMethod;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionTask;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServiceRepairTasksTest {

    private static final Reference PAGE = Reference.parse("53-40-01");
    private static final LocalDate DAY = LocalDate.of(2025, 3, 14);
    private static final LocalDate FITTED = LocalDate.of(2025, 4, 2);
    private static final Serial MSN = new Serial("5127");
    private static final PanelId PART = new PanelId(53, 10, 143, "L");
    private static final RepairId FIRST = new RepairId(53, 1);

    private Database database;

    @BeforeEach
    void stocked() {
        database = Desk.stocked();
    }

    private static Length inch(String text) {
        return Sample.inch(text);
    }

    private static Placement placed() {
        return Placement.place(Sample.tailContext(), Sample.card(Sample.cabinPointOn(10)));
    }

    private static RepairRecord record(int sequence, boolean temporary) {
        return new RepairRecord(
                new RepairId(53, sequence),
                PART,
                new FindingId(2025, sequence),
                Schemes.selectScheme(inch("2.000"), inch("2.000"), placed(), PAGE, null, temporary),
                new Offset(inch("468.000"), inch("91.000")),
                FITTED,
                UtilisationLog.usage(13900, 24400));
    }

    private static RepairRecord record(boolean temporary) {
        return record(1, temporary);
    }

    private static RepairRecord record() {
        return record(false);
    }

    private FindingId assessed(int sequence) {
        FindingId reference = new FindingId(2025, sequence);
        UnitOfWork.run(database, unit -> {
            unit.findings().raiseFinding(Sample.card(sequence, Sample.cabinPointOn(10)));
            unit.dispositions()
                    .save(DispositionRecord.raised(reference).movedTo(DispositionState.ASSESSED, DAY, "a.okonkwo"));
        });
        return reference;
    }

    private FindingId assessed() {
        return assessed(1);
    }

    private Embodiment fit(RepairRecord one, InspectionMethod method) {
        return UnitOfWork.apply(database, unit -> Repairs.embody(unit, one, MSN, "s.brandt", null, method));
    }

    private Embodiment fit(RepairRecord one) {
        return fit(one, Repairs.DEFAULT_METHOD);
    }

    private Embodiment fit() {
        return fit(record());
    }

    private InspectionTask task() {
        return UnitOfWork.apply(database, unit -> unit.inspections().get(FIRST));
    }

    private List<InspectionTask> onTail() {
        return UnitOfWork.apply(database, unit -> unit.inspections().onTail(MSN));
    }

    @Test
    void aCategoryARepairCarriesNoRepeatInspection() {
        assessed();
        Embodiment answer = fit();
        if (answer.reference() != null && record().scheme().category() == RepairCategory.A) {
            assertThat(answer.carriesInspection()).isFalse();
        }
    }

    @Test
    void aTemporaryRepairCarriesAReplacementLimit() {
        assessed();
        Embodiment answer = fit(record(true));
        assertThat(answer.carriesInspection()).isTrue();
        assertThat(answer.inspection().orElseThrow().repeat()).isEmpty();
    }

    @Test
    void theReplacementLimitIsOnTheCalendar() {
        assessed();
        fit(record(true));
        InspectionTask held = task();
        assertThat(held.schedule().threshold().months()).isPresent();
        assertThat(held.isAOneOff()).isTrue();
    }

    @Test
    void aRepairWithATaskLandsOnTheTailList() {
        assessed();
        fit(record(true));
        assertThat(onTail()).hasSize(1);
    }

    @Test
    void theTaskIsFiledAgainstTheRepairThatCarriesIt() {
        assessed();
        fit(record(true));
        assertThat(task().against()).isEqualTo(FIRST);
    }

    @Test
    void theTaskStartsAtTheCountersTheRepairWentOnAt() {
        assessed();
        fit(record(true));
        InspectionTask held = task();
        assertThat(held.atEmbodiment()).isEqualTo(UtilisationLog.usage(13900, 24400));
        assertThat(held.embodied()).isEqualTo(FITTED);
    }

    @Test
    void aTaskStartsNeverDone() {
        assessed();
        fit(record(true));
        List<InspectionTask> never =
                UnitOfWork.apply(database, unit -> unit.inspections().neverDone(MSN));
        assertThat(never).hasSize(1);
    }

    @Test
    void theMethodAskedForReachesTheTask() {
        assessed();
        fit(record(true), InspectionMethod.UT);
        assertThat(task().schedule().method()).isSameAs(InspectionMethod.UT);
    }

    @Test
    void removingARepairTakesItsTaskWithIt() {
        assessed();
        fit(record(true));
        UnitOfWork.run(database, unit -> Repairs.remove(unit, FIRST, LocalDate.of(2026, 1, 5)));
        UnitOfWork.run(database, unit -> {
            assertThat(unit.inspections().onTail(MSN)).isEmpty();
            assertThatThrownBy(() -> unit.inspections().get(FIRST)).isInstanceOf(NotOnFile.class);
        });
    }

    @Test
    void removingARepairWithNoTaskIsNotAnError() {
        assessed();
        Embodiment answer = fit();
        UnitOfWork.run(database, unit -> Repairs.remove(unit, FIRST, LocalDate.of(2026, 1, 5)));
        List<RepairRecord> fitted =
                UnitOfWork.apply(database, unit -> unit.repairs().onTail(MSN));
        assertThat(fitted).isEmpty();
        assertThat(answer.reference()).isEqualTo(FIRST);
    }

    @Test
    void removingARepairThatIsNotOnFileIsStillRefused() {
        UnitOfWork.run(database, unit -> assertThatThrownBy(
                        () -> Repairs.remove(unit, new RepairId(53, 9), LocalDate.of(2026, 1, 5)))
                .isInstanceOf(NotOnFile.class));
    }

    @Test
    void twoRepairsEachGetTheirOwnTask() {
        assessed(1);
        assessed(2);
        fit(record(1, true));
        fit(record(2, true));
        assertThat(onTail()).hasSize(2);
    }

    @Test
    void aTaskCanBeSignedOffAfterTheRepairGoesOn() {
        assessed();
        fit(record(true));
        InspectionTask signed = UnitOfWork.apply(database, unit -> unit.inspections()
                .signOff(FIRST, LocalDate.of(2026, 1, 5), UtilisationLog.usage(15000, 26000)));
        assertThat(signed.hasBeenDone()).isTrue();
    }
}
