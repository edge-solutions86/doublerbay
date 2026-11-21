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

/**
 * One damage card's whole life, through the http surface, in order.
 *
 * <p>Every other route test holds one thing still and pokes at it. This one does not hold anything
 * still. It raises a card the way a mechanic would, assesses it, defers it, asks the manufacturer,
 * gets an answer, fits a repair, signs off the inspection, and checks that every report says the
 * right thing at every step.
 *
 * <p>It is slow and it is worth it. The failures it catches are the ones no unit test can: a state
 * machine that allows each move on its own and no path through them, a report that is right in
 * isolation and disagrees with the register it was built from, a lifted deferral that stayed on the
 * work pack.
 *
 * <p>Nothing here reaches into a repository. If the http surface cannot do it, the desk cannot do it.
 */
class ScenarioTest {

    private static final String FOUND = "2025-03-14";
    private static final String DEFERRED = "2025-03-20";
    private static final String SENT = "2025-03-21";
    private static final String ANSWERED = "2025-06-01";
    private static final String FITTED = "2025-06-10";
    private static final String QUESTION = "dent in the chem mill land at FR15, past the allowable, what may we do";

    /** A chief engineer's client over an empty database. */
    private static Client api() {
        return Api.clientFor(Desk.newDatabase());
    }

    private static void enrol(Client api) {
        Client.Answer answer = api.post(
                "/aircraft",
                json(
                        "serial", "5127",
                        "registration", "N412VD",
                        "series", "200",
                        "line_number", 1140,
                        "delivered", "2016-04-12"));
        assertThat(answer.status()).as(answer.text()).isEqualTo(201);
        String[][] readings = {{"2024-03-14", "12000"}, {"2025-03-01", "13800"}};
        for (String[] reading : readings) {
            int cycles = Integer.parseInt(reading[1]);
            api.post(
                    "/aircraft/5127/readings",
                    json("taken", reading[0], "usage", json("cycles", cycles, "hours", (cycles * 2) + ".0")));
        }
    }

    private static String raiseCard(Client api) {
        return raiseCard(api, "0.220");
    }

    private static String raiseCard(Client api, String depth) {
        Client.Answer answer = api.post(
                "/findings",
                json(
                        "serial",
                        "5127",
                        "kind",
                        "dent",
                        "origin",
                        "impact",
                        "source",
                        "scheduled check",
                        "found_on",
                        FOUND,
                        "at_usage",
                        json("cycles", 13800, "hours", "24200.0"),
                        "fuselage",
                        json(
                                "structure", "fuselage",
                                "frame", 15,
                                "aft_of_frame", "10.000",
                                "stringer", 10,
                                "below_stringer", "6.000",
                                "side", "L"),
                        "measurements",
                        json(
                                "depth",
                                depth,
                                "along",
                                "3.000",
                                "around",
                                "2.000",
                                "sharp_bottom",
                                false,
                                "cracked",
                                false)),
                Desk.headersFor("mechanic"));
        assertThat(answer.status()).as(answer.text()).isEqualTo(201);
        return answer.json().get("reference").asText();
    }

    /** Walk the disposition by the routes that exist for each step. */
    private static Client.Answer move(Client api, String reference, String state, String on) {
        return api.post("/findings/" + reference + "/state", json("state", state, "on", on, "by", "a.okonkwo"));
    }

    private static Map<String, Object> raisedOrder(String reference) {
        return json("against", reference, "question", QUESTION, "raised", FOUND, "by", "a.okonkwo");
    }

    private static Map<String, Object> deferral(String reference, String granted) {
        return json("against", reference, "granted", granted, "by", "j.mercer", "limit", "300 FC");
    }

    private static Map<String, Object> repair(String reference, Object... rest) {
        Map<String, Object> sent = json(
                "against", reference,
                "sequence", 1,
                "along", "3.000",
                "around", "2.000",
                "centre_along", "468.000",
                "centre_around", "91.000",
                "embodied", FITTED,
                "by", "s.brandt");
        sent.putAll(json(rest));
        return sent;
    }

