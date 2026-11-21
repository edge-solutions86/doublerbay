package io.github.rkaushikdev.doublerbay.api;

/**
 * One line of a count: what was counted and how many.
 *
 * @param name what was counted, as written
 * @param count how many
 */
public record CountedOut(String name, int count) {}
