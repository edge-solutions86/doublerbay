package io.github.rkaushikdev.doublerbay.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Objects;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Lets a request through to a route only if its key may do what the route says.
 *
 * <p>It runs before a single argument of the route is read, so a caller who may not do this is told
 * so before anybody looks at what they sent: a refusal of the key beats a body that does not hold
 * up. The key that got through is handed to any route that asks for an {@link ApiKey}.
 */
final class Guard implements HandlerInterceptor, HandlerMethodArgumentResolver {

    /** The header every request carries its key in. */
    static final String HEADER = "x-api-key";

    private static final String HOLDER = Guard.class.getName() + ".holder";

    private final KeyRing ring;

    Guard(KeyRing ring) {
        this.ring = Objects.requireNonNull(ring, "ring");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method) {
            Requires needs = method.getMethodAnnotation(Requires.class);
            if (needs != null) {
                request.setAttribute(HOLDER, Security.check(ring, request.getHeader(HEADER), needs.value()));
            }
        }
        return true;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == ApiKey.class;
    }

    @Override
    public ApiKey resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer container,
            NativeWebRequest request,
            WebDataBinderFactory binders) {
        Object holder = request.getAttribute(HOLDER, NativeWebRequest.SCOPE_REQUEST);
        if (holder instanceof ApiKey key) {
            return key;
        }
        throw new IllegalStateException(
                parameter.getExecutable().getName() + " asks for a key and says nothing it requires");
    }
}
