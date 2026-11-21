package io.github.rkaushikdev.doublerbay.api;

import io.github.rkaushikdev.doublerbay.Refused;
import io.github.rkaushikdev.doublerbay.util.Text;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Who is allowed to do what, keyed on the role rather than on the person.
 *
 * <p>The important line is between raising a card and dispositioning it. The person who found the
 * damage is not the person who decides what happens to it, and a system that lets one account do
 * both has removed the only check the process has.
 */
public final class Security {

    /** What each role may do. */
    public static final Map<Role, Set<Action>> ALLOWED = allowed();

    private Security() {}

    /**
     * Whether this key may do this, refusing with the reason if not.
     *
     * @param key the key the request carried, null or empty when it carried none
     * @return who is behind the key
     * @throws Refused when there is no key, or the key's role may not do this
     * @throws io.github.rkaushikdev.doublerbay.NotOnFile when the key is not on the ring
     */
    public static ApiKey check(KeyRing ring, String key, Action action) {
        if (key == null || key.isEmpty()) {
            throw new Refused("no api key", "every request says who it is from");
        }
        ApiKey holder = ring.holderOf(key);
        if (!holder.role().can(action)) {
            throw new Refused("not allowed", "a " + holder.role() + " may not " + action);
        }
        return holder;
    }

    /** A ring with one key of each role on it, for a development run: {@code chief-key-0001} and so on. */
    public static KeyRing openRing() {
        KeyRing ring = new KeyRing();
        for (Role role : Role.values()) {
            ring.issue(new ApiKey(Text.lower(role.name()) + "-key-0001", "the " + role + " on duty", role));
        }
        return ring;
    }

    private static Map<Role, Set<Action>> allowed() {
        Map<Role, Set<Action>> held = new EnumMap<>(Role.class);
        held.put(Role.READER, Collections.unmodifiableSet(EnumSet.of(Action.READ)));
        held.put(
                Role.MECHANIC,
                Collections.unmodifiableSet(EnumSet.of(Action.READ, Action.RAISE_CARD, Action.POST_READING)));
        held.put(
                Role.ENGINEER,
                Collections.unmodifiableSet(
                        EnumSet.of(Action.READ, Action.RAISE_CARD, Action.POST_READING, Action.DISPOSITION)));
        held.put(Role.CHIEF, Collections.unmodifiableSet(EnumSet.allOf(Action.class)));
        return Collections.unmodifiableMap(held);
    }
}
