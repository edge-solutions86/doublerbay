package io.github.rkaushikdev.doublerbay.manual;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import io.github.rkaushikdev.doublerbay.fleet.Aircraft;
import io.github.rkaushikdev.doublerbay.grid.Chapter;
import io.github.rkaushikdev.doublerbay.grid.Reference;
import io.github.rkaushikdev.doublerbay.structure.PanelKind;
import io.github.rkaushikdev.doublerbay.structure.Region;
import io.github.rkaushikdev.doublerbay.v320.Manual53;
import io.github.rkaushikdev.doublerbay.v320.Manual57;
import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * Every chapter the desk holds, read as of a date and a tail: the whole book on the shelf.
 *
 * <p>Three things have to line up before a limit is the right limit. The chapter has to be the one
 * the structure belongs to, the revision has to be the one that was in force on the day, and the row
 * has to apply to this aeroplane. Every one of those is a place a desk can be wrong without noticing,
 * and the third is the quiet one: a row written for serials 5001 through 5240 looks like an answer on
 * a tail that is 5301, and nothing about it says otherwise.
 *
 * <p>So the book takes the day and the aeroplane and does all three at once. There is no way to get a
 * row out of it without saying which tail and when.
 *
 * <p>The V320 book, with both skin chapters shelved, is {@link #v320()}.
 */
public final class Book {

    private final Map<Chapter, Holding> holdings = new LinkedHashMap<>();
    private final CleanupRules cleanup;
    private final InteractionRules interaction;

    /** An empty shelf with the standard cleanup rules and the default interaction rules. */
    public Book() {
        this(Cleanups.standardRules(), InteractionRules.DEFAULT_RULES);
    }

    /** An empty shelf with these cleanup rules and the default interaction rules. */
    public Book(CleanupRules cleanup) {
        this(cleanup, InteractionRules.DEFAULT_RULES);
    }

    /** An empty shelf with the standard cleanup rules and these interaction rules. */
    public Book(InteractionRules interaction) {
        this(Cleanups.standardRules(), interaction);
    }

    /** An empty shelf with these cleanup and interaction rules. */
    public Book(CleanupRules cleanup, InteractionRules interaction) {
        this.cleanup = Objects.requireNonNull(cleanup, "cleanup");
        this.interaction = Objects.requireNonNull(interaction, "interaction");
    }

    /** The V320 book, with both skin chapters on the shelf. */
    public static Book v320() {
        Book book = new Book();
        book.shelve(Manual53.manual(), Manual53.PAGES, Manual53::allowableDamage);
        book.shelve(Manual57.manual(), Manual57.PAGES, Manual57::allowableDamage);
        return book;
    }

    /** The blend rules the book carries alongside its tables. */
    public CleanupRules cleanup() {
        return cleanup;
    }

    /** What the book says about damages near each other. */
    public InteractionRules interaction() {
        return interaction;
    }

    /**
     * Put a chapter on the shelf, with its pages and the way to read its table at a revision.
     *
     * @throws BadReading when the chapter is already shelved or a page belongs to another chapter
     */
    public void shelve(Manual manual, List<Page> pages, IntFunction<LimitTable> buildTable) {
        if (holdings.containsKey(manual.chapter())) {
            throw new BadReading("chapter " + manual.chapter() + " is already on the shelf");
        }
        for (Page page : pages) {
            if (page.reference().chapter() != manual.chapter()) {
                throw new BadReading(page.reference() + " is not a page of chapter " + manual.chapter());
            }
        }
        holdings.put(manual.chapter(), new Holding(manual, pages, buildTable));
    }

    /**
     * The chapter as the shelf holds it.
     *
     * @throws NotOnFile when the chapter is not on the shelf
     */
    public Holding holding(Chapter chapter) {
        Holding held = holdings.get(chapter);
        if (held == null) {
            throw new NotOnFile("chapter " + chapter + " is not on the shelf");
        }
        return held;
    }

    /** Every chapter on the shelf. */
    public Set<Chapter> chapters() {
        Set<Chapter> held = EnumSet.noneOf(Chapter.class);
        held.addAll(holdings.keySet());
        return Collections.unmodifiableSet(held);
    }

    /** The revision of a chapter that governed on a day. */
    public Revision revisionOn(Chapter chapter, LocalDate day) {
        return holding(chapter).manual().inForceOn(day);
    }

    /** The table of a chapter as it stood on a day. */
    public LimitTable tableOn(Chapter chapter, LocalDate day) {
        Revision revision = revisionOn(chapter, day);
        return holding(chapter).tableAt(revision.number());
    }

    /** The pages of a chapter in the book on a day. */
    public List<Page> pagesOn(Chapter chapter, LocalDate day) {
        Revision revision = revisionOn(chapter, day);
        return holding(chapter).pagesAt(revision.number());
    }

    /**
     * The page with this reference as the book stood on a day.
     *
     * @throws NotOnFile when no such page was in the book that day
     */
    public Page page(Reference reference, LocalDate day) {
        for (Page page : pagesOn(reference.chapter(), day)) {
            if (page.reference().equals(reference)) {
                return page;
            }
        }
        throw new NotOnFile(reference + " is not in the book on " + day);
    }

    /** The row that answers, for this chapter on this day for this tail. */
    public LimitRow limitsFor(
            Chapter chapter, LocalDate day, Aircraft aircraft, DamageKind damage, PanelKind panel, Region region) {
        LimitTable table = tableOn(chapter, day).effectiveFor(aircraft);
        return table.lookup(damage, panel, region);
    }

    /** How many chapters are on the shelf. */
    public int size() {
        return holdings.size();
    }

    /** {@code 2 chapters on the shelf}. */
    @Override
    public String toString() {
        return holdings.size() + " chapters on the shelf";
    }
}
