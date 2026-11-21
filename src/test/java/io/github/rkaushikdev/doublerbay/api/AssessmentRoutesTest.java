package io.github.rkaushikdev.doublerbay.api;

import static io.github.rkaushikdev.doublerbay.support.Api.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.doublerbay.support.Api;
import io.github.rkaushikdev.doublerbay.support.Client;
import io.github.rkaushikdev.doublerbay.support.Desk;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class AssessmentRoutesTest {

    private static Client client() {
        return Api.clientFor(Desk.stocked());
    }

    /** The parts of a card a test varies, with the defaults every other test takes. */
    private static final class Card {
        private String depth = "0.010";
        private String along = "6.000";
        private String around = "4.000";
        private int stringer = 10;
        private String aft = "10.000";

        Card depth(String value) {
            depth = value;
            return this;
        }

        Card aft(String value) {
            aft = value;
            return this;
        }

        Map<String, Object> body() {
            return json(
                    "serial", "5127",
                    "kind", "dent",
                    "origin", "impact",
                    "source", "scheduled check",
                    "found_on", "2025-03-14",
                    "at_usage", json("cycles", 13900, "hours", "24400.0"),
                    "fuselage",
                            json(
                                    "structure",
                                    "fuselage",
                                    "frame",
                                    15,
                                    "aft_of_frame",
                                    aft,
                                    "stringer",
                                    stringer,
                                    "below_stringer",
                                    "4.000",
                                    "side",
                                    "L"),
                    "measurements",
                            json(
                                    "depth", depth,
                                    "along", along,
                                    "around", around,
                                    "sharp_bottom", false,
                                    "cracked", false));
        }
    }

    private static Card card() {
        return new Card();
    }

    private static String raised(Client api) {
        return raised(api, card());
    }

    private static String raised(Client api, Card card) {
        Client.Answer answer = api.post("/findings", card.body());
        assertThat(answer.status()).as(answer.text()).isEqualTo(201);
        return answer.json().get("reference").asText();
    }

    @Test
    void aSmallDentAssessesAsAllowable() {
        Client api = client();
        String reference = raised(api);
        JsonNode body = api.get("/findings/" + reference + "/assessment").json();
        assertThat(body.get("verdict").get("disposition").asText()).isEqualTo("allowable as it stands");
        assertThat(body.get("part").asText()).startsWith("5310-");
    }

    @Test
    void theAssessmentCitesThePageAndTheRevision() {
        Client api = client();
        String reference = raised(api);
        JsonNode verdict =
                api.get("/findings/" + reference + "/assessment").json().get("verdict");
        assertThat(verdict.get("page").asText()).isEqualTo("53-10-01");
        assertThat(verdict.get("revision").asInt()).isEqualTo(12);
    }

    @Test
    void aDeepDentAssessesAsARepair() {
        Client api = client();
        String reference = raised(api, card().depth("0.090"));
        JsonNode verdict =
                api.get("/findings/" + reference + "/assessment").json().get("verdict");
        assertThat(verdict.get("disposition").asText()).isEqualTo("repair from the manual");
        assertThat(StreamSupport.stream(verdict.get("reasons").spliterator(), false))
                .anyMatch(one -> !one.get("passed").asBoolean());
    }

    @Test
    void theWholeTrailComesBackWithTheAnswer() {
        Client api = client();
        String reference = raised(api);
        JsonNode verdict =
                api.get("/findings/" + reference + "/assessment").json().get("verdict");
        Set<String> rules = new HashSet<>();
        verdict.get("reasons").forEach(one -> rules.add(one.get("rule").asText()));
        assertThat(rules).contains("allowable");
        assertThat(rules).contains("depth");
    }

    @Test
    void theSameCardAssessesDifferentlyBeforeTheCampaignRevision() {
        Client api = client();
        String reference = raised(api, card().depth("0.070"));
        JsonNode before = api.get("/findings/" + reference + "/assessment", Map.of("on", "2024-06-01"))
                .json();
        JsonNode after = api.get("/findings/" + reference + "/assessment", Map.of("on", "2025-03-14"))
                .json();
        assertThat(before.get("verdict").get("disposition").asText()).isEqualTo("allowable as it stands");
        assertThat(after.get("verdict").get("disposition").asText()).isEqualTo("repair from the manual");
    }

    @Test
    void theThicknessAndTheMarginComeBackAsText() {
        Client api = client();
        String reference = raised(api);
        JsonNode body = api.get("/findings/" + reference + "/assessment").json();
        assertThat(body.get("thickness").asText()).startsWith("0.0");
        assertThat(body.get("edge_margin").asText()).contains(".");
    }

    @Test
    void aCardThatIsNotThereHasNoAssessment() {
        Client api = client();
        assertThat(api.get("/findings/DR-2025-009999/assessment").status()).isEqualTo(404);
    }

    @Test
    void aTailWithNothingOpenAssessesAsAllowable() {
        Client api = client();
        JsonNode body = api.get("/aircraft/5127/assessment").json();
        assertThat(body.get("disposition").asText()).isEqualTo("allowable as it stands");
        assertThat(body.get("panels")).isEmpty();
    }

    @Test
    void aTailAssessmentGroupsTheCardsByPanel() {
        Client api = client();
        raised(api, card().aft("2.000"));
        raised(api, card().aft("16.000"));
        JsonNode body = api.get("/aircraft/5127/assessment").json();
        assertThat(body.get("panels")).hasSize(1);
        assertThat(body.get("panels").get(0).get("card_count").asInt()).isEqualTo(2);
    }

    @Test
    void theTailAnswerIsTheWorstOfEveryPanel() {
        Client api = client();
        raised(api, card().aft("2.000"));
        raised(api, card().aft("16.000").depth("0.090"));
        JsonNode body = api.get("/aircraft/5127/assessment").json();
        assertThat(body.get("disposition").asText()).isEqualTo("repair from the manual");
    }

    @Test
    void theDamageCountIsTakenAfterClustering() {
        Client api = client();
        raised(api, card().aft("8.000"));
        raised(api, card().aft("8.500"));
        JsonNode panel =
                api.get("/aircraft/5127/assessment").json().get("panels").get(0);
        assertThat(panel.get("card_count").asInt()).isEqualTo(2);
        assertThat(panel.get("damage_count").asInt()).isEqualTo(1);
    }

    @Test
    void aTailAssessmentCanBeAskedForADay() {
        Client api = client();
        raised(api, card().depth("0.070"));
        JsonNode early =
                api.get("/aircraft/5127/assessment", Map.of("on", "2024-06-01")).json();
        assertThat(early.get("on").asText()).isEqualTo("2024-06-01");
        assertThat(early.get("disposition").asText()).isEqualTo("allowable as it stands");
    }

    @Test
    void aTailThatIsNotOnTheRegisterHasNoAssessment() {
        Client api = client();
        assertThat(api.get("/aircraft/9999/assessment").status()).isEqualTo(404);
    }
}
