package io.github.rkaushikdev.doublerbay.disposition;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.time.LocalDate;
import java.util.Objects;

/** One move of a card, who made it and why. */
public record Transition(DispositionState was, DispositionState now, LocalDate on, String by, String note) {

    /** A move made by somebody, with a note that may be empty. */
    public Transition {
        Objects.requireNonNull(was, "was");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(on, "on");
        Objects.requireNonNull(by, "by");
        Objects.requireNonNull(note, "note");
        if (Text.isBlank(by)) {
            throw new BadReading("a move is made by somebody");
        }
    }

    /** A move with no note on it. */
    public Transition(DispositionState was, DispositionState now, LocalDate on, String by) {
        this(was, now, on, by, "");
    }

    /** {@code 2025-03-14 assessed to deferred by structures (awaiting parts)}. */
    @Override
    public String toString() {
        String tail = note.isEmpty() ? "" : " (" + note + ")";
        return on + " " + was + " to " + now + " by " + by + tail;
    }
}
