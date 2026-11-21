package io.github.rkaushikdev.doublerbay.api;

import jakarta.validation.constraints.NotNull;

/**
 * A shorter limit for a live deferral.
 *
 * @param limit the new limit as the manual prints it
 * @param by the engineer setting it
 * @param reason why, the reason already written when left out
 */
public record TightenIn(@NotNull String limit, @NotNull String by, String reason) {

    public TightenIn {
        reason = reason == null ? "" : reason;
    }
}
