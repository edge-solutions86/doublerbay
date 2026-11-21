package io.github.rkaushikdev.doublerbay.api;

import io.github.rkaushikdev.doublerbay.BadReading;
import io.github.rkaushikdev.doublerbay.DoublerbayError;
import io.github.rkaushikdev.doublerbay.NotOnFile;
import io.github.rkaushikdev.doublerbay.OutOfSequence;
import io.github.rkaushikdev.doublerbay.Refused;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * What every failure comes back as, and one shape for everything that goes wrong.
 *
 * <p>Every refusal this package raises already says which kind of wrong it is, so the mapping to a
 * status code is a table rather than a decision made per route. A bad reading off a card is the
 * caller's fault and comes back as unprocessable. A lookup miss is a not found. A move the lifecycle
 * does not allow is a conflict, because the record moved under the caller rather than the request
 * being malformed.
 *
 * <p>The body is the same shape every time. A client that has to parse a different error format per
 * endpoint ends up parsing none of them, and the field that matters is the one naming the kind,
 * because that is what a caller branches on.
 *
 * @param type a stable slug a client can branch on
 * @param kind which sort of refusal it was
 * @param detail what the desk would say out loud
 * @param where the path that was asked for
 * @param retryable whether the same request could work later
 */
public record Problem(
        @Schema(description = "a stable slug a client can branch on") String type,
        @Schema(description = "which sort of refusal it was") String kind,
        @Schema(description = "what the desk would say out loud") String detail,
        @Schema(description = "the path that was asked for") String where,
        @Schema(description = "whether the same request could work later") boolean retryable) {

    /** The status each refusal comes back with, looked up in this order. */
    public static final Map<Class<? extends DoublerbayError>, Integer> STATUS_FOR = statusTable();

    /**
     * A stable slug per refusal, because a client branching on a class name is a client that breaks
     * when somebody renames a class for tidiness.
     */
    public static final Map<Class<? extends DoublerbayError>, String> SLUG_FOR = slugTable();

    /** The slug for a refusal nobody mapped. */
    public static final String UNKNOWN_SLUG = "unexpected";

    public Problem {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(where, "where");
    }

    /** A problem that says nothing about where it happened and could never work later. */
    public Problem(String type, String kind, String detail) {
        this(type, kind, detail, "", false);
    }

    /** The stable name for one refusal, which never changes once it ships. */
    public static String slugFor(DoublerbayError error) {
        for (Map.Entry<Class<? extends DoublerbayError>, String> entry : SLUG_FOR.entrySet()) {
            if (entry.getKey().isInstance(error)) {
                return entry.getValue();
            }
        }
        return UNKNOWN_SLUG;
    }

    /**
     * Whether the same request might work later without the caller changing it.
     *
     * <p>A lookup miss might: the record could be created a minute from now. A bad reading never
     * will, because the caller sent something the desk cannot read, and a client that retries it will
     * retry it forever.
     */
    public static boolean isRetryable(DoublerbayError error) {
        return error instanceof NotOnFile || error instanceof OutOfSequence;
    }

    /** The status a refusal comes back with; one nobody mapped is the server's problem. */
    public static int statusFor(DoublerbayError error) {
        for (Map.Entry<Class<? extends DoublerbayError>, Integer> entry : STATUS_FOR.entrySet()) {
            if (entry.getKey().isInstance(error)) {
                return entry.getValue();
            }
        }
        return 500;
    }

    /** The problem for one refusal, saying nothing about where it happened. */
    public static Problem of(DoublerbayError error) {
        return of(error, "");
    }

    /** The problem for one refusal on one path. */
    public static Problem of(DoublerbayError error, String where) {
        return new Problem(
                slugFor(error), error.getClass().getSimpleName(), error.getMessage(), where, isRetryable(error));
    }

    private static Map<Class<? extends DoublerbayError>, Integer> statusTable() {
        Map<Class<? extends DoublerbayError>, Integer> table = new LinkedHashMap<>();
        table.put(BadReading.class, 422);
        table.put(Refused.class, 422);
        table.put(NotOnFile.class, 404);
        table.put(OutOfSequence.class, 409);
        return Collections.unmodifiableMap(table);
    }

    private static Map<Class<? extends DoublerbayError>, String> slugTable() {
        Map<Class<? extends DoublerbayError>, String> table = new LinkedHashMap<>();
        table.put(BadReading.class, "bad-reading");
        table.put(Refused.class, "refused");
        table.put(NotOnFile.class, "not-on-file");
        table.put(OutOfSequence.class, "out-of-sequence");
        return Collections.unmodifiableMap(table);
    }
}
