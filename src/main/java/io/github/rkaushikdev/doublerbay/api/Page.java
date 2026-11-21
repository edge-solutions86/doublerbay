package io.github.rkaushikdev.doublerbay.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One page of a list, and enough to ask for the next.
 *
 * @param total how many rows the whole list holds
 * @param offset where this page starts
 * @param limit how many rows were asked for
 * @param rows the rows on this page
 */
public record Page<T>(int total, int offset, int limit, List<T> rows) {

    public Page {
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
    }

    /** Whether there are rows after this page. */
    public boolean hasMore() {
        return offset + rows.size() < total;
    }

    /** Where the next page starts, if there is one. */
    public Optional<Integer> nextOffset() {
        return hasMore() ? Optional.of(offset + rows.size()) : Optional.empty();
    }
}
