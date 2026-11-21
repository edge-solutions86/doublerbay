package io.github.rkaushikdev.doublerbay.api;

import java.util.List;

/**
 * Every router the service serves.
 *
 * <p>The list is written out rather than found by scanning, so what the service serves is what is
 * written here and nothing a test happens to have on the class path. A router is a class of routes
 * that takes the {@link Deps} it needs in its constructor; serving one is a line here.
 */
final class Routers {

    /** The routers, in the order they are registered. */
    static final List<Class<?>> ALL = List.of(
            FindingRoutes.class,
            AssessmentRoutes.class,
            FleetRoutes.class,
            DecisionRoutes.class,
            InspectionRoutes.class,
            ServiceRoutes.class);

    private Routers() {}
}
