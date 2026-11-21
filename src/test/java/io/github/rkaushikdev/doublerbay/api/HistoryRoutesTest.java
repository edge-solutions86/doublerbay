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

class HistoryRoutesTest {

    private static final String QUESTION = "dent in the chem mill land at FR15, past the allowable, what may we do";

    /** A database holding one tail, and a chief engineer's client against it. */
    private record Built(Database database, Client api) {}

    /** One database and a chief engineer's client, off the shared fixtures. */
    private static Built built() {
        Database database = Desk.newDatabase();
        Desk.enrolled(database);
        return new Built(database, Api.clientFor(database));
    }

    /** One card on a fastener row, raised and looked up on the pinned day. */
    private static String raised(Database database) {
        return Desk.assessedCard(database, Sample.card(1, Sample.cabinPointOn(10)))
                .toString();
    }

    private static Client.Answer defer(Client api, String against) {
        return api.post(
                "/deferrals", json("against", against, "granted", "2025-03-14", "by", "j.mercer", "limit", "500 FC"));
    }

    private static Client.Answer fit(Client api, String against) {
        return api.post(
                "/repairs",
                json(
                        "against", against,
                        "sequence", 1,
                        "along", "2.000",
                        "around", "2.000",
                        "centre_along", "468.000",
                        "centre_around", "91.000",
                        "embodied", "2025-04-02",
                        "by", "s.brandt"));
    }

    private static List<String> cameFrom(JsonNode story) {
        List<String> registers = new ArrayList<>();
        story.get("entries").forEach(one -> registers.add(one.get("came_from").asText()));
        return registers;
    }

    @Test
    void theRegistersAreListedInReadingOrder() {
        Built built = built();
        JsonNode registers = built.api().get("/history/registers").json().get("registers");
        assertThat(registers.get(0).asText()).isEqualTo("card");
        assertThat(registers.get(registers.size() - 1).asText()).isEqualTo("repair");
    }

    @Test
    void aFreshlyRaisedCardHasTwoLines() {
        Built built = built();
        JsonNode body = built.api().get("/history/" + raised(built.database())).json();
        assertThat(body.get("entries")).hasSize(2);
        assertThat(body.get("entries").get(0).get("came_from").asText()).isEqualTo("card");
    }

    @Test
    void theStoryNamesItsTail() {
        Built built = built();
        assertThat(built.api()
                        .get("/history/" + raised(built.database()))
                        .json()
                        .get("serial")
                        .asText())
                .isEqualTo("5127");
    }

    @Test
    void aDeferralLandsOnTheStory() {
        Built built = built();
        String reference = raised(built.database());
        defer(built.api(), reference);
        JsonNode body = built.api().get("/history/" + reference).json();
        assertThat(cameFrom(body)).contains("deferral");
    }

    @Test
    void anOrderLandsOnTheStory() {
        Built built = built();
        String reference = raised(built.database());
        built.api()
                .post(
                        "/orders",
                        json("against", reference, "question", QUESTION, "raised", "2025-03-14", "by", "a.okonkwo"));
        JsonNode body = built.api().get("/history/" + reference).json();
        assertThat(cameFrom(body)).contains("engineering");
    }

    @Test
    void aRepairLandsOnTheStory() {
        Built built = built();
        String reference = raised(built.database());
        fit(built.api(), reference);
        JsonNode body = built.api().get("/history/" + reference).json();
        assertThat(cameFrom(body)).contains("repair");
    }

    @Test
    void theStorySpansFromTheFirstDayToTheLast() {
        Built built = built();
        String reference = raised(built.database());
        fit(built.api(), reference);
        JsonNode body = built.api().get("/history/" + reference).json();
        assertThat(body.get("opened").asText()).isEqualTo("2025-03-14");
        assertThat(body.get("latest").asText()).isEqualTo("2025-04-02");
        assertThat(body.get("days_open").asInt()).isEqualTo(19);
    }

    @Test
    void aCardThatIsNotOnFileIsNotFound() {
        Built built = built();
        assertThat(built.api().get("/history/DR-2025-000099").status()).isEqualTo(404);
    }

    @Test
    void aReferenceThatIsNotOneIsRefused() {
        Built built = built();
        assertThat(built.api().get("/history/nonsense").status()).isEqualTo(422);
    }

    @Test
    void aReaderMayPullAStory() {
        Built built = built();
        Client.Answer answer =
                built.api().get("/history/" + raised(built.database()), Map.of(), Desk.headersFor("reader"));
        assertThat(answer.status()).isEqualTo(200);
    }
}
