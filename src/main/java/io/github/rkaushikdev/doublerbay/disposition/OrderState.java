package io.github.rkaushikdev.doublerbay.disposition;

import io.github.rkaushikdev.doublerbay.util.Literal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Where a question to the manufacturer is.
 *
 * <p>An order is raised, sent, perhaps acknowledged, and then approved or rejected; it can be
 * withdrawn at any point before an answer. A rejected question can be reworked and raised again. An
 * approved or withdrawn order goes nowhere else.
 */
public enum OrderState {
    RAISED("raised"),
    SENT("sent"),
    ACKNOWLEDGED("acknowledged"),
    APPROVED("approved"),
    REJECTED("rejected"),
    WITHDRAWN("withdrawn");

    /** Every legal move, keyed on the state it is made from. */
    public static final Map<OrderState, Set<OrderState>> MOVES = moves();

    private final String value;

    OrderState(String value) {
        this.value = value;
    }

    /** The word the queue prints. */
    public String value() {
        return value;
    }

    /**
     * The state this word names.
     *
     * @throws IllegalArgumentException when no state is
     */
    public static OrderState fromValue(String value) {
        for (OrderState state : values()) {
            if (state.value.equals(value)) {
                return state;
            }
        }
        throw new IllegalArgumentException(Literal.of(value) + " is not a valid OrderState");
    }

    /** Whether the question is still waiting on somebody. */
    public boolean isOpen() {
        return this == RAISED || this == SENT || this == ACKNOWLEDGED;
    }

    /** Whether the manufacturer has given one of its two answers. */
    public boolean isAnswered() {
        return this == APPROVED || this == REJECTED;
    }

    /** Whether an order may move from here to another state. */
    public boolean canMoveTo(OrderState other) {
        return MOVES.get(this).contains(other);
    }

    private static Map<OrderState, Set<OrderState>> moves() {
        Map<OrderState, Set<OrderState>> held = new EnumMap<>(OrderState.class);
        held.put(RAISED, frozen(EnumSet.of(SENT, WITHDRAWN)));
        held.put(SENT, frozen(EnumSet.of(ACKNOWLEDGED, APPROVED, REJECTED, WITHDRAWN)));
        held.put(ACKNOWLEDGED, frozen(EnumSet.of(APPROVED, REJECTED, WITHDRAWN)));
        held.put(APPROVED, frozen(EnumSet.noneOf(OrderState.class)));
        held.put(REJECTED, frozen(EnumSet.of(RAISED)));
        held.put(WITHDRAWN, frozen(EnumSet.noneOf(OrderState.class)));
        return Collections.unmodifiableMap(held);
    }

    private static Set<OrderState> frozen(Set<OrderState> states) {
        return Collections.unmodifiableSet(states);
    }

    /** {@code acknowledged}. */
    @Override
    public String toString() {
        return value;
    }
}
