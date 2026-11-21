package io.github.rkaushikdev.doublerbay.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.OutOfSequence;
import io.github.rkaushikdev.doublerbay.api.Deps;
import io.github.rkaushikdev.doublerbay.config.Config;
import io.github.rkaushikdev.doublerbay.config.Settings;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.support.Api;
import io.github.rkaushikdev.doublerbay.support.Cli;
import io.github.rkaushikdev.doublerbay.support.Cli.Ran;
import io.github.rkaushikdev.doublerbay.support.Client;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.ApplicationContextFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.GenericWebApplicationContext;

class CliServingTest {

    private static final String IN_MEMORY = Database.IN_MEMORY;

    private static final List<Map<String, String>> FOUR = List.of(
            Map.of("key", "reader-key-9001", "holder", "the front desk", "role", "reader"),
            Map.of("key", "mechanic-key-9002", "holder", "s.brandt", "role", "mechanic"),
            Map.of("key", "engineer-key-9003", "holder", "a.okonkwo", "role", "engineer"),
            Map.of("key", "chief-key-9004", "holder", "j.mercer", "role", "chief engineer"));

    private static final Map<String, Object> TAIL = Api.json(
            "serial", "5127",
            "registration", "N412VD",
            "series", "200",
            "line_number", 1140,
            "delivered", "2016-04-12");

    @TempDir
    Path tmp;

    private final List<ConfigurableApplicationContext> started = new ArrayList<>();

    @AfterEach
    void closeWhatWasStarted() {
        started.forEach(ConfigurableApplicationContext::close);
    }

    private Path keyFile() {
        return keyFile(FOUR, "keys.json");
    }

    private Path keyFile(List<Map<String, String>> entries, String name) {
        return Cli.written(tmp, name, entries);
    }

    private Settings settings() {
        return new Settings(IN_MEMORY, keyFile());
    }

    private Settings settings(Path keys) {
        return new Settings(IN_MEMORY, keys);
    }

    private Settings settings(LocalDate today) {
        return new Settings(
                IN_MEMORY,
                keyFile(),
                Config.DEFAULT_HOST,
                Config.DEFAULT_PORT,
                Config.DEFAULT_RECENT_DAYS,
                Optional.of(today));
    }

    private Map<String, String> env() {
        return env(Map.of());
    }

    private Map<String, String> env(Map<String, String> extra) {
        Map<String, String> held = new HashMap<>();
        held.put("DOUBLERBAY_DATABASE", IN_MEMORY);
        held.put("DOUBLERBAY_KEYS", keyFile().toString());
        held.putAll(extra);
        return held;
    }

    private Ran run(String command) {
        return Cli.invoke(env(), command);
    }

    private Ran run(String command, Map<String, String> extra) {
        return Cli.invoke(env(extra), command);
    }

    /** The application started over a servlet context that lives in memory rather than a port. */
    private ConfigurableApplicationContext started(SpringApplicationBuilder built) {
        ConfigurableApplicationContext running = built.contextFactory(
                        ApplicationContextFactory.of(() -> new GenericWebApplicationContext(new MockServletContext())))
                .run();
        started.add(running);
        return running;
    }

    /**
     * A built application with its schema in place, the way a deployment runs.
     *
     * <p>The application does not build its own schema. Migrations do that, and a service that quietly
     * created tables at start up would hide a database that was never migrated.
     */
    private ConfigurableApplicationContext stocked() {
        ConfigurableApplicationContext built = started(Serving.application(settings()));
        built.getBean(Deps.class).database().buildSchema();
        return built;
    }

    private static Map<String, String> rowsOf(List<List<String>> rows) {
        Map<String, String> named = new LinkedHashMap<>();
        rows.forEach(row -> named.put(row.get(0), row.get(1)));
        return named;
    }

