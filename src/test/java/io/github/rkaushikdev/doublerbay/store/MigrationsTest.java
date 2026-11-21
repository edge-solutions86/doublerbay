package io.github.rkaushikdev.doublerbay.store;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.doublerbay.support.Databases;
import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/**
 * The migrations and the mapped rows have to describe the same schema.
 *
 * <p>The check that matters is easy to skip: build one database from the migrations and another from
 * the rows themselves, then compare them table by table. Anything that differs is a row change
 * somebody made without writing the migration, and it will not show up until a deployment.
 */
class MigrationsTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");
    private static final Pattern REVISION = Pattern.compile("V([0-9]+)__[a-z0-9_]+\\.sql");
    private static final String HISTORY = "flyway_schema_history";
    private static final String ROWS = Database.class.getPackageName();

    /** A database built by the migrations. */
    private static Database upgraded() {
        return Databases.fresh();
    }

    /** A database built by the persistence unit's own schema export, straight off the mapped rows. */
    private static Database exported() {
        Database made = Databases.empty();
        EntityManagerFactory factory = Database.build(made.dataSource(), ROWS, "create", sql -> {});
        factory.close();
        return made;
    }

    private static List<String> revisionsOnDisk() throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            for (Path file : files.toList()) {
                Matcher name = REVISION.matcher(file.getFileName().toString());
                if (name.matches()) {
                    found.add(name.group(1));
                }
            }
        }
        found.sort((one, other) -> Integer.compare(Integer.parseInt(one), Integer.parseInt(other)));
        return found;
    }

    private static List<String> columnsOf(Map<String, List<String>> table, String name) {
        return table.getOrDefault(name, List.of());
    }

    @Test
    void theMigrationsBuildEveryTableTheModelsDefine() {
        Set<String> built = Shape.of(upgraded().dataSource()).tables();
        Set<String> expected = new TreeSet<>(Shape.of(exported().dataSource()).tables());
        expected.addAll(Database.TABLES);
        expected.add(HISTORY);
        assertThat(expected).hasSize(Database.TABLES.size() + 1);
        assertThat(built).containsAll(expected);
    }

    @Test
    void nothingIsLeftOverOnceTheMigrationsHaveRun() {
        Shape migrated = Shape.of(upgraded().dataSource());
        Shape mapped = Shape.of(exported().dataSource());
        Set<String> tables = new TreeSet<>(migrated.tables());
        tables.remove(HISTORY);
        assertThat(tables).isEqualTo(mapped.tables());
        mapped.comparisons()
                .forEach((what, fromRows) -> assertThat(migrated.comparisons().get(what))
                        .as("the %s the migrations build against the ones the rows describe", what)
                        .isEqualTo(fromRows));
    }

    @Test
    void theComparisonSeesEveryKindOfDifference() throws SQLException {
        Shape mapped = Shape.of(exported().dataSource());
        Database planted = upgraded();
        Shape before = Shape.of(planted.dataSource());
        mapped.comparisons().forEach((what, read) -> {
            assertThat(read).as("the %s read off the rows", what).isNotEmpty();
            assertThat(before.comparisons().get(what))
                    .as("the %s read off the migrations", what)
                    .isNotEmpty();
        });
        Shape.execute(
                planted.dataSource(),
                "ALTER TABLE repair ADD COLUMN planted INTEGER",
                "ALTER TABLE reading DROP CONSTRAINT pk_reading",
                "ALTER TABLE reading ADD CONSTRAINT pk_reading PRIMARY KEY (taken, serial)",
                "ALTER TABLE engineering_order ADD CONSTRAINT uq_engineering_order_question UNIQUE (question)",
                "ALTER TABLE embodiment DROP CONSTRAINT fk_embodiment_serial_aircraft",
                "CREATE INDEX ix_finding_note ON finding (note)");
        Shape after = Shape.of(planted.dataSource());
        mapped.comparisons().forEach((what, fromRows) -> {
            assertThat(after.comparisons().get(what)).as("the planted %s", what).isNotEqualTo(fromRows);
            assertThat(columnsOf(after.comparisons().get(what), "DISPOSITION"))
                    .as("the %s of a table nothing was planted on", what)
                    .isEqualTo(columnsOf(fromRows, "DISPOSITION"));
        });
        assertThat(columnsOf(after.columns(), "REPAIR")).anyMatch(one -> one.startsWith("PLANTED "));
        assertThat(columnsOf(after.primaryKeys(), "READING")).containsExactly("TAKEN,SERIAL");
        assertThat(columnsOf(mapped.primaryKeys(), "READING")).containsExactly("SERIAL,TAKEN");
    }

    @Test
    void theSchemaComesApartAgain() {
        Database made = upgraded();
        assertThat(Shape.of(made.dataSource()).tables()).containsAll(Database.TABLES);
        made.dropSchema();
        assertThat(Shape.of(made.dataSource()).tables()).isSubsetOf(Set.of(HISTORY));
    }

    @Test
    void theHeadIsTheLatestRevisionWritten() throws IOException {
        Database made = upgraded();
        List<String> written = revisionsOnDisk();
        assertThat(written).isNotEmpty();
        assertThat(made.migrations().info().current().getVersion().getVersion())
                .isEqualTo(written.get(written.size() - 1));
    }

    @Test
    void everyRevisionFileIsNumberedInSequence() throws IOException {
        List<String> every = revisionsOnDisk();
        assertThat(every).isNotEmpty();
        assertThat(every)
                .isEqualTo(IntStream.rangeClosed(1, every.size())
                        .mapToObj(String::valueOf)
                        .toList());
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            assertThat(files.map(one -> one.getFileName().toString()))
                    .allMatch(one -> REVISION.matcher(one).matches());
        }
    }

    @Test
    void theConditionsColumnArrivesWithItsOwnRevision() {
        Database made = Databases.empty();
        Flyway.configure()
                .dataSource(made.dataSource())
                .locations("classpath:db/migration")
                .target("1")
                .load()
                .migrate();
        assertThat(columnsOf(Shape.of(made.dataSource()).columns(), "ENGINEERING_ORDER"))
                .noneMatch(one -> one.startsWith("CONDITIONS "))
                .isNotEmpty();
        made.buildSchema();
        assertThat(columnsOf(Shape.of(made.dataSource()).columns(), "ENGINEERING_ORDER"))
                .anyMatch(one -> one.startsWith("CONDITIONS "));
    }

    @Test
    void everyConstraintCarriesANameFromTheConvention() {
        Shape migrated = Shape.of(upgraded().dataSource());
        List<String> foreign = migrated.comparisons().get("foreign keys").values().stream()
                .flatMap(List::stream)
                .toList();
        List<String> indexes = migrated.comparisons().get("indexes").values().stream()
                .flatMap(List::stream)
                .toList();
        assertThat(foreign).isNotEmpty().allMatch(one -> one.startsWith("FK_"));
        assertThat(indexes).isNotEmpty().allMatch(one -> one.startsWith("IX_"));
        assertThat(migrated.constraintNames()).isNotEmpty().noneMatch(one -> one.startsWith("CONSTRAINT_"));
    }

    @Test
    void theReportIndexesAreAllBuilt() {
        Set<String> built = new TreeSet<>();
        Shape.of(upgraded().dataSource())
                .indexes()
                .values()
                .forEach(table -> table.forEach(
                        one -> built.add(one.substring(0, one.indexOf(' ')).toLowerCase(Locale.ROOT))));
        assertThat(built).contains("ix_repair_fitted", "ix_deferral_live", "ix_corrosion_here_before");
    }

    @Test
    void anIndexCoversTheColumnsItsQueryFiltersOn() {
        assertThat(columnsOf(Shape.of(upgraded().dataSource()).indexes(), "REPAIR"))
                .contains("IX_REPAIR_FITTED INDEX(SERIAL,PART,REMOVED)");
    }

    @Test
    void theCorrosionIndexIsOrderedForTheHistoryCheck() {
        assertThat(columnsOf(Shape.of(upgraded().dataSource()).indexes(), "CORROSION_ENTRY"))
                .contains("IX_CORROSION_HERE_BEFORE INDEX(SERIAL,PART,FOUND_ON)");
    }

    /**
     * A schema as the database itself reports it, read table by table from its information schema:
     * every column with its type, length and nullability, the primary key's columns in order, the
     * unique constraints, the foreign keys with what they point at, and the indexes somebody asked
     * for, each by name with its columns in order. Indexes the database made for itself to back a
     * constraint are left out, because their names are its own.
     */
    private record Shape(
            Set<String> tables,
            Set<String> constraintNames,
            Map<String, List<String>> columns,
            Map<String, List<String>> primaryKeys,
            Map<String, List<String>> uniques,
            Map<String, List<String>> foreignKeys,
            Map<String, List<String>> indexes) {

        static Shape of(DataSource source) {
            try (Connection connection = source.getConnection();
                    Statement statement = connection.createStatement()) {
                Set<String> tables = new TreeSet<>();
                for (List<String> row : rows(
                        statement,
                        "SELECT table_name FROM information_schema.tables"
                                + " WHERE table_schema = 'PUBLIC' AND table_type = 'BASE TABLE'")) {
                    tables.add(row.get(0));
                }
                Map<String, List<String>> columns = new TreeMap<>();
                for (List<String> row : rows(
                        statement,
                        "SELECT table_name, column_name, data_type, character_maximum_length, numeric_precision,"
                                + " numeric_scale, datetime_precision, is_nullable, is_identity"
                                + " FROM information_schema.columns WHERE table_schema = 'PUBLIC'")) {
                    add(
                            columns,
                            row.get(0),
                            row.get(1) + " " + row.get(2) + "(" + row.get(3) + "," + row.get(4)
                                    + "," + row.get(5) + "," + row.get(6) + ") nullable " + row.get(7) + " identity "
                                    + row.get(8));
                }
                Map<String, String> tableOf = new LinkedHashMap<>();
                Map<String, String> kindOf = new LinkedHashMap<>();
                for (List<String> row : rows(
                        statement,
                        "SELECT constraint_name, table_name, constraint_type FROM information_schema.table_constraints"
                                + " WHERE table_schema = 'PUBLIC'")) {
                    tableOf.put(row.get(0), row.get(1));
                    kindOf.put(row.get(0), row.get(2));
                }
                Map<String, List<String>> keyed = new LinkedHashMap<>();
                for (List<String> row : rows(
                        statement,
                        "SELECT constraint_name, column_name FROM information_schema.key_column_usage"
                                + " WHERE table_schema = 'PUBLIC' ORDER BY constraint_name, ordinal_position")) {
                    keyed.computeIfAbsent(row.get(0), name -> new ArrayList<>()).add(row.get(1));
                }
                Map<String, List<String>> primaryKeys = new TreeMap<>();
                Map<String, List<String>> uniques = new TreeMap<>();
                kindOf.forEach((name, kind) -> {
                    String over = String.join(",", keyed.getOrDefault(name, List.of()));
                    if (kind.equals("PRIMARY KEY")) {
                        add(primaryKeys, tableOf.get(name), over);
                    } else if (kind.equals("UNIQUE")) {
                        add(uniques, tableOf.get(name), name + "(" + over + ")");
                    }
                });
                Map<String, List<String>> foreignKeys = new TreeMap<>();
                for (List<String> row : rows(
                        statement,
                        "SELECT constraint_name, unique_constraint_name, update_rule, delete_rule"
                                + " FROM information_schema.referential_constraints"
                                + " WHERE constraint_schema = 'PUBLIC'")) {
                    String name = row.get(0);
                    String target = row.get(1);
                    add(
                            foreignKeys,
                            tableOf.get(name),
                            name + "(" + String.join(",", keyed.get(name)) + ") -> "
                                    + tableOf.get(target) + "(" + String.join(",", keyed.get(target)) + ") on update "
                                    + row.get(2) + " on delete " + row.get(3));
                }
                Map<String, List<String>> held = new LinkedHashMap<>();
                Map<String, String> indexed = new LinkedHashMap<>();
                for (List<String> row : rows(
                        statement,
                        "SELECT i.table_name, i.index_name, i.index_type_name, c.column_name"
                                + " FROM information_schema.indexes i JOIN information_schema.index_columns c"
                                + " ON c.index_schema = i.index_schema AND c.index_name = i.index_name"
                                + " WHERE i.table_schema = 'PUBLIC' AND i.is_generated = FALSE"
                                + " ORDER BY i.table_name, i.index_name, c.ordinal_position")) {
                    String key = row.get(1) + " " + row.get(2);
                    indexed.put(key, row.get(0));
                    held.computeIfAbsent(key, name -> new ArrayList<>()).add(row.get(3));
                }
                Map<String, List<String>> indexes = new TreeMap<>();
                held.forEach((key, over) -> add(indexes, indexed.get(key), key + "(" + String.join(",", over) + ")"));
                return new Shape(
                        lower(tables),
                        new TreeSet<>(kindOf.keySet()),
                        columns,
                        primaryKeys,
                        uniques,
                        foreignKeys,
                        indexes);
            } catch (SQLException unreadable) {
                throw new IllegalStateException("the schema could not be read: " + unreadable.getMessage(), unreadable);
            }
        }

        /** Every comparison, by what it compares. History bookkeeping is no part of either schema. */
        Map<String, Map<String, List<String>>> comparisons() {
            Map<String, Map<String, List<String>>> every = new LinkedHashMap<>();
            every.put("columns", withoutHistory(columns));
            every.put("primary keys", withoutHistory(primaryKeys));
            every.put("unique constraints", withoutHistory(uniques));
            every.put("foreign keys", withoutHistory(foreignKeys));
            every.put("indexes", withoutHistory(indexes));
            return every;
        }

        /** Run statements against a database, for planting differences in one. */
        static void execute(DataSource source, String... statements) throws SQLException {
            try (Connection connection = source.getConnection();
                    Statement statement = connection.createStatement()) {
                for (String one : statements) {
                    statement.execute(one);
                }
            }
        }

        private static Map<String, List<String>> withoutHistory(Map<String, List<String>> table) {
            Map<String, List<String>> kept = new TreeMap<>(table);
            kept.keySet().removeIf(name -> name.equalsIgnoreCase(HISTORY));
            return kept;
        }

        private static void add(Map<String, List<String>> table, String name, String entry) {
            List<String> held = table.computeIfAbsent(name, key -> new ArrayList<>());
            held.add(entry);
            held.sort(null);
        }

        private static Set<String> lower(Set<String> names) {
            Set<String> lowered = new TreeSet<>();
            names.forEach(one -> lowered.add(one.toLowerCase(Locale.ROOT)));
            return lowered;
        }

        private static List<List<String>> rows(Statement statement, String query) throws SQLException {
            List<List<String>> found = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery(query)) {
                int width = rows.getMetaData().getColumnCount();
                while (rows.next()) {
                    List<String> row = new ArrayList<>();
                    for (int column = 1; column <= width; column++) {
                        row.add(rows.getString(column));
                    }
                    found.add(row);
                }
            }
            return found;
        }
    }
}
