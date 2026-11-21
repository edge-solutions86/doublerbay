package io.github.rkaushikdev.doublerbay.fleet;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.util.Literal;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A service bulletin, which a tail either has embodied or has not: {@code SB 53-018}. */
public record Bulletin(int chapter, int number) {

    private static final Pattern SHAPE = Pattern.compile("SB\\s*([0-9]{2})-([0-9]{3})", Pattern.CASE_INSENSITIVE);

    /** A bulletin in a chapter that carries them, numbered one to 999. */
    public Bulletin {
        if (chapter < 20 || chapter > 80) {
            throw new BadReading("chapter " + chapter + " does not carry bulletins");
        }
        if (number < 1 || number > 999) {
            throw new BadReading(number + " is not a bulletin number");
        }
    }

    /** A bulletin number as written, read regardless of case and of the space after {@code SB}. */
    public static Bulletin parse(String text) {
        Matcher match = SHAPE.matcher(Text.strip(text));
        if (!match.matches()) {
            throw new BadReading(Literal.of(text) + " is not a bulletin number");
        }
        return new Bulletin(Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2)));
    }

    /** {@code SB 53-018}, padded. */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, "SB %02d-%03d", chapter, number);
    }
}
