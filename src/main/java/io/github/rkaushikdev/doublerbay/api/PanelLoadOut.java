package io.github.rkaushikdev.doublerbay.api;

/**
 * What one panel is carrying, as it goes back.
 *
 * @param part the panel
 * @param repairs how many repairs are fitted to it
 * @param temporary how many of those are temporary
 * @param crowded whether it carries enough to be worth a conversation about replacing it
 */
public record PanelLoadOut(String part, int repairs, int temporary, boolean crowded) {}
