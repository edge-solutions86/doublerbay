package io.github.rkaushikdev.doublerbay.api;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import io.github.rkaushikdev.doublerbay.DoublerbayError;
import io.github.rkaushikdev.doublerbay.logging.Logs;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.util.UriUtils;

/**
 * Turns everything that goes wrong in a request into the answer the caller gets back.
 *
 * <p>A refusal the desk makes comes back as a {@link Problem} with the status its kind maps to, and
 * is written to the log with the path, the kind, the reason and the role it was asked under. A
 * request the service could not read before the desk was asked anything comes back as {@link
 * Invalid}'s list of problems. Neither is logged as anything worse: both are somebody being told no.
 */
@RestControllerAdvice
final class Refusals {

    private final KeyRing ring;

    Refusals(KeyRing ring) {
        this.ring = Objects.requireNonNull(ring, "ring");
    }

    /**
     * Which role a refused request was made under, if the key is one this ring knows.
     *
     * <p>The key itself never reaches the log. The role does, because the question an audit asks is
     * whether a mechanic has been trying to grant deferrals, not which of four mechanics it was.
     */
    static Optional<String> roleOn(KeyRing ring, HttpServletRequest request) {
        String sent = request.getHeader(Guard.HEADER);
        if (ring == null || sent == null || sent.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ring.holderOf(sent).role().toString());
        } catch (DoublerbayError unknown) {
            return Optional.empty();
        }
    }

    /** The path a request asked for, as the caller wrote it before it was percent encoded. */
    static String pathOf(HttpServletRequest request) {
        String asked =
                request.getRequestURI().substring(request.getContextPath().length());
        return UriUtils.decode(asked, StandardCharsets.UTF_8);
    }

    @ExceptionHandler(DoublerbayError.class)
    ResponseEntity<Problem> refused(DoublerbayError error, HttpServletRequest request) {
        String path = pathOf(request);
        Logs.write(Logs.refused(
                path,
                error.getClass().getSimpleName(),
                error.getMessage(),
                roleOn(ring, request).orElse(null)));
        return ResponseEntity.status(Problem.statusFor(error)).body(Problem.of(error, path));
    }

    @ExceptionHandler(Invalid.class)
    ResponseEntity<Map<String, Object>> invalid(Invalid invalid) {
        return ResponseEntity.unprocessableEntity().body(invalid.body());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> bodyBroke(MethodArgumentNotValidException failure) {
        List<ConstraintViolation<?>> broken = new ArrayList<>();
        for (ObjectError error : failure.getBindingResult().getAllErrors()) {
            if (error.contains(ConstraintViolation.class)) {
                broken.add(error.unwrap(ConstraintViolation.class));
            }
        }
        return invalid(Invalid.fromViolations(broken, List.of("body"), BodyKept.sent()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException failure) {
        return invalid(new Invalid(List.of(unreadableProblem(failure))));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<Map<String, Object>> notJson() {
        return invalid(new Invalid(List.of(Invalid.error(
                "model_attributes_type",
                List.of("body"),
                "Input should be a valid dictionary or object to extract fields from",
                null))));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Map<String, Object>> parameterUnreadable(MethodArgumentTypeMismatchException failure) {
        String from = failure.getParameter().hasParameterAnnotation(PathVariable.class) ? "path" : "query";
        Object value = failure.getValue();
        JsonNode sent = value == null ? Invalid.nothing() : Invalid.text(String.valueOf(value));
        return invalid(new Invalid(List.of(typeProblem(
                Objects.requireNonNullElse(failure.getRequiredType(), String.class),
                List.of(from, failure.getName()),
                sent))));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<Map<String, Object>> parameterMissing(MissingServletRequestParameterException failure) {
        return invalid(new Invalid(List.of(
                Invalid.error("missing", List.of("query", failure.getParameterName()), "Field required", null))));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Map<String, Object>> noSuchRoute() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "Not Found"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Map<String, Object>> wrongMethod(HttpRequestMethodNotSupportedException failure) {
        ResponseEntity.BodyBuilder answer = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        Collection<String> allowed =
                failure.getSupportedMethods() == null ? List.of() : List.of(failure.getSupportedMethods());
        if (!allowed.isEmpty()) {
            answer.header(HttpHeaders.ALLOW, String.join(", ", allowed));
        }
        return answer.body(Map.of("detail", "Method Not Allowed"));
    }

    private static Map<String, Object> unreadableProblem(HttpMessageNotReadableException failure) {
        Throwable cause = failure.getCause();
        if (cause instanceof JsonParseException broken) {
            long offset = 0;
            if (broken.getLocation() != null) {
                long at = broken.getLocation().getCharOffset();
                offset = (at >= 0 ? at : broken.getLocation().getByteOffset()) - 1;
            }
            return Invalid.error(
                    "json_invalid",
                    List.of("body", Math.max(0, offset)),
                    "JSON decode error",
                    Invalid.emptyObject(),
                    Map.of("error", Objects.requireNonNullElse(broken.getOriginalMessage(), "")));
        }
        if (cause instanceof MismatchedInputException mismatched) {
            List<Object> inside = steps(mismatched);
            List<Object> where = new ArrayList<>(List.of("body"));
            where.addAll(inside);
            Class<?> target = Objects.requireNonNullElse(mismatched.getTargetType(), Object.class);
            return typeProblem(target, where, Invalid.at(BodyKept.sent(), inside));
        }
        return Invalid.error("missing", List.of("body"), "Field required", null);
    }

    private static List<Object> steps(JsonMappingException failure) {
        List<Object> steps = new ArrayList<>();
        for (JsonMappingException.Reference step : failure.getPath()) {
            steps.add(step.getFieldName() == null ? Integer.valueOf(step.getIndex()) : step.getFieldName());
        }
        return steps;
    }

    /** The problem with a value that is not of the type its field takes. */
    private static Map<String, Object> typeProblem(Class<?> target, List<Object> where, JsonNode sent) {
        boolean text = sent.isTextual();
        if (CharSequence.class.isAssignableFrom(target)) {
            return Invalid.error("string_type", where, "Input should be a valid string", sent);
        }
        if (target == Integer.class || target == int.class || target == Long.class || target == long.class) {
            if (text) {
                return Invalid.error(
                        "int_parsing",
                        where,
                        "Input should be a valid integer, unable to parse string as an integer",
                        sent);
            }
            if (sent.isFloatingPointNumber()) {
                return Invalid.error(
                        "int_from_float",
                        where,
                        "Input should be a valid integer, got a number with a fractional part",
                        sent);
            }
            return Invalid.error("int_type", where, "Input should be a valid integer", sent);
        }
        if (target == Boolean.class || target == boolean.class) {
            if (text) {
                return Invalid.error(
                        "bool_parsing", where, "Input should be a valid boolean, unable to interpret input", sent);
            }
            return Invalid.error("bool_type", where, "Input should be a valid boolean", sent);
        }
        if (target == LocalDate.class) {
            if (text) {
                String reason = sent.textValue().length() < 10 ? "input is too short" : "invalid date format";
                return Invalid.error(
                        "date_from_datetime_parsing",
                        where,
                        "Input should be a valid date or datetime, " + reason,
                        sent);
            }
            return Invalid.error("date_type", where, "Input should be a valid date", sent);
        }
        if (Map.class.isAssignableFrom(target)) {
            return Invalid.error("dict_type", where, "Input should be a valid dictionary", sent);
        }
        if (Collection.class.isAssignableFrom(target)) {
            return Invalid.error("list_type", where, "Input should be a valid list", sent);
        }
        return Invalid.error(
                "model_attributes_type",
                where,
                "Input should be a valid dictionary or object to extract fields from",
                sent);
    }
}
