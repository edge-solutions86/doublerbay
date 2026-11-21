package io.github.rkaushikdev.doublerbay.cli;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Objects;

/**
 * What a command talks to: the environment it was started in, the keyboard and the two streams.
 *
 * <p>Everything a command prints goes to {@code out}, and nothing goes to {@code err} unless it
 * failed. A job that prints on success sends mail every night, the mail gets filtered, and a real
 * failure goes unread for a month.
 *
 * @param environment the variables the process was started with
 * @param in what the operator types
 * @param out where the answers go
 * @param err where refusals and usage errors go
 */
public record Terminal(Map<String, String> environment, BufferedReader in, PrintWriter out, PrintWriter err) {

    public Terminal {
        environment = Map.copyOf(environment);
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");
    }

    /** The process's own environment, keyboard and streams, in the console's own charset. */
    public static Terminal system() {
        Charset charset = Charset.defaultCharset();
        return new Terminal(
                System.getenv(),
                new BufferedReader(new InputStreamReader(System.in, charset)),
                new PrintWriter(System.out, true, charset),
                new PrintWriter(System.err, true, charset));
    }
}
