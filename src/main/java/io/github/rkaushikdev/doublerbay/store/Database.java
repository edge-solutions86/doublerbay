package io.github.rkaushikdev.doublerbay.store;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.rkaushikdev.doublerbay.OutOfSequence;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

/**
 * One database: its connections, its schema migrations, and the persistence unit over its tables.
 *
 * <p>Opening one is an explicit call, so loading this package opens nothing and a unit test of the
 * rules never touches a file. The schema is built by the migrations under {@code db/migration} and
 * only by them; the persistence unit checks the tables against the mapped rows the first time it is
 * used, and refuses to start against a schema that does not match.
 *
 * <p>An in-memory database lives as long as this object, and every one opened is a separate, empty
 * database, so two of them in one process never see each other's rows.
 */
public final class Database implements AutoCloseable {

    /** The url that asks for a fresh in-memory database. */
    public static final String IN_MEMORY = "jdbc:h2:mem:";

    /** Every table the schema defines, in the order the migrations create them. */
    public static final List<String> TABLES = List.of(
            "aircraft",
            "deferral",
            "disposition",
            "engineering_order",
            "corrosion_entry",
            "disposition_move",
            "embodiment",
            "finding",
            "reading",
            "repair",
            "inspection");

    private static final AtomicLong OPENED = new AtomicLong();
    private static final String ROWS = Database.class.getPackageName();

    private final String url;
    private final boolean inMemory;
    private final HikariDataSource connections;
    private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
    private EntityManagerFactory factory;
    private boolean closed;

    private Database(String url, boolean inMemory) {
        this.url = url;
        this.inMemory = inMemory;
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(5_000);
        config.setPoolName("doublerbay-" + OPENED.incrementAndGet());
        this.connections = new HikariDataSource(config);
    }

    /**
     * Open the database at a JDBC url. {@link #IN_MEMORY} opens a fresh in-memory database that
     * lives until this is closed.
     */
    public static Database open(String url) {
        Objects.requireNonNull(url, "url");
        if (url.equals(IN_MEMORY)) {
            return inMemory();
        }
        return new Database(url, url.startsWith(IN_MEMORY));
    }

    /** A fresh, empty in-memory database of its own. */
    public static Database inMemory() {
        String name = "doublerbay-" + OPENED.incrementAndGet();
        return new Database(IN_MEMORY + name + ";DB_CLOSE_DELAY=-1", true);
    }

    /** A fresh in-memory database with the schema built on it. */
    public static Database withSchema() {
        Database made = inMemory();
        made.buildSchema();
        return made;
    }

    /** The url the database was opened at. */
    public String url() {
        return url;
    }

    /** Whether the database lives only as long as this process. */
    public boolean isInMemory() {
        return inMemory;
    }

    /** The connections, for whatever needs to talk to the database below the mapped rows. */
    public DataSource dataSource() {
        return connections;
    }

    /** Build every table, by running the migrations not yet applied. Running it twice does nothing. */
    public void buildSchema() {
        migrations().migrate();
    }

    /** Take every table off again, back to an empty database. */
    public void dropSchema() {
        closeFactory();
        migrations().clean();
    }

    /** The migrations, run against this database. */
    public Flyway migrations() {
        return Flyway.configure()
                .dataSource(connections)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
    }

    /** Every table the schema defines, in the order the migrations create them. */
    public static List<String> tableNames() {
        return TABLES;
    }

    /**
     * Run one piece of work in its own session, committed when the work returns and rolled back
     * if it throws.
     */
    public <T> T fromSession(Function<EntityManager, T> work) {
        EntityManager session = factory().createEntityManager();
        EntityTransaction transaction = session.getTransaction();
        try {
            transaction.begin();
            T answer = work.apply(session);
            transaction.commit();
            return answer;
        } finally {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            session.close();
        }
    }

    /** The same, for work that answers nothing. */
    public void inSession(Consumer<EntityManager> work) {
        fromSession(session -> {
            work.accept(session);
            return null;
        });
    }

    /**
     * Every statement the database is sent while some work runs, which is how a report's query count
     * is asserted rather than hoped for. Statements sent after the work returns are not counted.
     */
    public List<String> statementsDuring(Runnable work) {
        List<String> heard = new CopyOnWriteArrayList<>();
        Consumer<String> listener = heard::add;
        listeners.add(listener);
        try {
            work.run();
        } finally {
            listeners.remove(listener);
        }
        return List.copyOf(heard);
    }

    /** The persistence unit over this database's tables, built and checked on first use. */
    public synchronized EntityManagerFactory factory() {
        if (closed) {
            throw new OutOfSequence("this database has been closed");
        }
        if (factory == null) {
            factory = build(connections, ROWS, "validate", this::heard);
        }
        return factory;
    }

    /**
     * A persistence unit over the rows in some packages, managing the schema the way it is told
     * ({@code validate}, {@code create}, {@code none}). Tests use it for rows of their own.
     */
    public static EntityManagerFactory build(
            DataSource source, String packages, String schema, Consumer<String> statements) {
        LocalContainerEntityManagerFactoryBean bean = new LocalContainerEntityManagerFactoryBean();
        bean.setDataSource(source);
        bean.setPackagesToScan(packages);
        bean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        bean.setPersistenceUnitName("doublerbay");
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", schema);
        properties.put("hibernate.column_ordering_strategy", "legacy");
        properties.put("hibernate.session_factory.statement_inspector", (StatementInspector) sql -> {
            statements.accept(sql);
            return sql;
        });
        bean.setJpaPropertyMap(properties);
        bean.afterPropertiesSet();
        return bean.getObject();
    }

    private void heard(String statement) {
        for (Consumer<String> listener : listeners) {
            listener.accept(statement);
        }
    }

    private synchronized void closeFactory() {
        if (factory != null) {
            factory.close();
            factory = null;
        }
    }

    /** Close the persistence unit and the connections, and throw an in-memory database away. */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closeFactory();
        connections.close();
        if (inMemory) {
            try (Connection connection = DriverManager.getConnection(url, "sa", "");
                    Statement statement = connection.createStatement()) {
                statement.execute("SHUTDOWN");
            } catch (SQLException refused) {
                throw new OutOfSequence("the in-memory database could not be shut down: " + refused.getMessage());
            }
        }
        closed = true;
    }

    @Override
    public String toString() {
        return "database at " + url;
    }
}
