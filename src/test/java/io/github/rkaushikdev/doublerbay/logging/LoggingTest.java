package io.github.rkaushikdev.doublerbay.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LoggingTest {

    private static final OffsetDateTime AT = OffsetDateTime.of(2025, 3, 14, 6, 30, 0, 0, ZoneOffset.UTC);
    private static final ObjectMapper JSON = new ObjectMapper();

    /** A handler that keeps what it was given, for asserting on. */
    private static final class Collected extends AppenderBase<ILoggingEvent> {
        private final List<String> lines = new ArrayList<>();

        @Override
        protected void append(ILoggingEvent event) {
            lines.add(event.getFormattedMessage());
        }
    }

    private Collected attached;

    /**
     * Attach a handler to this package's logger, taken off again after the test. The logger
     * deliberately does not pass its lines up to the root, so a test listening on the root would
     * be testing nothing and passing.
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
            attached = null;
        }
    }

    private static Map<String, Object> parsed(String text) throws Exception {
        return JSON.readValue(text, new TypeReference<Map<String, Object>>() {});
    }

    private static int appenders(Logger logger) {
        int count = 0;
        for (Iterator<?> each = logger.iteratorForAppenders(); each.hasNext(); each.next()) {
            count++;
        }
        return count;
    }

    @Test
    void aLineSaysWhatHappened() {
        assertThat(new Line("decided").event()).isEqualTo("decided");
    }

    @Test
    void aLineWithNoEventIsRefused() {
        assertThatThrownBy(() -> new Line("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("says what happened");
    }

    @Test
    void aLineDropsTheFieldsThatCarryNothing() {
        assertThat(new Line("decided").fields()).isEqualTo(Map.of("event", "decided"));
    }

    @Test
    void aLineKeepsTheFieldsThatCarrySomething() {
        Map<String, String> held = new Line("decided")
                .withReference("DR-2025-000001")
                .withRole("engineer")
                .fields();
        assertThat(held).isEqualTo(Map.of("event", "decided", "reference", "DR-2025-000001", "role", "engineer"));
    }

    @Test
    void aLineReadsBackAsKeyValuePairs() {
        Line made = new Line("refused").withReference("DR-2025-000001").withOutcome("no");
        assertThat(made).hasToString("event=refused reference=DR-2025-000001 outcome=no");
    }

    @Test
    void theFieldsComeOutInAFixedOrder() {
        Line made = new Line("decided").withDetail("x").withReference("DR-2025-000001");
        assertThat(made.fields().keySet()).containsExactly("event", "reference", "detail");
    }

    @Test
    void aSerialIsNeverWritten() {
        assertThat(Logs.NEVER).contains("serial");
        assertThat(Logs.scrubbed(Map.of("event", "decided", "serial", "5127"))).isEqualTo(Map.of("event", "decided"));
    }

    @Test
    void aKeyIsNeverWritten() {
        assertThat(Logs.scrubbed(Map.of("event", "refused", "key", "chief-key-0001")))
                .isEqualTo(Map.of("event", "refused"));
    }

    @Test
    void aMeasurementIsNeverWritten() {
        assertThat(Logs.scrubbed(Map.of("event", "decided", "depth", "0.040"))).isEqualTo(Map.of("event", "decided"));
    }

    @Test
    void nothingAllowedIsAlsoForbidden() {
        Set<String> both = new HashSet<>(Logs.ALLOWED);
        both.retainAll(Logs.NEVER);
        assertThat(both).isEmpty();
    }

    @Test
    void anUnknownFieldIsDroppedToo() {
        assertThat(Logs.scrubbed(Map.of("event", "decided", "something", "else")))
                .isEqualTo(Map.of("event", "decided"));
    }

    @Test
    void aLineBecomesJsonWithATimestamp() throws Exception {
        Map<String, Object> written = parsed(Logs.asJson(new Line("decided").withReference("DR-2025-000001"), AT));
        assertThat((String) written.get("at")).startsWith("2025-03-14T06:30");
        assertThat(written.get("reference")).isEqualTo("DR-2025-000001");
    }

    @Test
    void theJsonCarriesNoForbiddenField() throws Exception {
        Map<String, Object> written = parsed(Logs.asJson(new Line("decided").withReference("DR-2025-000001"), AT));
        assertThat(written.keySet()).doesNotContainAnyElementsOf(Logs.NEVER);
    }

    @Test
    void theJsonGetsATimestampOfItsOwnWhenNoneIsGiven() throws Exception {
        Map<String, Object> written = parsed(Logs.asJson(new Line("decided")));
        assertThat((String) written.get("at")).isNotEmpty();
    }

    @Test
    void aRefusalLineCarriesItsReason() {
        Line made = Logs.refused("DR-2025-000001", "deferral", "a mechanic may not", "mechanic");
        assertThat(made.event()).isEqualTo("refused");
        assertThat(made.outcome()).contains("no");
        assertThat(made.toString()).contains("mechanic may not");
    }

    @Test
    void aDecisionLineCarriesWhoMadeIt() {
        Line made = Logs.decided("DR-2025-000001", "deferral", "chief engineer");
        assertThat(made.event()).isEqualTo("decided");
        assertThat(made.role()).contains("chief engineer");
        assertThat(made.outcome()).contains("yes");
    }

    @Test
    void theLoggerIsThisPackageSOwn() {
        assertThat(Logs.logger().getName()).isEqualTo(Logs.NAME);
    }

    @Test
    void configuringAttachesExactlyOneHandler() {
        Logger held = Logs.configure();
        int first = appenders(held);
        Logs.configure();
        assertThat(appenders(held)).isEqualTo(first).isEqualTo(1);
    }

    @Test
    void configuringDoesNotReachTheRootLogger() {
        Logger held = Logs.configure();
        assertThat(held.isAdditive()).isFalse();
    }

    @Test
    void theLevelCanBeSet() {
        assertThat(Logs.configure(Level.WARN).getLevel()).isEqualTo(Level.WARN);
        Logs.configure(Level.INFO);
    }

    @Test
    void aWrittenLineLandsOnTheLogger() {
        Collected collected = collected();
        Logs.write(Logs.decided("DR-2025-000001", "deferral", "chief engineer"), AT);
        assertThat(collected.lines.get(collected.lines.size() - 1)).contains("DR-2025-000001");
    }

    @Test
    void aWrittenLineIsJson() throws Exception {
        Collected collected = collected();
        Logs.write(Logs.refused("DR-2025-000001", "deferral", "no key"), AT);
        assertThat(parsed(collected.lines.get(collected.lines.size() - 1)).get("outcome"))
                .isEqualTo("no");
    }

    @Test
    void aWrittenLineCarriesNoForbiddenField() throws Exception {
        Collected collected = collected();
        Logs.write(Logs.decided("DR-2025-000001", "deferral", "chief engineer"), AT);
        assertThat(parsed(collected.lines.get(collected.lines.size() - 1)).keySet())
                .doesNotContainAnyElementsOf(Logs.NEVER);
    }

    @Test
    void twoWritesLandAsTwoLines() {
        Collected collected = collected();
        Logs.write(Logs.decided("DR-2025-000001", "deferral", "chief engineer"), AT);
        Logs.write(Logs.refused("DR-2025-000002", "deferral", "no key"), AT);
        assertThat(collected.lines).hasSize(2);
    }
}
