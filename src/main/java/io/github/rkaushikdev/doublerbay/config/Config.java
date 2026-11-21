package io.github.rkaushikdev.doublerbay.config;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.util.Literal;
import io.github.rkaushikdev.doublerbay.util.Text;
import io.github.rkaushikdev.doublerbay.util.Whole;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What the service reads out of its environment, and what it refuses to guess.
 *
 * <p>Two settings have no safe default and this says so rather than picking one. The database
 * url, because defaulting to an in-memory database means a service that starts, accepts writes,
 * and loses them at the next restart with nothing in the log. And the key file, because defaulting
 * to the development ring would put four known keys on a production service.
 *
 * <p>Everything else has a default that is right for a small operator, and every default is a
 * constant here rather than a literal buried in a call, so the whole configuration surface is
 * readable in one screen.
 */
public final class Config {

    /** What every variable this service reads starts with. */
    public static final String PREFIX = "DOUBLERBAY_";

    /** The interface the service listens on unless told otherwise. */
    public static final String DEFAULT_HOST = "127.0.0.1";

    /** The port the service listens on unless told otherwise. */
    public static final int DEFAULT_PORT = 8000;

    /** How many days back "recent" reaches unless told otherwise. */
    public static final int DEFAULT_RECENT_DAYS = 30;

    /** The lowest port the service can listen on. */
    public static final int LOWEST_PORT = 1;

    /** The highest port there is. */
    public static final int HIGHEST_PORT = 65535;

    private static final List<String> SETTINGS = List.of("database", "keys", "host", "port", "recent_days", "today");

    private Config() {}

    /** The environment variable one setting is read from: {@code database} is {@code DOUBLERBAY_DATABASE}. */
    public static String named(String setting) {
        return PREFIX + Text.upper(setting);
    }

    /**
     * One setting off the environment, trimmed, with a blank treated as absent.
     *
     * <p>An empty variable is how a deployment tool says "I have not set this", and reading it as
     * an empty string produces a database url of nothing, which fails four layers down with an
     * unrecognisable message.
     */
    public static Optional<String> read(String setting, Map<String, String> environment) {
        String held = environment.get(named(setting));
        if (held == null) {
            return Optional.empty();
        }
        String stripped = Text.strip(held);
        return stripped.isEmpty() ? Optional.empty() : Optional.of(stripped);
    }

    /** One setting off the process environment. */
    public static Optional<String> read(String setting) {
        return read(setting, System.getenv());
    }

    /** A setting with no safe default, refused by name when it is missing. */
    public static String required(String setting, Map<String, String> environment) {
        return read(setting, environment)
                .orElseThrow(() -> new BadReading(named(setting) + " is not set and there is no safe default for it"));
    }

    /** A count off the environment, refusing anything that is not one. */
    public static int wholeNumber(String setting, int fallback, Map<String, String> environment) {
        Optional<String> held = read(setting, environment);
        if (held.isEmpty()) {
            return fallback;
        }
        try {
            return Whole.parse(held.get());
        } catch (NumberFormatException refused) {
            throw new BadReading(named(setting) + " is " + Literal.of(held.get()) + ", which is not a whole number");
        }
    }

    /** A pinned day off the environment, written {@code 2025-03-14} or {@code 20250314}. */
    public static Optional<LocalDate> aDay(String setting, Map<String, String> environment) {
        Optional<String> held = read(setting, environment);
        if (held.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(isoDate(held.get()));
        } catch (DateTimeParseException refused) {
            throw new BadReading(named(setting) + " is " + Literal.of(held.get()) + ", which is not a date");
        }
    }

    /** Read the whole configuration once, refusing what it will not guess. */
    public static Settings fromEnvironment(Map<String, String> environment) {
        return new Settings(
                required("database", environment),
                Path.of(required("keys", environment)),
                read("host", environment).orElse(DEFAULT_HOST),
                wholeNumber("port", DEFAULT_PORT, environment),
                wholeNumber("recent_days", DEFAULT_RECENT_DAYS, environment),
                aDay("today", environment));
    }

    /** Read the whole configuration off the process environment. */
    public static Settings fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    /** Every variable this service reads, for the message a bad start up prints. */
    public static List<String> described() {
        return SETTINGS.stream().map(Config::named).toList();
    }

    private static LocalDate isoDate(String text) {
        if (text.length() == 8 && text.chars().allMatch(Character::isDigit)) {
            return LocalDate.parse(text, DateTimeFormatter.BASIC_ISO_DATE);
        }
        return LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
    }
}
