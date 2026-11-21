package io.github.rkaushikdev.doublerbay.api;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.util.Objects;

/**
 * One issued key, and who is behind it.
 *
 * <p>The key itself never appears in the text form, which names only the holder and the role.
 */
public record ApiKey(String key, String holder, Role role) {

    private static final int SHORTEST = 8;

    /** A key of at least eight characters, held by somebody. */
    public ApiKey {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(role, "role");
        if (key.codePointCount(0, key.length()) < SHORTEST) {
            throw new BadReading("an api key is at least eight characters");
        }
        if (Text.isBlank(holder)) {
            throw new BadReading("a key belongs to somebody");
        }
    }

    /** {@code the duty engineer (engineer)}. */
    @Override
    public String toString() {
        return holder + " (" + role + ")";
    }
}
