package io.github.rkaushikdev.doublerbay.api;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The keys the service will accept, each issued once. */
public final class KeyRing {

    private final Map<String, ApiKey> keys = new LinkedHashMap<>();

    /** A ring with no keys on it. */
    public KeyRing() {}

    /** Put a key on the ring, refusing one already issued and naming only its first four characters. */
    public void issue(ApiKey key) {
        if (keys.containsKey(key.key())) {
            throw new BadReading(firstFour(key.key()) + " has already been issued");
        }
        keys.put(key.key(), key);
    }

    /** Take a key off the ring, so it stops working. */
    public void revoke(String key) {
        if (!keys.containsKey(key)) {
            throw new NotOnFile("that key was never issued");
        }
        keys.remove(key);
    }

    /** Who is behind a key. */
    public ApiKey holderOf(String key) {
        ApiKey held = keys.get(key);
        if (held == null) {
            throw new NotOnFile("that key is not one this service accepts");
        }
        return held;
    }

    /** How many keys are on the ring. */
    public int size() {
        return keys.size();
    }

    /** Every key on the ring, in the order they were issued. */
    List<ApiKey> issued() {
        return List.copyOf(keys.values());
    }

    private static String firstFour(String key) {
        int end = key.offsetByCodePoints(0, Math.min(4, key.codePointCount(0, key.length())));
        return key.substring(0, end);
    }
}
