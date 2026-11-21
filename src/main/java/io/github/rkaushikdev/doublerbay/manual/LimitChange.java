package io.github.rkaushikdev.doublerbay.manual;

import io.github.rkaushikdev.doublerbay.util.Decimals;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * One field of one row, and what it moved from and to.
 *
 * <p>The field is named the way the revision report writes it, {@code max_depth} or
 * {@code min_residual_fraction}, or {@code allowed} when the row stopped or started allowing
 * anything. The values are the limits themselves, a length, a ratio, an amount or a yes or no, and
 * absent where the row stated none.
 */
public record LimitChange(LimitKey key, String field, Optional<Object> was, Optional<Object> now, Direction direction) {

    /** What the report prints for a limit the row did not state. */
    private static final String ABSENT = "None";

    /** A change to one field of one row. */
    public LimitChange {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(was, "was");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(direction, "direction");
    }

    /**
     * {@code dent on any skin anywhere: max_depth 0.080 in to 0.060 in (tighter)}. A limit the row did
     * not state prints as {@code None}, and a yes or no as {@code True} or {@code False}.
     */
    @Override
    public String toString() {
        return key + ": " + field + " " + shown(was) + " to " + shown(now) + " (" + direction + ")";
    }

    private static String shown(Optional<Object> value) {
        if (value.isEmpty()) {
            return ABSENT;
        }
        Object held = value.get();
        if (held instanceof Boolean flag) {
            return flag ? "True" : "False";
        }
        if (held instanceof BigDecimal amount) {
            return Decimals.text(amount);
        }
        return held.toString();
    }
}
