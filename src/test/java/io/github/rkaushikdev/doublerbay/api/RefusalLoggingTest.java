package io.github.rkaushikdev.doublerbay.api;

import static io.github.rkaushikdev.doublerbay.support.Api.json;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rkaushikdev.doublerbay.logging.Logs;
import io.github.rkaushikdev.doublerbay.support.Api;
import io.github.rkaushikdev.doublerbay.support.Client;
import io.github.rkaushikdev.doublerbay.support.Desk;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;

class RefusalLoggingTest {

    private static final Map<String, String> MECHANIC = Map.of("x-api-key", "mechanic-key-0001");

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final class Collected extends AppenderBase<ILoggingEvent> {
        private final List<String> lines = new ArrayList<>();

        @Override
        protected void append(ILoggingEvent event) {
            lines.add(event.getFormattedMessage());
        }
    }

    private Collected attached;

    /**
     * Attach a handler to this package's logger, taken off again after the test. The logger does
     * not pass its lines up to the root, so a test listening on the root would hear nothing.
     */
    private Collected collected() {
        Logger held = Logs.configure();
        Collected handler = new Collected();
        handler.start();
        held.addAppender(handler);
        attached = handler;
        return handler;
    }

    @AfterEach
    void detach() {
        if (attached != null) {
            Logs.logger().detachAppender(attached);
            attached.stop();
        }
    }

    /** A chief engineer's client over a database holding one tail, off the shared fixtures. */
    private static Client built() {
        return Api.clientFor(Desk.stocked());
    }

    private static JsonNode last(Collected collected) {
        try {
            return JSON.readTree(collected.lines.get(collected.lines.size() - 1));
        } catch (JsonProcessingException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static Set<String> fieldsOf(JsonNode line) {
        Set<String> fields = new HashSet<>();
        line.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    private static MockHttpServletRequest requestWith(Map<String, String> headers) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        headers.forEach(request::addHeader);
        return request;
    }

    @Test
    void aCleanReadWritesNothing() {
        Collected collected = collected();
        Client api = built();
        api.get("/aircraft/5127");
        assertThat(collected.lines).isEmpty();
    }

    @Test
    void aCleanWriteWritesNothing() {
        Collected collected = collected();
        Client api = built();
        api.post(
                "/aircraft/5127/readings",
                json("taken", "2025-06-01", "usage", json("cycles", 14000, "hours", "24500.0")));
        assertThat(collected.lines).isEmpty();
    }

    @Test
    void aLookupMissIsLogged() {
        Collected collected = collected();
        Client api = built();
        api.get("/aircraft/9999");
        assertThat(last(collected).get("event").asText()).isEqualTo("refused");
        assertThat(last(collected).get("kind").asText()).isEqualTo("NotOnFile");
    }

    @Test
    void theLoggedPathSaysWhatWasAskedFor() {
        Collected collected = collected();
        Client api = built();
        api.get("/aircraft/9999");
        assertThat(last(collected).get("reference").asText()).isEqualTo("/aircraft/9999");
    }

    @Test
    void theReasonIsLogged() {
        Collected collected = collected();
        Client api = built();
        api.get("/aircraft/9999");
        assertThat(last(collected).get("detail").asText()).contains("not on the register");
    }

    @Test
    void aBadReadingIsLoggedWithItsKind() {
        Collected collected = collected();
        Client api = built();
        api.post(
                "/aircraft",
                json(
                        "serial", "5240",
                        "registration", "not-a-tail",
                        "series", "200",
                        "line_number", 1140,
                        "delivered", "2016-04-12"));
        assertThat(last(collected).get("kind").asText()).isEqualTo("BadReading");
    }

    @Test
    void aRoleThatMayNotDoSomethingIsLoggedByRole() {
        Collected collected = collected();
        Client api = built();
        api.post(
                "/aircraft",
                json(
                        "serial", "5240",
                        "registration", "N908QA",
                        "series", "200",
                        "line_number", 1140,
                        "delivered", "2016-04-12"),
                MECHANIC);
        assertThat(last(collected).get("role").asText()).isEqualTo("mechanic");
    }

    @Test
    void theKeyItselfIsNeverLogged() {
        Collected collected = collected();
        Client api = built();
        api.get("/aircraft/9999", Map.of(), MECHANIC);
        assertThat(collected.lines.get(collected.lines.size() - 1)).doesNotContain("mechanic-key-0001");
    }

    @Test
    void noForbiddenFieldReachesTheLine() {
        Collected collected = collected();
        Client api = built();
        api.get("/aircraft/9999");
        assertThat(fieldsOf(last(collected))).doesNotContainAnyElementsOf(Logs.NEVER);
    }

    @Test
    void aRequestWithNoKeyLogsNoRole() {
        Collected collected = collected();
        Client api = built();
        api.get("/aircraft/9999", Map.of(), Map.of("x-api-key", ""));
        assertThat(fieldsOf(last(collected))).doesNotContain("role");
    }

    @Test
    void aKeyThatIsNotOnTheRingLogsNoRole() {
        Collected collected = collected();
        Client api = built();
        api.get("/aircraft/9999", Map.of(), Map.of("x-api-key", "not-a-real-key"));
        assertThat(fieldsOf(last(collected))).doesNotContain("role");
    }

    @Test
    void twoRefusalsWriteTwoLines() {
        Collected collected = collected();
        Client api = built();
        api.get("/aircraft/9999");
        api.get("/aircraft/8888");
        assertThat(collected.lines).hasSize(2);
    }

    @Test
    void theLoggerIsTheOneThisPackageOwns() {
        collected();
        Client api = built();
        api.get("/aircraft/9999");
        assertThat(((Logger) LoggerFactory.getLogger(Logs.NAME))
                        .iteratorForAppenders()
                        .hasNext())
                .isTrue();
    }

    @Test
    void theRoleHelperReadsAKnownKey() {
        Client api = built();
        KeyRing ring = api.app().getBean(KeyRing.class);
        assertThat(Refusals.roleOn(ring, requestWith(Map.of("x-api-key", "chief-key-0001"))))
                .contains("chief engineer");
    }

    @Test
    void theRoleHelperSaysNothingForAnUnknownKey() {
        Client api = built();
        KeyRing ring = api.app().getBean(KeyRing.class);
        assertThat(Refusals.roleOn(ring, requestWith(Map.of("x-api-key", "nope"))))
                .isEmpty();
    }

    @Test
    void theRoleHelperSaysNothingWhenNoKeyIsSent() {
        Client api = built();
        KeyRing ring = api.app().getBean(KeyRing.class);
        assertThat(Refusals.roleOn(ring, requestWith(Map.of()))).isEmpty();
    }
}
