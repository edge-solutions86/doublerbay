package io.github.rkaushikdev.doublerbay.api;

/**
 * One step of an assessment's trail, as it goes back.
 *
 * @param rule the rule that was asked
 * @param detail what it found
 * @param passed whether the damage passed it
 */
public record ReasonOut(String rule, String detail, boolean passed) {}
