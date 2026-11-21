package io.github.rkaushikdev.doublerbay.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.support.Api;
import io.github.rkaushikdev.doublerbay.support.Client;
import io.github.rkaushikdev.doublerbay.support.Desk;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditRoutesTest {

    private static final String FITTED = "2025-04-02";

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

    @Test
    void aTailWithNoRepairsIsClean() {
        Built built = built();
        JsonNode body = built.api().get("/audit/5127", Map.of("on", FITTED)).json();
        assertThat(body.get("total_repairs").asInt()).isZero();
        assertThat(body.get("clean").asBoolean()).isTrue();
        assertThat(body.get("panels")).hasToString("[]");
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
}
