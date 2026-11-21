package io.github.rkaushikdev.doublerbay.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.PanelId;
import io.github.rkaushikdev.doublerbay.ids.Registration;
import io.github.rkaushikdev.doublerbay.ids.RepairId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Cycles;
import io.github.rkaushikdev.doublerbay.measure.Hours;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.support.Databases;
import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkTablesTest {

    private static final Serial MSN = new Serial("5127");
    private static final PanelId PART = new PanelId(53, 10, 143, "L");
    private static final FindingId DR = new FindingId(2025, 4182);
    private static final RepairId RP = new RepairId(53, 142);

    private Database database;

    @BeforeEach
    void oneTail() {
        database = Databases.fresh();
        database.inSession(session -> session.persist(
                new AircraftRow(MSN, new Registration("N412VD"), "200", 1140, LocalDate.of(2016, 4, 12))));
    }

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    private static RepairRow repair(LocalDate removed) {
        return new RepairRow()
                .setReference(RP)
                .setSerial(MSN)
                .setPart(PART)
                .setAgainst(DR)
                .setForm("external doubler")
                .setCategory("A")
                .setSealed(true)
                .setPage("53-40-01")
                .setMaterial("2024-T3 CLAD")
                .setGauge(inch("0.050"))
                .setAlong(inch("5.282"))
                .setAround(inch("5.282"))
                .setFiller(null)
                .setFastenerDash(5)
                .setRowsEachSide(2)
                .setPerRow(4)
                .setPitch(inch("0.625"))
                .setRowSpacing(inch("0.625"))
                .setEdgeDistance(inch("0.391"))
                .setCentreAlong(inch("468.000"))
                .setCentreAround(inch("91.000"))
                .setEmbodied(LocalDate.of(2025, 4, 2))
                .setAtCycles(new Cycles(13900))
                .setAtHours(Hours.ofHours(24400))
                .setRemoved(removed);
    }

    private static RepairRow repair() {
        return repair(null);
    }

    private static InspectionRow inspection(String method) {
        return new InspectionRow()
                .setAgainst(RP)
                .setPart(PART)
                .setMethod(method)
                .setEmbodied(LocalDate.of(2025, 4, 2))
                .setAtCycles(new Cycles(13900))
                .setAtHours(Hours.ofHours(24400));
    }

    private static DeferralRow deferral(LocalDate granted, int cycles, long hours, int days) {
        return new DeferralRow()
                .setAgainst(DR)
                .setGranted(granted)
                .setAtCycles(new Cycles(cycles))
                .setAtHours(Hours.ofHours(hours))
                .setLimitDays(days)
                .setMethod("detailed visual")
                .setGrantedBy("structures");
    }

    private static OrderRow order(String reference, LocalDate raised) {
        return new OrderRow()
                .setReference(reference)
                .setAgainst(DR)
                .setQuestion("crack past the book at FR41")
                .setRaised(raised)
                .setState("raised");
    }

    private static OrderRow order() {
        return order("EO-2025-0001", LocalDate.of(2025, 3, 14));
    }

    private <T> T onlyOne(Class<T> type) {
        return database.fromSession(session -> session.createQuery("select r from " + type.getSimpleName() + " r", type)
                .getSingleResult());
    }

    @Test
    void theWorkTablesAreInTheSchema() {
        List<String> names = Database.tableNames();
        assertThat(names).contains("repair", "inspection", "deferral", "engineering_order", "corrosion_entry");
    }

    @Test
    void aRepairGoesInWithItsWholeDrawingOnTheRow() {
        database.inSession(session -> session.persist(repair()));
        RepairRow row = onlyOne(RepairRow.class);
        assertThat(row.getReference()).isEqualTo(RP);
        assertThat(row.getGauge()).isEqualTo(inch("0.050"));
        assertThat(row.getRowsEachSide()).isEqualTo(2);
        assertThat(row.getCategory()).isEqualTo("A");
    }

    @Test
    void aRepairNumberOnlyGoesInOnce() {
        database.inSession(session -> session.persist(repair()));
        assertThatThrownBy(() -> database.inSession(session -> session.persist(repair())))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aRemovedRepairKeepsTheDayItCameOff() {
        database.inSession(session -> session.persist(repair(LocalDate.of(2026, 6, 1))));
        assertThat(onlyOne(RepairRow.class).getRemoved()).contains(LocalDate.of(2026, 6, 1));
    }

    @Test
    void anInspectionHangsOffTheRepairItBelongsTo() {
        database.inSession(session -> {
            session.persist(repair());
            session.persist(inspection("detailed visual")
                    .setThresholdCycles(new Cycles(55000))
                    .setRepeatCycles(new Cycles(27500)));
        });
        database.inSession(session -> {
            RepairRow row = session.createQuery("select r from RepairRow r", RepairRow.class)
                    .getSingleResult();
            assertThat(row.getInspection()).isPresent();
            assertThat(row.getInspection().orElseThrow().getMethod()).isEqualTo("detailed visual");
        });
    }

    @Test
    void aRepairCarriesAtMostOneInspection() {
        database.inSession(session -> {
            session.persist(repair());
            session.persist(inspection("detailed visual"));
        });
        assertThatThrownBy(
                        () -> database.inSession(session -> session.persist(inspection("high frequency eddy current"))))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aDeferralStoresEveryClockTheLimitNames() {
        database.inSession(session -> session.persist(deferral(LocalDate.of(2025, 3, 14), 13800, 24200, 180)
                .setLimitCycles(new Cycles(500))
                .setLimitHours(Hours.ofHours(750))));
        DeferralRow row = onlyOne(DeferralRow.class);
        assertThat(row.getLimitCycles()).contains(new Cycles(500));
        assertThat(row.getLimitDays()).contains(180);
        assertThat(row.getLimitMonths()).isEmpty();
    }

    @Test
    void aCardCarriesOneDeferralAtATime() {
        database.inSession(session -> session.persist(deferral(LocalDate.of(2025, 3, 14), 13800, 24200, 180)));
        assertThatThrownBy(() -> database.inSession(
                        session -> session.persist(deferral(LocalDate.of(2025, 6, 1), 14000, 24500, 30))))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void anOrderGoesInAndComesBack() {
        database.inSession(session -> session.persist(order()));
        OrderRow row = onlyOne(OrderRow.class);
        assertThat(row.getReference()).isEqualTo("EO-2025-0001");
        assertThat(row.getSheet()).isEmpty();
    }

    @Test
    void oneCardDoesNotGetTwoOrdersRaisedOnOneDay() {
        database.inSession(session -> session.persist(order()));
        assertThatThrownBy(() -> database.inSession(
                        session -> session.persist(order("EO-2025-0002", LocalDate.of(2025, 3, 14)))))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void theSameCardCanHaveASecondOrderOnAnotherDay() {
        database.inSession(session -> {
            session.persist(order());
            session.persist(order("EO-2025-0002", LocalDate.of(2025, 6, 1)));
        });
        List<OrderRow> rows = database.fromSession(session ->
                session.createQuery("select o from OrderRow o", OrderRow.class).getResultList());
        assertThat(rows).hasSize(2);
    }

    @Test
    void aCorrosionEntryRecordsTheLevelAndWhenItWasReported() {
        database.inSession(session -> session.persist(new CorrosionRow()
                .setSerial(MSN)
                .setAgainst(DR)
                .setPart(PART)
                .setLevel(3)
                .setFoundOn(LocalDate.of(2025, 5, 6))
                .setReportedOn(LocalDate.of(2025, 5, 8))));
        CorrosionRow row = onlyOne(CorrosionRow.class);
        assertThat(row.getLevel()).isEqualTo(3);
        assertThat(row.getReportedOn()).contains(LocalDate.of(2025, 5, 8));
    }

    @Test
    void theWorkRowsPrintReadablyForALog() {
        assertThat(repair()).hasToString("<RepairRow RP-53-0142 on 5310-143L>");
        assertThat(order()).hasToString("<OrderRow EO-2025-0001 raised>");
    }
}
