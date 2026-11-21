package io.github.rkaushikdev.doublerbay.cli;

/**
 * A command line that was written wrong, found while the command ran rather than while its words
 * were read: a method the shop has no short name for, a level the programme does not grade.
 *
 * <p>It is not a refusal of the desk's. Nothing was looked up and nothing was decided; the operator
 * typed something the command cannot take, and the answer is the same usage error, and the same
 * exit code, as a missing option.
 */
public final class Misused extends RuntimeException {

    private final boolean aValue;

    private Misused(String message, boolean aValue) {
        super(message);
        this.aValue = aValue;
    }

    /** A command line that cannot be run as written, saying why. */
    public static Misused because(String why) {
        return new Misused(why, false);
    }

    /** One value on the command line that is not one the command takes, saying why. */
    public static Misused badValue(String why) {
        return new Misused(why, true);
    }

    /** The line the operator sees: {@code Invalid value: 'magic' is not one of gvi, dvi}. */
    public String said() {
        return aValue ? "Invalid value: " + getMessage() : getMessage();
    }
}
