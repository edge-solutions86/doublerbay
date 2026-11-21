package io.github.rkaushikdev.doublerbay.api;

/**
 * An order that has been out too long, and how long.
 *
 * @param order the order
 * @param daysWaiting the days it has waited on the manufacturer
 */
public record ChasedOut(OrderOut order, int daysWaiting) {}
