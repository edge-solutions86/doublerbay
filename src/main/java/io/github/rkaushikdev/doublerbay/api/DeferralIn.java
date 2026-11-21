package io.github.rkaushikdev.doublerbay.api;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.github.rkaushikdev.doublerbay.tolerance.InspectionMethod;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Optional;

/**
 * A deferral as the engineer's sheet fills it in.
 *
 * @param against the card it is granted against
 * @param granted the day it is granted
 * @param by the engineer granting it
 * @param method how the damage is looked at again, a detailed visual when left out
 * @param limit the limit as the manual prints it, the book's own when left out or empty
 * @param nextCheck the next check the tail goes into, which the limit may not run past
 * @param reason why, if anybody wrote it down
 */
public record DeferralIn(
        @NotNull String against,
        @NotNull LocalDate granted,
        @NotNull String by,
        String method,
        @JsonSetter(nulls = Nulls.AS_EMPTY) Optional<String> limit,
        @JsonSetter(nulls = Nulls.AS_EMPTY) Optional<LocalDate> nextCheck,
        String reason) {

    public DeferralIn {
        method = method == null ? InspectionMethod.DVI.value() : method;
        limit = limit == null ? Optional.empty() : limit;
        nextCheck = nextCheck == null ? Optional.empty() : nextCheck;
        reason = reason == null ? "" : reason;
    }
}
