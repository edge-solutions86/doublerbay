package io.github.rkaushikdev.doublerbay.api;

/**
 * A card looked at again, and how the answer moved.
 *
 * @param finding the card as it now stands
 * @param was what the book made of it before
 * @param now what the book makes of it now
 * @param gotWorse whether the answer got worse
 * @param stillDeferred whether it is still flying against a deferral
 * @param outgrewItsDeferral whether it is flying against a limit it no longer fits
 */
public record ReMeasuredOut(
        FindingOut finding,
        String was,
        String now,
        boolean gotWorse,
        boolean stillDeferred,
        boolean outgrewItsDeferral) {}
