package io.github.rkaushikdev.doublerbay.api;

import java.util.List;

/**
 * Where a card is now, and everything it has been through.
 *
 * @param reference the card
 * @param state where it is in its life
 * @param open whether the desk still owns it
 * @param moves every move it has made, oldest first
 */
public record DispositionOut(String reference, String state, boolean open, List<String> moves) {

    public DispositionOut {
        moves = List.copyOf(moves);
    }
}
