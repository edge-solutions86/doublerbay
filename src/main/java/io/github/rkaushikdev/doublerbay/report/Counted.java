package io.github.rkaushikdev.doublerbay.report;

import java.util.List;
import java.util.Objects;

/**
 * One row of a count: a thing, and how many of it there are.
 *
 * <p>The reports hand their counts back as ordered lists of these rather than as maps, because the
 * order is part of the answer: most first, or in level order, and the same order on every run.
 */
public record Counted<T>(T what, int count) {

    /** A thing and its count. */
    public Counted {
        Objects.requireNonNull(what, "what");
    }

    /** The count against one thing in a list of rows, nought when it is not there. */
    public static <T> int countOf(List<Counted<T>> rows, T what) {
        for (Counted<T> row : rows) {
            if (row.what.equals(what)) {
                return row.count;
            }
        }
        return 0;
    }
}
