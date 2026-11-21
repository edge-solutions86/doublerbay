package io.github.rkaushikdev.doublerbay.api;

/**
 * Where a card is, as it goes back: which structure, the point as written, and the side.
 *
 * @param structure {@code fuselage} or {@code wing}
 * @param written the point the way it is written on the card
 * @param side the side of the aeroplane
 */
public record LocationOut(String structure, String written, String side) {}
