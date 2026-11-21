package io.github.rkaushikdev.doublerbay.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.Registration;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.measure.Cycles;
import io.github.rkaushikdev.doublerbay.measure.Hours;
import io.github.rkaushikdev.doublerbay.measure.Length;
import io.github.rkaushikdev.doublerbay.support.Databases;
import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TablesTest {

    private static final Serial MSN = new Serial("5127");

    private Database database;

    @BeforeEach
    void schema() {
        database = Databases.fresh();
    }

    private static Length inch(String text) {
        return Length.ofInches(text);
    }

    private static AircraftRow aircraft(Serial serial, String marks) {
        return new AircraftRow(serial, new Registration(marks), "200", 1140, LocalDate.of(2016, 4, 12));
    }

    private static AircraftRow aircraft() {
        return aircraft(MSN, "N412VD");
    }

    private static FindingRow finding(int revision) {
        Map<String, Object> measurements = new LinkedHashMap<>();
        measurements.put("depth", 20);
        measurements.put("along", 3000);
        measurements.put("around", 2000);
        return new FindingRow(
                        new FindingId(2025, 4182),
                        revision,
                        MSN,
                        "dent",
                        LocalDate.of(2025, 3, 14),
                        "scheduled check",
                        "impact",
                        new Cycles(13900),
                        Hours.ofHours(24400),
                        "fuselage",
                        "L",
                        measurements)
                .onFuselage(15, inch("10.000"), 6, inch("0.000"));
    }

    private static FindingRow finding() {
        return finding(1);
    }

    private static ReadingRow reading(int cycles, int minutes) {
        return new ReadingRow(MSN, LocalDate.of(2025, 1, 1), new Cycles(cycles), new Hours(minutes));
    }

    private static MoveRow move(FindingId reference, String was, String now, LocalDate on) {
        return new MoveRow(reference, 1, was, now, on, "structures");
    }

    private <T> List<T> every(Class<T> type) {
        return database.fromSession(session -> session.createQuery("select r from " + type.getSimpleName() + " r", type)
                .getResultList());
    }

    private <T> T onlyOne(Class<T> type) {
        List<T> rows = every(type);
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    @Test
    void theSchemaNamesEveryTableItDefines() {
        assertThat(Database.tableNames()).contains("aircraft", "embodiment", "reading", "finding", "disposition");
    }

    @Test
    void aTailGoesInAndComesBackWithItsOwnTypes() {
        database.inSession(session -> session.persist(aircraft()));
        AircraftRow row = onlyOne(AircraftRow.class);
        assertThat(row.getSerial()).isEqualTo(MSN);
        assertThat(row.getRegistration()).isEqualTo(new Registration("N412VD"));
        assertThat(row.getDelivered()).isEqualTo(LocalDate.of(2016, 4, 12));
        assertThat(row.getRecordedAt()).isPresent();
    }

    @Test
    void aTailIsKeyedOnTheSerialAndNotTheMarks() {
        database.inSession(session -> session.persist(aircraft()));
        assertThatThrownBy(() -> database.inSession(session -> session.persist(aircraft(MSN, "N908QA"))))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void twoTailsCanWearTheSameMarksAtDifferentTimes() {
        database.inSession(session -> {
            session.persist(aircraft());
            session.persist(aircraft(new Serial("5240"), "N412VD"));
        });
        assertThat(every(AircraftRow.class)).hasSize(2);
    }

    @Test
    void aBulletinOnlyGoesOnATailOnce() {
        database.inSession(session -> {
            session.persist(aircraft());
            session.persist(new EmbodimentRow(MSN, 53, 18));
        });
        assertThatThrownBy(() -> database.inSession(session -> session.persist(new EmbodimentRow(MSN, 53, 18))))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void twoReadingsOnOneDayAreRefusedByTheKey() {
        database.inSession(session -> {
            session.persist(aircraft());
            session.persist(reading(13800, 1452000));
        });
        assertThatThrownBy(() -> database.inSession(session -> session.persist(reading(13900, 1460000))))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void theCountersComeBackAsCounters() {
        database.inSession(session -> {
            session.persist(aircraft());
            session.persist(reading(13800, 1452000));
        });
        ReadingRow row = onlyOne(ReadingRow.class);
        assertThat(row.getCycles()).isEqualTo(new Cycles(13800));
        assertThat(row.getHours()).isEqualTo(new Hours(1452000));
    }

    @Test
    void aCardGoesInWithItsLocationAsItWasWritten() {
        database.inSession(session -> {
            session.persist(aircraft());
            session.persist(finding());
        });
        FindingRow row = onlyOne(FindingRow.class);
        assertThat(row.getReference()).isEqualTo(new FindingId(2025, 4182));
        assertThat(row.getFrame()).contains(15);
        assertThat(row.getAftOfFrame()).contains(inch("10.000"));
        assertThat(row.getRib()).isEmpty();
        assertThat(row.getStructure()).isEqualTo("fuselage");
    }

    @Test
    void theMeasurementsRideAlongAsADocument() {
        database.inSession(session -> {
            session.persist(aircraft());
            session.persist(finding());
        });
        FindingRow row = onlyOne(FindingRow.class);
        assertThat(row.getMeasurements()).containsEntry("depth", 20).containsEntry("along", 3000);
    }

    @Test
    void aCardIsKeyedOnItsNumberAndItsRevisionTogether() {
        database.inSession(session -> {
            session.persist(aircraft());
            session.persist(finding(1));
            session.persist(finding(2));
        });
        assertThat(every(FindingRow.class)).extracting(FindingRow::getRevision).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void theSameRevisionOfACardCannotGoInTwice() {
        database.inSession(session -> {
            session.persist(aircraft());
            session.persist(finding());
        });
        assertThatThrownBy(() -> database.inSession(session -> session.persist(finding())))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aCardsMovesHangOffItsDisposition() {
        FindingId reference = new FindingId(2025, 4182);
        database.inSession(session -> {
            session.persist(aircraft());
            DispositionRow row = new DispositionRow(reference, "assessed");
            row.setMoves(List.of(move(reference, "raised", "assessed", LocalDate.of(2025, 3, 14))));
            session.persist(row);
        });
        database.inSession(session -> {
            DispositionRow row = session.createQuery("select d from DispositionRow d", DispositionRow.class)
                    .getSingleResult();
            assertThat(row.getMoves()).hasSize(1);
            assertThat(row.getMoves().get(0).getBy()).isEqualTo("structures");
        });
    }

    @Test
    void twoMovesCannotShareASequenceOnOneCard() {
        FindingId reference = new FindingId(2025, 4182);
        database.inSession(session -> {
            session.persist(new DispositionRow(reference, "assessed"));
            session.flush();
            session.persist(move(reference, "raised", "assessed", LocalDate.of(2025, 3, 14)));
        });
        assertThatThrownBy(() -> database.inSession(
                        session -> session.persist(move(reference, "assessed", "deferred", LocalDate.of(2025, 4, 1)))))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void theRowsPrintReadablyForALog() {
        assertThat(aircraft()).hasToString("<AircraftRow MSN 5127 N412VD>");
        assertThat(finding()).hasToString("<FindingRow DR-2025-004182 rev 1>");
    }
}
