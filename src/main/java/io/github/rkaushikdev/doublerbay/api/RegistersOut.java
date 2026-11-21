package io.github.rkaushikdev.doublerbay.api;

import java.util.List;

/**
 * Which registers a story can carry lines from, in reading order.
 *
 * @param registers the registers, in the order they read on one day
 */
public record RegistersOut(List<String> registers) {}
