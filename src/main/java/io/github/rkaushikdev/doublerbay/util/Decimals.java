package io.github.rkaushikdev.doublerbay.util;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Decimal arithmetic done the one way every measurement on the desk is done.
 *
 * <p>Every intermediate result is carried to 28 significant digits with a tie going to the even
 * neighbour, and every rounding to a fixed number of places breaks a tie the same way. A limit of
 * exactly 0.040 and a damage of exactly 0.040 then compare equal however they were reached, and a
 * value that needs rounding twice is rounded the same way twice.
 */
public final class Decimals {

    /** The working precision: 28 significant digits, ties to even. */
    public static final MathContext CONTEXT = new MathContext(28, RoundingMode.HALF_EVEN);

    private Decimals() {}

    /**
     * Read decimal text, allowing white space around it and an underscore between two digits.
     *
     * @throws NumberFormatException when the text is not a finite decimal
     */
    public static BigDecimal parse(String text) {
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
                    throw new NumberFormatException("'" + text + "' is not a decimal");
                }
                continue;
            }
            digits.append(at);
        }
        try {
            return new BigDecimal(digits.toString());
        } catch (NumberFormatException refused) {
            throw new NumberFormatException("'" + text + "' is not a decimal");
        }
    }

    /** The product of two decimals at the working precision. */
    public static BigDecimal times(BigDecimal left, BigDecimal right) {
        return left.multiply(right, CONTEXT);
    }

    /** The product of a decimal and a whole number at the working precision. */
    public static BigDecimal times(BigDecimal left, long right) {
        return left.multiply(BigDecimal.valueOf(right), CONTEXT);
    }

    /**
     * One decimal over another at the working precision.
     *
     * @throws ArithmeticException when the divisor is zero
     */
    public static BigDecimal over(BigDecimal numerator, BigDecimal denominator) {
        return numerator.divide(denominator, CONTEXT);
    }

    /** One decimal over a whole number at the working precision. */
    public static BigDecimal over(BigDecimal numerator, long denominator) {
        return over(numerator, BigDecimal.valueOf(denominator));
    }

    /** A decimal rounded to a fixed number of places, a tie going to the even neighbour. */
    public static BigDecimal quantize(BigDecimal value, int places) {
        return value.setScale(places, RoundingMode.HALF_EVEN);
    }

    /** The nearest whole value, a tie going to the even neighbour. */
    public static BigDecimal integral(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_EVEN);
    }

    /** The nearest whole number, a tie going to the even neighbour. */
    public static int toInt(BigDecimal value) {
        return integral(value).intValueExact();
    }

    /** Whether a decimal is already a whole number. */
    public static boolean isWhole(BigDecimal value) {
        return value.compareTo(integral(value)) == 0;
    }

    /**
     * A decimal as the desk prints it: plain digits for an ordinary value, an exponent only for a
     * value with a positive exponent or one smaller than a millionth.
     */
    public static String text(BigDecimal value) {
        return value.toString();
    }
}
