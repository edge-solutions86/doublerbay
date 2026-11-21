package io.github.rkaushikdev.doublerbay.api;

import io.github.rkaushikdev.doublerbay.BadReading;
import java.util.List;

/**
 * How much of a list a caller asked for.
 *
 * @param offset how many rows to skip
 * @param limit how many rows at most to hand back
 */
public record Window(int offset, int limit) {

    public Window {
        if (offset < 0) {
            throw new BadReading("a page starts at nothing or later, not before it");
        }
        if (limit < 1) {
            throw new BadReading("a page holds at least one row");
        }
        if (limit > Paging.HIGHEST_LIMIT) {
            throw new BadReading("a page holds at most " + Paging.HIGHEST_LIMIT + " rows");
        }
    }

    /** The first page, at the usual size. */
    public Window() {
        this(0, Paging.DEFAULT_LIMIT);
    }

    /** One past the last row the window covers. */
    public int stop() {
        return offset + limit;
    }

    /** The rows of a list this window covers, which is none past its end. */
    public <T> List<T> cut(List<T> held) {
        int from = Math.min(offset, held.size());
        int to = Math.min(stop(), held.size());
        return List.copyOf(held.subList(from, to));
    }

    @Override
    public String toString() {
        return "rows " + offset + " to " + stop();
    }
}
