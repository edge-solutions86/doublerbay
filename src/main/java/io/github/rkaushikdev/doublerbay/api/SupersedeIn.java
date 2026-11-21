package io.github.rkaushikdev.doublerbay.api;

import jakarta.validation.constraints.NotNull;

/**
 * Which card now carries a damage, and who said so.
 *
 * @param by the card that carries it now
 * @param byWhom who closed the old one, the structures desk when left out
 */
public record SupersedeIn(@NotNull String by, String byWhom) {

    public SupersedeIn {
        byWhom = byWhom == null ? "structures" : byWhom;
    }

    /** Superseded by the structures desk. */
    public SupersedeIn(String by) {
        this(by, null);
    }
}
