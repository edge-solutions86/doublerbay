package io.github.rkaushikdev.doublerbay.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * What a route lets a request through to do, checked against the key it came with.
 *
 * <p>The action is written on the route rather than checked inside it, so what a route needs is
 * visible where the route is declared, the contract can list which routes are guarded, and a write
 * that forgot to ask stands out.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Requires {

    /** What the caller is trying to do. */
    Action value();
}
