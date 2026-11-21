package io.github.rkaushikdev.doublerbay.api;

/**
 * One line of a work pack as it goes back.
 *
 * @param what the block it belongs to: deferral, inspection or waiting
 * @param reference the card or repair it is against
 * @param detail what is owed, in words
 * @param overrun whether it was already over when the check opened
 */
public record PackLineOut(String what, String reference, String detail, boolean overrun) {}
