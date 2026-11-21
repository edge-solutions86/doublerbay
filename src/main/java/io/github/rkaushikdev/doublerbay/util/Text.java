package io.github.rkaushikdev.doublerbay.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The handful of string operations the desk's parsers lean on, with white space and case defined
 * once.
 *
 * <p>White space is every character Unicode calls a space or a separator, the control characters
 * from tab to carriage return and from U+001C to U+001F, and the next-line and no-break spaces
 * (U+0085, U+00A0, U+2007, U+202F) that a paste from a spreadsheet tends to bring along.
 */
public final class Text {

    private Text() {}

    /** Whether one code point is white space in the sense above. */
    public static boolean isSpace(int codePoint) {
        return Character.isWhitespace(codePoint)
                || codePoint == 0x85
                || codePoint == 0xA0
                || codePoint == 0x2007
                || codePoint == 0x202F;
    }

    /** The text with white space taken off both ends. */
    public static String strip(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && isSpace(text.codePointAt(start))) {
            start += Character.charCount(text.codePointAt(start));
        }
        while (end > start && isSpace(text.codePointBefore(end))) {
            end -= Character.charCount(text.codePointBefore(end));
        }
        return text.substring(start, end);
    }

    /** Whether the text is empty or nothing but white space. */
    public static boolean isBlank(String text) {
        return strip(text).isEmpty();
    }

    /** The text in capitals, the same on every machine whatever its locale. */
    public static String upper(String text) {
        return text.toUpperCase(Locale.ROOT);
    }

    /** The text in lower case, the same on every machine whatever its locale. */
    public static String lower(String text) {
        return text.toLowerCase(Locale.ROOT);
    }

    /** The words of the text, split on runs of white space, with no empty words. */
    public static List<String> words(String text) {
        List<String> found = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        text.codePoints().forEach(codePoint -> {
            if (isSpace(codePoint)) {
                if (!word.isEmpty()) {
                    found.add(word.toString());
                    word.setLength(0);
                }
            } else {
                word.appendCodePoint(codePoint);
            }
        });
        if (!word.isEmpty()) {
            found.add(word.toString());
        }
        return List.copyOf(found);
    }

    /** Whether a character ends a line: a line or paragraph separator of any kind. */
    public static boolean isLineBreak(char at) {
        return switch (at) {
            case '\n', '\r', '\u000B', '\u000C', '\u001C', '\u001D', '\u001E', '\u0085', ' ', ' ' -> true;
            default -> false;
        };
    }

    /**
     * The lines of the text, split at every line break (a carriage return and line feed pair
     * counting as one), with the breaks dropped and no empty line after a final break.
     */
    public static List<String> lines(String text) {
        List<String> found = new ArrayList<>();
        int start = 0;
        int index = 0;
        while (index < text.length()) {
            char at = text.charAt(index);
            if (isLineBreak(at)) {
                found.add(text.substring(start, index));
                if (at == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') {
                    index++;
                }
                start = index + 1;
            }
            index++;
        }
        if (start < text.length()) {
            found.add(text.substring(start));
        }
        return List.copyOf(found);
    }

    /** The text padded on the right with spaces to at least a width. */
    public static String padRight(String text, int width) {
        return text.length() >= width ? text : text + " ".repeat(width - text.length());
    }

    /** The text padded on the left with spaces to at least a width. */
    public static String padLeft(String text, int width) {
        return text.length() >= width ? text : " ".repeat(width - text.length()) + text;
    }

    /** The text with trailing white space removed. */
    public static String stripTrailing(String text) {
        int end = text.length();
        while (end > 0 && isSpace(text.codePointBefore(end))) {
            end -= Character.charCount(text.codePointBefore(end));
        }
        return text.substring(0, end);
    }
}
