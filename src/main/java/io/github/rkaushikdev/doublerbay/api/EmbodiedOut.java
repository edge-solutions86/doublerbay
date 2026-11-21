package io.github.rkaushikdev.doublerbay.api;

import java.util.List;

/**
 * A repair just fitted, and what fitting it changed.
 *
 * @param repair the repair as drawn
 * @param cardState where it left the card it answers
 * @param deferralLifted whether a deferral was lifted because of it
 * @param clashes the pairs of repairs on the panel now too close together, by number
 */
public record EmbodiedOut(RepairOut repair, String cardState, boolean deferralLifted, List<List<String>> clashes) {}
