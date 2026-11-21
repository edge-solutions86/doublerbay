package io.github.rkaushikdev.doublerbay.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rkaushikdev.doublerbay.Doublerbay;
import io.github.rkaushikdev.doublerbay.util.JsonText;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.OpenAPI;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springdoc.api.AbstractOpenApiResource;
import org.springdoc.webmvc.api.OpenApiWebMvcResource;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * What the service promises, listed so it can be checked rather than assumed.
 *
 * <p>An HTTP surface grows a route at a time and nobody ever reads all of it. The questions that
 * matter are the ones nobody asks until an audit: which routes write, which of those are guarded,
 * and which of them nobody wrote down.
 *
 * <p>So the surface can list itself. Every route, its method, whether it changes anything, whether
 * it is guarded, and whether it carries a summary somebody could read. A test then asserts the
 * properties that have to hold, and a route added next year without a guard fails that test rather
 * than shipping.
 *
 * <p>The OpenAPI document comes out of the same place. Writing it to a file is what lets a client
 * generate against a version of the contract rather than against whatever the service happened to
 * be running when they pointed a tool at it.
 */
public final class Contract {

    /** The methods that change something. Everything else is a read. */
    public static final Set<String> WRITING = Set.of("POST", "PUT", "PATCH", "DELETE");

    /** Routes the service exposes without a key, and why each one is on the list. */
    public static final Map<String, String> UNGUARDED = unguarded();

    private static final String OURS = Doublerbay.class.getPackageName() + ".";

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final TypeReference<Map<String, Object>> WRITTEN = new TypeReference<>() {};

    private Contract() {}

