package io.github.rkaushikdev.doublerbay.ids;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.util.Literal;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A skin panel part number, as chapter, section, sequence and hand: {@code 5310-142L}. */
public record PanelId(int chapter, int section, int sequence, String hand) {

    private static final Pattern SHAPE = Pattern.compile("([0-9]{2})([0-9]{2})-([0-9]{3})([LRC])");

    /** A panel number with every field inside what the part numbering allows. */
    public PanelId {
        Objects.requireNonNull(hand, "hand");
        if (chapter < 50 || chapter > 59) {
            throw new BadReading("chapter " + chapter + " does not carry skin panels");
        }
        if (section < 0 || section > 99) {
            throw new BadReading(section + " is not a section");
        }
        if (sequence < 0 || sequence > 999) {
            throw new BadReading(sequence + " is not a panel sequence");
        }
        if (!hand.equals("L") && !hand.equals("R") && !hand.equals("C")) {
            throw new BadReading(Literal.of(hand) + " is not a hand");
        }
    }

    /** A panel number as written, read regardless of case. */
    public static PanelId parse(String text) {
        Matcher match = SHAPE.matcher(Text.upper(Text.strip(text)));
        if (!match.matches()) {
            throw new BadReading(Literal.of(text) + " is not a panel number");
        }
        return new PanelId(
                Integer.parseInt(match.group(1)),
                Integer.parseInt(match.group(2)),
                Integer.parseInt(match.group(3)),
                match.group(4));
    }

    /** The same panel on the other side of the aircraft. A centre panel is its own mirror. */
    public PanelId mirrored() {
        if (hand.equals("C")) {
            return this;
        }
        return new PanelId(chapter, section, sequence, hand.equals("L") ? "R" : "L");
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%02d%02d-%03d%s", chapter, section, sequence, hand);
    }
}
