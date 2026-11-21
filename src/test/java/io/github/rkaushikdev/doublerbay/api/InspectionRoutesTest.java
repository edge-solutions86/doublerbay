package io.github.rkaushikdev.doublerbay.api;

import static io.github.rkaushikdev.doublerbay.support.Api.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.doublerbay.store.Database;
import io.github.rkaushikdev.doublerbay.support.Api;
import io.github.rkaushikdev.doublerbay.support.Client;
import io.github.rkaushikdev.doublerbay.support.Desk;
import io.github.rkaushikdev.doublerbay.support.Sample;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InspectionRoutesTest {

    private static final String FITTED = "2025-04-02";

    /** A database holding one tail, and a chief engineer's client against it. */
    private record Built(Database database, Client api) {}

    /** One database and a chief engineer's client, off the shared fixtures. */
    private static Built built() {
        Database database = Desk.newDatabase();
        Desk.enrolled(database);
        return new Built(database, Api.clientFor(database));
    }

    /** One card on a fastener row, raised and looked up. */
    private static String assessed(Database database) {
        return Desk.assessedCard(database, Sample.card(1, Sample.cabinPointOn(10)))
                .toString();
    }

    /** A temporary repair against a card, which is the kind that carries a task. */
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
                        "embodied", FITTED,
                        "by", "s.brandt",
                        "temporary", true));
    }

    private static Map<String, Object> signedOff(String on) {
        return json("on", on, "flown", json("cycles", 15000, "hours", "26000.0"));
    }

    @Test
    void aTailWithNoRepairsCarriesNoTasks() {
        Built built = built();
        assertThat(built.api().get("/inspections/aircraft/5127").json()).hasToString("[]");
    }

    @Test
    void aTemporaryRepairPutsATaskOnTheTail() {
        Built built = built();
        fit(built.api(), assessed(built.database()));
        JsonNode body = built.api().get("/inspections/aircraft/5127").json();
        assertThat(body).hasSize(1);
        assertThat(body.get(0).get("against").asText()).isEqualTo("RP-53-0001");
        assertThat(body.get(0).get("one_off").asBoolean()).isTrue();
    }

    @Test
    void aTaskCarriesItsThresholdAsWritten() {
        Built built = built();
        fit(built.api(), assessed(built.database()));
        JsonNode body = built.api().get("/inspections/aircraft/5127").json();
        assertThat(body.get(0).get("threshold").asText()).contains("MO");
        assertThat(body.get(0).get("repeat").isNull()).isTrue();
    }

    @Test
    void aNewTaskHasNeverBeenDone() {
        Built built = built();
        fit(built.api(), assessed(built.database()));
        JsonNode body = built.api().get("/inspections/aircraft/5127").json();
        assertThat(body.get(0).get("last_done").isNull()).isTrue();
        assertThat(built.api().get("/inspections/aircraft/5127/never-done").json())
                .hasSize(1);
    }

    @Test
    void aTaskSaysWhenItNextFallsDue() {
        Built built = built();
        fit(built.api(), assessed(built.database()));
        JsonNode due =
                built.api().get("/inspections/aircraft/5127").json().get(0).get("due");
        assertThat(due.get("on").isNull()).isFalse();
        assertThat(due.get("written").asText()).isNotEmpty();
    }

    @Test
    void oneTaskCanBeReadBack() {
        Built built = built();
        fit(built.api(), assessed(built.database()));
        assertThat(built.api().get("/inspections/53/1").json().get("part").asText())
                .startsWith("53");
    }

    @Test
    void aRepairCarryingNoTaskIsNotFound() {
        Built built = built();
        assertThat(built.api().get("/inspections/53/1").status()).isEqualTo(404);
    }

    @Test
    void aTailThatIsNotOnTheRegisterIsNotFound() {
        Built built = built();
        assertThat(built.api().get("/inspections/aircraft/9999").status()).isEqualTo(404);
    }

    @Test
    void nothingIsDueTheDayTheRepairGoesOn() {
        Built built = built();
        fit(built.api(), assessed(built.database()));
        assertThat(built.api()
                        .get("/inspections/aircraft/5127/due", Map.of("on", FITTED))
                        .json())
                .hasToString("[]");
    }

    @Test
    void aTaskPastItsThresholdIsDue() {
        Built built = built();
        fit(built.api(), assessed(built.database()));
        JsonNode body = built.api()
                .get("/inspections/aircraft/5127/due", Map.of("on", "2030-01-01"))
                .json();
        assertThat(body).hasSize(1);
    }

    @Test
    void theDueRouteNeedsADay() {
        Built built = built();
        assertThat(built.api().get("/inspections/aircraft/5127/due").status()).isEqualTo(422);
    }

    @Test
    void aTaskCanBeSignedOff() {
        Built built = built();
        fit(built.api(), assessed(built.database()));
        Client.Answer answer = built.api().post("/inspections/53/1/done", signedOff("2026-01-05"));
        assertThat(answer.status()).as(answer.text()).isEqualTo(200);
        assertThat(answer.json().get("last_done").asText()).isEqualTo("2026-01-05");
        assertThat(answer.json().get("at_last_done").get("cycles").asInt()).isEqualTo(15000);
    }

    @Test
    void aSignedOffOneOffHasNoNextDue() {
        Built built = built();
        fit(built.api(), assessed(built.database()));
        Client.Answer answer = built.api().post("/inspections/53/1/done", signedOff("2026-01-05"));
        assertThat(answer.json().get("due").isNull()).isTrue();
    }

    @Test
    void aSignedOffTaskDropsOffTheNeverDoneList() {
        Built built = built();
        fit(built.api(), assessed(built.database()));
        built.api().post("/inspections/53/1/done", signedOff("2026-01-05"));
        assertThat(built.api().get("/inspections/aircraft/5127/never-done").json())
                .hasToString("[]");
    }

    @Test
    void signingOffBeforeTheRepairWentOnIsRefused() {
        Built built = built();
        fit(built.api(), assessed(built.database()));
        Client.Answer answer = built.api().post("/inspections/53/1/done", signedOff("2024-01-01"));
        assertThat(answer.status()).isEqualTo(422);
    }

    @Test
    void signingOffATaskThatIsNotThereIsNotFound() {
        Built built = built();
        Client.Answer answer = built.api().post("/inspections/53/9/done", signedOff("2026-01-05"));
        assertThat(answer.status()).isEqualTo(404);
    }

    @Test
    void aReplacementLimitIsOnlySignedOffOnce() {
        Built built = built();
        fit(built.api(), assessed(built.database()));
        Map<String, Object> body = signedOff("2026-01-05");
        built.api().post("/inspections/53/1/done", body);
        assertThat(built.api().post("/inspections/53/1/done", body).status()).isEqualTo(422);
    }

    @Test
    void aMechanicMayNotSignOffAnInspection() {
        Built built = built();
        fit(built.api(), assessed(built.database()));
        Client.Answer answer =
                built.api().post("/inspections/53/1/done", signedOff("2026-01-05"), Desk.headersFor("mechanic"));
        assertThat(answer.status()).isEqualTo(422);
    }

    @Test
    void aReaderMayLookAtTheTasks() {
        Built built = built();
        Client.Answer answer = built.api().get("/inspections/aircraft/5127", Map.of(), Desk.headersFor("reader"));
        assertThat(answer.status()).isEqualTo(200);
    }

    @Test
    void removingTheRepairTakesTheTaskOffTheService() {
        Built built = built();
        fit(built.api(), assessed(built.database()));
        built.api().post("/repairs/53/1/remove", json("on", "2026-01-05"));
        assertThat(built.api().get("/inspections/aircraft/5127").json()).hasToString("[]");
        assertThat(built.api().get("/inspections/53/1").status()).isEqualTo(404);
    }
}
