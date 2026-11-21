package io.github.rkaushikdev.doublerbay.api;

import java.util.List;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Hangs the guard on every route, so a route that says what it requires is checked before it runs. */
final class ApiWeb implements WebMvcConfigurer {

    private final Guard guard;

    ApiWeb(KeyRing keys) {
        this.guard = new Guard(keys);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(guard);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(guard);
    }
}
