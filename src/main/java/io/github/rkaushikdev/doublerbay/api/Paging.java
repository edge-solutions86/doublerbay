package io.github.rkaushikdev.doublerbay.api;

import java.util.List;
import java.util.function.Function;

/**
 * Cutting a long list into pages, the same way on every route that needs one.
 *
 * <p>Some routes return a list that grows without bound. A tail with twenty years of cards has four
 * figures of them, and a client that asks for all of them gets a response nobody renders and a query
 * nobody wants to run twice.
 *
 * <p>Offset and limit rather than a cursor. A cursor is the better answer when rows are being
 * inserted under the reader, and on this service they are not: a damage card is raised once a week
 * per aeroplane, and the page a planner is looking at does not move under them. An offset is also
 * the only paging a person can drive from a terminal without keeping state.
 *
 * <p>The page says how many there are in total. A list that only says "here are fifty" leaves the
 * caller counting pages by asking for them, and the count is the number the desk actually wanted
 * half the time anyway.
 */
public final class Paging {

    /** How many rows a page holds when the caller does not say. */
    public static final int DEFAULT_LIMIT = 50;

    /** The most rows one page will hold. */
    public static final int HIGHEST_LIMIT = 500;

    private Paging() {}

    /** The first page, at the usual size. */
    public static Window window() {
        return new Window();
    }

    /** A window from what arrived, filling in what did not. */
    public static Window window(Integer offset, Integer limit) {
        return new Window(offset != null ? offset : 0, limit != null ? limit : DEFAULT_LIMIT);
    }

    /**
     * Cut a list to a window and shape each row, keeping the total honest.
     *
     * <p>The total is the length before the cut. A total counted after it would be the page size,
     * which reads as a fleet with fifty cards on it however many it really has.
     */
    public static <T, R> Page<R> paged(List<T> held, Window asked, Function<? super T, ? extends R> shape) {
        List<R> rows = asked.cut(held).stream().<R>map(shape).toList();
        return new Page<>(held.size(), asked.offset(), asked.limit(), rows);
    }
}
