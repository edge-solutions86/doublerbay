package io.github.rkaushikdev.doublerbay.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rkaushikdev.doublerbay.cli.Main;
import io.github.rkaushikdev.doublerbay.cli.Terminal;
import io.github.rkaushikdev.doublerbay.store.Database;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Runs the command line the way an operator would, with what it printed and what it exited with
 * kept for the test to read.
 *
 * <p>A command is run in a terminal of its own: the environment the test gives it, nothing to read
 * from the keyboard, and two streams kept as text. A refusal, or anything else a command throws, is
 * caught and kept as the run's exception with an exit code of one, so a test can say both that the
 * command failed and what it failed on.
 */
public final class Cli {

    private static final ObjectMapper JSON = new ObjectMapper();

    private Cli() {}

    /** Write a document as JSON into a directory, as UTF-8, and say where it went. */
    public static Path written(Path directory, String name, Object document) {
        try {
            return writtenText(directory, name, JSON.writeValueAsString(document));
        } catch (JsonProcessingException unwritable) {
            throw new IllegalStateException(unwritable);
        }
    }

    /** Write text into a directory, as UTF-8, and say where it went. */
    public static Path writtenText(Path directory, String name, String text) {
        Path path = directory.resolve(name);
        try {
            Files.writeString(path, text, StandardCharsets.UTF_8);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
        return path;
    }

    /** Run a command line against a database the test holds, which is left open. */
    public static Ran invoke(Database database, String... args) {
        return invoke(Map.of(), database, args);
    }

    /** Run a command line that is handed no database, in an empty environment. */
    public static Ran invoke(String... args) {
        return invoke(Map.of(), null, args);
    }

    /** Run a command line that is handed no database, in an environment of the test's own. */
    public static Ran invoke(Map<String, String> environment, String... args) {
        return invoke(environment, null, args);
    }

    private static Ran invoke(Map<String, String> environment, Database databaseOrNull, String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        Terminal terminal = new Terminal(
                environment,
                new BufferedReader(new StringReader("")),
                new PrintWriter(out, true),
                new PrintWriter(err, true));
        int code;
        Throwable thrown = null;
        try {
            code = databaseOrNull == null ? Main.execute(terminal, args) : Main.execute(terminal, databaseOrNull, args);
        } catch (RuntimeException failed) {
            code = Main.REFUSED;
            thrown = failed;
        }
        terminal.out().flush();
        terminal.err().flush();
        return new Ran(code, out.toString(), err.toString(), Optional.ofNullable(thrown));
    }

    /**
     * One run of the command line.
     *
     * @param exitCode what it exited with
     * @param output what it printed on standard output
     * @param errors what it printed on standard error
     * @param exception what a command threw, if it threw
     */
    public record Ran(int exitCode, String output, String errors, Optional<Throwable> exception) {

        /** What the run failed with, in words: the exception's message, or what it printed on error. */
        public String failure() {
            return exception.map(Throwable::getMessage).orElse(errors);
        }

        /** The last word it printed, which is the reference a command that raises something prints last. */
        public String lastWord() {
            String[] words = output.strip().split("\\s+", -1);
            return words[words.length - 1];
        }
    }
}