    /** Every route the application serves, in path order. */
    public static List<Endpoint> endpoints(ApplicationContext app) {
        RequestMappingHandlerMapping mapping =
                app.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        List<Endpoint> found = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry :
                mapping.getHandlerMethods().entrySet()) {
            HandlerMethod handler = entry.getValue();
            if (!handler.getBeanType().getName().startsWith(OURS)) {
                continue;
            }
            List<String> methods = entry.getKey().getMethodsCondition().getMethods().stream()
                    .map(RequestMethod::name)
                    .sorted()
                    .toList();
            for (String path : entry.getKey().getPatternValues()) {
                for (String method : methods) {
                    found.add(new Endpoint(
                            path,
                            method,
                            Wire.snake(handler.getMethod().getName()),
                            summaryOf(handler),
                            handler.hasMethodAnnotation(Requires.class),
                            tagsOf(handler)));
                }
            }
        }
        found.sort(Comparator.comparing(Endpoint::path).thenComparing(Endpoint::method));
        return List.copyOf(found);
    }

    /** Every route that changes something. */
    public static List<Endpoint> writes(ApplicationContext app) {
        return endpoints(app).stream().filter(Endpoint::writes).toList();
    }

    /** Every write nothing is guarding, which should always be empty. */
    public static List<Endpoint> unguardedWrites(ApplicationContext app) {
        return endpoints(app).stream()
                .filter(one -> one.needsAGuard() && !one.guarded())
                .toList();
    }

    /** Every route with nothing written on it. */
    public static List<Endpoint> undocumented(ApplicationContext app) {
        return endpoints(app).stream().filter(one -> !one.isDocumented()).toList();
    }

    /** Every tag the surface groups its routes under, in order. */
    public static List<String> tags(ApplicationContext app) {
        Set<String> held = new TreeSet<>();
        for (Endpoint one : endpoints(app)) {
            held.addAll(one.tags());
        }
        return List.copyOf(held);
    }

    /**
     * The OpenAPI contract, formatted so a diff between versions is readable: keys sorted, two spaces
     * of indent, and every character outside ASCII escaped.
     *
     * <p>Where the service happens to be served from is left out, so the same build always writes
     * the same document.
     */
    public static String asDocument(ApplicationContext app) {
        Map<String, Object> document = new LinkedHashMap<>(document(app));
        document.remove("servers");
        StringBuilder written = new StringBuilder();
        dump(document, 0, written);
        return written.toString();
    }

    private static Map<String, Object> document(ApplicationContext app) {
        AbstractOpenApiResource resource = app.getBean(OpenApiWebMvcResource.class);
        try {
            Method build = AbstractOpenApiResource.class.getDeclaredMethod("getOpenApi", Locale.class);
            build.setAccessible(true);
            Method write = AbstractOpenApiResource.class.getDeclaredMethod("writeJsonValue", OpenAPI.class);
            write.setAccessible(true);
            Object contract = build.invoke(resource, Locale.ROOT);
            return JSON.readValue((byte[]) write.invoke(resource, contract), WRITTEN);
        } catch (NoSuchMethodException | IllegalAccessException refused) {
            throw new IllegalStateException("the contract could not be built", refused);
        } catch (InvocationTargetException failed) {
            throw new IllegalStateException("the contract could not be built", failed.getCause());
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static String summaryOf(HandlerMethod handler) {
        Operation operation = handler.getMethodAnnotation(Operation.class);
        if (operation == null) {
            return "";
        }
        return operation.summary().strip().lines().findFirst().orElse("");
    }

    private static List<String> tagsOf(HandlerMethod handler) {
        List<String> tags = new ArrayList<>();
        for (Tag tag : AnnotatedElementUtils.findMergedRepeatableAnnotations(handler.getBeanType(), Tag.class)) {
            tags.add(tag.name());
        }
        for (Tag tag : AnnotatedElementUtils.findMergedRepeatableAnnotations(handler.getMethod(), Tag.class)) {
            tags.add(tag.name());
        }
        return tags;
    }

    private static void dump(Object value, int depth, StringBuilder written) {
        switch (value) {
            case null -> written.append("null");
            case Map<?, ?> members -> dumpObject(members, depth, written);
            case List<?> items -> dumpArray(items, depth, written);
            case String text -> written.append(JsonText.string(text));
            case Boolean flag -> written.append(flag ? "true" : "false");
            case Double fraction -> written.append(Printed.fraction(fraction));
            case Float fraction -> written.append(Printed.fraction(fraction));
            case BigDecimal fraction -> written.append(Printed.fraction(fraction.doubleValue()));
            default -> written.append(value);
        }
    }

    private static void dumpObject(Map<?, ?> members, int depth, StringBuilder written) {
        List<String> names = new ArrayList<>();
        members.keySet().forEach(name -> names.add(String.valueOf(name)));
        Collections.sort(names);
        if (names.isEmpty()) {
            written.append("{}");
            return;
        }
        written.append("{\n");
        Iterator<String> each = names.iterator();
        while (each.hasNext()) {
            String name = each.next();
            indent(depth + 1, written);
            written.append(JsonText.string(name)).append(": ");
            dump(members.get(name), depth + 1, written);
            written.append(each.hasNext() ? ",\n" : "\n");
        }
        indent(depth, written);
        written.append('}');
    }

    private static void dumpArray(List<?> items, int depth, StringBuilder written) {
        if (items.isEmpty()) {
            written.append("[]");
            return;
        }
        written.append("[\n");
        for (int index = 0; index < items.size(); index++) {
            indent(depth + 1, written);
            dump(items.get(index), depth + 1, written);
            written.append(index < items.size() - 1 ? ",\n" : "\n");
        }
        indent(depth, written);
        written.append(']');
    }

    private static void indent(int depth, StringBuilder written) {
        written.append("  ".repeat(depth));
    }

    private static Map<String, String> unguarded() {
        Map<String, String> held = new LinkedHashMap<>();
        held.put("/health", "a load balancer has no key");
        held.put("/openapi.json", "the contract is public");
        held.put("/openapi.json.yaml", "the contract is public");
        held.put("/openapi.json/swagger-config", "the contract is public");
        held.put("/docs", "the contract is public");
        held.put("/swagger-ui/index.html", "the contract is public");
        held.put("/swagger-ui/oauth2-redirect.html", "the contract is public");
        held.put("/history/registers", "a fixed list with no fleet data on it");
        return Collections.unmodifiableMap(held);
    }
}
