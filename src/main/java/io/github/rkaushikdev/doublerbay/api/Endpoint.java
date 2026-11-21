package io.github.rkaushikdev.doublerbay.api;

import java.util.List;
import java.util.Objects;

/**
 * One route, described the way an audit asks about it.
 *
 * @param path the path it answers on, with its parameters in braces
 * @param method the HTTP method
 * @param name the handler's name
 * @param summary the first line written on it, or nothing
 * @param guarded whether anything on it asks for a key
 * @param tags what the contract groups it under
 */
public record Endpoint(String path, String method, String name, String summary, boolean guarded, List<String> tags) {

    public Endpoint {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(summary, "summary");
        tags = List.copyOf(tags);
    }

    /** Whether it changes anything. */
    public boolean writes() {
        return Contract.WRITING.contains(method);
    }

    /** Whether somebody wrote down what it does. */
    public boolean isDocumented() {
        return !summary.isBlank();
    }

    /** A write is guarded unless it is on the list of ones that are not. */
    public boolean needsAGuard() {
        return writes() && !Contract.UNGUARDED.containsKey(path);
    }

    /** {@code GET /health (open)} or {@code POST /findings (guarded)}. */
    @Override
    public String toString() {
        return method + " " + path + " (" + (guarded ? "guarded" : "open") + ")";
    }
}
