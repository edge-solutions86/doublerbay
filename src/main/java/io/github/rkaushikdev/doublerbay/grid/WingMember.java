package io.github.rkaushikdev.doublerbay.grid;

/**
 * A wing box member a margin can be measured off: a rib or a spar.
 *
 * <p>Ribs take the place of frames and spars take the place of stringers, so the nearest member
 * bounding a wing panel is one or the other.
 */
public sealed interface WingMember permits Rib, Spar {}
