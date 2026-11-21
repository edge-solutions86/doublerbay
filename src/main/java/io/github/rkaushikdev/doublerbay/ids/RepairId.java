package io.github.rkaushikdev.doublerbay.ids;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.util.Literal;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A repair number, filed under the chapter the repair is in: {@code RP-53-0142}. */
public record RepairId(int chapter, int sequence) {

    private static final Pattern SHAPE = Pattern.compile("RP-([0-9]{2})-([0-9]{4})");

    /** A repair number in a structures chapter, 50 to 59. */
    public RepairId {
        if (chapter < 50 || chapter > 59) {
            throw new BadReading("chapter " + chapter + " does not carry repairs");
        }
        if (sequence < 1 || sequence > 9999) {
            throw new BadReading(sequence + " is not a repair sequence");
        }
    }

    /** A repair number as written, read regardless of case. */
    public static RepairId parse(String text) {
        Matcher match = SHAPE.matcher(Text.upper(Text.strip(text)));
        if (!match.matches()) {
            throw new BadReading(Literal.of(text) + " is not a repair number");
        }
        return new RepairId(Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2)));
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "RP-%02d-%04d", chapter, sequence);
    }
}
