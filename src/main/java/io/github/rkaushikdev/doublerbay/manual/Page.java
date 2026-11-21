package io.github.rkaushikdev.doublerbay.manual;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.fleet.Effectivity;
import io.github.rkaushikdev.doublerbay.grid.Reference;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.util.Objects;
import java.util.Optional;

/**
 * One subject in the manual, at one revision, for one set of tails.
 *
 * <p>A page comes into the book at the revision that issued it and leaves at the revision that
 * withdrew it, if one has.
 */
public record Page(
        Reference reference, String title, int revision, Effectivity effectivity, Optional<Integer> withdrawnAt) {

    /** A titled page issued at a revision, withdrawn no earlier than the one after. */
    public Page {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(effectivity, "effectivity");
        Objects.requireNonNull(withdrawnAt, "withdrawnAt");
        if (Text.isBlank(title)) {
            throw new BadReading(reference + " has a title");
        }
        if (revision < 1) {
            throw new BadReading(reference + " is issued at a revision");
        }
        if (withdrawnAt.isPresent() && withdrawnAt.get() <= revision) {
            throw new BadReading(reference + " cannot be withdrawn before it was issued");
        }
    }

    /** A page for every tail, still in the book. */
    public Page(Reference reference, String title, int revision) {
        this(reference, title, revision, Effectivity.allAircraft(), Optional.empty());
    }

    /** A page for some tails, still in the book. */
    public Page(Reference reference, String title, int revision, Effectivity effectivity) {
        this(reference, title, revision, effectivity, Optional.empty());
    }

    /** A page from what was to hand; a null withdrawal is a page still in the book. */
    public static Page of(
            Reference reference, String title, int revision, Effectivity effectivity, Integer withdrawnAt) {
        return new Page(reference, title, revision, effectivity, Optional.ofNullable(withdrawnAt));
    }

    /** Whether the page is in the book at a given revision number. */
    public boolean isLiveAt(int at) {
        if (at < revision) {
            return false;
        }
        return withdrawnAt.isEmpty() || at < withdrawnAt.get();
    }

    /** {@code 53-10-03 allowable damage, fuselage skin}. */
    @Override
    public String toString() {
        return reference + " " + title;
    }
}
