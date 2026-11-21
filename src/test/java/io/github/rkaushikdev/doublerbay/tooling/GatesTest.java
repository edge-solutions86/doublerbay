package io.github.rkaushikdev.doublerbay.tooling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.Doublerbay;
import io.github.rkaushikdev.doublerbay.support.Cli;
import io.github.rkaushikdev.doublerbay.support.Cli.Ran;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The gates, and the assertion that the workflow actually runs all of them.
 *
 * <p>This is the class that keeps continuous integration honest. Every other test in the suite says
 * something about an aeroplane. This one says that the checks this repository claims to run are the
 * checks a runner will actually run, which is the claim everything else rests on.
 */
class GatesTest {

    private static final Path ROOT = Path.of("").toAbsolutePath();

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static String workflow() {
        return read(Gates.workflowPath(ROOT));
    }

    /** The command that runs the built jar, the way the workflow runs it. */
    private static String jar() {
        return "java -jar build/libs/doublerbay-" + Doublerbay.VERSION + ".jar";
    }

    @Test
    void theWorkflowIsWhereThisRepositorySaysItIs() {
        assertThat(Gates.workflowPath(ROOT)).exists();
    }

    @Test
    void theWorkflowRunsEveryGateThisRepositoryNames() {
        Set<String> missing = new HashSet<>(Gates.names());
        missing.removeAll(Gates.gatesNamedIn(workflow()));
        assertThat(missing).as("the workflow does not run: %s", missing).isEmpty();
        assertThat(Gates.gatesNamedIn("steps:\n  - name: lint\n  - name: tests\n"))
                .containsExactly("lint", "tests");
    }

    @Test
    void theWorkflowNamesNoGateThisRepositoryDoesNotHave() {
        assertThat(Gates.names()).containsAll(Gates.gatesNamedIn(workflow()));
    }

    @Test
    void theWorkflowBuildsWithTheWrapper() {
        assertThat(workflow()).contains("run: ./gradlew build");
    }

    @Test
    void theWorkflowSetsUpJava21() {
        String text = workflow();
        assertThat(text).contains("uses: actions/checkout@v4");
        assertThat(text).contains("uses: actions/setup-java@v4");
        assertThat(text).contains("distribution: temurin");
        assertThat(text).contains("java-version: '21'");
        assertThat(text).contains("cache: gradle");
    }

    @Test
    void theWorkflowRunsTheJavaTheBuildFileAsksFor() {
        String built = read(ROOT.resolve("build.gradle"));
        assertThat(built).contains("languageVersion = JavaLanguageVersion.of(21)");
        assertThat(workflow()).contains("java-version: '21'");
    }

    @Test
    void theWorkflowChecksTheRouteGuards() {
        assertThat(workflow()).contains("- name: routes\n        run: " + jar() + " routes\n");
    }

    @Test
    void theWorkflowKeepsTheContract() {
        String text = workflow();
        assertThat(text).contains("- name: contract\n        run: " + jar() + " contract --to openapi.json\n");
        assertThat(text).contains("uses: actions/upload-artifact@v4");
        assertThat(text).contains("path: openapi.json");
    }

    @Test
    void theWorkflowRunsTheMigrationsBothWays() {
        String text = workflow();
        assertThat(text).contains("run: " + Gates.named("migrations").written() + "\n");
        assertThat(text).contains("run: " + Gates.named("rollback").written() + "\n");
    }

    @Test
    void everyGateHasACommand() {
        for (Gate gate : Gates.GATES) {
            assertThat(gate.command()).isNotEmpty();
        }
    }

    @Test
    void everyGateSaysWhyItIsWorthTheSeconds() {
        for (Gate gate : Gates.GATES) {
            assertThat(gate.why().length()).isGreaterThan(20);
        }
    }

    @Test
    void theGateNamesAreAllDifferent() {
        assertThat(new HashSet<>(Gates.names())).hasSize(Gates.GATES.size());
    }

    @Test
    void aGateReadsBackAsItsCommand() {
        Gate made = new Gate("lint", List.of("./gradlew", "spotlessCheck"), "x".repeat(30));
        assertThat(made).hasToString("lint: ./gradlew spotlessCheck");
    }

    @Test
    void aGateWithNoNameIsRefused() {
        assertThatThrownBy(() -> new Gate("  ", List.of("./gradlew"), "x".repeat(30)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("has a name");
    }

    @Test
    void aGateWithNoCommandIsRefused() {
        assertThatThrownBy(() -> new Gate("lint", List.of(), "x".repeat(30)))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("no command to run");
    }

    @Test
    void aGateCanBeFoundByName() {
        assertThat(Gates.named("tests").command()).isEqualTo(List.of("./gradlew", "test"));
    }

    @Test
    void aGateThatIsNotOneNamesTheOnesThatAre() {
        assertThatThrownBy(() -> Gates.named("vibes"))
                .isInstanceOf(BadReading.class)
                .hasMessageContaining("lint, format");
    }

    @Test
    void nothingFailedWhenEverythingPassed() {
        List<GateResult> results =
                Gates.GATES.stream().map(one -> new GateResult(one, 0)).toList();
        assertThat(Gates.failed(results)).isEmpty();
    }

    @Test
    void aFailureIsReportedByGate() {
        List<GateResult> results = Gates.GATES.stream()
                .map(one -> new GateResult(one, one.name().equals("types") ? 1 : 0))
                .toList();
        assertThat(Gates.failed(results).stream().map(Gate::name)).containsExactly("types");
    }

    @Test
    void theCommandListsTheGates() {
        Ran answer = Cli.invoke("gates");
        assertThat(answer.exitCode()).as(answer.output()).isZero();
        for (String name : Gates.names()) {
            assertThat(answer.output()).contains(name);
        }
    }

    @Test
    void theCommandPrintsTheCommandEachGateRuns() {
        Ran answer = Cli.invoke("gates");
        assertThat(answer.output()).contains("./gradlew compileJava compileTestJava");
        assertThat(answer.output()).contains("./gradlew test --tests *MigrationsTest");
    }

    @Test
    void theCommandPrintsWhyEachGateExists() {
        Ran answer = Cli.invoke("gates");
        assertThat(answer.output()).contains("deploy time");
    }
}
