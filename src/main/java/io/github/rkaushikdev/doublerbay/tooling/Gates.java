package io.github.rkaushikdev.doublerbay.tooling;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.util.Literal;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The checks that have to pass, written down once and run from two places.
 *
 * <p>A repository with a continuous integration file and a developer's habit is a repository with
 * two definitions of "green". They agree for about a month. Then somebody adds a check to the
 * workflow and nobody's laptop runs it, or somebody adds one to their laptop and the workflow never
 * sees it, and the argument about which one is authoritative happens on the day it matters most.
 *
 * <p>So the gates are data. The workflow runs them by name and so does the command line, and a test
 * asserts the workflow file names every gate this class does. Adding a gate here without adding it
 * to the workflow fails that test, which is the only way this stays true.
 *
 * <p>Nothing here starts a process until somebody asks for a gate to run. The commands are words
 * until then, so loading this class in a test costs nothing.
 */
public final class Gates {

    /** Every gate, in the order they run. */
    public static final List<Gate> GATES = List.of(
            new Gate(
                    "lint",
                    List.of("./gradlew", "compileJava", "compileTestJava"),
                    "the rules the whole repository is written to"),
            new Gate(
                    "format",
                    List.of("./gradlew", "spotlessCheck"),
                    "a formatting argument in a review is an argument nobody needs"),
            new Gate(
                    "types",
                    List.of("./gradlew", "compileJava"),
                    "a measurement passed as the wrong unit is a type error here"),
            new Gate(
                    "tests",
                    List.of("./gradlew", "test"),
                    "the suite, which is the only thing that says the rules are right"),
            new Gate(
                    "migrations",
                    List.of("./gradlew", "test", "--tests", "*MigrationsTest"),
                    "a migration that does not apply is found at deploy time otherwise"),
            new Gate(
                    "rollback",
                    List.of("./gradlew", "test", "--tests", "*MigrationsTest.theSchemaComesApartAgain"),
                    "the schema can be taken back to empty, so a failed deploy starts over from nothing"));

    private Gates() {}

    /** One gate by name, refusing one this repository does not have. */
    public static Gate named(String name) {
        for (Gate gate : GATES) {
            if (gate.name().equals(name)) {
                return gate;
            }
        }
        String every = GATES.stream().map(Gate::name).collect(Collectors.joining(", "));
        throw new BadReading(Literal.of(name) + " is not a gate, it is one of " + every);
    }

    /** Every gate's name, in the order they run. */
    public static List<String> names() {
        return GATES.stream().map(Gate::name).toList();
    }

    /** Run one gate where the process was started and hand back what it exited with. */
    public static int run(Gate gate) {
        return run(gate, null);
    }

    /**
     * Run one gate in a directory and hand back what it exited with. The command's output goes
     * straight to this process's own.
     *
     * @param where the directory to run in, or null for where the process was started
     * @throws UncheckedIOException when the command cannot be started at all
     */
    public static int run(Gate gate, Path where) {
        ProcessBuilder builder = new ProcessBuilder(gate.command()).inheritIO();
        if (where != null) {
            builder.directory(where.toFile());
        }
        Process running;
        try {
            running = builder.start();
        } catch (IOException refused) {
            throw new UncheckedIOException(refused);
        }
        try {
            return running.waitFor();
        } catch (InterruptedException stopped) {
            running.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IllegalStateException(gate.name() + " was interrupted", stopped);
        }
    }

    /** Run every gate in order where the process was started, keeping what each one exited with. */
    public static List<GateResult> runAll() {
        return runAll(null);
    }

    /**
     * Run every gate in order and keep what each one exited with.
     *
     * <p>Every gate runs even after one fails. A run that stopped at the first failure would mean four
     * rounds of push and wait to find four problems.
     */
    public static List<GateResult> runAll(Path where) {
        return GATES.stream()
                .map(gate -> new GateResult(gate, run(gate, where)))
                .toList();
    }

    /** The gates that exited with anything but nought, in the order they ran. */
    public static List<Gate> failed(List<GateResult> results) {
        return results.stream().filter(GateResult::failed).map(GateResult::gate).toList();
    }

    /** Where the workflow lives, so a test can read it rather than guess. */
    public static Path workflowPath(Path root) {
        return root.resolve(".github").resolve("workflows").resolve("ci.yml");
    }

    /** Which gates a workflow file actually names, read off its own text, in gate order. */
    public static Set<String> gatesNamedIn(String text) {
        Set<String> named = new LinkedHashSet<>();
        for (String one : names()) {
            if (text.contains("name: " + one)) {
                named.add(one);
            }
        }
        return Collections.unmodifiableSet(named);
    }
}
