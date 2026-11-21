package io.github.rkaushikdev.doublerbay.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.damage.Finding;
import io.github.rkaushikdev.doublerbay.disposition.DispositionRecord;
import io.github.rkaushikdev.doublerbay.disposition.DispositionState;
import io.github.rkaushikdev.doublerbay.fleet.Aircraft;
import io.github.rkaushikdev.doublerbay.fleet.UtilisationLog;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.support.Desk;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The shared test helpers, tested, because everything else leans on them. A fixture that quietly
 * does the wrong thing makes a hundred tests pass while proving nothing, and it is the one file in a
 * suite nobody checks.
 */
class ConftestHelpersTest {

    private static List<Aircraft> fleet(Database database) {
        return UnitOfWork.apply(database, unit -> unit.aircraft().all());
    }

    private static DispositionRecord disposition(Database database, FindingId reference) {
        return UnitOfWork.apply(database, unit -> unit.dispositions().get(reference));
    }

    private static Finding latest(Database database, FindingId reference) {
        return UnitOfWork.apply(database, unit -> unit.findings().latest(reference));
    }

    private static Database enrolled() {
        Database database = Desk.newDatabase();
        Desk.enrolled(database);
        return database;
    }

    @Test
    void aNewDatabaseHasTheSchemaOnIt() {
        assertThat(fleet(Desk.newDatabase())).isEmpty();
        assertThat(Database.tableNames().size()).isGreaterThan(5);
    }

    @Test
    void aNewDatabaseHoldsNoFleet() {
        assertThat(fleet(Desk.newDatabase())).isEmpty();
    }

    @Test
    void enrollingPutsOneTailOn() {
        assertThat(fleet(enrolled())).hasSize(1);
    }

    @Test
    void enrollingOpensALog() {
        UtilisationLog log =
                UnitOfWork.apply(enrolled(), unit -> unit.aircraft().logFor(new Serial("5127")));
        assertThat(log.snapshots()).isNotEmpty();
    }

    @Test
    void anotherSerialCanBeEnrolled() {
        Database database = Desk.newDatabase();
        Desk.enrolled(database, "5240");
        Aircraft held = UnitOfWork.apply(database, unit -> unit.aircraft().get(new Serial("5240")));
        assertThat(held.serial()).isEqualTo(new Serial("5240"));
    }

    @Test
    void theDependenciesPinTheDayTheSuiteAnswersAsOf() {
        assertThat(Desk.answeredOn()).isEqualTo(Desk.TODAY);
    }

    @Test
    void theDayCanBeLeftUnpinned() {
        assertThat(Desk.answeredOn(null)).isEqualTo(LocalDate.now());
    }

    @Test
    void aClientCarriesTheRoleItWasAskedFor() {
        assertThat(Desk.headersFor("reader")).containsEntry("x-api-key", Desk.KEYS.get("reader"));
        assertThatThrownBy(() -> Desk.headersFor("visitor")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyRoleHasAKey() {
        assertThat(Desk.KEYS).containsOnlyKeys("reader", "mechanic", "engineer", "chief");
    }

    @Test
    void aRaisedCardIsOnTheRegister() {
        Database database = enrolled();
        FindingId reference = Desk.raiseCard(database);
        assertThat(latest(database, reference).reference()).isEqualTo(reference);
    }

    @Test
    void aRaisedCardStartsRaised() {
        Database database = enrolled();
        FindingId reference = Desk.raiseCard(database);
        assertThat(disposition(database, reference).state()).isSameAs(DispositionState.RAISED);
    }

    @Test
    void aRaisedCardTakesItsSequence() {
        assertThat(Desk.raiseCard(enrolled(), 7)).isEqualTo(new FindingId(2025, 7));
    }

    @Test
    void aRaisedCardCanBePutOnAnotherTail() {
        Database database = enrolled();
        Desk.enrolled(database, "5240");
        FindingId reference = Desk.raiseCard(database, 1, "5240");
        assertThat(latest(database, reference).serial()).isEqualTo(new Serial("5240"));
    }

    @Test
    void assessingMovesTheCard() {
        Database database = enrolled();
        FindingId reference = Desk.assessCard(database, Desk.raiseCard(database));
        assertThat(disposition(database, reference).state()).isSameAs(DispositionState.ASSESSED);
    }

    @Test
    void anAssessedCardDoesBothSteps() {
        Database database = enrolled();
        FindingId reference = Desk.assessedCard(database);
        assertThat(disposition(database, reference).state()).isSameAs(DispositionState.ASSESSED);
        assertThat(latest(database, reference)).isNotNull();
    }

    @Test
    void anAssessedCardRecordsOneMove() {
        Database database = enrolled();
        FindingId reference = Desk.assessedCard(database);
        assertThat(disposition(database, reference).history()).hasSize(1);
    }

    @Test
    void assessingACardThatIsNotThereIsRefused() {
        Database database = enrolled();
        assertThatThrownBy(() -> Desk.assessCard(database, new FindingId(2025, 99)))
                .isInstanceOf(NotOnFile.class);
    }
}