    @Test
    void theApplicationBuildsFromSettings() {
        Client api = Client.over(started(Serving.application(settings())));
        Client.Answer answer = api.get("/health");
        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.json().get("service").asText()).isEqualTo("doublerbay");
    }

    @Test
    void theBuiltApplicationUsesTheLoadedKeys() {
        Client api = Client.over(stocked());
        api.post("/aircraft", TAIL, Map.of("x-api-key", "chief-key-9004"));
        assertThat(api.get("/aircraft/5127").status()).isEqualTo(200);
    }

    @Test
    void aKeyFromTheDevelopmentRingNoLongerWorks() {
        Client api = Client.over(stocked());
        Client.Answer answer = api.post("/aircraft", TAIL, Map.of("x-api-key", "chief-key-0001"));
        assertThat(answer.status()).isEqualTo(404);
    }

    @Test
    void aPinnedDayReachesTheApplication() {
        ConfigurableApplicationContext built = started(Serving.application(settings(LocalDate.of(2025, 3, 14))));
        assertThat(built.getBean(Deps.class).on()).isEqualTo(LocalDate.of(2025, 3, 14));
    }

    @Test
    void aKeyFileThatIsNotThereStopsTheBuild() {
        assertThatThrownBy(() -> Serving.application(settings(tmp.resolve("missing.json"))))
                .isInstanceOf(NotOnFile.class);
    }

    @Test
    void theSettingsRowsNameWhatWasFound() {
        Map<String, String> rows = rowsOf(Serving.settingsRows(settings()));
        assertThat(rows.get("keys loaded")).isEqualTo("4");
        assertThat(rows.get("roles missing")).isEqualTo("none");
        assertThat(rows.get("stored")).isEqualTo("in memory");
    }

    @Test
    void theSettingsRowsNameAMissingRole() {
        Map<String, String> rows = rowsOf(Serving.settingsRows(settings(keyFile(FOUR.subList(0, 3), "three.json"))));
        assertThat(rows.get("roles missing")).contains("chief engineer");
    }

    @Test
    void theSettingsRowsCountTheTables() {
        Map<String, String> rows = rowsOf(Serving.settingsRows(settings()));
        assertThat(Integer.parseInt(rows.get("tables"))).isGreaterThan(5);
    }

    @Test
    void theSettingsCommandPrintsWhatItFound() {
        Ran answer = run("settings");
        assertThat(answer.exitCode()).as(answer.output()).isZero();
        assertThat(answer.output()).contains("keys loaded");
        assertThat(answer.output()).contains("in memory");
    }

    @Test
    void theSettingsCommandWarnsAboutAMissingRole() {
        Path path = keyFile(FOUR.subList(0, 3), "three.json");
        Ran answer = run("settings", Map.of("DOUBLERBAY_KEYS", path.toString()));
        assertThat(answer.output()).contains("nothing on this ring can act as");
    }

    @Test
    void theSettingsCommandRefusesAMissingDatabase() {
        Ran answer = Cli.invoke(Map.of("DOUBLERBAY_KEYS", keyFile().toString()), "settings");
        assertThat(answer.exitCode()).isNotZero();
        assertThat(answer.exception()).containsInstanceOf(BadReading.class);
    }

    @Test
    void theSettingsCommandTakesAPinnedDay() {
        Ran answer = run("settings", Map.of("DOUBLERBAY_TODAY", "2025-03-14"));
        assertThat(answer.output()).contains("2025-03-14");
    }

    @Test
    void theVariablesCommandListsEverySetting() {
        Ran answer = run("variables");
        assertThat(answer.output()).contains("DOUBLERBAY_DATABASE");
        assertThat(answer.output().strip().split("\n", -1)).hasSize(6);
    }

    @Test
    void theServeCommandIsRegistered() {
        Ran answer = Cli.invoke("--help");
        assertThat(answer.output()).contains("serve");
        assertThat(answer.output()).contains("settings");
    }

    /**
     * A service started against an unmigrated database fails loudly.
     *
     * <p>It does not come back as a tidy not found, which would read as an empty register and have
     * somebody enrolling a fleet that is already on file.
     */
    @Test
    void theApplicationDoesNotBuildItsOwnSchema() {
        Client api = Client.over(started(Serving.application(settings()))).as("reader-key-9001");
        assertThatThrownBy(() -> api.get("/aircraft/5127")).rootCause().hasMessageContaining("missing table");
    }

    @Test
    void theServeCommandListensUntilItIsStopped() throws Exception {
        int port;
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        Terminal terminal = new Terminal(
                env(), new BufferedReader(new StringReader("")), new PrintWriter(out), new PrintWriter(err));
        AtomicInteger code = new AtomicInteger(-1);
        String[] args = {"serve", "--port", String.valueOf(port)};
        Thread serving = new Thread(() -> code.set(Main.run(terminal, args)), "serving");
        serving.start();
        String listening = "serving on http://127.0.0.1:" + port;
        long until = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (!out.toString().contains(listening) && serving.isAlive() && System.nanoTime() < until) {
            Thread.sleep(50);
        }
        try {
            assertThat(out.toString()).startsWith("doublerbay on 127.0.0.1:8000, database in memory\n");
            assertThat(out.toString()).contains(listening);
            HttpResponse<String> health = HttpClient.newHttpClient()
                    .send(
                            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/health"))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString());
            assertThat(health.statusCode()).isEqualTo(200);
            assertThat(health.body()).contains("doublerbay");
        } finally {
            serving.interrupt();
            serving.join(Duration.ofSeconds(60).toMillis());
        }
        assertThat(serving.isAlive()).isFalse();
        assertThat(code.get()).isEqualTo(Main.OK);
        assertThat(err.toString().lines().filter(line -> line.contains("ERROR")).collect(Collectors.toList()))
                .isEmpty();
    }

    @Test
    void theApplicationClosesItsDatabaseWhenItIsClosed() {
        ConfigurableApplicationContext built = started(Serving.application(settings()));
        Database database = built.getBean(Deps.class).database();
        built.close();
        assertThatThrownBy(database::factory).isInstanceOf(OutOfSequence.class).hasMessageContaining("closed");
    }
}
