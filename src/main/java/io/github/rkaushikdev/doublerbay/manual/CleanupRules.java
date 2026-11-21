package io.github.rkaushikdev.doublerbay.manual;

import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.damage.DamageKind;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** The blend rules for every kind the book has one for. */
public record CleanupRules(Map<DamageKind, CleanupRule> byKind) {

    /** The rules, held in the order they were given. */
    public CleanupRules {
        byKind = Collections.unmodifiableMap(new LinkedHashMap<>(byKind));
    }

    /**
     * The rule for a kind.
     *
     * @throws NotOnFile when the book has none for it
     */
    public CleanupRule forKind(DamageKind kind) {
        CleanupRule rule = byKind.get(kind);
        if (rule == null) {
            throw new NotOnFile("the book has no cleanup rule for a " + kind);
        }
        return rule;
    }

    /** Whether the book has a rule for a kind. */
    public boolean covers(DamageKind kind) {
        return byKind.containsKey(kind);
    }

    /** Every kind the book has a rule for. */
    public Set<DamageKind> kinds() {
        Set<DamageKind> held = EnumSet.noneOf(DamageKind.class);
        held.addAll(byKind.keySet());
        return Collections.unmodifiableSet(held);
    }

    /** {@code cleanup rules for 11 kinds}. */
    @Override
    public String toString() {
        return "cleanup rules for " + byKind.size() + " kinds";
    }
}
