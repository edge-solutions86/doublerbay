package io.github.rkaushikdev.doublerbay.cli;

import io.github.rkaushikdev.doublerbay.api.Contract;
import io.github.rkaushikdev.doublerbay.api.Deps;
import io.github.rkaushikdev.doublerbay.api.DoublerbayApi;
import io.github.rkaushikdev.doublerbay.api.Endpoint;
import io.github.rkaushikdev.doublerbay.api.KeyFile;
import io.github.rkaushikdev.doublerbay.api.KeyRing;
import io.github.rkaushikdev.doublerbay.api.Role;
import io.github.rkaushikdev.doublerbay.config.Config;
import io.github.rkaushikdev.doublerbay.config.Settings;
import io.github.rkaushikdev.doublerbay.fleet.TypeCatalogue;
import io.github.rkaushikdev.doublerbay.manual.Book;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.tooling.Gate;
import io.github.rkaushikdev.doublerbay.tooling.GateResult;
import io.github.rkaushikdev.doublerbay.tooling.Gates;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Starting the service, and saying what it would start with before it does.
 *
 * <p>The settings command exists because the failure everybody hits is the same one: the service
 * starts on a machine where one environment variable is spelled differently, and the first sign of
 * it is a request failing at three in the morning. So there is a command that reads the
 * configuration, builds the key ring, says what it found, and exits without listening on anything.
 *
 * <p>It also says which roles the ring can act as. A ring with no chief engineer on it is a service
 * that cannot grant a deferral, and that is worth knowing on the day it is deployed rather than the
 * day an aeroplane is on the ground.
 *
 * <p>Only {@code serve} starts a web server for anybody to use. The route and contract commands build
 * the application over an in-memory database, read it, and close it again.
 */
public final class Serving {

    /** Where the route and contract commands build the application to read it. */
    private static final String LOOPBACK = "127.0.0.1";

    /** The web server's own lines, which say it started and stopped and nothing a reader of the routes needs. */
    private static final String SERVER_LOGS = "org.apache";

    private Serving() {}

    /**
     * The application the way a real start up builds it: the database the settings name, the V320
     * book, and the key ring off the key file. The database is closed when the application is.
     *
     * @throws io.github.rkaushikdev.doublerbay.NotOnFile when the key file is not there
     */
    public static SpringApplicationBuilder application(Settings settings) {
        KeyRing keys = KeyFile.ringFromFile(settings.keys());
        Database database = Database.open(settings.database());
        Deps deps = new Deps(database, Book.v320(), new TypeCatalogue(), settings.today(), Optional.of(keys));
        ApplicationListener<ContextClosedEvent> closing = event -> database.close();
        return DoublerbayApi.application(deps).listeners(closing);
    }

