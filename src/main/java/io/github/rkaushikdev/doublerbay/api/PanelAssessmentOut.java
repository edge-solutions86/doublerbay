package io.github.rkaushikdev.doublerbay.api;

import java.util.List;

/**
 * Every card on one panel, assessed together.
 *
 * @param part the panel
 * @param cardCount how many cards are on it
 * @param damageCount how many damages those come to once close ones are counted as one
 * @param disposition the worst answer on the panel
 * @param cards each card's own assessment
 */
public record PanelAssessmentOut(
        String part, int cardCount, int damageCount, String disposition, List<AssessmentOut> cards) {

    public PanelAssessmentOut {
        cards = List.copyOf(cards);
    }
}
