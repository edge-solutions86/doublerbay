package io.github.rkaushikdev.doublerbay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.assess.Placement;
import io.github.rkaushikdev.doublerbay.disposition.Deferral;
import io.github.rkaushikdev.doublerbay.disposition.DispositionRecord;
import io.github.rkaushikdev.doublerbay.disposition.DispositionState;
import io.github.rkaushikdev.doublerbay.fleet.UtilisationLog;
import io.github.rkaushikdev.doublerbay.grid.Offset;
import io.github.rkaushikdev.doublerbay.grid.Reference;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.ids.RepairId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Interval;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.repair.RepairCategory;
import io.github.rkaushikdev.doublerbay.repair.RepairPair;
import io.github.rkaushikdev.doublerbay.repair.RepairRecord;
import io.github.rkaushikdev.doublerbay.repair.Schemes;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.store.DeferralRow;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.support.Desk;
import io.github.rkaushikdev.doublerbay.support.Sample;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionMethod;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServiceRepairsTest {

    private static final Reference PAGE = Reference.parse("53-40-01");
    private static final LocalDate DAY = LocalDate.of(2025, 3, 14);
    private static final LocalDate FITTED = LocalDate.of(2025, 4, 2);
    private static final Serial MSN = new Serial("5127");
    private static final Serial OTHER = new Serial("5240");
    private static final PanelId PART = new PanelId(53, 10, 143, "L");

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

    private static RepairRecord record(int sequence, String along, String around, boolean temporary) {
        return new RepairRecord(
                new RepairId(53, sequence),
                PART,
                new FindingId(2025, sequence),
                Schemes.selectScheme(inch("2.000"), inch("2.000"), placed(), PAGE, null, temporary),
                new Offset(inch(along), inch(around)),
                FITTED,
                UtilisationLog.usage(13900, 24400));
    }

    private static RepairRecord record(int sequence, String along, String around) {
        return record(sequence, along, around, false);
    }

    private static RepairRecord record(boolean temporary) {
        return record(1, "468.000", "91.000", temporary);
    }

    private static RepairRecord record() {
        return record(false);
    }

    private FindingId assessed(int sequence) {
        FindingId reference = new FindingId(2025, sequence);
        UnitOfWork.run(database, unit -> {
            unit.findings().raiseFinding(Sample.card(sequence, "5127"));
            unit.dispositions()
                    .save(DispositionRecord.raised(reference).movedTo(DispositionState.ASSESSED, DAY, "a.okonkwo"));
        });
        return reference;
    }

    private FindingId assessed() {
        return assessed(1);
    }

    private FindingId deferred() {
        assessed(1);
        FindingId reference = new FindingId(2025, 1);
        UnitOfWork.run(database, unit -> {
            unit.deferrals()
                    .grant(Deferral.grant(
                            reference,
                            DAY,
                            Sample.flown(13800, 24200),
                            "j.mercer",
                            InspectionMethod.DVI,
                            Interval.parse("500 FC"),
                            null,
                            ""));
            DispositionRecord held = unit.dispositions().get(reference);
            unit.dispositions().save(held.movedTo(DispositionState.DEFERRED, DAY, "j.mercer"));
        });
        return reference;
    }

    private Embodiment fit(RepairRecord one, Serial serial, String by, LocalDate on) {
        return UnitOfWork.apply(database, unit -> Repairs.embody(unit, one, serial, by, on));
    }

    private Embodiment fit(RepairRecord one) {
        return fit(one, MSN, "s.brandt", null);
    }

    private Embodiment fit() {
        return fit(record());
    }

    private DispositionState cardState() {
        return UnitOfWork.apply(
                database,
                unit -> unit.dispositions().get(new FindingId(2025, 1)).state());
    }

    private List<RepairRecord> fittedOnTail() {
        return UnitOfWork.apply(database, unit -> unit.repairs().onTail(MSN));
    }

    @Test
    void aPermanentRepairClosesTheCard() {
        assessed();
        Embodiment answer = fit();
        assertThat(answer.state()).isSameAs(DispositionState.CLOSED);
        assertThat(cardState()).isSameAs(DispositionState.CLOSED);
    }

    @Test
    void aTemporaryRepairLeavesTheCardInWork() {
        assessed();
        Embodiment answer = fit(record(true));
        assertThat(answer.state()).isSameAs(DispositionState.IN_WORK);
    }

    @Test
    void theStateARepairLeavesACardInFollowsItsCategory() {
        assertThat(Repairs.stateAfter(record())).isSameAs(DispositionState.CLOSED);
        assertThat(Repairs.stateAfter(record(true))).isSameAs(DispositionState.IN_WORK);
        assertThat(record(true).scheme().category()).isSameAs(RepairCategory.C);
    }

    @Test
    void theRepairItselfGoesOnFile() {
        assessed();
        fit();
        RepairRecord held = UnitOfWork.apply(database, unit -> unit.repairs().get(new RepairId(53, 1)));
        assertThat(held.part()).isEqualTo(PART);
    }

    @Test
    void embodyingLiftsTheDeferralItAnswers() {
        deferred();
        Embodiment answer = fit();
        assertThat(answer.deferralLifted()).isTrue();
        List<Deferral> live =
                UnitOfWork.apply(database, unit -> unit.deferrals().live());
        assertThat(live).isEmpty();
    }

    @Test
    void aCardThatWasNeverDeferredLiftsNothing() {
        assessed();
        assertThat(fit().deferralLifted()).isFalse();
    }

    @Test
    void theDeferralIsLiftedOnTheDayTheRepairWentOn() {
        deferred();
        fit();
        Optional<LocalDate> lifted = UnitOfWork.apply(database, unit -> unit.session()
                .find(DeferralRow.class, new FindingId(2025, 1).toString())
                .getLifted());
        assertThat(lifted).contains(FITTED);
    }

    @Test
    void aRepairMustBeEmbodiedBySomebody() {
        assessed();
        assertThatThrownBy(() -> fit(record(), MSN, "   ", null))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("embodied by somebody");
    }

    @Test
    void aRepairCannotBeSignedOffBeforeItWasFitted() {
        assessed();
        assertThatThrownBy(() -> fit(record(), MSN, "s.brandt", LocalDate.of(2024, 1, 1)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("before it was fitted");
    }

    @Test
    void aRepairAgainstAnotherTailsCardIsRefused() {
        assessed();
        assertThatThrownBy(() -> fit(record(), OTHER, "s.brandt", null))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("is not on MSN 5240");
    }

    @Test
    void aRepairAgainstACardThatIsNotOnFileIsRefused() {
        UnitOfWork.run(database, unit -> assertThatThrownBy(() -> Repairs.embody(unit, record(), MSN, "s.brandt"))
                .isInstanceOf(NotOnFile.class));
    }

    @Test
    void aRepairNumberUsedTwiceIsRefused() {
        assessed();
        fit();
        assertThatThrownBy(this::fit).isInstanceOf(BadReading.class).hasMessageContaining("already on file");
    }

    @Test
    void aCleanFitReportsNoClashes() {
        assessed();
        Embodiment answer = fit();
        assertThat(answer.isClean()).isTrue();
        assertThat(answer.clashes()).isEmpty();
    }

    @Test
    void aRepairTooCloseToAnOlderOneIsReported() {
        assessed(1);
        assessed(2);
        fit(record(1, "468.000", "91.000"));
        Embodiment answer = fit(record(2, "469.000", "91.000"));
        assertThat(answer.isClean()).isFalse();
        assertThat(answer.clashes().get(0)).isEqualTo(new RepairPair(new RepairId(53, 1), new RepairId(53, 2)));
    }

    @Test
    void aRepairFarFromTheOlderOneIsClean() {
        assessed(1);
        assessed(2);
        fit(record(1, "468.000", "91.000"));
        assertThat(fit(record(2, "620.000", "91.000")).isClean()).isTrue();
    }

    @Test
    void aClashDoesNotStopTheRepairGoingOn() {
        assessed(1);
        assessed(2);
        fit(record(1, "468.000", "91.000"));
        fit(record(2, "469.000", "91.000"));
        assertThat(fittedOnTail()).hasSize(2);
    }

    @Test
    void anEmbodimentReadsBackAsOneLine() {
        assessed();
        assertThat(fit()).hasToString("RP-53-0001 fitted, card closed");
    }

    @Test
    void anEmbodimentWithClashesSaysHowMany() {
        Embodiment made = new Embodiment(
                new RepairId(53, 2),
                DispositionState.CLOSED,
                false,
                List.of(new RepairPair(new RepairId(53, 1), new RepairId(53, 2))));
        assertThat(made.toString()).contains("1 clashes");
    }

    @Test
    void removingARepairTakesItOffTheFittedList() {
        assessed();
        fit();
        UnitOfWork.run(database, unit -> Repairs.remove(unit, new RepairId(53, 1), LocalDate.of(2026, 1, 5)));
        assertThat(fittedOnTail()).isEmpty();
    }

    @Test
    void removingARepairDoesNotReopenTheCard() {
        assessed();
        fit();
        UnitOfWork.run(database, unit -> Repairs.remove(unit, new RepairId(53, 1), LocalDate.of(2026, 1, 5)));
        assertThat(cardState()).isSameAs(DispositionState.CLOSED);
    }

    @Test
    void removingOneThatIsNotOnFileIsRefused() {
        UnitOfWork.run(database, unit -> assertThatThrownBy(() -> Repairs.remove(unit, new RepairId(53, 9), DAY))
                .isInstanceOf(NotOnFile.class));
    }
}
