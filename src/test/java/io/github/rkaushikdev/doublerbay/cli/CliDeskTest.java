package io.github.rkaushikdev.doublerbay.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.disposition.Deferral;
import io.github.rkaushikdev.doublerbay.disposition.DispositionRecord;
import io.github.rkaushikdev.doublerbay.disposition.DispositionState;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.support.Cli;
import io.github.rkaushikdev.doublerbay.support.Cli.Ran;
import io.github.rkaushikdev.doublerbay.support.Databases;
import io.github.rkaushikdev.doublerbay.support.Sample;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionMethod;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CliDeskTest {

    private static final String DAY = "2025-03-14";
    private static final String LATER = "2025-06-01";
    private static final FindingId FIRST = new FindingId(2025, 1);

    private static Ran invoke(Database database, String... args) {
        return Cli.invoke(database, args);
    }

    private static Database stocked() {
        Database database = Databases.fresh();
        enrol(database, "5127", "N412VD");
        return database;
    }

    private static void enrol(Database database, String serial, String marks) {
        invoke(database, "fleet", "enrol", serial, marks, "--line-number", "1140", "--delivered", "2016-04-12");
        invoke(database, "fleet", "post", serial, "2024-03-14", "--cycles", "12000", "--hours", "21000");
        invoke(database, "fleet", "post", serial, "2025-03-01", "--cycles", "13800", "--hours", "24200");
    }

    /** A card on file that has been looked up, which is what a deferral needs. */
    private static String assessed(Database database) {
        return assessed(database, 1, "5127");
    }

    private static String assessed(Database database, int sequence, String serial) {
        FindingId reference = new FindingId(2025, sequence);
        UnitOfWork.run(database, unit -> {
            unit.findings().raiseFinding(Sample.card(sequence, serial));
            DispositionRecord record = DispositionRecord.raised(reference);
            unit.dispositions().save(record.movedTo(DispositionState.ASSESSED, LocalDate.of(2025, 3, 14), "a.okonkwo"));
        });
        return reference.toString();
    }

    private static void raisedOnly(Database database) {
        UnitOfWork.run(database, unit -> {
            unit.findings().raiseFinding(Sample.card(1));
            unit.dispositions().save(DispositionRecord.raised(FIRST));
        });
    }

    private static Ran granted(Database database, String reference, String... extra) {
        List<String> args = new ArrayList<>(List.of("defer", "grant", reference, "--by", "j.mercer", "--on", DAY));
        args.addAll(List.of(extra));
        return invoke(database, args.toArray(String[]::new));
    }

    private static Deferral deferralOf(Database database, FindingId reference) {
        return UnitOfWork.apply(database, unit -> unit.deferrals().get(reference));
    }

    private static List<Deferral> live(Database database) {
        return UnitOfWork.apply(database, unit -> unit.deferrals().live());
    }

    @Test
    void aMethodIsReadOffItsShortName() {
        assertThat(Desk.methodNamed("hfec")).isSameAs(InspectionMethod.HFEC);
        assertThat(Desk.methodNamed(" DVI ")).isSameAs(InspectionMethod.DVI);
    }

    @Test
    void aMethodThatIsNotOneNamesTheOnesThatAre() {
        assertThatThrownBy(() -> Desk.methodNamed("magic"))
                .isInstanceOf(Misused.class)
                .hasMessageContaining("lfec");
    }

    @Test
    void aDayLeftOffFallsBackToTheRealOne() {
        assertThat(Desk.todayOr(null)).isEqualTo(LocalDate.now());
        assertThat(Desk.todayOr("2025-03-14")).isEqualTo(LocalDate.of(2025, 3, 14));
    }

    @Test
    void aCardCanBeDeferred() {
        Database database = stocked();
        Ran answer = granted(database, assessed(database));
        assertThat(answer.exitCode()).as(answer.output()).isZero();
        assertThat(answer.output()).contains("deferred DR-2025-000001");
    }

    @Test
    void deferringMovesTheDispositionAsWellAsWritingTheLimit() {
        Database database = stocked();
        granted(database, assessed(database));
        UnitOfWork.run(database, unit -> {
            assertThat(unit.dispositions().get(FIRST).state()).isSameAs(DispositionState.DEFERRED);
            assertThat(unit.deferrals().get(FIRST).by()).isEqualTo("j.mercer");
        });
    }

    @Test
    void aCardThatHasNotBeenAssessedCannotBeDeferred() {
        Database database = stocked();
        raisedOnly(database);
        Ran answer = granted(database, "DR-2025-000001");
        assertThat(answer.exitCode()).isNotZero();
        assertThat(answer.failure()).contains("cannot go from raised to deferred");
    }

    @Test
    void aRefusedDeferralLeavesNoLimitBehind() {
        Database database = stocked();
        raisedOnly(database);
        granted(database, "DR-2025-000001");
        assertThat(live(database)).isEmpty();
    }

    @Test
    void anEngineerSetLimitIsUsed() {
        Database database = stocked();
        granted(database, assessed(database), "--limit", "50 FC");
        assertThat(deferralOf(database, FIRST).limit().cycles().orElseThrow().count())
                .isEqualTo(50);
    }

    @Test
    void aDeferralToACheckIsCutAtTheCheck() {
        Database database = stocked();
        granted(database, assessed(database), "--until-check", "2025-04-01");
        assertThat(deferralOf(database, FIRST).limit().days()).contains(18);
    }

    @Test
    void theMethodReachesTheStoredDeferral() {
        Database database = stocked();
        granted(database, assessed(database), "--method", "hfec");
        assertThat(deferralOf(database, FIRST).reInspectWith()).isSameAs(InspectionMethod.HFEC);
    }

    @Test
    void aDeferralShowsWhatItIsDueAgainst() {
        Database database = stocked();
        String reference = assessed(database);
        granted(database, reference, "--limit", "500 FC", "--reason", "awaiting parts");
        String shown = invoke(database, "defer", "show", reference).output();
        assertThat(shown).contains("awaiting parts");
        assertThat(shown).contains("14300 FC");
    }

    @Test
    void aDeferralWithNoReasonSaysSo() {
        Database database = stocked();
        String reference = assessed(database);
        granted(database, reference);
        assertThat(invoke(database, "defer", "show", reference).output()).contains("not given");
    }

    @Test
    void aLiveDeferralCanBeTightened() {
        Database database = stocked();
        String reference = assessed(database);
        granted(database, reference, "--limit", "500 FC");
        Ran answer = invoke(database, "defer", "tighten", reference, "--limit", "50 FC", "--by", "a.okonkwo");
        assertThat(answer.exitCode()).isZero();
        assertThat(deferralOf(database, FIRST).limit().cycles().orElseThrow().count())
                .isEqualTo(50);
    }

    @Test
    void tighteningOneThatIsNotDeferredIsRefused() {
        Database database = stocked();
        Ran answer = invoke(database, "defer", "tighten", "DR-2025-000009", "--limit", "50 FC", "--by", "a.okonkwo");
        assertThat(answer.exitCode()).isNotZero();
    }

    @Test
    void aDeferralCanBeLifted() {
        Database database = stocked();
        String reference = assessed(database);
        granted(database, reference);
        assertThat(invoke(database, "defer", "lift", reference, "--on", LATER).exitCode())
                .isZero();
        assertThat(live(database)).isEmpty();
    }

    @Test
    void liftingOneThatWasNeverGrantedIsRefused() {
        Database database = stocked();
        Ran answer = invoke(database, "defer", "lift", "DR-2025-000009", "--on", LATER);
        assertThat(answer.exitCode()).isNotZero();
    }

    @Test
    void aFleetWithNothingDueSaysSo() {
        Database database = stocked();
        assertThat(invoke(database, "report", "due", "--on", DAY).output().strip())
                .isEqualTo("nothing due");
    }

    @Test
    void theDueReportListsTheDeferral() {
        Database database = stocked();
        granted(database, assessed(database), "--limit", "500 FC");
        String printed = invoke(database, "report", "due", "--on", DAY).output();
        assertThat(printed).contains("DR-2025-000001");
        assertThat(printed).contains("days on");
    }

    @Test
    void theDueReportCanBeAskedForOneTail() {
        Database database = stocked();
        enrol(database, "5240", "N908QA");
        granted(database, assessed(database, 1, "5127"), "--limit", "500 FC");
        String printed =
                invoke(database, "report", "due", "--tail", "5240", "--on", DAY).output();
        assertThat(printed.strip()).isEqualTo("nothing due");
    }

    @Test
    void theDueReportTakesAHorizon() {
        Database database = stocked();
        granted(database, assessed(database), "--limit", "500 FC");
        String printed =
                invoke(database, "report", "due", "--on", DAY, "--within", "5").output();
        assertThat(printed.strip()).isEqualTo("nothing due");
    }

    @Test
    void nothingGroundedSaysSo() {
        Database database = stocked();
        assertThat(invoke(database, "report", "grounded", "--on", DAY).output().strip())
                .isEqualTo("nothing grounded");
    }

    @Test
    void aTailPastItsLimitIsListedAsGrounded() {
        Database database = stocked();
        granted(database, assessed(database), "--limit", "30 DY");
        String printed =
                invoke(database, "report", "grounded", "--on", "2026-06-01").output();
        assertThat(printed).contains("5127");
        assertThat(printed).contains("measured");
    }

    @Test
    void theOpenReportCountsTheFleet() {
        Database database = stocked();
        assessed(database);
        String printed = invoke(database, "report", "open", "--on", DAY).output();
        assertThat(printed).contains("fleet");
        assertThat(printed).contains("5127");
    }

    @Test
    void theOpenReportOnOneTailBreaksItDown() {
        Database database = stocked();
        assessed(database);
        String printed = invoke(database, "report", "open", "--tail", "5127", "--on", DAY)
                .output();
        assertThat(printed).contains("  dent");
        assertThat(printed).contains("  chapter 53");
    }

    @Test
    void theOpenReportTakesARecentWindow() {
        Database database = stocked();
        assessed(database);
        String printed = invoke(database, "report", "open", "--tail", "5127", "--on", DAY, "--recent", "1")
                .output();
        assertThat(printed).contains("raised recently");
    }

    @Test
    void aTailThatIsNotOnTheRegisterCannotBeReportedOn() {
        Database database = stocked();
        Ran answer = invoke(database, "report", "open", "--tail", "9999", "--on", DAY);
        assertThat(answer.exitCode()).isNotZero();
    }

    @Test
    void theSerialOnTheDueReportIsTheOneAskedFor() {
        Database database = stocked();
        granted(database, assessed(database), "--limit", "500 FC");
        Serial serial =
                UnitOfWork.apply(database, unit -> unit.findings().latest(FIRST).serial());
        assertThat(serial).isEqualTo(new Serial("5127"));
    }

    @Test
    void aMethodTheShopHasNoNameForIsAUsageError() {
        Database database = stocked();
        Ran answer = granted(database, assessed(database), "--method", "magic");
        assertThat(answer.exitCode()).isEqualTo(Main.MISUSED);
        assertThat(answer.errors())
                .contains("Error: Invalid value: 'magic' is not one of gvi, dvi, xray, ut, lfec, hfec");
        assertThat(live(database)).isEmpty();
    }
}
