package io.github.rkaushikdev.doublerbay.api;

import io.github.rkaushikdev.doublerbay.disposition.OrderId;
import io.github.rkaushikdev.doublerbay.ids.FindingId;
import io.github.rkaushikdev.doublerbay.ids.RepairId;
import io.github.rkaushikdev.doublerbay.ids.Serial;
import io.github.rkaushikdev.doublerbay.store.UnitOfWork;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * What every route needs from the request, in one place.
 *
 * <p>Every route reached into the same few helpers: the dependencies, the reference parsers, the
 * day a request is answered as of, the serial checked against the register. Kept beside one of the
 * routes, they made every other route depend on that one, so touching the findings routes could
 * break routes that have nothing to do with findings.
 *
 * <p>Nothing here knows about a route. Everything here is the small shared plumbing a route reaches
 * for before it does anything of its own, which is why it can sit underneath all of them.
 */
public final class Dependencies {

    private Dependencies() {}

    /** The application's dependencies, off the application the request came in to. */
    public static Deps deps(HttpServletRequest request) {
        return RequestContextUtils.findWebApplicationContext(request).getBean(Deps.class);
    }

    /** A damage report number off the path, refused by name if it is not one. */
    public static FindingId referenceFrom(String text) {
        return FindingId.parse(text);
    }

    /** A repair number off the path. */
    public static RepairId repairFrom(String text) {
        return RepairId.parse(text);
    }

    /** An engineering order number off the path. */
    public static OrderId orderFrom(String text) {
        return OrderId.parse(text);
    }

    /** A tail serial off the path. */
    public static Serial serialFrom(String text) {
        return new Serial(text);
    }

    /** The day a request is answered as of, taking the service's own if none was given. */
    public static LocalDate dayOr(Deps deps, Optional<LocalDate> on) {
        return on.orElseGet(deps::on);
    }

    /**
     * The serial off a path, checked against the register before anything else.
     *
     * <p>The lookup is not decoration. Without it a mistyped serial comes back as an empty list,
     * which reads as an aeroplane with nothing wrong with it.
     */
    public static Serial tailOn(UnitOfWork unit, String serial) {
        Serial held = serialFrom(serial);
        unit.aircraft().get(held);
        return held;
    }
}
