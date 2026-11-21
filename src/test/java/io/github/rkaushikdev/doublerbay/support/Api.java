package io.github.rkaushikdev.doublerbay.support;

import io.github.rkaushikdev.doublerbay.api.Deps;
import io.github.rkaushikdev.doublerbay.api.DoublerbayApi;
import io.github.rkaushikdev.doublerbay.fleet.TypeCatalogue;
import io.github.rkaushikdev.doublerbay.manual.Book;
import io.github.rkaushikdev.doublerbay.store.Database;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.boot.ApplicationContextFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.GenericWebApplicationContext;

/**
 * The fixtures every route test starts from: the service's dependencies with the pinned day, the
 * application built over them, and a client acting as one role.
 *
 * <p>The application is started through the same factory the command line serves it through, over
 * a servlet context that lives in memory rather than a port, and it is closed when the test that
 * started it finishes, as the database under it is.
 */
public final class Api implements AfterEachCallback {

    private static final List<ConfigurableApplicationContext> STARTED = new ArrayList<>();

    /** The dependencies over one database, answering as of the pinned day. */
    public static Deps depsFor(Database database) {
        return depsFor(database, Desk.TODAY);
    }

    /** The dependencies over one database, answering as of a day, or the real day when none. */
    public static Deps depsFor(Database database, LocalDate todayOrNull) {
        return Deps.of(database, Book.v320(), new TypeCatalogue(), todayOrNull, null);
    }

    /** The application over these dependencies, with any routes the test adds beside its own. */
    public static ConfigurableApplicationContext app(Deps deps, Class<?>... extra) {
        ConfigurableApplicationContext started = DoublerbayApi.application(deps, List.of(extra))
                .contextFactory(
                        ApplicationContextFactory.of(() -> new GenericWebApplicationContext(new MockServletContext())))
                .run();
        synchronized (Api.class) {
            STARTED.add(started);
        }
        return started;
    }

    /** A client acting as the chief engineer, against one database. */
    public static Client clientFor(Database database) {
        return clientFor(database, "chief");
    }

    /** A client acting as one role, against one database. */
    public static Client clientFor(Database database, String role) {
        return clientFor(database, role, Desk.TODAY);
    }

    /** A client acting as one role, against one database, answering as of a day or the real day. */
    public static Client clientFor(Database database, String role, LocalDate todayOrNull) {
        return Client.over(app(depsFor(database, todayOrNull))).as(Desk.KEYS.get(role));
    }

    /** A JSON object for a request body, its members in the order given: name, value, name, value. */
    public static Map<String, Object> json(Object... namesAndValues) {
        if (namesAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("a body is names and values in pairs");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        for (int index = 0; index < namesAndValues.length; index += 2) {
            body.put((String) namesAndValues[index], namesAndValues[index + 1]);
        }
        return body;
    }

    @Override
    public void afterEach(ExtensionContext context) {
        List<ConfigurableApplicationContext> closing;
        synchronized (Api.class) {
            closing = new ArrayList<>(STARTED);
            STARTED.clear();
        }
        for (ConfigurableApplicationContext started : closing) {
            started.close();
        }
    }
}
