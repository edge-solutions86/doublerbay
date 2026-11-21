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
import io.github.rkaushikdev.doublerbay.support.probe.ProbeRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class StoreTypesTest {

    private static final String PROBES = ProbeRow.class.getPackageName();
    private static final Path BASELINE = Path.of("src/main/resources/db/migration/V1__baseline_schema.sql");

    private final List<EntityManagerFactory> built = new ArrayList<>();
    private Database database;

    @AfterEach
    void closeTheProbeUnits() {
        built.forEach(EntityManagerFactory::close);
    }

    /** A database holding nothing but the probe table, and the persistence unit over it. */
    private EntityManagerFactory probe() {
        database = Databases.empty();
        EntityManagerFactory factory = Database.build(database.dataSource(), PROBES, "create", sql -> {});
        built.add(factory);
        return factory;
    }

    private ProbeRow roundTrip(ProbeRow row) {
        EntityManagerFactory factory = probe();
        EntityManager writing = factory.createEntityManager();
        try {
            writing.getTransaction().begin();
            writing.persist(row);
            writing.getTransaction().commit();
        } finally {
            writing.close();
        }
        EntityManager reading = factory.createEntityManager();
        try {
            return reading.find(ProbeRow.class, 1);
        } finally {
            reading.close();
        }
    }

    private List<Object> raw(String query) throws SQLException {
        try (Connection connection = database.dataSource().getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(query)) {
            List<Object> found = new ArrayList<>();
            int width = rows.getMetaData().getColumnCount();
            while (rows.next()) {
                for (int column = 1; column <= width; column++) {
                    found.add(rows.getObject(column));
                }
            }
            return found;
        }
    }

    private long aircraftOnFile() throws SQLException {
        return ((Number) raw("select count(*) from aircraft").get(0)).longValue();
    }

    private static AircraftRow aTail() {
        return new AircraftRow(new Serial("5127"), new Registration("N412VD"), "200", 1140, LocalDate.of(2016, 4, 12));
    }

    @Test
    void aLengthGoesInAsMilsAndComesBackAsALength() throws SQLException {
        ProbeRow row = roundTrip(new ProbeRow(1).setDepth(Length.ofInches("0.040")));
        assertThat(row.getDepth()).isInstanceOf(Length.class).isEqualTo(Length.ofInches("0.040"));
        assertThat(raw("select depth from probe where id = 1")).containsExactly(40);
    }

    @Test
    void aThousandthSurvivesTheRoundTripExactly() {
        ProbeRow row = roundTrip(new ProbeRow(1).setDepth(new Length(37)));
        assertThat(row.getDepth().mils()).isEqualTo(37);
    }

    @Test
    void theCountersGoInAsWholeNumbers() throws SQLException {
        ProbeRow row = roundTrip(new ProbeRow(1).setCycles(new Cycles(13800)).setHours(Hours.ofHours("24200.5")));
        assertThat(row.getCycles()).isEqualTo(new Cycles(13800));
        assertThat(row.getHours()).isEqualTo(Hours.ofHours("24200.5"));
        assertThat(raw("select cycles, hours from probe where id = 1")).containsExactly(13800, 1452030);
    }

    @Test
    void aSerialIsStoredWithoutThePrefixItPrintsWith() throws SQLException {
        ProbeRow row = roundTrip(new ProbeRow(1).setSerial(new Serial("5127")));
        assertThat(raw("select serial from probe")).containsExactly("5127");
        assertThat(row.getSerial()).isEqualTo(new Serial("5127"));
    }

    @Test
    void theIdentifiersGoInAsTheTextPeopleRead() throws SQLException {
        roundTrip(new ProbeRow(1).setFinding(new FindingId(2025, 4182)).setRepair(new RepairId(53, 142)));
        assertThat(raw("select finding, repair from probe where id = 1"))
                .containsExactly("DR-2025-004182", "RP-53-0142");
    }

    @Test
    void everyIdentifierComesBackAsItsOwnType() {
        ProbeRow row = roundTrip(new ProbeRow(1)
                .setSerial(new Serial("5127"))
                .setMarks(new Registration("N412VD"))
                .setFinding(new FindingId(2025, 4182))
                .setRepair(new RepairId(53, 142))
                .setPanel(new PanelId(53, 10, 143, "L")));
        assertThat(row.getSerial()).isInstanceOf(Serial.class);
        assertThat(row.getMarks()).isInstanceOf(Registration.class);
        assertThat(row.getFinding()).isInstanceOf(FindingId.class);
        assertThat(row.getRepair()).isInstanceOf(RepairId.class);
        assertThat(row.getPanel()).isInstanceOf(PanelId.class).isEqualTo(new PanelId(53, 10, 143, "L"));
    }

    @Test
    void nothingStaysNothingThroughEveryColumn() throws SQLException {
        ProbeRow row = roundTrip(new ProbeRow(1).setNote("empty"));
        assertThat(row.getDepth()).isNull();
        assertThat(row.getCycles()).isNull();
        assertThat(row.getHours()).isNull();
        assertThat(row.getSerial()).isNull();
        assertThat(row.getMarks()).isNull();
        assertThat(row.getFinding()).isNull();
        assertThat(row.getRepair()).isNull();
        assertThat(row.getPanel()).isNull();
        assertThat(raw("select depth, cycles, hours, serial, marks, finding, repair, panel from probe"))
                .hasSize(8)
                .containsOnlyNulls();
    }

    @Test
    void everyKindOfConstraintIsNamedByTheConvention() throws SQLException {
        database = Databases.fresh();
        Map<String, String> prefixes = Map.of("PRIMARY KEY", "pk_", "UNIQUE", "uq_", "FOREIGN KEY", "fk_");
        List<Object> found = raw("select constraint_type, constraint_name, table_name"
                + " from information_schema.table_constraints where table_schema = 'PUBLIC'"
                + " and lower(table_name) <> 'flyway_schema_history'");
        Map<String, Integer> kinds = new LinkedHashMap<>();
        for (int at = 0; at < found.size(); at += 3) {
            String kind = (String) found.get(at);
            String name = ((String) found.get(at + 1)).toLowerCase(Locale.ROOT);
            String table = ((String) found.get(at + 2)).toLowerCase(Locale.ROOT);
            assertThat(prefixes).as("a %s on %s", kind, table).containsKey(kind);
            if (kind.equals("PRIMARY KEY")) {
                assertThat(name).isEqualTo("pk_" + table);
            } else {
                assertThat(name).startsWith(prefixes.get(kind) + table + "_");
            }
            kinds.merge(kind, 1, Integer::sum);
        }
        assertThat(kinds).containsOnlyKeys(prefixes.keySet());
        assertThat(kinds.get("PRIMARY KEY")).isEqualTo(Database.TABLES.size());
    }

    @Test
    void theSchemaBuildsAndComesApartAgain() throws SQLException {
        database = Databases.empty();
        database.buildSchema();
        assertThat(aircraftOnFile()).isZero();
        database.dropSchema();
        assertThat(raw("select count(*) from information_schema.tables where table_schema = 'PUBLIC'"))
                .containsExactly(0L);
    }

    @Test
    void aSessionCommitsOnTheWayOut() throws SQLException {
        database = Databases.fresh();
        database.inSession(session -> session.persist(aTail()));
        assertThat(aircraftOnFile()).isOne();
    }

    @Test
    void aSessionRollsBackWhenSomethingThrows() throws SQLException {
        database = Databases.fresh();
        assertThatThrownBy(() -> database.inSession(session -> {
                    session.persist(aTail());
                    session.flush();
                    throw new IllegalStateException("something went wrong");
                }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(aircraftOnFile()).isZero();
    }

    @Test
    void theTableListComesBackInCreationOrder() throws IOException {
        List<String> created = new ArrayList<>();
        Matcher table = Pattern.compile("(?m)^CREATE TABLE ([a-z_]+)").matcher(Files.readString(BASELINE));
        while (table.find()) {
            created.add(table.group(1));
        }
        assertThat(created).isNotEmpty();
        assertThat(Database.tableNames()).isEqualTo(created);
        assertThatThrownBy(() -> Database.tableNames().add("probe")).isInstanceOf(UnsupportedOperationException.class);
    }
}
