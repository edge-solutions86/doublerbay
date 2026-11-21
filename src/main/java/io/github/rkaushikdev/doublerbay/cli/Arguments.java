package io.github.rkaushikdev.doublerbay.cli;

import io.github.rkaushikdev.doublerbay.util.Literal;
import io.github.rkaushikdev.doublerbay.util.Whole;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/**
 * How the words on a command line become the values a command takes, and the sentence the
 * operator reads when one cannot.
 *
 * <p>A whole number is read the way an operator writes one: white space around it is ignored and
 * so is an underscore between two digits. A file a command reads has to be there before anything
 * is looked up, so a mistyped path is a usage error rather than half a run.
 */
public final class Arguments {

    private Arguments() {}

    /** Read a whole number, refusing anything that is not one. */
    public static int wholeNumber(String text) {
        try {
            return Whole.parse(text);
        } catch (NumberFormatException notOne) {
            throw new TypeConversionException(Literal.of(text) + " is not a valid integer.");
        }
    }

    /** Read a path that has to name a file that is there, and not a directory. */
    public static Path existingFile(String text) {
        Path path = pathOf(text);
        if (!Files.exists(path)) {
            throw new TypeConversionException("File " + Literal.of(text) + " does not exist.");
        }
        if (Files.isDirectory(path)) {
            throw new TypeConversionException("File " + Literal.of(text) + " is a directory.");
        }
        return path;
    }

    /** Read a path that has to name something that is there. */
    public static Path existingPath(String text) {
        Path path = pathOf(text);
        if (!Files.exists(path)) {
            throw new TypeConversionException("Path " + Literal.of(text) + " does not exist.");
        }
        return path;
    }

    /** Read a path a command may write to, there or not. */
    public static Path pathOf(String text) {
        try {
            return Path.of(text);
        } catch (InvalidPathException unusable) {
            throw new TypeConversionException(Literal.of(text) + " is not a path: " + unusable.getReason());
        }
    }

    /** A whole number option or argument. */
    static final class WholeNumber implements ITypeConverter<Integer> {
        @Override
        public Integer convert(String text) {
            return wholeNumber(text);
        }
    }

    /** A file that has to be there. */
    static final class ExistingFile implements ITypeConverter<Path> {
        @Override
        public Path convert(String text) {
            return existingFile(text);
        }
    }

    /** A file or directory that has to be there. */
    static final class ExistingPath implements ITypeConverter<Path> {
        @Override
        public Path convert(String text) {
            return existingPath(text);
        }
    }

    /** A path that may or may not be there yet. */
    static final class AnyPath implements ITypeConverter<Path> {
        @Override
        public Path convert(String text) {
            return pathOf(text);
        }
    }
}
