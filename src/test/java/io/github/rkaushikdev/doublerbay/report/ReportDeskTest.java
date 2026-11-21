package io.github.rkaushikdev.doublerbay.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.doublerbay.disposition.Deferral;
import io.github.rkaushikdev.doublerbay.fleet.Snapshot;
import io.github.rkaushikdev.doublerbay.fleet.UtilisationLog;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Interval;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import io.github.rkaushikdev.doublerbay.support.Databases;
import io.github.rkaushikdev.doublerbay.support.Sample;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionMethod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReportDeskTest {

    private static final LocalDate DAY = LocalDate.of(2025, 3, 14);
    private static final Serial MSN = new Serial("5127");
    private static final Serial OTHER = new Serial("5240");

    private Database database;

    private static UtilisationLog logWithTwoReadings(String serial) {
        return UtilisationLog.opened(new Serial(serial), LocalDate.of(2016, 4, 12))
                .posted(new Snapshot(LocalDate.of(2024, 3, 14), Sample.flown(12000, 21000)))
                .posted(new Snapshot(LocalDate.of(2025, 3, 1), Sample.flown(13800, 24200)));
    }

    @BeforeEach
    void oneTailWithARate() {
        database = Databases.fresh();
        UnitOfWork.run(database, unit -> {
            unit.aircraft().add(Sample.aircraft());
            unit.aircraft().openLog(logWithTwoReadings("5127"));
        });
    }

    private void enrolOther(boolean readings) {
        UnitOfWork.run(database, unit -> {
            unit.aircraft().add(Sample.aircraft("5240"));
            unit.aircraft().openLog(readings ? logWithTwoReadings("5240") : UtilisationLog.opened(OTHER, DAY));
        });
    }

    private void enrolOther() {
        enrolOther(true);
    }

    private FindingId raiseCard(int sequence, String serial, LocalDate found) {
        UnitOfWork.run(database, unit -> unit.findings()
                .raiseFinding(Sample.cardWith()
                        .sequence(sequence)
                        .serial(serial)
                        .found(found)
                        .build()));
        return new FindingId(2025, sequence);
    }

    private FindingId raiseCard(int sequence, String serial) {
        return raiseCard(sequence, serial, DAY);
    }

    private FindingId raiseCard(int sequence) {
        return raiseCard(sequence, "5127");
    }

    private FindingId raiseCard() {
        return raiseCard(1);
    }

    private void defer(FindingId reference, String limit, LocalDate on, int cycles, long hours) {
        UnitOfWork.run(database, unit -> unit.deferrals()
                .grant(Deferral.grant(
                        reference,
                        on,
                        Sample.flown(cycles, hours),
                        "j.mercer",
                        InspectionMethod.DVI,
                        Interval.parse(limit),
                        null,
                        "")));
    }

    private void defer(FindingId reference, String limit) {
        defer(reference, limit, DAY, 13800, 24200);
    }

    private void defer(FindingId reference) {
        defer(reference, "500 FC");
    }

    /** Past its limit long ago: one cycle allowed, granted at a hundred. */
    private void deferredLongAgo(FindingId reference) {
        defer(reference, "1 FC", LocalDate.of(2024, 1, 1), 100, 200);
    }

    private DueReport due(Serial serial) {
        return UnitOfWork.apply(database, unit -> DeskReports.dueOnTail(unit, serial, DAY));
    }

    private List<DueReport> fleet() {
        return UnitOfWork.apply(database, unit -> DeskReports.dueAcrossFleet(unit, DAY));
    }

    @Test
    void aTailWithTwoReadingsGetsItsOwnRate() {
        TailRate rate = UnitOfWork.apply(database, unit -> DeskReports.rateFor(unit, MSN));
        assertThat(rate.measured()).isTrue();
        assertThat(rate.forecast().cyclesADay()).isPositive();
    }

    @Test
    void aTailWithOneReadingFallsBackToThePlanningRate() {
        enrolOther(false);
        TailRate rate = UnitOfWork.apply(database, unit -> DeskReports.rateFor(unit, OTHER));
        assertThat(rate.measured()).isFalse();
        assertThat(rate.forecast().cyclesADay()).isEqualByComparingTo(DeskReports.PLANNED_CYCLES_A_DAY);
        assertThat(rate.forecast().hoursADay()).isEqualByComparingTo(new BigDecimal("8.1"));
    }

    @Test
    void aTailWithNothingDeferredHasNothingDue() {
        DueReport report = due(MSN);
        assertThat(report.items()).isEmpty();
        assertThat(report.isGrounded()).isFalse();
    }

    @Test
    void aDeferralShowsUpOnTheDueList() {
        defer(raiseCard());
        DueReport report = due(MSN);
        assertThat(report.items()).hasSize(1);
        assertThat(report.items().get(0).against()).isEqualTo(new FindingId(2025, 1));
    }

    @Test
    void theReportSaysWhetherTheRateBehindItIsReal() {
        defer(raiseCard());
        assertThat(due(MSN).measuredRate()).isTrue();
    }

    @Test
    void aPlanningRateIsNamedInTheLine() {
        enrolOther(false);
        assertThat(due(OTHER)).hasToString("MSN 5240: 0 due, 0 over, on the planning rate");
    }

    @Test
    void anotherTailsDeferralIsNotOnThisTailsList() {
        enrolOther();
        defer(raiseCard(2, "5240"));
        assertThat(due(MSN).items()).isEmpty();
        assertThat(due(OTHER).items()).hasSize(1);
    }

    @Test
    void aDeferralAgainstACardThatIsNotOnFileIsLeftOut() {
        defer(new FindingId(2025, 99));
        assertThat(due(MSN).items()).isEmpty();
    }

    @Test
    void aLiftedDeferralDropsOffTheDueList() {
        FindingId reference = raiseCard();
        defer(reference);
        UnitOfWork.run(database, unit -> unit.deferrals().lift(reference, DAY));
        assertThat(due(MSN).items()).isEmpty();
    }

    @Test
    void aHorizonCutsTheTailReportDown() {
        defer(raiseCard(1), "500 FC");
        defer(raiseCard(2), "20 FC");
        DueReport report = UnitOfWork.apply(database, unit -> DeskReports.dueOnTail(unit, MSN, DAY, 30));
        assertThat(report.items()).hasSize(1);
    }

    @Test
    void aTailPastALimitIsGrounded() {
        deferredLongAgo(raiseCard());
        DueReport report = due(MSN);
        assertThat(report.isGrounded()).isTrue();
        assertThat(report.overrun()).hasSize(1);
    }

    @Test
    void theFleetReportCoversEveryTail() {
        enrolOther();
        assertThat(fleet()).hasSize(2);
    }

    @Test
    void theFleetReportPutsTheGroundedTailFirst() {
        enrolOther();
        deferredLongAgo(raiseCard(2, "5240"));
        assertThat(fleet().get(0).serial()).isEqualTo(OTHER);
    }

    @Test
    void twoTailsWithNothingDueSortBySerial() {
        enrolOther();
        assertThat(fleet()).extracting(DueReport::serial).containsExactly(MSN, OTHER);
    }

    @Test
    void theGroundingListIsOnlyTheTailsThatAreOver() {
        enrolOther();
        deferredLongAgo(raiseCard(2, "5240"));
        defer(raiseCard(1), "500 FC");
        List<DueReport> grounded = UnitOfWork.apply(database, unit -> DeskReports.grounded(unit, DAY));
        assertThat(grounded).extracting(DueReport::serial).containsExactly(OTHER);
    }

    @Test
    void aFleetWithNothingOverGroundsNothing() {
        defer(raiseCard(), "500 FC");
        List<DueReport> grounded = UnitOfWork.apply(database, unit -> DeskReports.grounded(unit, DAY));
        assertThat(grounded).isEmpty();
    }

    @Test
    void theOpenCountOnOneTailComesOffTheCards() {
        raiseCard(1);
        raiseCard(2);
        DamageCount count = UnitOfWork.apply(database, unit -> DeskReports.openOnTail(unit, MSN, DAY));
        assertThat(count.total()).isEqualTo(2);
    }

    @Test
    void aTailWithNoCardsCountsClean() {
        DamageCount count = UnitOfWork.apply(database, unit -> DeskReports.openOnTail(unit, MSN, DAY));
        assertThat(count.isClean()).isTrue();
    }

    @Test
    void theRecentWindowReachesTheTailCount() {
        raiseCard(1, "5127", LocalDate.of(2024, 12, 1));
        UnitOfWork.run(database, unit -> {
            assertThat(DeskReports.openOnTail(unit, MSN, DAY).raisedRecently()).isZero();
            assertThat(DeskReports.openOnTail(unit, MSN, DAY, 365).raisedRecently())
                    .isOne();
        });
    }

    @Test
    void theFleetOpenCountCoversTheRegister() {
        enrolOther();
        raiseCard(1);
        raiseCard(2, "5240");
        FleetDamage report = UnitOfWork.apply(database, unit -> DeskReports.openAcrossFleet(unit, DAY));
        assertThat(report.total()).isEqualTo(2);
        assertThat(report.tails()).hasSize(2);
    }

    @Test
    void aTailNotYetDeliveredIsLeftOutOfTheFleetCount() {
        FleetDamage report =
                UnitOfWork.apply(database, unit -> DeskReports.openAcrossFleet(unit, LocalDate.of(2000, 1, 1)));
        assertThat(report.tails()).isEmpty();
    }
}
