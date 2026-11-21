package io.github.rkaushikdev.doublerbay.api;

import static io.github.rkaushikdev.doublerbay.support.Api.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.support.Api;
import io.github.rkaushikdev.doublerbay.support.Client;
import io.github.rkaushikdev.doublerbay.support.Desk;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportRoutesTest {

    private static final String DAY = "2025-03-14";

    /** A database and a chief engineer's client against it. */
    private record Built(Database database, Client api) {}

    private static Built built() {
        Database database = Desk.newDatabase();
        return new Built(database, Api.clientFor(database));
    }

    /** Put a tail on the register over the wire, with two readings a year apart. */
    private static void enrol(Client api) {
        api.post(
                "/aircraft",
                json(
                        "serial", "5127",
                        "registration", "N412VD",
                        "series", "200",
                        "line_number", 1140,
                        "delivered", "2016-04-12"));
        api.post(
                "/aircraft/5127/readings",
                json("taken", "2024-03-14", "usage", json("cycles", 12000, "hours", "21000.0")));
        api.post(
                "/aircraft/5127/readings",
                json("taken", "2025-03-01", "usage", json("cycles", 13800, "hours", "24200.0")));
    }

    private static Built stocked() {
        Built built = built();
        enrol(built.api());
        return built;
    }

    /** One card on the tail, raised and moved to assessed on the pinned day. */
    private static String assessed(Database database) {
        return assessed(database, 1);
    }

    private static String assessed(Database database, int sequence) {
        return Desk.assessedCard(database, sequence, "5127").toString();
    }

    private static Client.Answer defer(Client api, String reference, Object... rest) {
        Map<String, Object> sent = json("against", reference, "granted", DAY, "by", "j.mercer");
        sent.putAll(json(rest));
        return api.post("/deferrals", sent);
    }

    @Test
    void aFleetWithNothingDueReportsNothing() {
        Built built = stocked();
        JsonNode body = built.api().get("/reports/due", Map.of("on", DAY)).json();
        assertThat(body.get("overrun").asInt()).isZero();
        assertThat(body.get("tails").get(0).get("items")).hasToString("[]");
    }

    @Test
    void aDeferralLandsOnTheFleetDueReport() {
        Built built = stocked();
        defer(built.api(), assessed(built.database()), "limit", "500 FC");
        JsonNode body = built.api().get("/reports/due", Map.of("on", DAY)).json();
        JsonNode first = body.get("tails").get(0).get("items").get(0);
        assertThat(first.get("against").asText()).isEqualTo("DR-2025-000001");
        assertThat(first.get("clock").asText()).isEqualTo("flight cycles");
    }

    @Test
    void theDueReportSaysWhetherTheRateIsMeasured() {
        Built built = stocked();
        defer(built.api(), assessed(built.database()));
        assertThat(built.api()
                        .get("/reports/due", Map.of("on", DAY))
                        .json()
                        .get("tails")
                        .get(0)
                        .get("measured_rate")
                        .asBoolean())
                .isTrue();
    }

    @Test
    void oneTailCanBeAskedFor() {
        Built built = stocked();
        defer(built.api(), assessed(built.database()), "limit", "500 FC");
        JsonNode body = built.api().get("/reports/due/5127", Map.of("on", DAY)).json();
        assertThat(body.get("serial").asText()).isEqualTo("5127");
        assertThat(body.get("items")).hasSize(1);
    }

    @Test
    void aTailThatIsNotOnTheRegisterComesBackAsNotFound() {
        Built built = stocked();
        assertThat(built.api().get("/reports/due/9999", Map.of("on", DAY)).status())
                .isEqualTo(404);
    }

    @Test
    void aHorizonCutsTheReportDown() {
        Built built = stocked();
        defer(built.api(), assessed(built.database()), "limit", "500 FC");
        JsonNode body = built.api()
                .get("/reports/due/5127", Map.of("on", DAY, "within", 5))
                .json();
        assertThat(body.get("items")).hasToString("[]");
    }

    @Test
    void aHorizonThatRunsBackwardsIsRefused() {
        Built built = stocked();
        Client.Answer answer = built.api().get("/reports/due", Map.of("on", DAY, "within", -1));
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("detail").asText()).contains("count of days ahead");
    }

    @Test
    void aHorizonPastWhatTheDeskPlansForIsRefused() {
        Built built = stocked();
        Client.Answer answer = built.api().get("/reports/due", Map.of("on", DAY, "within", 99999));
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("detail").asText()).contains("does not plan past");
    }

    @Test
    void nothingIsGroundedBeforeALimitPasses() {
        Built built = stocked();
        defer(built.api(), assessed(built.database()), "limit", "500 FC");
        assertThat(built.api()
                        .get("/reports/grounded", Map.of("on", DAY))
                        .json()
                        .get("tails"))
                .hasToString("[]");
    }

    @Test
    void aTailPastACalendarLimitIsGrounded() {
        Built built = stocked();
        defer(built.api(), assessed(built.database()), "limit", "30 DY");
        JsonNode body =
                built.api().get("/reports/grounded", Map.of("on", "2026-06-01")).json();
        assertThat(body.get("tails").get(0).get("serial").asText()).isEqualTo("5127");
        assertThat(body.get("overrun").asInt()).isEqualTo(1);
    }

    @Test
    void aGroundedLineCarriesTheOverrunFlag() {
        Built built = stocked();
        defer(built.api(), assessed(built.database()), "limit", "30 DY");
        JsonNode body =
                built.api().get("/reports/grounded", Map.of("on", "2026-06-01")).json();
        JsonNode first = body.get("tails").get(0).get("items").get(0);
        assertThat(first.get("overrun").asBoolean()).isTrue();
        assertThat(first.get("days").asInt()).isNegative();
    }

    @Test
    void theOpenReportCountsTheFleet() {
        Built built = stocked();
        assessed(built.database(), 1);
        JsonNode body = built.api().get("/reports/open", Map.of("on", DAY)).json();
        assertThat(body.get("total").asInt()).isEqualTo(1);
        assertThat(body.get("clean_tails")).hasToString("[]");
    }

    @Test
    void aCleanTailIsNamed() {
        Built built = stocked();
        JsonNode body = built.api().get("/reports/open", Map.of("on", DAY)).json();
        assertThat(body.get("clean_tails")).hasToString("[\"5127\"]");
    }

    @Test
    void theOpenReportBreaksOneTailDown() {
        Built built = stocked();
        assessed(built.database(), 1);
        JsonNode body = built.api().get("/reports/open/5127", Map.of("on", DAY)).json();
        assertThat(body.get("by_kind").get(0)).hasToString("{\"name\":\"dent\",\"count\":1}");
        assertThat(body.get("by_chapter").get(0)).hasToString("{\"name\":\"53\",\"count\":1}");
    }

    @Test
    void theOpenReportCarriesTheOriginSplit() {
        Built built = stocked();
        assessed(built.database(), 1);
        JsonNode body = built.api().get("/reports/open/5127", Map.of("on", DAY)).json();
        assertThat(body.get("by_origin").get(0).get("name").asText()).isEqualTo("impact");
    }

    @Test
    void theRecentWindowCanBeWidened() {
        Built built = stocked();
        assessed(built.database(), 1);
        JsonNode narrow = built.api()
                .get("/reports/open/5127", Map.of("on", "2025-12-01"))
                .json();
        JsonNode wide = built.api()
                .get("/reports/open/5127", Map.of("on", "2025-12-01", "recent", 365))
                .json();
        assertThat(narrow.get("raised_recently").asInt()).isZero();
        assertThat(wide.get("raised_recently").asInt()).isEqualTo(1);
    }

    @Test
    void theOpenReportOnATailThatIsNotThereIsNotFound() {
        Built built = stocked();
        assertThat(built.api().get("/reports/open/9999", Map.of("on", DAY)).status())
                .isEqualTo(404);
    }

    @Test
    void aDayIsRequired() {
        Built built = stocked();
        assertThat(built.api().get("/reports/due").status()).isEqualTo(422);
    }

    @Test
    void aReaderMayPullTheReports() {
        Built built = stocked();
        Client.Answer answer = built.api().get("/reports/open", Map.of("on", DAY), Desk.headersFor("reader"));
        assertThat(answer.status()).isEqualTo(200);
    }
}
