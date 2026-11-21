package io.github.rkaushikdev.doublerbay.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.damage.Corrosion;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.damage.DamageOrigin;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.fleet.TypeCatalogue;
import io.github.rkaushikdev.doublerbay.manual.Book;
import io.github.rkaushikdev.doublerbay.service.Intake;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.support.Cli;
import io.github.rkaushikdev.doublerbay.support.Cli.Ran;
import io.github.rkaushikdev.doublerbay.support.Desk;
import io.github.rkaushikdev.doublerbay.support.Sample;
import io.github.rkaushikdev.doublerbay.tolerance.CorrosionLevel;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CliCorrodingTest {

    private static final LocalDate DAY = LocalDate.of(2025, 3, 14);
    private static final LocalDate LATER = LocalDate.of(2026, 3, 14);

    private static Ran invoke(Database database, String... args) {
        return Cli.invoke(database, args);
    }

    private static Database stocked() {
        Database database = Desk.newDatabase();
        UnitOfWork.run(database, unit -> {
            unit.aircraft().add(Sample.aircraft());
            unit.aircraft().openLog(Sample.flyingLog());
        });
        return database;
    }

    private static Corrosion rust(String depth) {
        return new Corrosion(Sample.inch("2.000"), Sample.inch("1.000"), Sample.inch(depth));
    }

    private static Intake graded(Database database) {
        return graded(database, 1, DAY, false, "0.030");
    }

    private static Intake graded(Database database, int sequence, LocalDate found) {
        return graded(database, sequence, found, false, "0.030");
    }

    private static Intake graded(Database database, int sequence, LocalDate found, boolean urgent, String depth) {
        Finding finding = Sample.cardWith()
                .sequence(sequence)
                .kind(DamageKind.CORROSION)
                .geometry(rust(depth))
                .found(found)
                .origin(DamageOrigin.ENVIRONMENT)
                .build();
        return UnitOfWork.apply(
                database, unit -> Intake.takeIn(unit, Book.v320(), new TypeCatalogue(), finding, urgent));
    }

    private static Ran programme(Database database) {
        return invoke(
                database, "corrosion", "programme", "--tail", "5127", "--since", "2020-01-01", "--until", "2026-12-31");
    }

    @Test
    void aLevelIsReadOffItsNumber() {
        assertThat(Corroding.levelNumbered(2)).isSameAs(CorrosionLevel.TWO);
    }

    @Test
    void aLevelThatIsNotOneNamesTheOnesThatAre() {
        assertThatThrownBy(() -> Corroding.levelNumbered(9))
                .isInstanceOf(Misused.class)
                .hasMessageContaining("1, 2, 3");
    }

    @Test
    void theLevelsAreTheThreeTheProgrammeGrades() {
        assertThat(Corroding.LEVELS).containsExactly("1", "2", "3");
    }

    @Test
    void aTailWithNoCorrosionSaysSo() {
        String printed =
                invoke(stocked(), "corrosion", "list", "--tail", "5127").output();
        assertThat(printed.strip()).isEqualTo("nothing graded");
    }

    @Test
    void aGradedFindingIsListed() {
        Database database = stocked();
        graded(database);
        String printed = invoke(database, "corrosion", "list", "--tail", "5127").output();
        assertThat(printed).contains("5310");
        assertThat(printed).contains("found on");
    }

    @Test
    void aTailThatIsNotOnTheRegisterIsRefused() {
        assertThat(invoke(stocked(), "corrosion", "list", "--tail", "9999").exitCode())
                .isNotZero();
    }

    @Test
    void theFleetCanBeAskedForOneLevel() {
        Database database = stocked();
        graded(database, 1, DAY);
        graded(database, 2, LATER);
        String printed = invoke(database, "corrosion", "list", "--level", "2").output();
        assertThat(printed).contains("5310");
    }

    @Test
    void aLevelNobodyHasReachedSaysNothing() {
        Database database = stocked();
        graded(database);
        String printed = invoke(database, "corrosion", "list", "--level", "3").output();
        assertThat(printed.strip()).isEqualTo("nothing graded");
    }

    @Test
    void aLevelThatIsNotOneIsRefused() {
        assertThat(invoke(stocked(), "corrosion", "list", "--level", "9").exitCode())
                .isNotZero();
    }

    @Test
    void nothingIsWaitingToBeReported() {
        Database database = stocked();
        graded(database);
        String printed = invoke(database, "corrosion", "unreported", "--on", "2025-03-20")
                .output();
        assertThat(printed.strip()).isEqualTo("nothing waiting to be reported");
    }

    @Test
    void aLevelThreeIsWaitingToBeReported() {
        Database database = stocked();
        graded(database, 1, DAY, true, "0.030");
        String printed = invoke(database, "corrosion", "unreported", "--on", "2025-03-16")
                .output();
        assertThat(printed).contains("2025-03-17");
        assertThat(printed).contains("no");
    }

    @Test
    void aLevelThreePastItsClockIsLate() {
        Database database = stocked();
        graded(database, 1, DAY, true, "0.030");
        String printed = invoke(database, "corrosion", "unreported", "--on", "2025-06-01")
                .output();
        assertThat(printed).contains("yes");
    }

    @Test
    void aFindingCanBeMarkedReported() {
        Database database = stocked();
        graded(database, 1, DAY, true, "0.030");
        Ran answer = invoke(database, "corrosion", "reported", "DR-2025-000001", "--on", "2025-03-16");
        assertThat(answer.exitCode()).as(answer.output()).isZero();
        assertThat(answer.output()).contains("reported DR-2025-000001");
    }

    @Test
    void aReportedFindingDropsOffTheList() {
        Database database = stocked();
        graded(database, 1, DAY, true, "0.030");
        invoke(database, "corrosion", "reported", "DR-2025-000001", "--on", "2025-03-16");
        String printed = invoke(database, "corrosion", "unreported", "--on", "2025-06-01")
                .output();
        assertThat(printed).contains("nothing waiting");
    }

    @Test
    void reportingOneThatIsNotOnTheRegisterIsRefused() {
        Database database = stocked();
        Ran answer = invoke(database, "corrosion", "reported", "DR-2025-000099", "--on", "2025-03-16");
        assertThat(answer.exitCode()).isNotZero();
    }

    @Test
    void theProgrammeReportSaysItIsCoping() {
        Database database = stocked();
        graded(database, 1, DAY, false, "0.002");
        String printed = programme(database).output();
        assertThat(printed).contains("coping");
        assertThat(printed).contains("yes");
    }

    @Test
    void theProgrammeReportNamesItsDenominator() {
        Database database = stocked();
        assertThat(programme(database).output()).contains("not panels inspected");
    }

    @Test
    void aRepeatShowsOnTheProgrammeReport() {
        Database database = stocked();
        graded(database, 1, DAY);
        graded(database, 2, LATER);
        String printed = programme(database).output();
        assertThat(printed).contains("repeat");
        assertThat(printed).contains("2 times over");
    }

    @Test
    void aPersistentLocationPrintsTheWarning() {
        Database database = stocked();
        graded(database, 1, LocalDate.of(2025, 1, 1));
        graded(database, 2, LocalDate.of(2026, 1, 1));
        graded(database, 3, LocalDate.of(2026, 6, 1));
        assertThat(programme(database).output()).contains("keep corroding");
    }

    @Test
    void aWindowThatEndsBeforeItStartsIsRefused() {
        Database database = stocked();
        Ran answer = invoke(
                database, "corrosion", "programme", "--tail", "5127", "--since", "2026-12-31", "--until", "2020-01-01");
        assertThat(answer.exitCode()).isNotZero();
    }

    @Test
    void aLevelThatIsNotOneIsAUsageError() {
        Ran answer = invoke(stocked(), "corrosion", "list", "--level", "9");
        assertThat(answer.exitCode()).isEqualTo(Main.MISUSED);
        assertThat(answer.errors()).contains("Error: Invalid value: 9 is not one of 1, 2, 3");
    }
}
