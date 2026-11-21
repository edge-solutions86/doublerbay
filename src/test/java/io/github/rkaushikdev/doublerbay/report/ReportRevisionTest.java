package io.github.rkaushikdev.doublerbay.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.assess.Disposition;
import io.github.rkaushikdev.doublerbay.fleet.TypeCatalogue;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.manual.Book;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.support.Desk;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReportRevisionTest {

    private static final LocalDate BEFORE = LocalDate.of(2025, 3, 14);
    private static final LocalDate AFTER = LocalDate.of(2026, 3, 14);
    private static final Serial MSN = new Serial("5127");

    private Database database;

    @BeforeEach
    void stocked() {
        database = Desk.newDatabase();
        Desk.enrolled(database);
    }

    private FindingId raised(int sequence) {
        UnitOfWork.run(database, unit -> unit.findings().raiseFinding(Sample.card(sequence)));
        return new FindingId(2025, sequence);
    }

    private FindingId raised() {
        return raised(1);
    }

    private Sweep swept(LocalDate before, LocalDate after) {
        return UnitOfWork.apply(
                database, unit -> Revisions.sweep(unit, Book.v320(), new TypeCatalogue(), before, after));
    }

    private Sweep swept() {
        return swept(BEFORE, AFTER);
    }

    @Test
    void aFleetWithNoCardsLooksAtNothing() {
        Sweep built = swept();
        assertThat(built.lookedAt()).isZero();
        assertThat(built.isQuiet()).isTrue();
    }

    @Test
    void aQuietSweepReadsBackAsQuiet() {
        assertThat(swept()).hasToString("0 cards looked at, nothing moved");
    }

    @Test
    void everyOpenCardIsLookedAt() {
        raised(1);
        raised(2);
        assertThat(swept().lookedAt()).isEqualTo(2);
    }

    @Test
    void aRevisionThatChangesNothingMovesNothing() {
        raised();
        Sweep built = swept();
        assertThat(built.moved()).isEmpty();
        assertThat(built.isQuiet()).isTrue();
    }

    @Test
    void aSupersededCardIsNotLookedAt() {
        raised(1);
        raised(2);
        UnitOfWork.run(database, unit -> unit.findings().supersede(new FindingId(2025, 1), new FindingId(2025, 2)));
        assertThat(swept().lookedAt()).isEqualTo(1);
    }

    @Test
    void aDayAfterThatIsNotAfterIsRefused() {
        assertThatThrownBy(() -> swept(AFTER, BEFORE))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("comes after the day before it");
    }

    @Test
    void theSameDayTwiceIsRefused() {
        assertThatThrownBy(() -> swept(BEFORE, BEFORE)).isInstanceOf(BadReading.class);
    }

    @Test
    void aCardThatCannotBeAssessedIsListedRatherThanDropped() {
        raised();
        Sweep built = swept(BEFORE, LocalDate.of(2050, 1, 1));
        assertThat(built.lookedAt()).isEqualTo(1);
        assertThat(built.moved()).isEmpty();
    }

    /**
     * A window before delivery looks at nothing rather than refusing.
     *
     * <p>The register is asked which tails were in service, and a sweep over a window the fleet did not
     * exist in is an empty sweep, not an error.
     */
    @Test
    void aTailNotInServiceOnTheLaterDayIsSkipped() {
        raised();
        Sweep built = swept(LocalDate.of(2000, 1, 1), LocalDate.of(2000, 1, 2));
        assertThat(built.lookedAt()).isZero();
        assertThat(built.isQuiet()).isTrue();
    }

    @Test
    void aMoveThatGotWorseSaysSo() {
        Moved made = new Moved(new FindingId(2025, 1), MSN, Disposition.ALLOWABLE, Disposition.REPAIRABLE);
        assertThat(made.gotWorse()).isTrue();
        assertThat(made.gotBetter()).isFalse();
        assertThat(made.toString()).contains("(worse)");
    }

    @Test
    void aMoveThatGotBetterSaysSo() {
        Moved made = new Moved(new FindingId(2025, 1), MSN, Disposition.OUT_OF_LIMITS, Disposition.ALLOWABLE);
        assertThat(made.gotBetter()).isTrue();
        assertThat(made.toString()).contains("(better)");
    }

    private static List<Moved> bothWays() {
        return List.of(
                new Moved(new FindingId(2025, 1), MSN, Disposition.ALLOWABLE, Disposition.REPAIRABLE),
                new Moved(new FindingId(2025, 2), MSN, Disposition.OUT_OF_LIMITS, Disposition.ALLOWABLE));
    }

    @Test
    void aSweepCountsBothDirections() {
        Sweep built = new Sweep(BEFORE, AFTER, 3, bothWays(), List.of());
        assertThat(built.worse()).hasSize(1);
        assertThat(built.better()).hasSize(1);
        assertThat(built.isQuiet()).isFalse();
    }

    @Test
    void aSweepLineNamesBothDirections() {
        Sweep built = new Sweep(BEFORE, AFTER, 3, bothWays(), List.of());
        assertThat(built.toString()).contains("1 worse");
        assertThat(built.toString()).contains("1 better");
    }

    @Test
    void aSweepWithSomethingUnreadableSaysSo() {
        Sweep built = new Sweep(BEFORE, AFTER, 1, List.of(), List.of(new FindingId(2025, 1)));
        assertThat(built.toString()).contains("1 could not be assessed");
        assertThat(built.isQuiet()).isFalse();
    }

    @Test
    void theMovesOnOneTailCanBePickedOut() {
        Serial other = new Serial("5240");
        Sweep built = new Sweep(
                BEFORE,
                AFTER,
                2,
                List.of(
                        new Moved(new FindingId(2025, 1), MSN, Disposition.ALLOWABLE, Disposition.REPAIRABLE),
                        new Moved(new FindingId(2025, 2), other, Disposition.ALLOWABLE, Disposition.REPAIRABLE)),
                List.of());
        assertThat(Revisions.worseOn(built, MSN)).hasSize(1);
    }
}
