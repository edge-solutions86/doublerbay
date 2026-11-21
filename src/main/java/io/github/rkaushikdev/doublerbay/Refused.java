package io.github.rkaushikdev.doublerbay;

import java.util.Optional;

/**
 * A disposition could not be reached, with the reason it could not.
 *
 * <p>The message reads as the reason alone, or as the reason and the detail separated by a colon
 * when there is a detail.
 */
public class Refused extends DoublerbayError {

    private final String reason;
    private final String detail;

    /** A refusal that needs no more than its reason. */
    public Refused(String reason) {
        this(reason, null);
    }

    /** A refusal with a detail saying which record or which rule it was about. */
    public Refused(String reason, String detail) {
        super(detail == null ? reason : reason + ": " + detail);
        this.reason = reason;
        this.detail = detail;
    }

    /** Why the disposition could not be reached. */
    public String reason() {
        return reason;
    }

    /** What the refusal was about, when it said. */
    public Optional<String> detail() {
        return Optional.ofNullable(detail);
    }
}
