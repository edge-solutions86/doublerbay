package io.github.rkaushikdev.doublerbay.api;

import java.time.LocalDate;
import java.util.List;

/**
 * How much damage is open across the register, as it goes back.
 *
 * @param on the day it was asked for
 * @param total how many cards are open across every tail
 * @param cleanTails the tails with nothing open
 * @param tails one count per tail in service on the day
 */
public record OpenFleetOut(LocalDate on, int total, List<String> cleanTails, List<OpenTailOut> tails) {

    public OpenFleetOut {
        cleanTails = List.copyOf(cleanTails);
        tails = List.copyOf(tails);
    }
}
