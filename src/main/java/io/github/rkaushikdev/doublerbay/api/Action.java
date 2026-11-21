package io.github.rkaushikdev.doublerbay.api;

import io.github.rkaushikdev.doublerbay.util.Literal;

/** What somebody is trying to do, which is what a key is checked against. */
public enum Action {
    READ("read"),
    RAISE_CARD("raise a damage card"),
    POST_READING("post a counter reading"),
    DISPOSITION("disposition a card"),
    DEFER("grant a deferral"),
    AMEND_FLEET("amend the register");

    private final String value;

    Action(String value) {
        this.value = value;
    }

    /** The words a refusal uses for it. */
    public String value() {
        return value;
    }

    /**
     * The action these words name.
     *
     * @throws IllegalArgumentException when no action is
     */
    public static Action fromValue(String value) {
        for (Action action : values()) {
            if (action.value.equals(value)) {
                return action;
            }
        }
        throw new IllegalArgumentException(Literal.of(value) + " is not a valid Action");
    }

    @Override
    public String toString() {
        return value;
    }
}
