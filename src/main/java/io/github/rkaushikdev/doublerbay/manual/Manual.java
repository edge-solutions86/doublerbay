package io.github.rkaushikdev.doublerbay.manual;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.grid.Chapter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One chapter of the book, with its revision history: the manual as a document, with revisions that
 * come into force on a date.
 *
 * <p>A structural repair manual is not a fixed thing. It is reissued every few months, limits move,
 * pages get withdrawn, and the revision that answers for a damage is the one that was in force when
 * the damage was dispositioned, not the one on the shelf today. Reassessing an old finding against
 * today's book is how a repair that was correct becomes a finding in an audit.
 *
 * <p>So a revision has an issue date and an effective date and they are not the same day. The book
 * arrives, the operator has a window to work it into the maintenance programme, and until the
 * effective date the previous revision is still the one that governs. Anything that reads a limit
 * reads it as of a date.
 */
public record Manual(Chapter chapter, List<Revision> revisions) {

    /** A chapter with at least one revision, numbered and taking effect strictly in order. */
    public Manual {
        Objects.requireNonNull(chapter, "chapter");
        revisions = List.copyOf(revisions);
        if (revisions.isEmpty()) {
            throw new BadReading("chapter " + chapter + " has at least one revision");
        }
        for (int index = 1; index < revisions.size(); index++) {
            if (revisions.get(index).number() <= revisions.get(index - 1).number()) {
                throw new BadReading("revisions are listed in order with no repeats");
            }
        }
        for (int index = 1; index < revisions.size(); index++) {
            if (!revisions
                    .get(index)
                    .effective()
                    .isAfter(revisions.get(index - 1).effective())) {
                throw new BadReading("revisions take effect in order with no two on one day");
            }
        }
    }

    /** The newest revision on the chapter. */
    public Revision latest() {
        return revisions.get(revisions.size() - 1);
    }

    /** The oldest revision on file. */
    public Revision first() {
        return revisions.get(0);
    }

    /** The revision that governed on a day, which is the last one to have started. */
    public Revision inForceOn(LocalDate day) {
        for (int index = revisions.size() - 1; index >= 0; index--) {
            Revision revision = revisions.get(index);
            if (!revision.effective().isAfter(day)) {
                return revision;
            }
        }
        throw new NotOnFile("chapter " + chapter + " had no revision in force on " + day);
    }

    /** The revision with this number. */
    public Revision numbered(int number) {
        for (Revision revision : revisions) {
            if (revision.number() == number) {
                return revision;
            }
        }
        throw new NotOnFile("chapter " + chapter + " has no revision " + number);
    }

    /** The chapter with one more revision on it. */
    public Manual issued(Revision revision) {
        List<Revision> longer = new ArrayList<>(revisions);
        longer.add(Objects.requireNonNull(revision, "revision"));
        return new Manual(chapter, longer);
    }

    /** {@code chapter 53, rev 12 of 2024-12-01}. */
    @Override
    public String toString() {
        return "chapter " + chapter + ", " + latest();
    }
}
