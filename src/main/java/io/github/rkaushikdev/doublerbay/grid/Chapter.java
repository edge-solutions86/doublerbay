package io.github.rkaushikdev.doublerbay.grid;

import io.github.rkaushikdev.doublerbay.util.Literal;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * The manual chapters this desk reads. Anything else is somebody else's manual.
 *
 * <p>An allowable damage limit does not live anywhere on the aircraft. It lives at 53-10-03 in the
 * structural repair manual, and the only way to get from a piece of damaged skin to that number is
 * to know which chapter the structure belongs to. Fuselage skin is 53, wing skin is 57, the
 * stabilisers are 55, and the standard practices every repair leans on are 51.
 */
public enum Chapter {
    STANDARD_PRACTICES(51),
    DOORS(52),
    FUSELAGE(53),
    NACELLES(54),
    STABILISERS(55),
    WINDOWS(56),
    WINGS(57);

    /**
     * The chapters that carry skin the desk can be asked to disposition. Doors and windows have skin
     * too, but their damage goes to the systems desk with the hardware it is attached to.
     */
    public static final Set<Chapter> SKIN_CHAPTERS =
            Collections.unmodifiableSet(EnumSet.of(FUSELAGE, NACELLES, STABILISERS, WINGS));

    private final int number;

    Chapter(int number) {
        this.number = number;
    }

    /** The chapter with this number. */
    public static Chapter fromValue(int number) {
        for (Chapter chapter : values()) {
            if (chapter.number == number) {
                return chapter;
            }
        }
        throw new IllegalArgumentException(Literal.of(number) + " is not a valid Chapter");
    }

    /** Whether a chapter carries skin this desk dispositions. */
    public static boolean isSkinChapter(Chapter chapter) {
        return SKIN_CHAPTERS.contains(chapter);
    }

    /** The chapter number. */
    public int value() {
        return number;
    }

    /** The chapter number, as the reference is written with it. */
    public int number() {
        return number;
    }

    /** The chapter's name in words: {@code standard practices}. */
    public String title() {
        return Text.lower(name().replace('_', ' '));
    }

    /** {@code 53}, always two digits. */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%02d", number);
    }
}
