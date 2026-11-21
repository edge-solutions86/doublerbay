package io.github.rkaushikdev.doublerbay.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reading and writing the files a command is pointed at, always as UTF-8.
 *
 * <p>The encoding is named rather than left to the platform. A default is one thing on the desk's
 * machines and another on the server, so a card carrying a degree sign or an engineer's accented
 * name would read as one thing in one place and another in the other, and nothing would tell
 * anybody which one went into the database. A file written here is written exactly as given: no
 * line ending is translated on the way out.
 */
final class Texts {

    private Texts() {}

    /** The whole of a file, as UTF-8 text. */
    static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    /** Write text to a file as UTF-8, replacing whatever was there. */
    static void write(Path path, String text) {
        try {
            Files.writeString(path, text, StandardCharsets.UTF_8);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }
}
