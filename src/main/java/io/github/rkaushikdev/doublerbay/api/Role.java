package io.github.rkaushikdev.doublerbay.api;

import io.github.rkaushikdev.doublerbay.util.Literal;

/**
 * The four jobs on the desk, which is what a permission is keyed on rather than the person.
 *
 * <p>A reader looks at things. A mechanic raises damage cards and posts counter readings, which is
 * what happens on the line at three in the morning. An engineer assesses, defers and approves
 * repairs. The chief engineer does everything an engineer does and also grants the deferrals that
 * go past the ordinary limits. What each may do is {@link Security#ALLOWED}.
 */
public enum Role {
    READER("reader"),
    MECHANIC("mechanic"),
    ENGINEER("engineer"),
    CHIEF("chief engineer");

    private final String value;

    Role(String value) {
        this.value = value;
    }

    /** The name the job goes by on a key file and in a refusal. */
    public String value() {
        return value;
    }

    /**
     * The role with this name.
     *
     * @throws IllegalArgumentException when no role has it
     */
    public static Role fromValue(String value) {
        for (Role role : values()) {
            if (role.value.equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException(Literal.of(value) + " is not a valid Role");
    }

    /** Whether somebody in this job may do this. */
    public boolean can(Action action) {
        return Security.ALLOWED.get(this).contains(action);
    }

    @Override
    public String toString() {
        return value;
    }
}
