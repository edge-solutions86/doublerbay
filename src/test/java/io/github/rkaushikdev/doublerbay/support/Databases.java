package io.github.rkaushikdev.doublerbay.support;

import io.github.rkaushikdev.doublerbay.store.Database;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Fresh databases for tests, each one thrown away when the test that opened it finishes.
 *
 * <p>Registered for every test class through the extension service file, so a test asks for a
 * database the way it would ask for a temporary directory and never has to close it.
 */
public final class Databases implements AfterEachCallback {

    private static final List<Database> OPENED = new ArrayList<>();

    /** An empty in-memory database with no tables on it. */
    public static Database empty() {
        return kept(Database.inMemory());
    }

    /** An empty in-memory database with the schema built on it. */
    public static Database fresh() {
        Database made = empty();
        made.buildSchema();
        return made;
    }

    /** Close a database when the current test finishes. */
    public static synchronized Database kept(Database database) {
        OPENED.add(database);
        return database;
    }

    @Override
    public void afterEach(ExtensionContext context) {
        List<Database> closing;
        synchronized (Databases.class) {
            closing = new ArrayList<>(OPENED);
            OPENED.clear();
        }
        for (Database database : closing) {
            database.close();
        }
    }
}
