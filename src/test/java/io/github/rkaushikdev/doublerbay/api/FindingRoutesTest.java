package io.github.rkaushikdev.doublerbay.api;

import static io.github.rkaushikdev.doublerbay.support.Api.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.doublerbay.support.Api;
import io.github.rkaushikdev.doublerbay.support.Client;
import io.github.rkaushikdev.doublerbay.support.Desk;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FindingRoutesTest {

    private static Client client() {
        return Api.clientFor(Desk.stocked());
    }

    private static Map<String, Object> dentMeasurements(String depth) {
        return json("depth", depth, "along", "3.000", "around", "2.000", "sharp_bottom", false, "cracked", false);
    }

    private static Map<String, Object> cardBody() {
        return json(
                "serial", "5127",
                "kind", "dent",
                "origin", "impact",
                "source", "scheduled check",
                "found_on", "2025-03-14",
                "at_usage", json("cycles", 13900, "hours", "24400.0"),
                "fuselage",
                        json(
                                "structure", "fuselage",
                                "frame", 15,
                                "aft_of_frame", "10.000",
                                "stringer", 10,
                                "below_stringer", "0.000",
                                "side", "L"),
                "measurements", dentMeasurements("0.020"));
    }

    private static Map<String, Object> cardBody(String name, Object value) {
        Map<String, Object> body = cardBody();
        body.put(name, value);
        return body;
    }

    private static String raised(Client api) {
        Client.Answer answer = api.post("/findings", cardBody());
        assertThat(answer.status()).as(answer.text()).isEqualTo(201);
        return answer.json().get("reference").asText();
    }

    private static Map<String, Object> revision(String foundOn, int cycles, String hours) {
        return json(
                "found_on", foundOn,
                "at_usage", json("cycles", cycles, "hours", hours),
                "measurements", dentMeasurements("0.040"));
    }

    private static List<String> references(JsonNode rows) {
        List<String> held = new ArrayList<>();
        rows.forEach(one -> held.add(one.get("reference").asText()));
        return held;
    }

    @Test
    void raisingACardAllocatesItsNumber() {
        Client api = client();
        String first = raised(api);
        String second = raised(api);
        assertThat(first).isEqualTo("DR-2025-000001");
        assertThat(second).isEqualTo("DR-2025-000002");
    }

    @Test
    void aRaisedCardStartsOutRaised() {
        Client api = client();
        Client.Answer answer = api.post("/findings", cardBody());
        assertThat(answer.json().get("state").asText()).isEqualTo("raised");
    }

    @Test
    void aRaisedCardCanBeReadBack() {
        Client api = client();
        String reference = raised(api);
        JsonNode body = api.get("/findings/" + reference).json();
        assertThat(body.get("reference").asText()).isEqualTo(reference);
        assertThat(body.get("kind").asText()).isEqualTo("dent");
        assertThat(body.get("measurements").get("depth").asText()).isEqualTo("0.020");
        assertThat(body.get("location").get("structure").asText()).isEqualTo("fuselage");
    }

    @Test
    void aCardThatIsNotThereComesBackAsNotFound() {
        Client api = client();
        Client.Answer answer = api.get("/findings/DR-2025-009999");
        assertThat(answer.status()).isEqualTo(404);
        assertThat(answer.json().get("kind").asText()).isEqualTo("NotOnFile");
    }

    @Test
    void aReferenceThatIsNotAReferenceComesBackAsUnprocessable() {
        Client api = client();
        Client.Answer answer = api.get("/findings/not-a-number");
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("kind").asText()).isEqualTo("BadReading");
    }

    @Test
    void aCardWithNoLocationIsRefused() {
        Client api = client();
        Map<String, Object> body = cardBody();
        body.remove("fuselage");
        Client.Answer answer = api.post("/findings", body);
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("detail").asText()).contains("says where it is");
    }

    @Test
    void aCardWithAMeasurementThatIsNotANumberIsRefused() {
        Client api = client();
        Client.Answer answer = api.post("/findings", cardBody("measurements", dentMeasurements("deep")));
        assertThat(answer.status()).isEqualTo(422);
    }

    @Test
    void aCardWhoseKindAndCauseDoNotGoTogetherIsRefused() {
        Client api = client();
        Client.Answer answer = api.post("/findings", cardBody("origin", "lightning"));
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("detail").asText()).contains("does not come from lightning");
    }

    @Test
    void nothingIsWrittenWhenACardIsRefused() {
        Client api = client();
        api.post("/findings", cardBody("origin", "lightning"));
        assertThat(api.get("/aircraft/5127/findings").json().get("rows")).isEmpty();
    }

    @Test
    void aReMeasurementMakesANewRevision() {
        Client api = client();
        String reference = raised(api);
        Client.Answer answer =
                api.post("/findings/" + reference + "/revisions", revision("2025-06-01", 14200, "24900.0"));
        assertThat(answer.status()).isEqualTo(201);
        assertThat(answer.json().get("finding").get("revision").asInt()).isEqualTo(2);
        assertThat(answer.json().get("finding").get("measurements").get("depth").asText())
                .isEqualTo("0.040");
    }

    @Test
    void theHistoryCarriesEveryRevision() {
        Client api = client();
        String reference = raised(api);
        api.post("/findings/" + reference + "/revisions", revision("2025-06-01", 14200, "24900.0"));
        JsonNode history = api.get("/findings/" + reference + "/history").json();
        List<Integer> revisions = new ArrayList<>();
        history.forEach(one -> revisions.add(one.get("revision").asInt()));
        assertThat(revisions).containsExactly(1, 2);
    }

    @Test
    void aReMeasurementBeforeTheFindingIsRefused() {
        Client api = client();
        String reference = raised(api);
        Client.Answer answer =
                api.post("/findings/" + reference + "/revisions", revision("2024-01-01", 13000, "24000.0"));
        assertThat(answer.status()).isEqualTo(422);
    }

    @Test
    void supersedingClosesACardAndNamesWhatReplacedIt() {
        Client api = client();
        String first = raised(api);
        String second = raised(api);
        Client.Answer answer = api.post("/findings/" + first + "/supersede", json("by", second));
        assertThat(answer.status()).isEqualTo(200);
        assertThat(answer.json().get("superseded_by").asText()).isEqualTo(second);
    }

    @Test
    void aSupersededCardDropsOffTheOpenList() {
        Client api = client();
        String first = raised(api);
        String second = raised(api);
        api.post("/findings/" + first + "/supersede", json("by", second));
        JsonNode openCards = api.get("/aircraft/5127/findings", Map.of("open_only", true))
                .json()
                .get("rows");
        assertThat(references(openCards)).containsExactly(second);
    }

    @Test
    void supersedingByACardThatIsNotThereIsRefused() {
        Client api = client();
        String first = raised(api);
        Client.Answer answer = api.post("/findings/" + first + "/supersede", json("by", "DR-2025-009999"));
        assertThat(answer.status()).isEqualTo(404);
    }

    @Test
    void theCardsOnATailComeBackInNumberOrder() {
        Client api = client();
        raised(api);
        raised(api);
        JsonNode listed = api.get("/aircraft/5127/findings").json().get("rows");
        assertThat(references(listed)).containsExactly("DR-2025-000001", "DR-2025-000002");
    }

    @Test
    void aTailWithNothingOnItComesBackEmpty() {
        Client api = client();
        assertThat(api.get("/aircraft/5127/findings").json().get("rows")).isEmpty();
    }

    @Test
    void cardsCanBePulledOutByKind() {
        Client api = client();
        raised(api);
        assertThat(api.get("/findings-of-kind/dent").json()).hasSize(1);
        assertThat(api.get("/findings-of-kind/crack").json()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"dent", "crack", "corrosion"})
    void everyKindTheDeskMeasuresIsAValidQuery(String kind) {
        Client api = client();
        assertThat(api.get("/findings-of-kind/" + kind).status()).isEqualTo(200);
    }
}
