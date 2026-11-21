package io.github.rkaushikdev.doublerbay.manual;

import io.github.rkaushikdev.doublerbay.grid.Chapter;
import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;

/**
 * One chapter as the shelf holds it: its history, its pages and its table.
 *
 * <p>The table is held as the way to build it at a revision number, because each revision of a
 * chapter has a table of its own and the shelf only ever reads one of them at a time.
 */
public record Holding(Manual manual, List<Page> pages, IntFunction<LimitTable> buildTable) {

    /** A chapter with its pages and the way to read its table at a revision. */
    public Holding {
        Objects.requireNonNull(manual, "manual");
        Objects.requireNonNull(buildTable, "buildTable");
        pages = List.copyOf(pages);
    }

    /** The chapter this holding is. */
    public Chapter chapter() {
        return manual.chapter();
    }

    /** The table as it stood at a revision. */
    public LimitTable tableAt(int revision) {
        return buildTable.apply(revision);
    }

    /** The pages in the book at a revision, in the order they were shelved. */
    public List<Page> pagesAt(int revision) {
        return pages.stream().filter(page -> page.isLiveAt(revision)).toList();
    }

    /** {@code chapter 53, 5 pages, rev 12 of 2024-12-01}. */
    @Override
    public String toString() {
        return "chapter " + chapter() + ", " + pages.size() + " pages, " + manual.latest();
    }
}
