package io.github.rkaushikdev.doublerbay.grid;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.util.Literal;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A place in the manual, as chapter, section and optionally subject: {@code 53-10-03}.
 *
 * <p>Chapter, section and subject is how every manual on the shelf is filed. The reference is three
 * fields and it sorts and prints as one, because a page number that reads 53-10-3 in one place and
 * 53-10-03 in another is how a limit gets looked up in the wrong table.
 */
public record Reference(Chapter chapter, int section, Optional<Integer> subject) implements Comparable<Reference> {

    private static final Pattern SHAPE = Pattern.compile("([0-9]{2})-([0-9]{2})(?:-([0-9]{2}))?");

    private static final Comparator<Reference> ORDER = Comparator.comparingInt(
                    (Reference reference) -> reference.chapter().number())
            .thenComparingInt(Reference::section)
            .thenComparingInt(reference -> reference.subject().orElse(-1));

    /** A reference whose section and subject are two digits each. */
    public Reference {
        Objects.requireNonNull(chapter, "chapter");
        Objects.requireNonNull(subject, "subject");
        if (section < 0 || section > 99) {
            throw new BadReading("a section is two digits, got " + section);
        }
        if (subject.isPresent() && (subject.get() < 0 || subject.get() > 99)) {
            throw new BadReading("a subject is two digits, got " + subject.get());
        }
    }

    /** A whole section, with no subject. */
    public Reference(Chapter chapter, int section) {
        this(chapter, section, Optional.empty());
    }

    /** One subject inside a section. */
    public Reference(Chapter chapter, int section, int subject) {
        this(chapter, section, Optional.of(subject));
    }

    /** Read the printed form, {@code 53-10-03} or {@code 53-10}. */
    public static Reference parse(String text) {
        Matcher match = SHAPE.matcher(Text.strip(text));
        if (!match.matches()) {
            throw new BadReading(Literal.of(text) + " is not a manual reference");
        }
        int chapterNumber = Integer.parseInt(match.group(1));
        Chapter chapter;
        try {
            chapter = Chapter.fromValue(chapterNumber);
        } catch (IllegalArgumentException offTheShelf) {
            throw new BadReading("chapter " + chapterNumber + " is not one this desk reads");
        }
        String subject = match.group(3);
        int section = Integer.parseInt(match.group(2));
        return subject == null
                ? new Reference(chapter, section)
                : new Reference(chapter, section, Integer.parseInt(subject));
    }

    /** A reference with no subject names a whole section. */
    public boolean isSection() {
        return subject.isEmpty();
    }

    /** The section this reference is filed under. */
    public Reference sectionOnly() {
        return new Reference(chapter, section);
    }

    /**
     * Whether this reference is at or above another in the filing.
     *
     * <p>A section covers every subject inside it, which is what lets a limit filed at 53-10 answer
     * for damage looked up at 53-10-03.
     */
    public boolean covers(Reference other) {
        if (chapter != other.chapter || section != other.section) {
            return false;
        }
        return subject.isEmpty() || subject.equals(other.subject);
    }

    /** Chapter, then section, with a section ahead of every subject inside it. */
    @Override
    public int compareTo(Reference other) {
        return ORDER.compare(this, other);
    }

    /** Whether this reference files ahead of another. */
    public boolean isLessThan(Reference other) {
        return compareTo(other) < 0;
    }

    /** {@code 53-10-03}, or {@code 53-10} for a whole section. */
    @Override
    public String toString() {
        String head = chapter + "-" + String.format(Locale.ROOT, "%02d", section);
        return subject.map(value -> head + String.format(Locale.ROOT, "-%02d", value))
                .orElse(head);
    }
}