    @Test
    void theWholeLifeOfOneCard() {
        Client api = api();
        enrol(api);
        String reference = raiseCard(api);

        // It is open, it is on the fleet report, and the book does not allow it.
        assertThat(api.get("/findings/" + reference).json().get("revision").asInt())
                .isEqualTo(1);
        assertThat(api.get("/reports/open", Map.of("on", FOUND))
                        .json()
                        .get("total")
                        .asInt())
                .isEqualTo(1);
        JsonNode verdict = api.get("/findings/" + reference + "/assessment", Map.of("on", FOUND))
                .json();
        assertThat(verdict.get("verdict").get("disposition").asText()).isNotEqualTo("allowable as it stands");

        // Nothing is due yet, because nothing has a limit on it.
        assertThat(api.get("/reports/due/5127", Map.of("on", FOUND)).json().get("items"))
                .hasToString("[]");

        // Somebody looks it up, which is a move of its own on a route of its own.
        Client.Answer assessed = move(api, reference, "assessed", FOUND);
        assertThat(assessed.status()).as(assessed.text()).isEqualTo(200);
        assertThat(assessed.json().get("state").asText()).isEqualTo("assessed");

        // It goes out to the manufacturer, which holds the card.
        Client.Answer order = api.post("/orders", raisedOrder(reference));
        assertThat(order.status()).as(order.text()).isEqualTo(201);
        String number = order.json().get("reference").asText();
        assertThat(api.get("/orders").json().get("total").asInt()).isEqualTo(1);

        // Nothing can be deferred while it is held.
        Client.Answer refused = api.post("/deferrals", deferral(reference, DEFERRED));
        assertThat(refused.status()).isEqualTo(409);

        // The answer comes back approved, which releases the card.
        api.post("/orders/" + number + "/sent", json("on", SENT));
        Client.Answer approved = api.post(
                "/orders/" + number + "/approved",
                json(
                        "on", ANSWERED,
                        "by", "a.okonkwo",
                        "sheet", "RAS-2025-4417",
                        "category", "permanent with a repeat inspection",
                        "conditions", List.of("inspect at every C check"),
                        "valid_until", "2027-01-01"));
        assertThat(approved.status()).as(approved.text()).isEqualTo(200);
        assertThat(api.get("/orders").json().get("total").asInt()).isZero();

        // Now it can be deferred, and it lands on the due list and the work pack.
        Client.Answer granted = api.post("/deferrals", deferral(reference, ANSWERED));
        assertThat(granted.status()).as(granted.text()).isEqualTo(201);
        assertThat(api.get("/reports/due/5127", Map.of("on", ANSWERED)).json().get("items"))
                .hasSize(1);
        JsonNode pack = api.get("/checks/5127/pack", Map.of("opens", FITTED, "closes", "2026-06-17", "within", 365))
                .json();
        assertThat(pack.get("deferrals")).hasSize(1);

        // A repair goes on, which closes the card and lifts the deferral.
        Client.Answer fitted = api.post("/repairs", repair(reference, "temporary", true));
        assertThat(fitted.status()).as(fitted.text()).isEqualTo(201);
        assertThat(fitted.json().get("card_state").asText()).isEqualTo("in work");
        assertThat(fitted.json().get("deferral_lifted").asBoolean()).isTrue();
        assertThat(api.get("/deferrals").json().get("total").asInt()).isZero();

        // The repair carries a repeat inspection, which nobody has done yet.
        JsonNode tasks = api.get("/inspections/aircraft/5127").json();
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).get("last_done").isNull()).isTrue();

        // It gets signed off at the counters it was done at.
        Client.Answer signed = api.post(
                "/inspections/53/1/done", json("on", "2027-01-05", "flown", json("cycles", 16000, "hours", "28000.0")));
        assertThat(signed.status()).as(signed.text()).isEqualTo(200);
        assertThat(signed.json().get("at_last_done").get("cycles").asInt()).isEqualTo(16000);

        // The audit now sees a temporary repair that has outlived its life.
        JsonNode audit = api.get("/audit/5127", Map.of("on", "2028-01-01")).json();
        assertThat(audit.get("total_repairs").asInt()).isEqualTo(1);
        assertThat(audit.get("temporary").get(0).get("overdue").asBoolean()).isTrue();
        assertThat(audit.get("clean").asBoolean()).isFalse();

        // And the whole story reads back in order, off every register.
        JsonNode story = api.get("/history/" + reference).json();
        List<String> cameFrom = new ArrayList<>();
        story.get("entries").forEach(one -> cameFrom.add(one.get("came_from").asText()));
        assertThat(cameFrom.get(0)).isEqualTo("card");
        assertThat(cameFrom).contains("engineering");
        assertThat(cameFrom).contains("repair");
        assertThat(story.get("days_open").asInt()).isGreaterThan(80);
    }

    @Test
    void theCardIsNoLongerOpenOnceTheRepairIsOn() {
        Client api = api();
        enrol(api);
        String reference = raiseCard(api);
        move(api, reference, "assessed", FOUND);
        api.post("/orders", raisedOrder(reference));
        api.post("/orders/EO-2025-0001/sent", json("on", SENT));
        api.post(
                "/orders/EO-2025-0001/approved",
                json("on", ANSWERED, "by", "a.okonkwo", "sheet", "RAS-2025-4417", "category", "permanent"));
        api.post("/repairs", repair(reference));
        JsonNode openCards =
                api.get("/aircraft/5127/findings", Map.of("open_only", true)).json();
        assertThat(openCards.get("total").asInt()).isEqualTo(1);
        assertThat(api.get("/reports/due/5127", Map.of("on", FITTED)).json().get("items"))
                .hasToString("[]");
    }

    @Test
    void aShallowDentNeverNeedsAnyOfThat() {
        Client api = api();
        enrol(api);
        String reference = raiseCard(api, "0.005");
        JsonNode verdict = api.get("/findings/" + reference + "/assessment", Map.of("on", FOUND))
                .json();
        assertThat(verdict.get("verdict").get("disposition").asText()).isEqualTo("allowable as it stands");
        assertThat(api.get("/reports/grounded", Map.of("on", FOUND)).json().get("tails"))
                .hasToString("[]");
        assertThat(api.get("/audit/5127", Map.of("on", FOUND))
                        .json()
                        .get("clean")
                        .asBoolean())
                .isTrue();
    }

    @Test
    void theFleetReportAgreesWithTheRegisterAtEveryStep() {
        Client api = api();
        enrol(api);
        raiseCard(api);
        raiseCard(api);
        JsonNode counted = api.get("/reports/open/5127", Map.of("on", FOUND)).json();
        JsonNode listed = api.get("/aircraft/5127/findings").json();
        assertThat(counted.get("total").asInt()).isEqualTo(listed.get("total").asInt());
        assertThat(listed.get("total").asInt()).isEqualTo(2);
    }

    @Test
    void aMoveTheLifecycleForbidsIsRefused() {
        Client api = api();
        enrol(api);
        String reference = raiseCard(api);
        Client.Answer answer = move(api, reference, "closed", FOUND);
        assertThat(answer.status()).isEqualTo(409);
        assertThat(answer.json().get("detail").asText()).contains("cannot go from raised to closed");
    }

    @Test
    void aStateThatIsNotOneIsRefused() {
        Client api = api();
        enrol(api);
        String reference = raiseCard(api);
        Client.Answer answer = move(api, reference, "sorted out", FOUND);
        assertThat(answer.status()).isEqualTo(422);
        assertThat(answer.json().get("detail").asText()).contains("not a disposition state");
    }

    @Test
    void aMechanicMayNotMoveACard() {
        Client api = api();
        enrol(api);
        String reference = raiseCard(api);
        Client.Answer answer = api.post(
                "/findings/" + reference + "/state",
                json("state", "assessed", "on", FOUND, "by", "a.okonkwo"),
                Desk.headersFor("mechanic"));
        assertThat(answer.status()).isEqualTo(422);
    }

    @Test
    void theStateCanBeReadBackWithItsMoves() {
        Client api = api();
        enrol(api);
        String reference = raiseCard(api);
        move(api, reference, "assessed", FOUND);
        JsonNode body = api.get("/findings/" + reference + "/state").json();
        assertThat(body.get("state").asText()).isEqualTo("assessed");
        assertThat(body.get("open").asBoolean()).isTrue();
        assertThat(body.get("moves")).hasSize(1);
    }

    @Test
    void aFreshlyRaisedCardHasNoMovesYet() {
        Client api = api();
        enrol(api);
        String reference = raiseCard(api);
        JsonNode body = api.get("/findings/" + reference + "/state").json();
        assertThat(body.get("state").asText()).isEqualTo("raised");
        assertThat(body.get("moves")).hasToString("[]");
    }

    @Test
    void aCardThatIsNotOnFileHasNoState() {
        Client api = api();
        enrol(api);
        assertThat(api.get("/findings/DR-2025-000099/state").status()).isEqualTo(404);
    }
}
