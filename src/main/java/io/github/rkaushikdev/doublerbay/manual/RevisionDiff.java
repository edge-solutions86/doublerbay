package io.github.rkaushikdev.doublerbay.manual;

import java.util.List;
import java.util.Objects;

/**
 * Everything that moved between two issues of one table.
 *
 * <p>The rows added and removed are listed by key, and the rows on both issues by each field that
 * changed, all in the order their keys print in. {@link Diffs#diffTables} works one out.
 */
public record RevisionDiff(
        String reference,
        int wasRevision,
        int nowRevision,
        List<LimitKey> added,
        List<LimitKey> removed,
        List<LimitChange> changed) {

    /** A diff between two revision numbers of the table at a reference. */
    public RevisionDiff {
        Objects.requireNonNull(reference, "reference");
        added = List.copyOf(added);
        removed = List.copyOf(removed);
        changed = List.copyOf(changed);
    }

    /** Whether nothing moved at all. */
    public boolean isEmpty() {
        return added.isEmpty() && removed.isEmpty() && changed.isEmpty();
    }

    /** The changes that made a limit tighter. */
    public List<LimitChange> tightenings() {
        return changed.stream()
                .filter(change -> change.direction() == Direction.TIGHTER)
                .toList();
    }

    /** The changes that made a limit looser. */
    public List<LimitChange> loosenings() {
        return changed.stream()
                .filter(change -> change.direction() == Direction.LOOSER)
                .toList();
    }

    /** The changes that put already dispositioned findings back on the desk. */
    public List<LimitChange> needsReview() {
        return changed.stream()
                .filter(change -> change.direction().needsReview())
                .toList();
    }

    /** Whether a row keyed on this was added, removed or changed. */
    public boolean touches(LimitKey key) {
        if (added.contains(key) || removed.contains(key)) {
            return true;
        }
        return changed.stream().anyMatch(change -> change.key().equals(key));
    }

    /** {@code 53-10 rev 11 to 12: 0 added, 0 removed, 1 tightened, 0 loosened}, or {@code ...: no change}. */
    @Override
    public String toString() {
        if (isEmpty()) {
            return reference + " rev " + wasRevision + " to " + nowRevision + ": no change";
        }
        return reference + " rev " + wasRevision + " to " + nowRevision + ": " + added.size() + " added, "
                + removed.size() + " removed, " + tightenings().size() + " tightened, "
                + loosenings().size()
                + " loosened";
    }
}
