package io.github.rkaushikdev.doublerbay.api;

/**
 * How many findings were graded at one level.
 *
 * @param level the level
 * @param count how many
 */
public record LevelCountOut(int level, int count) {}
