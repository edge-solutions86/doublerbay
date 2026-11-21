package io.github.rkaushikdev.doublerbay.disposition;

import io.github.rkaushikdev.doublerbay.util.Literal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Where a damage card is in its life, and which moves are legal from there.
 *
 * <p>A card is raised, assessed, and then it goes one of four ways: it is deferred with a limit on
 * it, it goes into work, it is held while somebody asks the manufacturer, or it is closed because
 * there was nothing to do. Every one of those can come back to assessed, because a re measurement
 * changes the answer.
 *
 * <p>The moves are written down rather than left to whoever is calling. A card going straight from
 * raised to closed has skipped the assessment, and the only way anybody finds that out later is by
 * reading the history and noticing what is not in it. Refusing the move is cheaper.
 *
 * <p>Superseded is the one terminal state and it is deliberately separate from closed. Closed means
 * the damage was dealt with. Superseded means this card was a duplicate and another one is carrying
 * it, which is a different thing to have in a fleet report.
 */
public enum DispositionState {
    RAISED("raised"),
    ASSESSED("assessed"),
    DEFERRED("deferred"),
    HELD("held for engineering"),
    IN_WORK("in work"),
    CLOSED("closed"),
    SUPERSEDED("superseded");

    /** Every legal move, keyed on the state it is made from. */
    public static final Map<DispositionState, Set<DispositionState>> MOVES = moves();

    private final String value;

    DispositionState(String value) {
        this.value = value;
    }

    /** The words the card prints. */
    public String value() {
        return value;
    }

    /**
     * The state the card names with these words.
     *
     * @throws IllegalArgumentException when no state is
     */
    public static DispositionState fromValue(String value) {
        for (DispositionState state : values()) {
            if (state.value.equals(value)) {
                return state;
            }
        }
        throw new IllegalArgumentException(Literal.of(value) + " is not a valid DispositionState");
    }

    /** Whether nothing can happen to the card any more. */
    public boolean isTerminal() {
        return this == SUPERSEDED;
    }

    /** Anything the desk still owns. */
    public boolean isOpen() {
        return this != CLOSED && this != SUPERSEDED;
    }

    /** States where the aeroplane is in service carrying the damage. */
    public boolean isFlyingWithIt() {
        return this == RAISED || this == ASSESSED || this == DEFERRED || this == HELD;
    }

    /** Whether the lifecycle allows a move from here to another state. */
    public boolean canMoveTo(DispositionState other) {
        return MOVES.get(this).contains(other);
    }

    private static Map<DispositionState, Set<DispositionState>> moves() {
        Map<DispositionState, Set<DispositionState>> held = new EnumMap<>(DispositionState.class);
        held.put(RAISED, frozen(EnumSet.of(ASSESSED, SUPERSEDED)));
        held.put(ASSESSED, frozen(EnumSet.of(DEFERRED, HELD, IN_WORK, CLOSED, SUPERSEDED)));
        held.put(DEFERRED, frozen(EnumSet.of(ASSESSED, HELD, IN_WORK, SUPERSEDED)));
        held.put(HELD, frozen(EnumSet.of(ASSESSED, IN_WORK, SUPERSEDED)));
        held.put(IN_WORK, frozen(EnumSet.of(CLOSED, HELD)));
        held.put(CLOSED, frozen(EnumSet.of(ASSESSED)));
        held.put(SUPERSEDED, frozen(EnumSet.noneOf(DispositionState.class)));
        return Collections.unmodifiableMap(held);
    }

    private static Set<DispositionState> frozen(Set<DispositionState> states) {
        return Collections.unmodifiableSet(states);
    }

    @Override
    public String toString() {
        return value;
    }
}
