package io.github.rkaushikdev.doublerbay.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;

/**
 * Keeps the body a request sent as it was sent, so a refusal of it can quote the value it refused.
 *
 * <p>The body is read once into memory and handed on unchanged. Only a request that turns out not to
 * hold up ever looks at the kept copy.
 */
@ControllerAdvice
final class BodyKept implements RequestBodyAdvice {

    private static final String KEPT = BodyKept.class.getName() + ".kept";

    /** What the current request sent, read as JSON; an empty object when it sent nothing readable. */
    static JsonNode sent() {
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        Object kept = request == null ? null : request.getAttribute(KEPT, RequestAttributes.SCOPE_REQUEST);
        if (kept instanceof byte[] bytes && bytes.length > 0) {
            try {
                return Wire.mapper().readTree(bytes);
            } catch (IOException unreadable) {
                return Invalid.emptyObject();
            }
        }
        return Invalid.emptyObject();
    }

    @Override
    public boolean supports(
            MethodParameter parameter, Type target, Class<? extends HttpMessageConverter<?>> converter) {
        return true;
    }

    @Override
    public HttpInputMessage beforeBodyRead(
            HttpInputMessage message,
            MethodParameter parameter,
            Type target,
            Class<? extends HttpMessageConverter<?>> converter)
            throws IOException {
        byte[] bytes;
        try (InputStream body = message.getBody()) {
            bytes = body.readAllBytes();
        }
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        if (request != null) {
            request.setAttribute(KEPT, bytes, RequestAttributes.SCOPE_REQUEST);
        }
        HttpHeaders headers = message.getHeaders();
        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(bytes);
            }

            @Override
            public HttpHeaders getHeaders() {
                return headers;
            }
        };
    }

    @Override
    public Object afterBodyRead(
            Object body,
            HttpInputMessage message,
            MethodParameter parameter,
            Type target,
            Class<? extends HttpMessageConverter<?>> converter) {
        return body;
    }

    @Override
    public Object handleEmptyBody(
            Object body,
            HttpInputMessage message,
            MethodParameter parameter,
            Type target,
            Class<? extends HttpMessageConverter<?>> converter) {
        return body;
    }
}
