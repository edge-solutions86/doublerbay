package io.github.rkaushikdev.doublerbay.util;

import java.util.Locale;

/**
 * A value quoted the way a refusal quotes what it was handed.
 *
 * <p>A refusal that says {@code '0.0405 ' is not a length callout} shows the trailing space that
 * caused it, which an unquoted message would hide. Text goes in single quotes, or in double quotes
 * when it holds a single quote and no double one. A backslash, the quote in use, tab, line feed and
 * carriage return are escaped with a backslash, and any other character that does not print is
 * written as its hexadecimal code ({@code \x07}, {@code ​}, {@code \U0001f600}).
 */
public final class Literal {

    private Literal() {}

    /** Text, quoted. */
    public static String of(String text) {
        char quote = text.indexOf('\'') >= 0 && text.indexOf('"') < 0 ? '"' : '\'';
        StringBuilder written = new StringBuilder(text.length() + 2).append(quote);
        text.codePoints().forEach(codePoint -> written.append(escaped(codePoint, quote)));
        return written.append(quote).toString();
    }

    /** A whole number, which needs no quoting. */
    public static String of(long number) {
        return Long.toString(number);
    }

    private static String escaped(int codePoint, char quote) {
        if (codePoint == quote || codePoint == '\\') {
            return "\\" + (char) codePoint;
        }
        return switch (codePoint) {
            case '\t' -> "\\t";
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            default -> printable(codePoint) ? new String(Character.toChars(codePoint)) : coded(codePoint);
        };
    }

    private static String coded(int codePoint) {
        if (codePoint < 0x100) {
            return String.format(Locale.ROOT, "\\x%02x", codePoint);
        }
        if (codePoint < 0x10000) {
            return String.format(Locale.ROOT, "\\u%04x", codePoint);
        }
        return String.format(Locale.ROOT, "\\U%08x", codePoint);
    }

    /** Whether a character prints: anything but controls, formats, separators other than space. */
    static boolean printable(int codePoint) {
        if (codePoint == ' ') {
            return true;
        }
        return switch (Character.getType(codePoint)) {
            case Character.CONTROL,
                    Character.FORMAT,
                    Character.SURROGATE,
                    Character.PRIVATE_USE,
                    Character.UNASSIGNED,
                    Character.LINE_SEPARATOR,
                    Character.PARAGRAPH_SEPARATOR,
                    Character.SPACE_SEPARATOR -> false;
            default -> true;
        };
    }
}
