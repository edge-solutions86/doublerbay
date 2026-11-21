package io.github.rkaushikdev.doublerbay.api;

import static io.github.rkaushikdev.doublerbay.support.Api.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.doublerbay.support.Api;
import io.github.rkaushikdev.doublerbay.support.Client;
import io.github.rkaushikdev.doublerbay.support.Databases;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FleetRoutesTest {

    private static Client client() {
        return Api.clientFor(Databases.fresh());
    }

    private static Map<String, Object> tailBody() {
        return tailBody("5127", "N412VD", "200");
    }

    private static Map<String, Object> tailBody(String serial, String marks, String series) {
        return json(
                "serial", serial,
                "registration", marks,
                "series", series,
                "line_number", 1140,
                "delivered", "2016-04-12");
    }

    private static JsonNode enrolled(Client api) {
        return enrolled(api, "5127", "N412VD");
    }

    private static JsonNode enrolled(Client api, String serial, String marks) {
        Client.Answer answer = api.post("/aircraft", tailBody(serial, marks, "200"));
        assertThat(answer.status()).as(answer.text()).isEqualTo(201);
        return answer.json();
    }

    private static Map<String, Object> reading(String taken, int cycles, String hours) {
        return json("taken", taken, "usage", json("cycles", cycles, "hours", hours));
    }

    @Test
    void aTailGoesOnTheRegisterAndComesBack() {
        Client api = client();
        JsonNode body = enrolled(api);
        assertThat(body.get("serial").asText()).isEqualTo("5127");
        assertThat(body.get("registration").asText()).isEqualTo("N412VD");
        assertThat(api.get("/aircraft/5127").json().get("line_number").asInt()).isEqualTo(1140);
    }

    @Test
    void enrollingATailOpensItsLogAtDelivery() {
        Client api = client();
        enrolled(api);
        JsonNode log = api.get("/aircraft/5127/utilisation").json();
        assertThat(log.get("opened_on").asText()).isEqualTo("2016-04-12");
        assertThat(log.get("latest").get("usage").get("cycles").asInt()).isZero();
    }

    @Test
    void aTailEnrolledTwiceIsRefused() {
        Client api = client();
        enrolled(api);
        Client.Answer answer = api.post("/aircraft", tailBody());
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("detail").asText()).contains("already on the register");
    }

    @Test
    void aTailThatIsNotThereComesBackAsNotFound() {
        Client api = client();
        assertThat(api.get("/aircraft/9999").status()).isEqualTo(404);
    }

    @Test
    void marksThatAreNotMarksAreRefused() {
        Client api = client();
        Client.Answer answer = api.post("/aircraft", tailBody("5127", "not-a-tail", "200"));
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("detail").asText()).contains("not a registration");
    }

    @Test
    void aTailCanBeFoundByTheMarksItIsWearing() {
        Client api = client();
        enrolled(api);
        assertThat(api.get("/aircraft-by-marks/N412VD").json().get("serial").asText())
                .isEqualTo("5127");
        assertThat(api.get("/aircraft-by-marks/N1").status()).isEqualTo(404);
    }

    @Test
    void theRegisterListsWhatIsOnIt() {
        Client api = client();
        enrolled(api);
        enrolled(api, "5240", "N908QA");
        assertThat(api.get("/aircraft").json()).hasSize(2);
    }

    @Test
    void theRegisterCanBeAskedForADay() {
        Client api = client();
        enrolled(api);
        assertThat(api.get("/aircraft", Map.of("in_service_on", "2020-01-01")).json())
                .isNotEmpty();
        assertThat(api.get("/aircraft", Map.of("in_service_on", "2000-01-01")).json())
                .isEmpty();
    }

    @Test
    void aPostedReadingLandsOnTheLog() {
        Client api = client();
        enrolled(api);
        Client.Answer answer = api.post("/aircraft/5127/readings", reading("2025-01-01", 13800, "24200.0"));
        assertThat(answer.status()).isEqualTo(201);
        assertThat(answer.json().get("latest").get("usage").get("cycles").asInt())
                .isEqualTo(13800);
        assertThat(answer.json().get("readings")).hasSize(2);
    }

    @Test
    void aReadingThatRunsACounterBackwardsIsRefused() {
        Client api = client();
        enrolled(api);
        api.post("/aircraft/5127/readings", reading("2025-01-01", 13800, "24200.0"));
        Client.Answer answer = api.post("/aircraft/5127/readings", reading("2025-06-01", 100, "200.0"));
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("detail").asText()).contains("cycles fell");
    }

    @Test
    void aRefusedReadingLeavesTheLogAlone() {
        Client api = client();
        enrolled(api);
        api.post("/aircraft/5127/readings", reading("2025-06-01", 100, "-1"));
        assertThat(api.get("/aircraft/5127/utilisation").json().get("readings")).hasSize(1);
    }

    @Test
    void twoReadingsOnOneDayAreRefused() {
        Client api = client();
        enrolled(api);
        Map<String, Object> body = reading("2025-01-01", 13800, "24200.0");
        api.post("/aircraft/5127/readings", body);
        assertThat(api.post("/aircraft/5127/readings", body).status()).isEqualTo(422);
    }

    @Test
    void theHoursCrossTheWireAsTextAndComeBackAsText() {
        Client api = client();
        enrolled(api);
        Client.Answer answer = api.post("/aircraft/5127/readings", reading("2025-01-01", 13800, "24200.5"));
        assertThat(answer.json().get("latest").get("usage").get("hours").asText())
                .isEqualTo("24200.5");
    }

    @Test
    void theRateComesOffTheRecentStretchOfTheLog() {
        Client api = client();
        enrolled(api);
        api.post("/aircraft/5127/readings", reading("2024-01-01", 12000, "21000.0"));
        api.post("/aircraft/5127/readings", reading("2025-01-01", 13800, "24200.0"));
        JsonNode rate = api.get("/aircraft/5127/rate").json();
        assertThat(rate.get("as_of").asText()).isEqualTo("2025-01-01");
        assertThat(Double.parseDouble(rate.get("cycles_a_day").asText())).isGreaterThan(0);
        assertThat(Double.parseDouble(rate.get("average_leg").asText())).isGreaterThan(1);
    }

    @Test
    void aTailThatHasOnlyEverBeenReadOnceHasNoRate() {
        Client api = client();
        enrolled(api);
        Client.Answer answer = api.get("/aircraft/5127/rate");
        assertThat(answer.status()).isEqualTo(404);
        assertThat(answer.json().get("detail").asText()).contains("one reading is not a rate");
    }

    @Test
    void aReadingAgainstATailThatIsNotThereIsRefused() {
        Client api = client();
        Client.Answer answer = api.post("/aircraft/9999/readings", reading("2025-01-01", 1, "1.0"));
        assertThat(answer.status()).isEqualTo(404);
    }
}
