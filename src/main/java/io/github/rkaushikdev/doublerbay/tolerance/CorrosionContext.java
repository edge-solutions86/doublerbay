package io.github.rkaushikdev.doublerbay.tolerance;

import io.github.rkaushikdev.doublerbay.ids.PanelId;
import java.util.Objects;

/** What the programme knows about this place, which the metal does not say. */
public record CorrosionContext(
        PanelId part, boolean withinLimits, boolean foundHereBefore, boolean urgentAirworthinessConcern) {

    /** A panel and what the programme knows about it. */
    public CorrosionContext {
        Objects.requireNonNull(part, "part");
    }

    /** A panel and what the programme knows about it, with no urgent airworthiness concern raised. */
    public CorrosionContext(PanelId part, boolean withinLimits, boolean foundHereBefore) {
        this(part, withinLimits, foundHereBefore, false);
    }

    /** {@code 5310-143L, first time here} or {@code 5310-143L, seen here before}. */
    @Override
    public String toString() {
        String history = foundHereBefore ? "seen here before" : "first time here";
        return part + ", " + history;
    }
}
