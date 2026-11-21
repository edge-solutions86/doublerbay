package io.github.rkaushikdev.doublerbay.api;

import static io.github.rkaushikdev.doublerbay.support.Api.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.support.Api;
import io.github.rkaushikdev.doublerbay.support.Client;
import io.github.rkaushikdev.doublerbay.support.Desk;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditRoutesTest {

    private static final String FITTED = "2025-04-02";
    private static final String LATER = "2026-11-02";

    /** A database holding one tail, and a chief engineer's client against it. */
    private record Built(Database database, Client api) {}

    /** One database and a chief engineer's client, off the shared fixtures. */
    private static Built built() {
        Database database = Desk.newDatabase();
        Desk.enrolled(database);
        return new Built(database, Api.clientFor(database));
    }

    private static void secondTail(Database database) {
        Desk.enrolled(database, "5240");
    }

    /** One card on a fastener row of MSN 5127, raised and looked up. */
    private static String assessed(Database database, int sequence) {
        return Desk.assessedCard(database, Sample.card(sequence, Sample.cabinPointOn(10)))
                .toString();
    }

    private static Client.Answer fit(Client api, String against) {
        return fit(api, against, 1, "468.000", "91.000", false);
    }

    private static Client.Answer fitTemporary(Client api, String against) {
        return fit(api, against, 1, "468.000", "91.000", true);
    }

    private static Client.Answer fit(
            Client api, String against, int sequence, String centreAlong, String centreAround, boolean temporary) {
        return api.post(
                "/repairs",
                json(
                        "against", against,
                        "sequence", sequence,
                        "along", "2.000",
                        "around", "2.000",
                        "centre_along", centreAlong,
                        "centre_around", centreAround,
                        "embodied", FITTED,
                        "by", "s.brandt",
                        "temporary", temporary));
    }

    @Test
    void aTailWithNoRepairsIsClean() {
        Built built = built();
        JsonNode body = built.api().get("/audit/5127", Map.of("on", FITTED)).json();
        assertThat(body.get("total_repairs").asInt()).isZero();
        assertThat(body.get("clean").asBoolean()).isTrue();
        assertThat(body.get("panels")).hasToString("[]");
    }

    @Test
    void oneRepairLandsOnItsPanel() {
        Built built = built();
        fit(built.api(), assessed(built.database(), 1));
        JsonNode body = built.api().get("/audit/5127", Map.of("on", FITTED)).json();
        assertThat(body.get("total_repairs").asInt()).isEqualTo(1);
        assertThat(body.get("panels").get(0).get("repairs").asInt()).isEqualTo(1);
        assertThat(body.get("panels").get(0).get("crowded").asBoolean()).isFalse();
    }

    @Test
    void threeRepairsOnOnePanelReadAsCrowded() {
        Built built = built();
        for (int step = 0; step < 3; step++) {
            fit(
                    built.api(),
                    assessed(built.database(), step + 1),
                    step + 1,
                    (468 + step * 200) + ".000",
                    "91.000",
                    false);
        }
        JsonNode body = built.api().get("/audit/5127", Map.of("on", FITTED)).json();
        assertThat(body.get("panels").get(0).get("crowded").asBoolean()).isTrue();
        assertThat(body.get("clean").asBoolean()).isFalse();
    }

    @Test
    void aTemporaryRepairIsListedWithItsAge() {
        Built built = built();
        fitTemporary(built.api(), assessed(built.database(), 1));
        JsonNode body = built.api().get("/audit/5127", Map.of("on", FITTED)).json();
        assertThat(body.get("temporary")).hasSize(1);
        assertThat(body.get("temporary").get(0).get("months_fitted").asText()).isEqualTo("0.0");
        assertThat(body.get("temporary").get(0).get("overdue").asBoolean()).isFalse();
    }

    @Test
    void aTemporaryRepairPastItsLifeIsOverdue() {
        Built built = built();
        fitTemporary(built.api(), assessed(built.database(), 1));
        JsonNode body = built.api().get("/audit/5127", Map.of("on", LATER)).json();
        assertThat(body.get("temporary").get(0).get("overdue").asBoolean()).isTrue();
        assertThat(body.get("clean").asBoolean()).isFalse();
    }

    @Test
    void twoRepairsTooCloseComeBackAsAClash() {
        Built built = built();
        fit(built.api(), assessed(built.database(), 1), 1, "468.000", "91.000", false);
        fit(built.api(), assessed(built.database(), 2), 2, "469.000", "91.000", false);
        JsonNode body = built.api().get("/audit/5127", Map.of("on", FITTED)).json();
        assertThat(body.get("clashing")).hasToString("[[\"RP-53-0001\",\"RP-53-0002\"]]");
    }

    @Test
    void aRemovedRepairDropsOffTheAudit() {
        Built built = built();
        fit(built.api(), assessed(built.database(), 1));
        built.api().post("/repairs/53/1/remove", json("on", "2026-01-05"));
        JsonNode body = built.api().get("/audit/5127", Map.of("on", LATER)).json();
        assertThat(body.get("total_repairs").asInt()).isZero();
    }

    @Test
    void aTailThatIsNotOnTheRegisterIsNotFound() {
        Built built = built();
        assertThat(built.api().get("/audit/9999", Map.of("on", FITTED)).status())
                .isEqualTo(404);
    }

    @Test
    void theDayIsRequired() {
        Built built = built();
        assertThat(built.api().get("/audit/5127").status()).isEqualTo(422);
    }

    @Test
    void theFleetAuditCoversTheRegister() {
        Built built = built();
        secondTail(built.database());
        JsonNode body = built.api().get("/audit", Map.of("on", FITTED)).json();
        assertThat(body.get("tails")).hasSize(2);
        assertThat(body.get("flagged").asInt()).isZero();
    }

    @Test
    void aFlaggedTailSortsToTheTop() {
        Built built = built();
        secondTail(built.database());
        fit(built.api(), assessed(built.database(), 1), 1, "468.000", "91.000", false);
        fit(built.api(), assessed(built.database(), 2), 2, "469.000", "91.000", false);
        JsonNode body = built.api().get("/audit", Map.of("on", FITTED)).json();
        assertThat(body.get("tails").get(0).get("serial").asText()).isEqualTo("5127");
        assertThat(body.get("tails").get(0).get("clashes").asInt()).isEqualTo(1);
        assertThat(body.get("flagged").asInt()).isEqualTo(1);
    }

    @Test
    void twoCleanTailsSortBySerial() {
        Built built = built();
        secondTail(built.database());
        JsonNode body = built.api().get("/audit", Map.of("on", FITTED)).json();
        List<String> serials = new ArrayList<>();
        body.get("tails").forEach(one -> serials.add(one.get("serial").asText()));
        assertThat(serials).containsExactly("5127", "5240");
    }

    @Test
    void aTailNotYetDeliveredIsLeftOut() {
        Built built = built();
        JsonNode body = built.api().get("/audit", Map.of("on", "2000-01-01")).json();
        assertThat(body.get("tails")).hasToString("[]");
    }

    @Test
    void aReaderMayPullTheAudit() {
        Built built = built();
        Client.Answer answer = built.api().get("/audit/5127", Map.of("on", FITTED), Desk.headersFor("reader"));
        assertThat(answer.status()).isEqualTo(200);
    }

    @Test
    void theFleetLineCountsTheOverdueTemporaries() {
        Built built = built();
        fitTemporary(built.api(), assessed(built.database(), 1));
        JsonNode body = built.api().get("/audit", Map.of("on", LATER)).json();
        assertThat(body.get("tails").get(0).get("overdue_temporary").asInt()).isEqualTo(1);
        assertThat(body.get("tails").get(0).get("clean").asBoolean()).isFalse();
    }
}
