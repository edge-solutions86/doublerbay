package io.github.rkaushikdev.doublerbay.fleet;

import io.github.rkaushikdev.doublerbay.util.Literal;

/**
 * What the aeroplane was in when the damage was found.
 *
 * <p>Where a finding comes from changes what can be believed about it. A walkaround is a man on the
 * ground looking up at a fuselage in the rain, and what he can see is a dent big enough to throw a
 * shadow. A hangar check has the aeroplane on stands with the fairings off and a light held against
 * the skin at an angle, and that will find a scratch the walkaround would never have shown.
 *
 * <p>That matters twice. It decides whether a finding is a new damage or one that has been there
 * for years and was only now looked at properly, and it decides what a repeat inspection can be
 * written against, because an inspection is only worth setting if the check it is set on could
 * actually find the thing.
 */
public enum CheckKind {
    WALKAROUND("walkaround", 0),
    TRANSIT("transit", 1),
    LINE("line check", 2),
    HANGAR("hangar check", 3),
    HEAVY("heavy check", 4),
    NDT_SWEEP("ndt sweep", 4);

    private final String value;
    private final int access;

    CheckKind(String value, int access) {
        this.value = value;
        this.access = access;
    }

    /** The words the check is booked under. */
    public String value() {
        return value;
    }

    /**
     * The check booked under these words.
     *
     * @throws IllegalArgumentException when no check is
     */
    public static CheckKind fromValue(String value) {
        for (CheckKind kind : values()) {
            if (kind.value.equals(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException(Literal.of(value) + " is not a valid CheckKind");
    }

    /** How much of the aeroplane is open, nought to four. */
    public int access() {
        return access;
    }

    /** Whether the check is planned, which is everything but the walkaround. */
    public boolean isScheduled() {
        return this != WALKAROUND;
    }

    /** Whether a scratch or a hairline crack would be seen at this level. */
    public boolean canFindFineDamage() {
        return access >= HANGAR.access;
    }

    /** Whether this check opens more of the aeroplane than another. */
    public boolean opensMoreThan(CheckKind other) {
        return access > other.access;
    }

    @Override
    public String toString() {
        return value;
    }
}
