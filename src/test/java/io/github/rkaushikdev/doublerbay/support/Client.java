package io.github.rkaushikdev.doublerbay.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * A small client over one running application: send a request, get the status and the body back.
 *
 * <p>It carries a set of headers onto every request, the key among them, and a request can add or
 * replace one of its own.
 */
public final class Client {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ConfigurableApplicationContext app;
    private final MockMvc mvc;
    private final Map<String, String> headers;

    private Client(ConfigurableApplicationContext app, MockMvc mvc, Map<String, String> headers) {
        this.app = app;
        this.mvc = mvc;
        this.headers = Map.copyOf(headers);
    }

    /** A client sending no key, against one application. */
    public static Client over(ConfigurableApplicationContext app) {
        return new Client(
                app,
                MockMvcBuilders.webAppContextSetup((WebApplicationContext) app).build(),
                Map.of());
    }

    /** The same client, presenting {@code key} on every request, or no key when it is null. */
    public Client as(String key) {
        Map<String, String> carried = new LinkedHashMap<>(headers);
        if (key == null) {
            carried.remove(Desk.KEY_HEADER);
        } else {
            carried.put(Desk.KEY_HEADER, key);
        }
        return new Client(app, mvc, carried);
    }

    /** The application this client talks to. */
    public ConfigurableApplicationContext app() {
        return app;
    }

    /** The headers carried onto every request. */
    public Map<String, String> headers() {
        return headers;
    }

    public Answer get(String path) {
        return get(path, Map.of(), Map.of());
    }

    /** A get with query parameters. */
    public Answer get(String path, Map<String, ?> params) {
        return get(path, params, Map.of());
    }

    /** A get with query parameters and headers of its own. */
    public Answer get(String path, Map<String, ?> params, Map<String, String> extra) {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(HttpMethod.GET, path);
        params.forEach((name, value) -> request.param(name, String.valueOf(value)));
        return send(request, extra);
    }

    /** A post with a JSON body. */
    public Answer post(String path, Object body) {
        return post(path, body, Map.of());
    }

    /** A post with a JSON body and headers of its own. */
    public Answer post(String path, Object body, Map<String, String> extra) {
        return send(
                MockMvcRequestBuilders.request(HttpMethod.POST, path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(write(body)),
                extra);
    }

    /** Send a request built by hand, with this client's headers and any of its own. */
    public Answer send(MockHttpServletRequestBuilder request, Map<String, String> extra) {
        Map<String, String> sent = new LinkedHashMap<>(headers);
        sent.putAll(extra);
        sent.forEach(request::header);
        try {
            MvcResult result = mvc.perform(request).andReturn();
            return new Answer(
                    result.getResponse().getStatus(), result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        } catch (Exception failure) {
            throw new IllegalStateException("the request could not be sent", failure);
        }
    }

    private static String write(Object body) {
        try {
            return JSON.writeValueAsString(body);
        } catch (JsonProcessingException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /**
     * What came back.
     *
     * @param status the HTTP status
     * @param text the body as text
     */
    public record Answer(int status, String text) {

        public Answer {
            Objects.requireNonNull(text, "text");
        }

        /** The body, read as JSON. */
        public JsonNode json() {
            try {
                return JSON.readTree(text);
            } catch (JsonProcessingException failure) {
                throw new UncheckedIOException(failure);
            }
        }
    }
}
