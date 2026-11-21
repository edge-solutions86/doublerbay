package io.github.rkaushikdev.doublerbay.ids;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.util.Literal;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A damage report number, which is the year it was raised and a counter: {@code DR-2025-004182}. */
public record FindingId(int year, int sequence) {

    private static final Pattern SHAPE = Pattern.compile("DR-([0-9]{4})-([0-9]{6})");

    /** A report number inside the years and counter the form allows. */
    public FindingId {
        if (year < 2000 || year > 2099) {
            throw new BadReading(year + " is not a report year");
        }
        if (sequence < 1 || sequence > 999999) {
            throw new BadReading(sequence + " is not a report sequence");
        }
    }

    /** A report number as written, read regardless of case. */
    public static FindingId parse(String text) {
        Matcher match = SHAPE.matcher(Text.upper(Text.strip(text)));
        if (!match.matches()) {
            throw new BadReading(Literal.of(text) + " is not a damage report number");
        }
        return new FindingId(Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2)));
    }

    /** The next report raised in the same year. */
    public FindingId nextInYear() {
        return new FindingId(year, sequence + 1);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "DR-%04d-%06d", year, sequence);
    }
}
