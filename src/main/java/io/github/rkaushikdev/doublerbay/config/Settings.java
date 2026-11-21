package io.github.rkaushikdev.doublerbay.config;

import io.github.rkaushikdev.doublerbay.BadReading;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Everything the service was told at start up, read once into a value that cannot change.
 *
 * <p>A setting read again later is a setting that can change under a request, and then two halves
 * of one answer are computed against different configuration.
 */
public record Settings(String database, Path keys, String host, int port, int recentDays, Optional<LocalDate> today) {

    /** Settings whose port is one the service can listen on and whose recent window is a day or more. */
    public Settings {
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(keys, "keys");
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(today, "today");
        if (port < Config.LOWEST_PORT || port > Config.HIGHEST_PORT) {
            throw new BadReading(port + " is not a port this service can listen on");
        }
        if (recentDays < 1) {
            throw new BadReading("a recent window is at least a day");
        }
    }

    /** The two settings with no safe default, everything else at its default. */
    public Settings(String database, Path keys) {
        this(database, keys, Config.DEFAULT_HOST, Config.DEFAULT_PORT, Config.DEFAULT_RECENT_DAYS, Optional.empty());
    }

    /** Whether the database lives only as long as the process. */
    public boolean isInMemory() {
        return database.contains(":memory:") || database.contains(":mem:");
    }

    /** Whether the service answers as of a fixed day rather than today. */
    public boolean hasAPinnedDay() {
        return today.isPresent();
    }

    /** {@code doublerbay on 127.0.0.1:8000, database on disk}. */
    @Override
    public String toString() {
        String where = isInMemory() ? "in memory" : "on disk";
        return "doublerbay on " + host + ":" + port + ", database " + where;
    }
}
