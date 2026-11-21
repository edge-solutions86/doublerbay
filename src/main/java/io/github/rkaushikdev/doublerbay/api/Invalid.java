package io.github.rkaushikdev.doublerbay.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;

/**
 * A request that does not hold up before any of the desk's own rules are asked about it.
 *
 * <p>A field left out, a number below what the field allows, a side that is not one of the three:
 * none of those is a refusal the desk makes, they are a request the service could not read. They
 * come back as an unprocessable request listing every problem found, each with where it was ({@code
 * ["body", "line_number"]}), a stable type, a sentence, and the value that was there.
 */
public final class Invalid extends IllegalArgumentException {

    private static final ValidatorFactory FACTORY = Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory();

    private static final ObjectMapper TREES = new ObjectMapper();

    private final transient List<Map<String, Object>> errors;

    /** A request refused for these problems, each already in its wire shape. */
    public Invalid(List<Map<String, Object>> errors) {
        super(summary(errors));
        this.errors = List.copyOf(errors);
    }

    /** Every problem found, in the order they were found. */
    public List<Map<String, Object>> errors() {
        return errors;
    }

    /** What goes back to the caller. */
    public Map<String, Object> body() {
        return Map.of("detail", errors);
    }

    /** One problem: what kind, where, the sentence, and the value that was there. */
    public static Map<String, Object> error(String type, List<Object> where, String says, Object input) {
        return error(type, where, says, input, Map.of());
    }

    /** One problem, with what the rule it broke was set to. */
    public static Map<String, Object> error(
            String type, List<Object> where, String says, Object input, Map<String, Object> context) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("type", type);
        error.put("loc", List.copyOf(where));
        error.put("msg", says);
        error.put("input", input == null ? nothing() : input);
        if (!context.isEmpty()) {
            error.put("ctx", context);
        }
        return Collections.unmodifiableMap(error);
    }

    /**
     * A request shape built in code, held to the same rules it is held to off the wire.
     *
     * @throws Invalid naming every rule it breaks
     */
    public static <T> T validated(T shape) {
        Set<ConstraintViolation<T>> broken = validator().validate(shape);
        if (!broken.isEmpty()) {
            throw fromViolations(broken, List.of(), Wire.mapper().valueToTree(shape));
        }
        return shape;
    }

    static Validator validator() {
        return FACTORY.getValidator();
    }

    /** The problems Bean Validation found, placed under {@code prefix} in a body that read as {@code sent}. */
    static Invalid fromViolations(
            Collection<? extends ConstraintViolation<?>> broken, List<Object> prefix, JsonNode sent) {
        List<Map<String, Object>> found = new ArrayList<>();
        for (ConstraintViolation<?> violation : broken) {
            List<Object> inside = steps(violation.getPropertyPath());
            List<Object> where = new ArrayList<>(prefix);
            where.addAll(inside);
            found.add(fromViolation(violation, where, inside, sent));
        }
        found.sort((one, other) -> String.valueOf(one.get("loc")).compareTo(String.valueOf(other.get("loc"))));
        return new Invalid(found);
    }

    private static Map<String, Object> fromViolation(
            ConstraintViolation<?> violation, List<Object> where, List<Object> inside, JsonNode sent) {
        Annotation rule = violation.getConstraintDescriptor().getAnnotation();
        Object value = violation.getInvalidValue();
        return switch (rule) {
            case NotNull ignored ->
                error("missing", where, "Field required", at(sent, inside.subList(0, Math.max(0, inside.size() - 1))));
            case Min least ->
                error(
                        "greater_than_equal",
                        where,
                        "Input should be greater than or equal to " + least.value(),
                        value,
                        Map.of("ge", least.value()));
            case Pattern pattern ->
                error(
                        "string_pattern_mismatch",
                        where,
                        "String should match pattern '" + pattern.regexp() + "'",
                        value,
                        Map.of("pattern", pattern.regexp()));
            default -> error("value_error", where, violation.getMessage(), value);
        };
    }

    /** A JSON null, for a value that was not there. */
    static JsonNode nothing() {
        return TREES.nullNode();
    }

    /** An empty JSON object, for a body that could not be read at all. */
    static JsonNode emptyObject() {
        return TREES.createObjectNode();
    }

    /** Text as a JSON value. */
    static JsonNode text(String text) {
        return TREES.getNodeFactory().textNode(text);
    }

    /** The value at a path in what was sent, or nothing if the path leads nowhere. */
    static JsonNode at(JsonNode sent, List<Object> where) {
        JsonNode here = sent == null ? nothing() : sent;
        for (Object step : where) {
            JsonNode next = step instanceof Integer index ? here.get(index) : here.get(String.valueOf(step));
            if (next == null) {
                return nothing();
            }
            here = next;
        }
        return here;
    }

    private static List<Object> steps(Path path) {
        List<Object> steps = new ArrayList<>();
        for (Path.Node step : path) {
            if (step.getIndex() != null) {
                steps.add(step.getIndex());
            }
            if (step.getName() != null && !step.getName().startsWith("<")) {
                steps.add(Wire.snake(step.getName()));
            }
        }
        return steps;
    }

    private static String summary(List<Map<String, Object>> errors) {
        StringBuilder written = new StringBuilder();
        written.append(errors.size()).append(errors.size() == 1 ? " validation error" : " validation errors");
        for (Map<String, Object> error : errors) {
            List<String> where = new ArrayList<>();
            for (Object step : (List<?>) error.get("loc")) {
                where.add(String.valueOf(step));
            }
            written.append('\n').append(String.join(".", where)).append("\n  ").append(error.get("msg"));
        }
        return written.toString();
    }
}
