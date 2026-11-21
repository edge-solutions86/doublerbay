package io.github.rkaushikdev.doublerbay.disposition;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.util.Literal;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** An engineering order number, as the year it was raised and a counter: {@code EO-2025-0007}. */
public record OrderId(int year, int sequence) {

    private static final Pattern SHAPE = Pattern.compile("EO-([0-9]{4})-([0-9]{4})");

    /** An order number in a year from 2000 to 2099, counting from 1 to 9999. */
    public OrderId {
        if (year < 2000 || year > 2099) {
            throw new BadReading(year + " is not an order year");
        }
        if (sequence < 1 || sequence > 9999) {
            throw new BadReading(sequence + " is not an order sequence");
        }
    }

    /**
     * An order number as written, read regardless of case and surrounding white space.
     *
     * @throws BadReading when the text is not an engineering order number
     */
    public static OrderId parse(String text) {
        Matcher match = SHAPE.matcher(Text.upper(Text.strip(text)));
        if (!match.matches()) {
            throw new BadReading(Literal.of(text) + " is not an engineering order number");
        }
        return new OrderId(Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2)));
    }

    /** {@code EO-2025-0007}. */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, "EO-%04d-%04d", year, sequence);
    }
}
