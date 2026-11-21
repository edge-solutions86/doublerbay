package io.github.rkaushikdev.doublerbay.api;

/**
 * One card walked through the book, as it goes back.
 *
 * @param reference the card
 * @param part the panel it is on
 * @param panelKind what kind of panel that is
 * @param region where on the panel it is
 * @param thickness the metal under it, in inches as written
 * @param edgeMargin how far it is from the nearest edge, in inches as written
 * @param verdict what the book made of it
 */
public record AssessmentOut(
        String reference,
        String part,
        String panelKind,
        String region,
        String thickness,
        String edgeMargin,
        VerdictOut verdict) {}
