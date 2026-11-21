package io.github.rkaushikdev.doublerbay.disposition;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.OutOfSequence;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One card's state and every move it has been through, oldest first. */
public record DispositionRecord(FindingId reference, DispositionState state, List<Transition> history) {

    /** A card in a state, with the moves that brought it there. */
    public DispositionRecord {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(state, "state");
        history = List.copyOf(history);
    }

    /** A card in a state with nothing behind it. */
    public DispositionRecord(FindingId reference, DispositionState state) {
        this(reference, state, List.of());
    }

    /** A card just raised. */
    public static DispositionRecord raised(FindingId reference) {
        return new DispositionRecord(reference, DispositionState.RAISED);
    }

    /** Whether the desk still owns the card. */
    public boolean isOpen() {
        return state.isOpen();
    }

    /** How many moves the card has been through. */
    public int movesMade() {
        return history.size();
    }

    /** The card after a move with no note, refusing anything the lifecycle does not allow. */
    public DispositionRecord movedTo(DispositionState next, LocalDate on, String by) {
        return movedTo(next, on, by, "");
    }

    /**
     * The card after a move, refusing anything the lifecycle does not allow and any move dated
     * before the last one. The record it came from is left as it was.
     */
    public DispositionRecord movedTo(DispositionState next, LocalDate on, String by, String note) {
        if (!state.canMoveTo(next)) {
            throw new OutOfSequence(reference + " cannot go from " + state + " to " + next);
        }
        if (!history.isEmpty() && on.isBefore(history.get(history.size() - 1).on())) {
            throw new BadReading(reference + ": a move cannot be dated before the one before it");
        }
        List<Transition> longer = new ArrayList<>(history);
        longer.add(new Transition(state, next, on, by, note));
        return new DispositionRecord(reference, next, longer);
    }

    /**
     * Whether the card has ever sat in a state, the one it started in included.
     *
     * <p>The history records where each move went, so the state a card was raised into only ever
     * appears as the state a move came from.
     */
    public boolean hasBeen(DispositionState other) {
        if (state == other) {
            return true;
        }
        return history.stream().anyMatch(move -> move.now() == other || move.was() == other);
    }

    /** When the card last went into a state, if it ever did. */
    public Optional<LocalDate> entered(DispositionState other) {
        for (int index = history.size() - 1; index >= 0; index--) {
            if (history.get(index).now() == other) {
                return Optional.of(history.get(index).on());
            }
        }
        return Optional.empty();
    }

    /** {@code DR-2025-004182 raised, 0 moves}. */
    @Override
    public String toString() {
        return reference + " " + state + ", " + movesMade() + " moves";
    }
}
