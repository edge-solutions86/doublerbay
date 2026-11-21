package io.github.rkaushikdev.doublerbay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.disposition.Deferral;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.report.DeskReports;
import io.github.rkaushikdev.doublerbay.report.DueReport;
import io.github.rkaushikdev.doublerbay.report.FleetDamage;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.support.Cli;
import io.github.rkaushikdev.doublerbay.support.Cli.Ran;
import io.github.rkaushikdev.doublerbay.support.Databases;
import io.github.rkaushikdev.doublerbay.tolerance.CorrosionEntry;
import io.github.rkaushikdev.doublerbay.tolerance.CorrosionLevel;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SeedingTest {

    private static final Serial CLEAN = new Serial("5461");
    private static final Serial RUSTY = new Serial("5240");
    private static final Serial DEFERRED = new Serial("5388");

    private Database database;

    @BeforeEach
    void empty() {
        database = Databases.fresh();
    }

    private Seeded sown(LocalDate today) {
        return UnitOfWork.apply(database, unit -> Seeding.seed(unit, today));
    }

    private Seeded sown() {
        return sown(Seeding.LATEST);
    }

    private FleetDamage fleetReport() {
        return UnitOfWork.apply(database, unit -> DeskReports.openAcrossFleet(unit, Seeding.LATEST));
    }

    private List<CorrosionEntry> graded() {
        return UnitOfWork.apply(database, unit -> unit.corrosion().onTail(RUSTY));
    }

    @Test
    void theSeedPutsTheWholeFleetOn() {
        Seeded answer = sown();
        assertThat(answer.aircraft()).isEqualTo(Seeding.FLEET.size());
        int enrolled = UnitOfWork.apply(database, unit -> unit.aircraft().all().size());
        assertThat(enrolled).isEqualTo(4);
    }

    @Test
    void everyTailHasTwoReadingsSoItHasARate() {
        sown();
        UnitOfWork.run(database, unit -> {
            for (SeedTail tail : Seeding.FLEET) {
                assertThat(unit.aircraft().logFor(new Serial(tail.serial())).snapshots())
                        .hasSize(3);
            }
        });
    }

    @Test
    void theSeedReadsBackAsOneLine() {
        assertThat(sown()).hasToString("4 aircraft, 5 damage cards, 1 deferrals");
    }

    @Test
    void seedingTwiceIsRefused() {
        sown();
        UnitOfWork.run(database, unit -> assertThatThrownBy(() -> Seeding.seed(unit))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("already holds a fleet"));
    }

    @Test
    void oneTailIsLeftClean() {
        sown();
        int cards =
                UnitOfWork.apply(database, unit -> unit.findings().onTail(CLEAN).size());
        assertThat(cards).isZero();
    }

    @Test
    void theCleanTailShowsUpAsCleanOnTheFleetReport() {
        sown();
        assertThat(fleetReport().cleanTails()).contains(CLEAN);
    }

    @Test
    void theDentedTailCarriesTwoCards() {
        sown();
        int cards = UnitOfWork.apply(
                database, unit -> unit.findings().onTail(new Serial("5127")).size());
        assertThat(cards).isEqualTo(2);
    }

    @Test
    void theCorrosionComesBackInTheSamePlaceAsALevelTwo() {
        sown();
        List<CorrosionEntry> graded = graded();
        assertThat(graded).hasSize(2);
        assertThat(graded.get(1).level()).isSameAs(CorrosionLevel.TWO);
    }

    @Test
    void theFirstCorrosionIsStillALevelOne() {
        sown();
        assertThat(graded().get(0).level()).isSameAs(CorrosionLevel.ONE);
    }

    @Test
    void oneTailHasSomethingDeferred() {
        sown();
        List<Deferral> live =
                UnitOfWork.apply(database, unit -> unit.deferrals().live());
        assertThat(live).hasSize(1);
        assertThat(live.get(0).against()).isEqualTo(new FindingId(2025, 5));
    }

    @Test
    void theDeferredTailLandsOnItsOwnDueList() {
        sown();
        DueReport report = UnitOfWork.apply(database, unit -> DeskReports.dueOnTail(unit, DEFERRED, Seeding.LATEST));
        assertThat(report.items()).hasSize(1);
    }

    @Test
    void theDeferralCarriesTheReasonItWasGrantedFor() {
        sown();
        String reason = UnitOfWork.apply(
                database, unit -> unit.deferrals().get(new FindingId(2025, 5)).reason());
        assertThat(reason).isEqualTo("awaiting a doubler");
    }

    @Test
    void theDeferredCardIsActuallyDeferred() {
        sown();
        String state = UnitOfWork.apply(
                database,
                unit -> unit.dispositions().get(new FindingId(2025, 5)).state().value());
        assertThat(state).isEqualTo("deferred");
    }

    @Test
    void theFleetReportCountsEveryOpenCard() {
        sown();
        assertThat(fleetReport().total()).isEqualTo(5);
    }

    @Test
    void aLaterDayStillSeeds() {
        Seeded answer = sown(LocalDate.of(2025, 6, 1));
        assertThat(answer.cards()).isEqualTo(5);
    }

    @Test
    void theSeedResultCanBeBuiltDirectly() {
        assertThat(new Seeded(1, 2, 3)).hasToString("1 aircraft, 2 damage cards, 3 deferrals");
    }

    @Test
    void theCommandSeedsAnEmptyDatabase() {
        Ran answer = Cli.invoke(database, "seed");
        assertThat(answer.exitCode()).as(answer.output()).isZero();
        assertThat(answer.output()).contains("seeded 4 aircraft");
    }

    @Test
    void theCommandTakesADay() {
        Ran answer = Cli.invoke(database, "seed", "--on", "2025-06-01");
        assertThat(answer.exitCode()).isZero();
    }

    @Test
    void theCommandRefusesADatabaseThatAlreadyHoldsAFleet() {
        Cli.invoke(database, "seed");
        Ran answer = Cli.invoke(database, "seed");
        assertThat(answer.exitCode()).isNotZero();
    }

    @Test
    void theSeededFleetCanBeListedFromTheCommandLine() {
        Cli.invoke(database, "seed");
        String listed = Cli.invoke(database, "fleet", "list").output();
        assertThat(listed).contains("N412VD");
        assertThat(listed.strip().split("\n", -1)).hasSize(5);
    }
}