    /** What the service was told, laid out for a start up check. */
    public static List<List<String>> settingsRows(Settings settings) {
        KeyRing ring = KeyFile.ringFromFile(settings.keys());
        List<Role> missing = KeyFile.missingRoles(ring);
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("database", settings.database()));
        rows.add(List.of("stored", settings.isInMemory() ? "in memory" : "on disk"));
        rows.add(List.of("keys", settings.keys().toString()));
        rows.add(List.of("keys loaded", String.valueOf(ring.size())));
        rows.add(List.of("roles", roles(KeyFile.rolesOn(ring))));
        rows.add(List.of("roles missing", missing.isEmpty() ? "none" : roles(missing)));
        rows.add(List.of("listen", settings.host() + ":" + settings.port()));
        rows.add(List.of("recent window", settings.recentDays() + " days"));
        rows.add(List.of("day", settings.today().map(Object::toString).orElse("the real one")));
        rows.add(List.of("tables", String.valueOf(Database.tableNames().size())));
        return rows;
    }

    private static String roles(List<Role> held) {
        return held.stream().map(Role::toString).collect(Collectors.joining(", "));
    }

    /**
     * Build the application over a fresh in-memory database on the development ring, read something
     * off it, and close it again. It listens only for as long as the reading takes, on a free port of
     * the loopback interface, so nothing off this machine can reach it.
     */
    static <T> T readOffTheApplication(Function<ConfigurableApplicationContext, T> reading) {
        Logger server = Logger.getLogger(SERVER_LOGS);
        Level was = server.getLevel();
        server.setLevel(Level.WARNING);
        try (Database database = Database.inMemory();
                ConfigurableApplicationContext built = DoublerbayApi.buildApp(
                        new Deps(database, Book.v320(), new TypeCatalogue()),
                        "--server.address=" + LOOPBACK,
                        "--server.port=0")) {
            return reading.apply(built);
        } finally {
            server.setLevel(was);
        }
    }

    /** Read the configuration, build the key ring, and print what was found. */
    @Command(
            name = "settings",
            customSynopsis = "doublerbay settings [OPTIONS]",
            description = "Read the configuration, build the key ring, and print what was found.")
    static final class ShowSettings extends Step {

        @Override
        public Integer call() {
            Settings settings = Config.fromEnvironment(main().terminal().environment());
            say(Output.columns(settingsRows(settings)));
            List<Role> missing = KeyFile.missingRoles(KeyFile.ringFromFile(settings.keys()));
            if (!missing.isEmpty()) {
                say("");
                say("nothing on this ring can act as: " + roles(missing));
            }
            return Main.OK;
        }
    }

    /** Every environment variable this service reads. */
    @Command(
            name = "variables",
            customSynopsis = "doublerbay variables [OPTIONS]",
            description = "Every environment variable this service reads.")
    static final class ShowVariables extends Step {

        @Override
        public Integer call() {
            say(String.join("\n", Config.described()));
            return Main.OK;
        }
    }

    /** Start the HTTP service against the configured database, and serve until it is stopped. */
    @Command(
            name = "serve",
            customSynopsis = "doublerbay serve [OPTIONS]",
            description = "Start the http service against the configured database.")
    static final class Serve extends Step {

        @Option(names = "--host", description = "override the configured host")
        String host;

        @Option(names = "--port", description = "override the configured port")
        Integer port;

        @Override
        public Integer call() {
            Settings settings = Config.fromEnvironment(main().terminal().environment());
            say(settings.toString());
            String address = given(host) ? host : settings.host();
            int listen = port != null && port != 0 ? port : settings.port();
            CountDownLatch closed = new CountDownLatch(1);
            ApplicationListener<ContextClosedEvent> stopping = event -> closed.countDown();
            try (ConfigurableApplicationContext service = application(settings)
                    .listeners(stopping)
                    .run("--server.address=" + address, "--server.port=" + listen)) {
                int listening =
                        ((WebServerApplicationContext) service).getWebServer().getPort();
                say("serving on http://" + address + ":" + listening);
                main().terminal().out().flush();
                if (service.isActive()) {
                    closed.await();
                }
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
            }
            return Main.OK;
        }
    }

    /** Every route this service serves, and whether anything guards it. */
    @Command(
            name = "routes",
            customSynopsis = "doublerbay routes [OPTIONS]",
            description = "Every route this service serves, and whether anything guards it.")
    static final class ShowRoutes extends Step {

        @Option(names = "--writes-only", description = "only the routes that change something")
        boolean writesOnly;

        private record Surface(List<Endpoint> found, List<Endpoint> openWrites) {}

        @Override
        public Integer call() {
            Surface surface = readOffTheApplication(built -> new Surface(
                    writesOnly ? Contract.writes(built) : Contract.endpoints(built), Contract.unguardedWrites(built)));
            List<List<String>> rows = new ArrayList<>();
            rows.add(List.of("method", "path", "guard", "summary"));
            for (Endpoint one : surface.found()) {
                rows.add(List.of(one.method(), one.path(), one.guarded() ? "guarded" : "open", one.summary()));
            }
            say(Output.columns(rows));
            if (!surface.openWrites().isEmpty()) {
                say("");
                for (Endpoint one : surface.openWrites()) {
                    say("nothing guards " + one);
                }
                // Not nought on purpose. The workflow runs this command, so a route added without a
                // key check fails the build rather than shipping.
                return Main.REFUSED;
            }
            return Main.OK;
        }
    }

    /** Print the OpenAPI contract, or write it to a file to be checked in. */
    @Command(
            name = "contract",
            customSynopsis = "doublerbay contract [OPTIONS]",
            description = "Print the openapi contract, or write it to a file to be checked in.")
    static final class ShowContract extends Step {

        @Option(names = "--to")
        Path path;

        @Override
        public Integer call() {
            String document = readOffTheApplication(Contract::asDocument);
            if (path == null) {
                say(document);
                return Main.OK;
            }
            Texts.write(path, document);
            say("wrote " + Text.lines(document).size() + " lines to " + path);
            return Main.OK;
        }
    }

    /** The checks this repository runs on itself, listed or run. */
    @Command(
            name = "gates",
            customSynopsis = "doublerbay gates [OPTIONS]",
            description = "The checks this repository runs on itself, listed or run.")
    static final class ShowGates extends Step {

        @Option(names = "--run", description = "run them rather than list them")
        boolean andRun;

        @Override
        public Integer call() {
            if (!andRun) {
                List<List<String>> rows = new ArrayList<>();
                for (Gate one : Gates.GATES) {
                    rows.add(List.of(one.name(), one.written(), one.why()));
                }
                say(Output.columns(rows));
                return Main.OK;
            }
            main().terminal().out().flush();
            List<GateResult> results = Gates.runAll();
            List<List<String>> rows = new ArrayList<>();
            for (GateResult one : results) {
                rows.add(List.of(one.gate().name(), one.failed() ? "failed" : "ok"));
            }
            say(Output.columns(rows));
            List<Gate> stopped = Gates.failed(results);
            if (!stopped.isEmpty()) {
                say("");
                say(stopped.size() + " gates failed: "
                        + stopped.stream().map(Gate::name).collect(Collectors.joining(", ")));
                return Main.REFUSED;
            }
            return Main.OK;
        }
    }
}
