package io.github.rkaushikdev.doublerbay.service;

/**
 * What the seed put on the database.
 *
 * @param aircraft how many tails were enrolled
 * @param cards how many damage cards were raised
 * @param deferrals how many deferrals were granted
 */
public record Seeded(int aircraft, int cards, int deferrals) {

    /** {@code 4 aircraft, 5 damage cards, 1 deferrals}. */
    @Override
    public String toString() {
        return aircraft + " aircraft, " + cards + " damage cards, " + deferrals + " deferrals";
    }
}
