package io.github.rkaushikdev.doublerbay.service;

import io.github.rkaushikdev.doublerbay.ids.Serial;
import java.util.List;

/**
 * What one counter file did: the tails whose readings landed, and the lines that did not.
 *
 * @param posted the serial of every line that landed, in file order
 * @param refused every line that did not, in file order
 */
public record Taken(List<Serial> posted, List<RefusedLine> refused) {

    public Taken {
        posted = List.copyOf(posted);
        refused = List.copyOf(refused);
    }

    /** How many lines the file carried. */
    public int read() {
        return posted.size() + refused.size();
    }

    /** Whether every line landed. */
    public boolean isClean() {
        return refused.isEmpty();
    }

    /** {@code 1 readings posted}, with the refusals counted after it when there were any. */
    @Override
    public String toString() {
        if (isClean()) {
            return posted.size() + " readings posted";
        }
        return posted.size() + " readings posted, " + refused.size() + " refused";
    }
}
