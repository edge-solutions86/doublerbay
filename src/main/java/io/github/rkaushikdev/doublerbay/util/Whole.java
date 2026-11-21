package io.github.rkaushikdev.doublerbay.util;

/**
 * Whole numbers read off text an operator typed: white space around the digits is ignored, a
 * leading sign is allowed, and so is a single underscore between two digits ({@code 1_000}).
 */
public final class Whole {

    private Whole() {}

    /**
     * Read a whole number.
     *
     * @throws NumberFormatException when the text is not one
     */
    public static int parse(String text) {
        String trimmed = Text.strip(text);
        StringBuilder digits = new StringBuilder(trimmed.length());
        for (int index = 0; index < trimmed.length(); index++) {
            char at = trimmed.charAt(index);
            if (at == '_') {
                boolean between = index > 0
                        && index < trimmed.length() - 1
                        && Character.isDigit(trimmed.charAt(index - 1))
                        && Character.isDigit(trimmed.charAt(index + 1));
                if (!between) {
                    throw new NumberFormatException("'" + text + "' is not a whole number");
                }
                continue;
            }
            digits.append(at);
        }
        return Integer.parseInt(digits.toString());
    }
}
