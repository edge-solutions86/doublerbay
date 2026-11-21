package io.github.rkaushikdev.doublerbay.service;

import io.github.rkaushikdev.doublerbay.disposition.DispositionState;
import io.github.rkaushikdev.doublerbay.disposition.EngineeringOrder;
import java.util.Objects;

/**
 * An order out to the manufacturer, and where it left the card.
 *
 * @param order the order as it went on file
 * @param cardState the state the card was left in
 */
public record Raised(EngineeringOrder order, DispositionState cardState) {

    public Raised {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(cardState, "cardState");
    }

    /** {@code EO-2025-0001 raised, card held for engineering}. */
    @Override
    public String toString() {
        return order.reference() + " raised, card " + cardState;
    }
}
