package io.github.rkaushikdev.doublerbay.api;

import java.util.Optional;

/**
 * A card just raised: its number, where it starts, and what the programme made of it.
 *
 * @param reference the number the desk gave it
 * @param state where it is in its life
 * @param corrosionLevel the programme's level, for a corrosion card
 * @param mustBeReported whether it has to go to the authority
 */
public record RaisedOut(String reference, String state, Optional<Integer> corrosionLevel, boolean mustBeReported) {}
